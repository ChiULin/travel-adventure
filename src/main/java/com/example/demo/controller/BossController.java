package com.example.demo.controller;

import com.example.demo.dto.BattleResultRequest;
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

    @PostMapping("/{cityId}/boss/challenge")
    public ResponseEntity<?> challenge(@PathVariable Long cityId, @RequestBody(required = false) Map<String, String> body,
                                       Authentication authentication) {
        try {
            String answer = body == null ? null : body.get("answer");
            String answerText = body == null ? null : body.get("answerText");
            String questionId = body == null ? null : body.get("questionId");
            String difficulty = body == null ? null : body.get("difficulty");
            return ResponseEntity.ok(bossService.challengeResult(userService.userIdFor(authentication.getName()), cityId,
                    answer, answerText, questionId, difficulty));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{cityId}/battle-result")
    public ResponseEntity<?> battleResult(@PathVariable Long cityId, @RequestBody BattleResultRequest request,
                                          Authentication authentication) {
        try {
            return ResponseEntity.ok(bossService.recordBattleResult(userService.userIdFor(authentication.getName()), cityId, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{cityId}/restart")
    public ResponseEntity<?> restart(@PathVariable Long cityId, Authentication authentication) {
        try {
            return ResponseEntity.ok(bossService.restartCity(userService.userIdFor(authentication.getName()), cityId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
