package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.QuizQuestionService;
import com.example.demo.service.MysteryChallengeService;
import com.example.demo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {
    private final QuizQuestionService quizQuestionService;
    private final UserService userService;
    private final MysteryChallengeService mysteryChallengeService;

    public QuizController(
            QuizQuestionService quizQuestionService,
            UserService userService,
            MysteryChallengeService mysteryChallengeService
    ) {
        this.quizQuestionService = quizQuestionService;
        this.userService = userService;
        this.mysteryChallengeService = mysteryChallengeService;
    }

    @GetMapping("/landmarks/{sceneId}/random")
    public ResponseEntity<ApiResponse<Map<String, Object>>> randomLandmark(
            @PathVariable Long sceneId,
            @RequestParam(defaultValue = "CASUAL") String difficulty,
            Authentication authentication) {
        mysteryChallengeService.rejectLegacyLandmarkStart(sceneId);
        return ResponseEntity.ok(ApiResponse.success("取得景點題目成功",
                quizQuestionService.randomSceneQuestion(
                        userService.userIdFor(authentication.getName()), sceneId, difficulty)));
    }

    @GetMapping("/cities/{cityId}/boss/random")
    public ResponseEntity<ApiResponse<Map<String, Object>>> randomBoss(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "CASUAL") String difficulty,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("取得守護者題目成功",
                quizQuestionService.randomBossQuestion(
                        userService.userIdFor(authentication.getName()), cityId, difficulty)));
    }
}
