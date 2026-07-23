package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GameRegistryValidator implements ApplicationRunner {

    private final LandmarkStageRegistry stageRegistry;
    private final ExplorationMissionRegistry explorationRegistry;
    private final VisualChallengeRegistry visualRegistry;
    private final LandmarkChallengePoolRegistry challengePoolRegistry;
    private final SceneRepository sceneRepository;
    private final CityRepository cityRepository;
    private final ResourceLoader resourceLoader;

    public GameRegistryValidator(
            LandmarkStageRegistry stageRegistry,
            ExplorationMissionRegistry explorationRegistry,
            VisualChallengeRegistry visualRegistry,
            LandmarkChallengePoolRegistry challengePoolRegistry,
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            ResourceLoader resourceLoader) {
        this.stageRegistry = stageRegistry;
        this.explorationRegistry = explorationRegistry;
        this.visualRegistry = visualRegistry;
        this.challengePoolRegistry = challengePoolRegistry;
        this.sceneRepository = sceneRepository;
        this.cityRepository = cityRepository;
        this.resourceLoader = resourceLoader;
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
        visualRegistry.findAll().forEach((key, definition) ->
                validateVisualDefinition(key, definition, citiesByOrder));
        challengePoolRegistry.findAll().forEach((stageKey, pool) ->
                validateVisualChallenge(stageKey, pool));
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

    private void validateVisualDefinition(
            VisualChallengeKey key,
            VisualChallengeDefinition definition,
            Map<Integer, City> citiesByOrder
    ) {
        List<VisualChallengeKey> candidates = definition.candidateStages();
        if (definition.challengeKey().isBlank()) {
            throw new IllegalStateException(key + " 缺少視覺挑戰識別");
        }
        if (candidates.size() != 4
                || candidates.stream().distinct().count() != candidates.size()) {
            throw new IllegalStateException(key + " 必須設定四個不重複的視覺候選景點");
        }
        if (!candidates.contains(definition.correctStage())) {
            throw new IllegalStateException(key + " 的正確景點不在視覺候選清單中");
        }
        if (!key.equals(definition.correctStage())) {
            throw new IllegalStateException(key + " 的正確景點必須是目前關卡");
        }
        candidates.forEach(stageKey -> validateVisualStage(
                "visual challenge " + definition.challengeKey(),
                stageKey,
                citiesByOrder));
    }

    private void validateVisualChallenge(
            LandmarkStageKey stageKey,
            List<MysteryChallengeType> pool
    ) {
        boolean usesFocusImage = pool.contains(MysteryChallengeType.IMAGE_RECOGNITION);
        boolean usesPuzzleImage = pool.contains(MysteryChallengeType.PUZZLE);
        if (!usesFocusImage && !usesPuzzleImage) {
            return;
        }

        VisualChallengeKey key =
                new VisualChallengeKey(stageKey.cityOrder(), stageKey.stageOrder());
        VisualChallengeDefinition definition = visualRegistry.findRequired(key);
        if (usesFocusImage) {
            validateAsset(key, "重點辨識圖片", definition.focusImageUrl());
            if (definition.focusPrompt().isBlank() || definition.cultureExplanation().isBlank()) {
                throw new IllegalStateException(key + " 缺少圖片辨識文字設定");
            }
        }
        if (usesPuzzleImage) {
            validateAsset(key, "拼圖圖片", definition.puzzleImageUrl());
        }
    }

    private void validateAsset(
            VisualChallengeKey key,
            String assetType,
            String imageUrl
    ) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalStateException(key + " 缺少" + assetType);
        }
        if (!resourceLoader.getResource("classpath:/static" + imageUrl).exists()) {
            throw new IllegalStateException(key + " 的" + assetType + "不存在：" + imageUrl);
        }
    }

    private void validateVisualStage(
            String source,
            VisualChallengeKey stageKey,
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
