package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.MysteryChallengeService;
import com.example.demo.service.PuzzleChallengeService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/puzzle-challenges")
public class PuzzleChallengeController {

    private final PuzzleChallengeService puzzleChallengeService;
    private final MysteryChallengeService mysteryChallengeService;
    private final UserService userService;

    public PuzzleChallengeController(
            PuzzleChallengeService puzzleChallengeService,
            MysteryChallengeService mysteryChallengeService,
            UserService userService
    ) {
        this.puzzleChallengeService = puzzleChallengeService;
        this.mysteryChallengeService = mysteryChallengeService;
        this.userService = userService;
    }

    @PostMapping("/{challengeId}/complete")
    public ResponseEntity<ApiResponse<PuzzleChallengeService.PuzzleChallengeResult>> complete(
            @PathVariable String challengeId,
            @Valid @RequestBody PuzzleCompleteRequest request,
            Authentication authentication
    ) {
        Long userId = userService.userIdFor(authentication.getName());
        PuzzleChallengeService.PuzzleChallengeResult result = puzzleChallengeService.complete(
                userId, challengeId, request.selectedLandmarkId());
        mysteryChallengeService.markConsumed(userId, challengeId);
        String message = result.correct()
                ? "拼圖辨識成功，完成景點打卡"
                : "景點辨識錯誤，本次拼圖挑戰結束";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    public record PuzzleCompleteRequest(
            @NotNull(message = "請選擇候選景點") Long selectedLandmarkId
    ) {
    }
}
