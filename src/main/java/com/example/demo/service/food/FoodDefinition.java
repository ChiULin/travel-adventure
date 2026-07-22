package com.example.demo.service.food;

import java.util.List;

public record FoodDefinition(
        String foodKey,
        Long cityId,
        String cityName,
        String name,
        String shortDescription,
        String fullDescription,
        boolean signatureFood,
        FoodEffectType effectType,
        int effectValue,
        String challengeQuestion,
        List<String> challengeOptions,
        String correctAnswer,
        String explanation
) {
}
