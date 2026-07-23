package com.example.demo.service;

public record VisualChallengeKey(
        int cityOrder,
        int stageOrder
) {
    public VisualChallengeKey {
        if (cityOrder < 1 || stageOrder < 1) {
            throw new IllegalArgumentException("視覺挑戰的城市與關卡順序必須大於零");
        }
    }

    public LandmarkStageKey toLandmarkStageKey() {
        return new LandmarkStageKey(cityOrder, stageOrder);
    }
}
