package com.example.demo.service;

import java.util.List;
import java.util.Objects;

public record ImageRecognitionDefinition(
        String challengeKey,
        Long cityId,
        Long targetSceneId,
        String prompt,
        String imageUrl,
        List<Long> candidateSceneIds,
        String cultureExplanation
) {
    public ImageRecognitionDefinition {
        Objects.requireNonNull(challengeKey, "challengeKey");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(targetSceneId, "targetSceneId");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(imageUrl, "imageUrl");
        candidateSceneIds = List.copyOf(candidateSceneIds);
        Objects.requireNonNull(cultureExplanation, "cultureExplanation");
        if (challengeKey.isBlank() || prompt.isBlank() || imageUrl.isBlank() || cultureExplanation.isBlank()) {
            throw new IllegalArgumentException("圖片辨識定義不可包含空白內容");
        }
        if (candidateSceneIds.size() < 4
                || candidateSceneIds.stream().distinct().count() != candidateSceneIds.size()
                || !candidateSceneIds.contains(targetSceneId)) {
            throw new IllegalArgumentException("圖片辨識必須包含目標景點與至少四個不同候選景點");
        }
    }
}
