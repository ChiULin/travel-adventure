package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ImageRecognitionRegistry {
    private final List<ImageRecognitionDefinition> definitions = List.of(
            new ImageRecognitionDefinition(
                    "TAIPEI-PALACE-MUSEUM-IMAGE",
                    1L,
                    2L,
                    "觀察屋頂與建築輪廓，找出正確的台灣景點。",
                    "/images/landmarks/national-palace-museum.png",
                    List.of(2L, 1L, 3L, 5L),
                    "國立故宮博物院以傳統宮殿式建築與典藏大量中華文物聞名。"
            )
    );

    public Optional<ImageRecognitionDefinition> findByTargetSceneId(Long sceneId) {
        return definitions.stream()
                .filter(definition -> definition.targetSceneId().equals(sceneId))
                .findFirst();
    }

    public List<ImageRecognitionDefinition> findAll() {
        return definitions;
    }
}
