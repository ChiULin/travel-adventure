package com.example.demo.service;

import com.example.demo.repository.CheckinRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExplorationService {
    private static final Long TAINAN_CITY_ID = 3L;

    private static final ExplorationMission ANPING_MISSION = new ExplorationMission(
            "TAINAN-ANPING-01",
            "尋找海港旁的古老城堡",
            "找出符合旅行委託的景點。",
            List.of(
                    "這座景點位於台南安平一帶。",
                    "它曾是荷蘭人在台灣的重要據點。",
                    "現場可看到紅磚城牆與古老遺跡。"
            ),
            List.of(
                    new SceneOption(7L, "赤崁樓"),
                    new SceneOption(8L, "安平古堡"),
                    new SceneOption(9L, "億載金城")
            ),
            8L
    );

    private final CheckinRepository checkinRepository;

    public ExplorationService(CheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    public ExplorationMissionView randomMission(Long userId, Long cityId) {
        if (!TAINAN_CITY_ID.equals(cityId)) {
            throw new IllegalArgumentException("目前只有台南探索試作任務");
        }
        boolean completed = checkinRepository.findByUserIdAndSceneId(userId, ANPING_MISSION.correctSceneId())
                .map(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .orElse(false);
        if (completed) {
            throw new IllegalArgumentException("目前沒有未完成的探索委託");
        }
        return ANPING_MISSION.toView();
    }

    public ExplorationGuessResult guess(String missionId, Long sceneId) {
        if (!ANPING_MISSION.missionId().equals(missionId)) {
            throw new IllegalArgumentException("找不到探索任務");
        }
        SceneOption selected = ANPING_MISSION.candidates().stream()
                .filter(candidate -> candidate.sceneId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("候選景點不在這次委託中"));
        return new ExplorationGuessResult(
                ANPING_MISSION.correctSceneId().equals(sceneId),
                selected.sceneId(),
                selected.name()
        );
    }

    private record ExplorationMission(
            String missionId,
            String title,
            String description,
            List<String> clues,
            List<SceneOption> candidates,
            Long correctSceneId
    ) {
        private ExplorationMissionView toView() {
            return new ExplorationMissionView(missionId, title, description, clues, candidates);
        }
    }

    public record ExplorationMissionView(
            String missionId,
            String title,
            String description,
            List<String> clues,
            List<SceneOption> candidates
    ) {
    }

    public record SceneOption(Long sceneId, String name) {
    }

    public record ExplorationGuessResult(boolean correct, Long sceneId, String sceneName) {
    }
}
