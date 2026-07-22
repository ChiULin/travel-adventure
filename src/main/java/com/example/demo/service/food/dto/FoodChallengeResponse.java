package com.example.demo.service.food.dto;

import java.time.Instant;
import java.util.List;

public record FoodChallengeResponse(
        String questionId,
        String question,
        List<String> options,
        Instant expiresAt
) {
}
