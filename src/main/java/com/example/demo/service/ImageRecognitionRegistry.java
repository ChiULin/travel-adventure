package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ImageRecognitionRegistry {
    private final List<ImageRecognitionDefinition> definitions = List.of(
            new ImageRecognitionDefinition(
                    "TAIPEI-101-FOCUS-IMAGE",
                    new LandmarkStageKey(1, 1),
                    "旅行筆記中出現了一張景點局部照片，觀察建築輪廓與外牆特徵。",
                    "/images/challenges/taipei101-focus.jpg",
                    List.of(
                            new LandmarkStageKey(1, 1),
                            new LandmarkStageKey(2, 2),
                            new LandmarkStageKey(4, 3),
                            new LandmarkStageKey(6, 2)
                    ),
                    "臺北 101 的竹節式外觀與層層收分設計，融合傳統意象與現代摩天建築技術。"
            ),
            new ImageRecognitionDefinition(
                    "TAIPEI-PALACE-MUSEUM-IMAGE",
                    new LandmarkStageKey(1, 2),
                    "觀察屋頂與建築輪廓，找出正確的台灣景點。",
                    "/images/landmarks/national-palace-museum.png",
                    List.of(
                            new LandmarkStageKey(1, 2),
                            new LandmarkStageKey(1, 1),
                            new LandmarkStageKey(1, 3),
                            new LandmarkStageKey(2, 2)
                    ),
                    "國立故宮博物院以傳統宮殿式建築與典藏大量中華文物聞名。"
            )
    );

    public Optional<ImageRecognitionDefinition> findByStage(int cityOrder, int stageOrder) {
        LandmarkStageKey stageKey = new LandmarkStageKey(cityOrder, stageOrder);
        return definitions.stream()
                .filter(definition -> definition.targetStage().equals(stageKey))
                .findFirst();
    }

    public List<ImageRecognitionDefinition> findAll() {
        return definitions;
    }
}
