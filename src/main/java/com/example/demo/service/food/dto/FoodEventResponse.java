package com.example.demo.service.food.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FoodEventResponse(
        boolean available,
        boolean claimed,
        int requiredCheckins,
        long completedCheckins,
        long remainingCheckins,
        String foodKey,
        String cityName,
        String name,
        String shortDescription,
        FoodEffectResponse effect,
        FoodChallengeResponse challenge
) {
    public static FoodEventResponse unavailable(int requiredCheckins, long completedCheckins) {
        return new FoodEventResponse(
                false,
                false,
                requiredCheckins,
                completedCheckins,
                Math.max(0, requiredCheckins - completedCheckins),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
