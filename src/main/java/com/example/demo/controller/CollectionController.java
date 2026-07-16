package com.example.demo.controller;

import com.example.demo.service.CollectionService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/collection")
public class CollectionController {
    private final CollectionService collectionService;
    private final UserService userService;

    public CollectionController(CollectionService collectionService, UserService userService) {
        this.collectionService = collectionService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> collection(Authentication authentication) {
        return ResponseEntity.ok(collectionService.collection(userService.userIdFor(authentication.getName())));
    }
}
