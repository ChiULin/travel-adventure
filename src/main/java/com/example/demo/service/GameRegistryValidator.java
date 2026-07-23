package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GameRegistryValidator implements ApplicationRunner {

    private final LandmarkStageRegistry stageRegistry;
    private final ExplorationMissionRegistry explorationRegistry;
    private final ImageRecognitionRegistry imageRegistry;
    private final SceneRepository sceneRepository;
    private final CityRepository cityRepository;

    public GameRegistryValidator(
            LandmarkStageRegistry stageRegistry,
            ExplorationMissionRegistry explorationRegistry,
            ImageRecognitionRegistry imageRegistry,
            SceneRepository sceneRepository,
            CityRepository cityRepository) {
        this.stageRegistry = stageRegistry;
        this.explorationRegistry = explorationRegistry;
        this.imageRegistry = imageRegistry;
        this.sceneRepository = sceneRepository;
        this.cityRepository = cityRepository;
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

        Map<Integer, City> citiesByOrder = cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                .collect(Collectors.toMap(City::getUnlockOrder, Function.identity()));
        imageRegistry.findAll().forEach(challenge ->
                challenge.candidateStages().forEach(stageKey ->
                        validateImageStage(
                                "image challenge " + challenge.challengeKey(),
                                stageKey,
                                citiesByOrder)));
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

    private void validateImageStage(
            String source,
            LandmarkStageKey stageKey,
            Map<Integer, City> citiesByOrder
    ) {
        City city = citiesByOrder.get(stageKey.cityOrder());
        if (city == null) {
            throw new IllegalStateException(
                    "%s references missing city order %d".formatted(source, stageKey.cityOrder()));
        }
        LandmarkStageDefinition stage = stageRegistry.findByCityId(city.getId()).stream()
                .filter(candidate -> candidate.stageOrder() == stageKey.stageOrder())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "%s references missing stage %s".formatted(source, stageKey)));
        validateTarget(source, city.getId(), stage.landmarkId());
    }
}
