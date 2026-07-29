package com.example.demo.stage;

public record LandmarkStageStatus(
        Long landmarkId,
        Long cityId,
        int stageOrder,
        StageStatus status
) {
}
