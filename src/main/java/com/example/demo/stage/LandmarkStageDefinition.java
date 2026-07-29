package com.example.demo.stage;

public record LandmarkStageDefinition(
        Long cityId,
        Long landmarkId,
        int stageOrder
) {
}
