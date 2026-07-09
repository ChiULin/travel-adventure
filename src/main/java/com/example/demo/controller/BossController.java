package com.example.demo.controller;

import com.example.demo.service.BossService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> challenge(@PathVariable Long cityId, Authentication authentication) {
        try {
            boolean win = bossService.challenge(userService.userIdFor(authentication.getName()), cityId);
            return ResponseEntity.ok(java.util.Map.of("win", win));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
