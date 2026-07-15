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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        List<UserProgress> progressRows = progressFor(user);
        Map<Long, UserProgress> progressByCityId = progressRows.stream()
                .collect(Collectors.toMap(progress -> progress.getCity().getId(), progress -> progress));
        Set<Long> checkedSceneIds = checkinRepository.findByUserIdAndCompletedTrue(userId).stream()
                .map(Checkin::getScene)
                .map(Scene::getId)
                .collect(Collectors.toSet());
        Set<Long> unlockedCityIds = progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .map(progress -> progress.getCity().getId())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<Long> defeatedCityIds = progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getBossCompleted()))
                .map(progress -> progress.getCity().getId())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<Map<String, Object>> cityDtos = cities.stream().map(city -> {
            List<Scene> scenes = sceneRepository.findByCityId(city.getId());
            long done = scenes.stream().filter(scene -> checkedSceneIds.contains(scene.getId())).count();
            UserProgress cityProgress = progressByCityId.get(city.getId());
            CityBadge badge = badgeFor(city);
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", city.getId());
            dto.put("name", city.getName());
            dto.put("code", city.getCode());
            dto.put("intro", city.getIntro());
            dto.put("badgeIcon", badge.icon());
            dto.put("badgeName", badge.name());
            dto.put("badgeUnlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBadgeUnlocked()));
            dto.put("bossName", city.getBossName());
            dto.put("bossPower", city.getBossPower());
            dto.put("bossQuestion", city.getBossQuestion());
            dto.put("bossOptions", optionsDto(city.getBossOptionA(), city.getBossOptionB(), city.getBossOptionC()));
            dto.put("unlockOrder", city.getUnlockOrder());
            dto.put("unlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getUnlocked()));
            dto.put("bossUnlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBossUnlocked()));
            dto.put("defeated", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBossCompleted()));
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

    public List<UserProgress> progressFor(User user) {
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        List<UserProgress> existing = userProgressRepository.findByUserId(user.getId());
        Map<Long, UserProgress> existingByCityId = existing.stream()
                .collect(Collectors.toMap(progress -> progress.getCity().getId(), progress -> progress));

        for (City city : cities) {
            if (!existingByCityId.containsKey(city.getId())) {
                boolean firstCity = city.getUnlockOrder() != null && city.getUnlockOrder() == 1;
                UserProgress progress = UserProgress.builder()
                        .user(user)
                        .city(city)
                        .unlocked(firstCity)
                        .bossUnlocked(false)
                        .bossCompleted(false)
                        .badgeUnlocked(false)
                        .build();
                existing.add(userProgressRepository.save(progress));
            }
        }
        return existing;
    }

    public void updateBossUnlock(Long userId, Long cityId) {
        long cityDone = checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(userId, cityId);
        long cityTotal = sceneRepository.countByCityId(cityId);
        if (cityTotal > 0 && cityDone >= cityTotal) {
            userProgressRepository.findByUserIdAndCityId(userId, cityId).ifPresent(progress -> {
                progress.setBossUnlocked(true);
                userProgressRepository.save(progress);
            });
        }
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

    public static LevelInfo calculateLevelInfo(int totalExp) {
        int level = 1;
        int remainingExp = Math.max(totalExp, 0);
        int requiredExp = 100;

        while (remainingExp >= requiredExp) {
            remainingExp -= requiredExp;
            level++;
            requiredExp = level * 100;
        }

        int progressPercent = (int) ((remainingExp * 100.0) / requiredExp);
        return new LevelInfo(level, remainingExp, requiredExp, progressPercent);
    }

    private Map<String, Object> sceneDto(Scene scene, boolean checked) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", scene.getId());
        dto.put("name", scene.getName());
        dto.put("type", scene.getType());
        dto.put("desc", scene.getDescription());
        dto.put("imageUrl", scene.getImageUrl());
        dto.put("rarity", scene.getRarity());
        dto.put("quizQuestion", scene.getQuizQuestion());
        dto.put("quizOptions", optionsDto(scene.getQuizOptionA(), scene.getQuizOptionB(), scene.getQuizOptionC()));
        dto.put("quizExplanation", scene.getQuizExplanation());
        dto.put("expReward", scene.getExpReward());
        dto.put("coinReward", scene.getCoinReward());
        dto.put("checked", checked);
        return dto;
    }

    private Map<String, String> optionsDto(String optionA, String optionB, String optionC) {
        List<String> options = new ArrayList<>();
        if (optionA != null && !optionA.isBlank()) {
            options.add(optionA);
        }
        if (optionB != null && !optionB.isBlank()) {
            options.add(optionB);
        }
        if (optionC != null && !optionC.isBlank()) {
            options.add(optionC);
        }
        Collections.shuffle(options);

        Map<String, String> dto = new LinkedHashMap<>();
        String[] labels = {"A", "B", "C"};
        for (int i = 0; i < options.size() && i < labels.length; i++) {
            dto.put(labels[i], options.get(i));
        }
        return dto;
    }

    private Map<String, Object> userDto(User user) {
        int totalExp = user.getExp() == null ? 0 : user.getExp();
        LevelInfo levelInfo = calculateLevelInfo(totalExp);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("level", levelInfo.level());
        dto.put("experience", totalExp);
        dto.put("exp", totalExp);
        dto.put("currentLevelExp", levelInfo.currentLevelExp());
        dto.put("nextLevelExp", levelInfo.nextLevelExp());
        dto.put("levelProgressPercent", levelInfo.levelProgressPercent());
        dto.put("coins", user.getCoins());
        dto.put("bossPoints", user.getBossPoints());
        dto.put("title", user.getTitle());
        return dto;
    }

    private CityBadge badgeFor(City city) {
        if (city.getBadgeIcon() != null && city.getBadgeName() != null) {
            return new CityBadge(city.getBadgeIcon(), city.getBadgeName());
        }
        String cityName = city.getName();
        return switch (cityName) {
            case "台北" -> new CityBadge("🏙️", "101 徽章");
            case "台中" -> new CityBadge("🌅", "高美濕地徽章");
            case "台南" -> new CityBadge("🏯", "古都徽章");
            case "高雄" -> new CityBadge("🌊", "港都徽章");
            case "花蓮" -> new CityBadge("⛰️", "山海徽章");
            case "澎湖" -> new CityBadge("🏝️", "海島徽章");
            default -> new CityBadge("🎒", cityName + "徽章");
        };
    }

    private record CityBadge(String icon, String name) {
    }

    public record LevelInfo(int level, int currentLevelExp, int nextLevelExp, int levelProgressPercent) {
    }
}
