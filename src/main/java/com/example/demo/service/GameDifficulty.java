package com.example.demo.service;

public enum GameDifficulty {
    CASUAL(10, 5, 1.0),
    NORMAL(5, 3, 1.2),
    EXTREME(3, 1, 1.5);

    private final int seconds;
    private final int lives;
    private final double rewardMultiplier;

    GameDifficulty(int seconds, int lives, double rewardMultiplier) {
        this.seconds = seconds;
        this.lives = lives;
        this.rewardMultiplier = rewardMultiplier;
    }

    public int seconds() {
        return seconds;
    }

    public int lives() {
        return lives;
    }

    public int rewardPercent() {
        return (int) Math.round(rewardMultiplier * 100);
    }

    public int reward(int baseReward) {
        return (int) Math.round(Math.max(0, baseReward) * rewardMultiplier);
    }

    public static GameDifficulty from(String value) {
        if (value == null || value.isBlank()) {
            return CASUAL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid difficulty");
        }
    }
}
