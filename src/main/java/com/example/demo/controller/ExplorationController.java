package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.ExplorationService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
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
                "取得探索任務成功",
                explorationService.randomMission(userId, cityId)
        ));
    }

    @PostMapping("/{missionId}/guess")
    public ResponseEntity<ApiResponse<ExplorationService.ExplorationGuessResult>> guess(
            @PathVariable String missionId,
            @Valid @RequestBody ExplorationGuessRequest request) {
        ExplorationService.ExplorationGuessResult result = explorationService.guess(missionId, request.sceneId());
        String message = result.correct() ? "推理成功" : "推理尚未成功";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    public record ExplorationGuessRequest(@NotNull(message = "請選擇候選景點") Long sceneId) {
    }
}
