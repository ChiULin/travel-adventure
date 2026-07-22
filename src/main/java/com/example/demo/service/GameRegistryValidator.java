package com.example.demo.service;

import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GameRegistryValidator implements ApplicationRunner {

    private final LandmarkStageRegistry stageRegistry;
    private final ExplorationMissionRegistry explorationRegistry;
    private final ImageRecognitionRegistry imageRegistry;
    private final SceneRepository sceneRepository;

    public GameRegistryValidator(
            LandmarkStageRegistry stageRegistry,
            ExplorationMissionRegistry explorationRegistry,
            ImageRecognitionRegistry imageRegistry,
            SceneRepository sceneRepository) {
        this.stageRegistry = stageRegistry;
        this.explorationRegistry = explorationRegistry;
        this.imageRegistry = imageRegistry;
        this.sceneRepository = sceneRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        stageRegistry.findAll().forEach(stage -> validateTarget(
                "landmark stage", stage.cityId(), stage.landmarkId()));

        explorationRegistry.findAll().forEach(mission -> {
            validateTarget("exploration mission " + mission.missionKey(),
                    mission.cityId(), mission.targetSceneId());
            mission.candidateSceneIds().forEach(sceneId -> validateSceneExists(
                    "exploration mission " + mission.missionKey(), sceneId));
        });

        imageRegistry.findAll().forEach(challenge -> {
            validateTarget("image challenge " + challenge.challengeKey(),
                    challenge.cityId(), challenge.targetSceneId());
            challenge.candidateSceneIds().forEach(sceneId -> validateSceneExists(
                    "image challenge " + challenge.challengeKey(), sceneId));
        });
    }

    private void validateTarget(String source, Long cityId, Long sceneId) {
        if (!sceneRepository.existsByIdAndCityId(sceneId, cityId)) {
            throw new IllegalStateException(
                    "%s references scene %d outside city %d".formatted(source, sceneId, cityId));
        }
    }

    private void validateSceneExists(String source, Long sceneId) {
        if (!sceneRepository.existsById(sceneId)) {
            throw new IllegalStateException(
                    "%s references missing scene %d".formatted(source, sceneId));
        }
    }
}
