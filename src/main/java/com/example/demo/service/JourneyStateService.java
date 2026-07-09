package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JourneyStateService {
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final SceneRepository sceneRepository;
    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;

    public JourneyStateService(UserRepository userRepository, CityRepository cityRepository, SceneRepository sceneRepository,
                               CheckinRepository checkinRepository, UserProgressRepository userProgressRepository) {
        this.userRepository = userRepository;
        this.cityRepository = cityRepository;
        this.sceneRepository = sceneRepository;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
    }

    public Map<String, Object> state(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("invalid user"));
        UserProgress progress = progressFor(user);
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        Set<Long> checkedSceneIds = checkinRepository.findByUserId(userId).stream()
                .map(Checkin::getScene)
                .map(Scene::getId)
                .collect(Collectors.toSet());
        Set<Long> unlockedCityIds = csvToLongSet(progress.getUnlockedCityIdsJson());
        Set<Long> defeatedCityIds = csvToLongSet(progress.getDefeatedBossCityIdsJson());

        List<Map<String, Object>> cityDtos = cities.stream().map(city -> {
            List<Scene> scenes = sceneRepository.findByCityId(city.getId());
            long done = scenes.stream().filter(scene -> checkedSceneIds.contains(scene.getId())).count();
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", city.getId());
            dto.put("name", city.getName());
            dto.put("code", city.getCode());
            dto.put("intro", city.getIntro());
            dto.put("bossName", city.getBossName());
            dto.put("bossPower", city.getBossPower());
            dto.put("unlockOrder", city.getUnlockOrder());
            dto.put("unlocked", unlockedCityIds.contains(city.getId()));
            dto.put("defeated", defeatedCityIds.contains(city.getId()));
            dto.put("done", done);
            dto.put("total", scenes.size());
            dto.put("scenes", scenes.stream().map(scene -> sceneDto(scene, checkedSceneIds.contains(scene.getId()))).toList());
            return dto;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userDto(user));
        result.put("cities", cityDtos);
        result.put("unlockedCityIds", unlockedCityIds);
        result.put("defeatedBossCityIds", defeatedCityIds);
        result.put("checkedSceneIds", checkedSceneIds);
        return result;
    }

    public UserProgress progressFor(User user) {
        return userProgressRepository.findByUserId(user.getId()).orElseGet(() -> {
            UserProgress progress = UserProgress.builder()
                    .user(user)
                    .unlockedCityIdsJson("1")
                    .defeatedBossCityIdsJson("")
                    .discoveredHiddenSceneIdsJson("")
                    .build();
            return userProgressRepository.save(progress);
        });
    }

    public static Set<Long> csvToLongSet(String csv) {
        if (csv == null || csv.isBlank()) return new java.util.LinkedHashSet<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(item -> item.replace("\"", ""))
                .filter(item -> !item.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static String longSetToCsv(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Map<String, Object> sceneDto(Scene scene, boolean checked) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", scene.getId());
        dto.put("name", scene.getName());
        dto.put("type", scene.getType());
        dto.put("desc", scene.getDescription());
        dto.put("rarity", scene.getRarity());
        dto.put("expReward", scene.getExpReward());
        dto.put("coinReward", scene.getCoinReward());
        dto.put("checked", checked);
        return dto;
    }

    private Map<String, Object> userDto(User user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("level", user.getLevel());
        dto.put("exp", user.getExp());
        dto.put("coins", user.getCoins());
        dto.put("bossPoints", user.getBossPoints());
        dto.put("title", user.getTitle());
        return dto;
    }
}
