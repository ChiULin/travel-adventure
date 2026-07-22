package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BossStartResponse(
        String difficulty,
        int baseQuestionSeconds,
        int questionSeconds,
        ActiveFood activeFood,
        Battle battle,
        Map<String, Object> question
) {
    public record ActiveFood(
            String foodKey,
            String name,
            String effectType,
            int effectValue,
            String description
    ) {
    }

    public record Battle(
            int playerLives,
            int combo
    ) {
    }
}
