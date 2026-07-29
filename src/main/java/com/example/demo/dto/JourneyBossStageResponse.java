package com.example.demo.dto;

import com.example.demo.stage.StageStatus;

public record JourneyBossStageResponse(
        Long cityId,
        String bossName,
        int stageOrder,
        String stageLabel,
        StageStatus stageStatus,
        String actionLabel
) {
}
