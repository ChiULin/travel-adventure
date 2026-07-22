package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.BattleResultRequest;
import com.example.demo.dto.BossChallengeRequest;
import com.example.demo.dto.BossStartResponse;
import com.example.demo.service.BossService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cities")
public class BossController {

    private final BossService bossService;
    private final UserService userService;

    public BossController(BossService bossService, UserService userService) {
        this.bossService = bossService;
        this.userService = userService;
    }

    @PostMapping("/{cityId}/boss/start")
    public ResponseEntity<ApiResponse<BossStartResponse>> start(
            @PathVariable Long cityId,
            @RequestBody(required = false) BossChallengeRequest request,
            Authentication authentication) {
        BossStartResponse result = bossService.startChallenge(
                userService.userIdFor(authentication.getName()),
                cityId,
                request == null ? null : request.difficulty(),
                request == null ? null : request.foodKey()
        );
        return ResponseEntity.ok(ApiResponse.success("守護者挑戰開始", result));
    }

    @PostMapping("/{cityId}/boss/challenge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> challenge(
            @PathVariable Long cityId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            String answer = body == null ? null : body.get("answer");
            String answerText = body == null ? null : body.get("answerText");
            String questionId = body == null ? null : body.get("questionId");
            String difficulty = body == null ? null : body.get("difficulty");
            Map<String, Object> result = bossService.challengeResult(
                    userService.userIdFor(authentication.getName()), cityId,
                    answer, answerText, questionId, difficulty);
            String message = Boolean.TRUE.equals(result.get("win")) ? "守護者挑戰成功" : "守護者挑戰失敗";
            return ResponseEntity.ok(ApiResponse.success(message, result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{cityId}/battle-result")
    public ResponseEntity<ApiResponse<Map<String, Object>>> battleResult(
            @PathVariable Long cityId,
            @RequestBody BattleResultRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.success("戰鬥結果已記錄",
                    bossService.recordBattleResult(
                            userService.userIdFor(authentication.getName()), cityId, request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{cityId}/restart")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restart(
            @PathVariable Long cityId, Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.success("城市挑戰已重設",
                    bossService.restartCity(userService.userIdFor(authentication.getName()), cityId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
