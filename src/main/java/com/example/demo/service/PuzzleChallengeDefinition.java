package com.example.demo.service;

import java.util.List;
import java.util.Objects;

public record PuzzleChallengeDefinition(
        String challengeKey,
        LandmarkStageKey targetStage,
        String imageUrl,
        List<LandmarkStageKey> candidateStages,
        LandmarkStageKey correctStage
) {
    public PuzzleChallengeDefinition {
        Objects.requireNonNull(challengeKey, "challengeKey");
        Objects.requireNonNull(targetStage, "targetStage");
        Objects.requireNonNull(imageUrl, "imageUrl");
        Objects.requireNonNull(correctStage, "correctStage");
        candidateStages = List.copyOf(candidateStages);
        if (challengeKey.isBlank() || imageUrl.isBlank()) {
            throw new IllegalArgumentException("拼圖挑戰定義不可包含空白內容");
        }
        if (candidateStages.size() != 4
                || candidateStages.stream().distinct().count() != candidateStages.size()
                || !candidateStages.contains(correctStage)
                || !targetStage.equals(correctStage)) {
            throw new IllegalArgumentException("拼圖挑戰必須包含目標景點與四個不同候選景點");
        }
    }
}
