package com.example.demo.repository;

import com.example.demo.entity.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckinRepository extends JpaRepository<Checkin, Long> {
	java.util.List<Checkin> findByUserId(Long userId);
	boolean existsByUserIdAndSceneId(Long userId, Long sceneId);
	long countByUserIdAndSceneCityId(Long userId, Long cityId);
}
