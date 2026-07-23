package com.example.demo.service;

import java.util.List;
import java.util.Objects;

public record ImageRecognitionDefinition(
        String challengeKey,
        LandmarkStageKey targetStage,
        String prompt,
        String imageUrl,
        List<LandmarkStageKey> candidateStages,
        String cultureExplanation
) {
    public ImageRecognitionDefinition {
        Objects.requireNonNull(challengeKey, "challengeKey");
        Objects.requireNonNull(targetStage, "targetStage");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(imageUrl, "imageUrl");
        candidateStages = List.copyOf(candidateStages);
        Objects.requireNonNull(cultureExplanation, "cultureExplanation");
        if (challengeKey.isBlank() || prompt.isBlank() || imageUrl.isBlank() || cultureExplanation.isBlank()) {
            throw new IllegalArgumentException("圖片辨識定義不可包含空白內容");
        }
        if (candidateStages.size() < 4
                || candidateStages.stream().distinct().count() != candidateStages.size()
                || !candidateStages.contains(targetStage)) {
            throw new IllegalArgumentException("圖片辨識必須包含目標景點與至少四個不同候選景點");
        }
    }
}
