package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.JourneyStateService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/journey")
public class JourneyController {

    private final JourneyStateService journeyStateService;
    private final UserService userService;

    public JourneyController(JourneyStateService journeyStateService, UserService userService) {
        this.journeyStateService = journeyStateService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("取得旅程狀態成功",
                journeyStateService.state(userService.userIdFor(authentication.getName()))));
    }

    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> missions(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("取得任務成功",
                journeyStateService.missions(userService.userIdFor(authentication.getName()))));
    }

    @GetMapping("/achievements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> achievements(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("取得成就成功",
                journeyStateService.achievements(userService.userIdFor(authentication.getName()))));
    }
}
