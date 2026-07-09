package com.example.demo.controller;

import com.example.demo.entity.Scene;
import com.example.demo.service.SceneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping("/{cityId}/scenes")
    public ResponseEntity<List<Scene>> listByCity(@PathVariable Long cityId) {
        return ResponseEntity.ok(sceneService.listByCity(cityId));
    }
}
