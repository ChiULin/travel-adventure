package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.AuthRequest;
import com.example.demo.dto.AuthResponse;
import com.example.demo.entity.User;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest req) {
        try {
            return ResponseEntity.ok(ApiResponse.success("登入成功",
                    authResponse(userService.login(req.getUsername(), req.getPassword()))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody AuthRequest req) {
        try {
            return ResponseEntity.status(201)
                    .body(ApiResponse.success("註冊成功",
                            authResponse(userService.register(req.getUsername(), req.getPassword()))));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
        }
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(user.getId(), user.getUsername(), jwtUtil.generateToken(user.getUsername()));
    }
}
