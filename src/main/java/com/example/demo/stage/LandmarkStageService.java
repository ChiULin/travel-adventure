package com.example.demo.stage;

import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class LandmarkStageService {

    private final LandmarkStageRegistry stageRegistry;
    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;

    public LandmarkStageService(
            LandmarkStageRegistry stageRegistry,
            CheckinRepository checkinRepository,
            UserProgressRepository userProgressRepository
    ) {
        this.stageRegistry = stageRegistry;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
    }

    public LandmarkStageStatus getStageStatus(Long userId, Long landmarkId) {
        LandmarkStageDefinition current = stageRegistry.findByLandmarkId(landmarkId)
                .orElseThrow(() -> new IllegalArgumentException("找不到景點關卡設定"));

        if (isCompleted(userId, landmarkId)) {
            return statusOf(current, StageStatus.COMPLETED);
        }

        if (current.stageOrder() == 1) {
            boolean cityUnlocked = userProgressRepository
                    .findByUserIdAndCityId(userId, current.cityId())
                    .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                    .orElse(false);

            return statusOf(current, cityUnlocked ? StageStatus.AVAILABLE : StageStatus.LOCKED);
        }

        LandmarkStageDefinition previous = stageRegistry.findByCityId(current.cityId()).stream()
                .filter(stage -> stage.stageOrder() == current.stageOrder() - 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到上一個關卡"));

        StageStatus status = isCompleted(userId, previous.landmarkId())
                ? StageStatus.AVAILABLE
                : StageStatus.LOCKED;
        return statusOf(current, status);
    }

    public void validateStageAvailable(Long userId, Long landmarkId) {
        LandmarkStageStatus stage = getStageStatus(userId, landmarkId);
        if (stage.status() == StageStatus.LOCKED) {
            throw new IllegalStateException("請先完成上一個景點關卡");
        }
    }

    private boolean isCompleted(Long userId, Long landmarkId) {
        return checkinRepository.existsByUserIdAndSceneIdAndCompletedTrue(userId, landmarkId);
    }

    private LandmarkStageStatus statusOf(LandmarkStageDefinition stage, StageStatus status) {
        return new LandmarkStageStatus(
                stage.landmarkId(),
                stage.cityId(),
                stage.stageOrder(),
                status
        );
    }
}
