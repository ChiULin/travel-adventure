package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.ExplorationService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explorations")
public class ExplorationController {
    private final ExplorationService explorationService;
    private final UserService userService;

    public ExplorationController(ExplorationService explorationService, UserService userService) {
        this.explorationService = explorationService;
        this.userService = userService;
    }

    @GetMapping("/cities/{cityId}/random")
    public ResponseEntity<ApiResponse<ExplorationService.ExplorationMissionView>> random(
            @PathVariable Long cityId, Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "取得旅行委託成功",
                explorationService.randomMission(userId, cityId)
        ));
    }

    @PostMapping("/{missionId}/investigate")
    public ResponseEntity<ApiResponse<ExplorationService.InvestigationResult>> investigate(
            @PathVariable String missionId,
            @Valid @RequestBody InvestigationRequest request,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        ExplorationService.InvestigationResult result = explorationService.investigate(
                userId, missionId, request.action());
        String message = result.alreadyDiscovered()
                ? "這項線索已經調查過"
                : investigationMessage(result.clueType());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/{missionId}/guess")
    public ResponseEntity<ApiResponse<ExplorationService.ExplorationGuessResult>> guess(
            @PathVariable String missionId,
            @Valid @RequestBody ExplorationGuessRequest request,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        ExplorationService.ExplorationGuessResult result = explorationService.submitGuess(
                userId, missionId, request.sceneId());
        String message = result.correct()
                ? "推理成功，請完成文化挑戰"
                : "這個地點與目前線索不完全吻合";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/{missionId}/complete")
    public ResponseEntity<ApiResponse<ExplorationService.ExplorationCompletionResult>> complete(
            @PathVariable String missionId,
            @Valid @RequestBody ExplorationCompleteRequest request,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        ExplorationService.ExplorationCompletionResult result = explorationService.complete(
                userId, missionId, request.questionId(), request.answer(), request.difficulty());
        String message = result.completed()
                ? "探索完成，成功打卡" + result.sceneName()
                : "文化挑戰答案錯誤";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    public record ExplorationGuessRequest(@NotNull(message = "請選擇候選景點") Long sceneId) {
    }

    public record InvestigationRequest(@NotBlank(message = "請選擇調查行動") String action) {
    }

    public record ExplorationCompleteRequest(
            @NotBlank(message = "缺少文化挑戰題目") String questionId,
            @NotBlank(message = "請選擇文化挑戰答案") String answer,
            @NotBlank(message = "請選擇挑戰難度") String difficulty
    ) {
    }

    private String investigationMessage(ExplorationService.ClueType clueType) {
        return switch (clueType) {
            case LOCAL -> "你從當地居民口中發現了新線索";
            case HISTORY -> "你從歷史文獻中發現了新線索";
            case VISUAL -> "你從舊照片中發現了新線索";
        };
    }
}
