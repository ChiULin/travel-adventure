package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CollectionService {
    private final CityRepository cityRepository;
    private final SceneRepository sceneRepository;
    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;

    public CollectionService(CityRepository cityRepository, SceneRepository sceneRepository,
                             CheckinRepository checkinRepository, UserProgressRepository userProgressRepository) {
        this.cityRepository = cityRepository;
        this.sceneRepository = sceneRepository;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> collection(Long userId) {
        Map<Long, Checkin> completedBySceneId = checkinRepository.findByUserIdAndCompletedTrue(userId).stream()
                .collect(Collectors.toMap(checkin -> checkin.getScene().getId(), Function.identity()));
        Map<Long, UserProgress> progressByCityId = userProgressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(progress -> progress.getCity().getId(), Function.identity()));

        List<Scene> scenes = sceneRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Scene scene) -> scene.getCity().getUnlockOrder())
                        .thenComparing(Scene::getId))
                .toList();
        List<Map<String, Object>> landmarkDtos = scenes.stream()
                .map(scene -> landmarkDto(scene, completedBySceneId.get(scene.getId())))
                .toList();

        List<Map<String, Object>> badgeDtos = cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                .map(city -> badgeDto(city, progressByCityId.get(city.getId())))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("landmarks", landmarkDtos);
        result.put("badges", badgeDtos);
        result.put("completedLandmarks", completedBySceneId.size());
        result.put("totalLandmarks", scenes.size());
        result.put("unlockedBadges", badgeDtos.stream()
                .filter(badge -> Boolean.TRUE.equals(badge.get("badgeUnlocked")))
                .count());
        result.put("totalBadges", badgeDtos.size());
        return result;
    }

    private Map<String, Object> landmarkDto(Scene scene, Checkin checkin) {
        boolean unlocked = checkin != null && Boolean.TRUE.equals(checkin.getCompleted());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", scene.getId());
        dto.put("name", unlocked ? scene.getName() : "??? 未解鎖");
        dto.put("realName", scene.getName());
        dto.put("cityName", scene.getCity().getName());
        dto.put("imageUrl", unlocked ? scene.getImageUrl() : null);
        dto.put("story", unlocked ? scene.getStory() : null);
        dto.put("description", unlocked ? scene.getDescription() : "完成對應景點後開放");
        dto.put("unlocked", unlocked);
        dto.put("completedAt", checkin == null ? null : checkin.getCompletedAt());
        dto.put("earnedExp", checkin == null || checkin.getEarnedExp() == null ? 0 : checkin.getEarnedExp());
        dto.put("earnedCoins", checkin == null || checkin.getEarnedCoins() == null ? 0 : checkin.getEarnedCoins());
        return dto;
    }

    private Map<String, Object> badgeDto(City city, UserProgress progress) {
        BadgeParts badge = badgeParts(city);
        boolean unlocked = progress != null && Boolean.TRUE.equals(progress.getBadgeUnlocked());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("cityId", city.getId());
        dto.put("cityName", city.getName());
        dto.put("badgeIcon", city.getBadgeIcon() == null ? "🏅" : city.getBadgeIcon());
        dto.put("badgeName", unlocked ? badge.name() : "??? 未取得");
        dto.put("realBadgeName", badge.name());
        dto.put("badgeQuote", unlocked ? badge.quote() : "");
        dto.put("badgeUnlocked", unlocked);
        dto.put("bestRank", progress == null ? null : progress.getBestRank());
        dto.put("bestCombo", progress == null || progress.getBestCombo() == null ? 0 : progress.getBestCombo());
        dto.put("acquiredAt", progress == null ? null : progress.getCompletedAt());
        return dto;
    }

    private BadgeParts badgeParts(City city) {
        String raw = city.getBadgeName() == null ? city.getName() + "探索者" : city.getBadgeName();
        String[] parts = raw.split("[：:]", 2);
        return new BadgeParts(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "");
    }

    private record BadgeParts(String name, String quote) {
    }
}
