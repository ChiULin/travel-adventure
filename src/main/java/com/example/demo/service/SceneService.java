package com.example.demo.service;

import com.example.demo.entity.Scene;
import com.example.demo.repository.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SceneService {
    private final SceneRepository sceneRepository;

    public SceneService(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    public List<Scene> listByCity(Long cityId) {
        return sceneRepository.findByCityId(cityId);
    }
}
