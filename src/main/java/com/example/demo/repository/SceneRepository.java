package com.example.demo.repository;

import com.example.demo.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findByCityId(Long cityId);
    long countByCityId(Long cityId);
    boolean existsByIdAndCityId(Long id, Long cityId);
}
