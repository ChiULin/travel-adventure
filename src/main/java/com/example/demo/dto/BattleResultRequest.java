package com.example.demo.dto;

import lombok.Data;

@Data
public class BattleResultRequest {
    private String battleResultToken;
    private String rank;
    private Integer maxCombo;
    private Integer remainingLives;
    private Integer correctAnswers;
    private Integer wrongAnswers;
    private Integer timeoutCount;
    private Integer earnedExp;
    private Integer earnedCoins;
    private String difficulty;
}
