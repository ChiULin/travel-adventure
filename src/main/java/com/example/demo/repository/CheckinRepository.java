package com.example.demo.repository;

import com.example.demo.entity.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CheckinRepository extends JpaRepository<Checkin, Long> {
	java.util.List<Checkin> findByUserId(Long userId);
	java.util.List<Checkin> findByUserIdAndCompletedTrue(Long userId);
	boolean existsByUserIdAndSceneId(Long userId, Long sceneId);
	java.util.Optional<Checkin> findByUserIdAndSceneId(Long userId, Long sceneId);
	long countByUserIdAndSceneCityId(Long userId, Long cityId);
	long countByUserIdAndSceneCityIdAndCompletedTrue(Long userId, Long cityId);

	@Transactional
	@Modifying
	@Query("delete from Checkin c where c.scene.id = :sceneId")
	void deleteBySceneId(@Param("sceneId") Long sceneId);

	@Transactional
	@Modifying
	@Query("delete from Checkin c where c.user.id = :userId and c.scene.city.id = :cityId and c.completed = false")
	void deleteIncompleteByUserIdAndCityId(@Param("userId") Long userId, @Param("cityId") Long cityId);
}
