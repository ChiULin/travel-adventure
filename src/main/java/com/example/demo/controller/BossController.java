package com.example.demo.controller;

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
            boolean win = bossService.challenge(userService.userIdFor(authentication.getName()), cityId, answer);
            return ResponseEntity.ok(java.util.Map.of("win", win));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
