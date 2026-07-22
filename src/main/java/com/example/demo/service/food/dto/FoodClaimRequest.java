package com.example.demo.service.food.dto;

import jakarta.validation.constraints.NotBlank;

public record FoodClaimRequest(
        @NotBlank(message = "題目代碼不可空白") String questionId,
        @NotBlank(message = "答案不可空白") String answer
) {
}
