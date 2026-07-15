package com.example.demo.controller;

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
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(journeyStateService.state(userService.userIdFor(authentication.getName())));
    }

    @GetMapping("/missions")
    public ResponseEntity<Map<String, Object>> missions(Authentication authentication) {
        return ResponseEntity.ok(journeyStateService.missions(userService.userIdFor(authentication.getName())));
    }

    @GetMapping("/achievements")
    public ResponseEntity<Map<String, Object>> achievements(Authentication authentication) {
        return ResponseEntity.ok(journeyStateService.achievements(userService.userIdFor(authentication.getName())));
    }
}
