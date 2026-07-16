package com.example.demo.dto;

import lombok.Data;

@Data
public class CheckinRequest {
    private Long sceneId;
    private String answer;
    private String answerText;
    private String questionId;
    private String difficulty;
}
