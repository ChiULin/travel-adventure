package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.ImageRecognitionService;
import com.example.demo.service.MysteryChallengeService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image-challenges")
public class ImageRecognitionController {
    private final ImageRecognitionService imageRecognitionService;
    private final UserService userService;
    private final MysteryChallengeService mysteryChallengeService;

    public ImageRecognitionController(ImageRecognitionService imageRecognitionService,
                                      UserService userService,
                                      MysteryChallengeService mysteryChallengeService) {
        this.imageRecognitionService = imageRecognitionService;
        this.userService = userService;
        this.mysteryChallengeService = mysteryChallengeService;
    }

    @GetMapping("/scenes/{sceneId}")
    public ResponseEntity<ApiResponse<ImageRecognitionService.ImageChallengeView>> issue(
            @PathVariable Long sceneId,
            @RequestParam(defaultValue = "CASUAL") String difficulty,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "取得圖片辨識挑戰成功",
                imageRecognitionService.issue(userId, sceneId, difficulty)
        ));
    }

    @PostMapping("/{questionId}/complete")
    public ResponseEntity<ApiResponse<ImageRecognitionService.ImageChallengeResult>> complete(
            @PathVariable String questionId,
            @Valid @RequestBody ImageChallengeAnswerRequest request,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        ImageRecognitionService.ImageChallengeResult result = imageRecognitionService.complete(
                userId, questionId, request.sceneId(), request.difficulty());
        mysteryChallengeService.markConsumed(userId, questionId);
        String message = result.correct()
                ? "辨識成功，完成景點打卡"
                : "辨識錯誤，請重新取得圖片挑戰";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    public record ImageChallengeAnswerRequest(
            @NotNull(message = "請選擇候選景點") Long sceneId,
            @NotBlank(message = "請選擇挑戰難度") String difficulty
    ) {
    }
}
