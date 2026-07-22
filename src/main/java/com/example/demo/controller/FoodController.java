package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.UserService;
import com.example.demo.service.food.FoodChallengeService;
import com.example.demo.service.food.dto.FoodClaimRequest;
import com.example.demo.service.food.dto.FoodClaimResponse;
import com.example.demo.service.food.dto.FoodEventResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities/{cityId}/food")
public class FoodController {

    private final FoodChallengeService foodChallengeService;
    private final UserService userService;

    public FoodController(FoodChallengeService foodChallengeService,
                          UserService userService) {
        this.foodChallengeService = foodChallengeService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FoodEventResponse>> getFoodEvent(
            @PathVariable Long cityId,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        FoodEventResponse event = foodChallengeService.getFoodEvent(userId, cityId);
        String message;
        if (!event.available()) {
            message = "完成台南 2 個景點後解鎖特色美食";
        } else if (event.claimed()) {
            message = "台南牛肉湯已解鎖";
        } else {
            message = "台南特色美食事件已解鎖";
        }
        return ResponseEntity.ok(ApiResponse.success(message, event));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<FoodClaimResponse>> claimFood(
            @PathVariable Long cityId,
            @Valid @RequestBody FoodClaimRequest request,
            Authentication authentication) {
        Long userId = userService.userIdFor(authentication.getName());
        FoodClaimResponse result = foodChallengeService.claim(userId, cityId, request);
        String message = result.correct()
                ? "成功解鎖台南牛肉湯"
                : "答案不正確，可以重新挑戰";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}
