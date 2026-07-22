package com.example.demo.service.food;

import java.time.Instant;
import java.util.List;

public final class IssuedFoodChallenge {

    private final String questionId;
    private final Long userId;
    private final Long cityId;
    private final String foodKey;
    private final String correctAnswer;
    private final String question;
    private final List<String> options;
    private final Instant expiresAt;
    private boolean used;

    public IssuedFoodChallenge(String questionId, Long userId, Long cityId,
                               String foodKey, String correctAnswer, String question,
                               List<String> options, Instant expiresAt) {
        this.questionId = questionId;
        this.userId = userId;
        this.cityId = cityId;
        this.foodKey = foodKey;
        this.correctAnswer = correctAnswer;
        this.question = question;
        this.options = List.copyOf(options);
        this.expiresAt = expiresAt;
    }

    public String questionId() {
        return questionId;
    }

    public Long userId() {
        return userId;
    }

    public Long cityId() {
        return cityId;
    }

    public String foodKey() {
        return foodKey;
    }

    public String correctAnswer() {
        return correctAnswer;
    }

    public String question() {
        return question;
    }

    public List<String> options() {
        return options;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean used() {
        return used;
    }

    public void markUsed() {
        used = true;
    }
}
