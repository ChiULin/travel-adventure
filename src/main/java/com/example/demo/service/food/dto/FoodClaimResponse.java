package com.example.demo.service.food.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FoodClaimResponse(
        boolean correct,
        String foodKey,
        String name,
        String effectType,
        Integer effectValue,
        String effectDescription,
        String explanation
) {
    public static FoodClaimResponse incorrect() {
        return new FoodClaimResponse(false, null, null, null, null, null, null);
    }
}
