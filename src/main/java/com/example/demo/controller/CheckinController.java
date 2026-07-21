package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.CheckinRequest;
import com.example.demo.service.CheckinService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final CheckinService checkinService;
    private final UserService userService;

    public CheckinController(CheckinService checkinService, UserService userService) {
        this.checkinService = checkinService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkin(
            @RequestBody CheckinRequest req, Authentication authentication) {
        try {
            var checkin = checkinService.checkin(userService.userIdFor(authentication.getName()), req.getSceneId(),
                    req.getAnswer(), req.getAnswerText(), req.getQuestionId(), req.getDifficulty());
            if (!Boolean.TRUE.equals(checkin.getCompleted())) {
                return ResponseEntity.ok(ApiResponse.success("答案錯誤", Map.of(
                        "ok", false,
                        "correct", false
                )));
            }
            return ResponseEntity.ok(ApiResponse.success("打卡成功", Map.of(
                    "ok", true,
                    "earnedExp", checkin.getEarnedExp(),
                    "earnedCoins", checkin.getEarnedCoins()
            )));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
