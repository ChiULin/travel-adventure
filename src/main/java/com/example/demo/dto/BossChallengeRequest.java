package com.example.demo.dto;

import com.example.demo.service.GameDifficulty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BossChallengeRequest(
        GameDifficulty difficulty,
        String foodKey
) {
}
