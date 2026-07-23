package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.MysteryChallengeService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mystery-challenges")
public class MysteryChallengeController {

    private final MysteryChallengeService mysteryChallengeService;
    private final UserService userService;

    public MysteryChallengeController(
            MysteryChallengeService mysteryChallengeService,
            UserService userService
    ) {
        this.mysteryChallengeService = mysteryChallengeService;
        this.userService = userService;
    }

    @PostMapping("/landmarks/{landmarkId}/start")
    public ResponseEntity<ApiResponse<MysteryChallengeService.MysteryChallengeResponse>> start(
            @PathVariable Long landmarkId,
            @Valid @RequestBody MysteryChallengeStartRequest request,
            Authentication authentication
    ) {
        Long userId = userService.userIdFor(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "未知挑戰已揭曉",
                mysteryChallengeService.start(userId, landmarkId, request.difficulty())
        ));
    }

    public record MysteryChallengeStartRequest(
            @NotBlank(message = "請選擇挑戰難度") String difficulty
    ) {
    }
}
