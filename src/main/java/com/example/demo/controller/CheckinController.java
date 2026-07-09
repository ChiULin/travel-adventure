package com.example.demo.controller;

import com.example.demo.dto.CheckinRequest;
import com.example.demo.service.CheckinService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final CheckinService checkinService;
    private final UserService userService;

    public CheckinController(CheckinService checkinService, UserService userService) {
        this.checkinService = checkinService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> checkin(@RequestBody CheckinRequest req, Authentication authentication) {
        try {
            checkinService.checkin(userService.userIdFor(authentication.getName()), req.getSceneId());
            return ResponseEntity.ok(java.util.Map.of("ok", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
