package com.example.demo.dto;

import java.util.Map;

public record BossStartResponse(
        String difficulty,
        int questionSeconds,
        int playerLives,
        int combo,
        Map<String, Object> question
) {
}
