package com.example.demo.service;

import com.example.demo.dto.JourneyBossStageResponse;
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
import com.example.demo.stage.LandmarkStageRegistry;
import com.example.demo.stage.LandmarkStageService;
import com.example.demo.stage.LandmarkStageStatus;
import com.example.demo.stage.StageStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    private final ExplorationMissionRegistry explorationMissionRegistry;
    private final ImageRecognitionRegistry imageRecognitionRegistry;
    private final LandmarkStageRegistry landmarkStageRegistry;
    private final LandmarkStageService landmarkStageService;

    public JourneyStateService(UserRepository userRepository, CityRepository cityRepository, SceneRepository sceneRepository,
                               CheckinRepository checkinRepository, UserProgressRepository userProgressRepository,
                               ExplorationMissionRegistry explorationMissionRegistry,
                               ImageRecognitionRegistry imageRecognitionRegistry,
                               LandmarkStageRegistry landmarkStageRegistry,
                               LandmarkStageService landmarkStageService) {
        this.userRepository = userRepository;
        this.cityRepository = cityRepository;
        this.sceneRepository = sceneRepository;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
        this.explorationMissionRegistry = explorationMissionRegistry;
        this.imageRecognitionRegistry = imageRecognitionRegistry;
        this.landmarkStageRegistry = landmarkStageRegistry;
        this.landmarkStageService = landmarkStageService;
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
            boolean stageMode = landmarkStageRegistry.isCityFullyConfigured(city.getId());
            List<Map<String, Object>> sceneDtos = scenes.stream()
                    .map(scene -> sceneDto(
                            userId, scene, checkedSceneIds.contains(scene.getId()), stageMode))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (stageMode) {
                sceneDtos.sort(Comparator.comparingInt(scene -> (Integer) scene.get("stageOrder")));
            }
            long done = scenes.stream().filter(scene -> checkedSceneIds.contains(scene.getId())).count();
            UserProgress cityProgress = progressByCityId.get(city.getId());
            CityBadge badge = badgeFor(city);
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", city.getId());
            dto.put("name", city.getName());
            dto.put("code", city.getCode());
            dto.put("intro", city.getIntro());
            dto.put("story", city.getStory());
            dto.put("badgeIcon", badge.icon());
            dto.put("badgeName", badge.name());
            dto.put("badgeUnlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBadgeUnlocked()));
            dto.put("bossName", city.getBossName());
            dto.put("bossPower", city.getBossPower());
            dto.put("bossQuestion", city.getBossQuestion());
            dto.put("bossOptions", optionsDto(city.getBossOptionA(), city.getBossOptionB(), city.getBossOptionC(), city.getBossOptionD()));
            dto.put("unlockOrder", city.getUnlockOrder());
            dto.put("unlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getUnlocked()));
            dto.put("bossUnlocked", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBossUnlocked()));
            dto.put("defeated", cityProgress != null && Boolean.TRUE.equals(cityProgress.getBossCompleted()));
            dto.put("bestRank", cityProgress == null ? null : cityProgress.getBestRank());
            dto.put("bestCombo", cityProgress == null || cityProgress.getBestCombo() == null ? 0 : cityProgress.getBestCombo());
            dto.put("bestRemainingLives", cityProgress == null || cityProgress.getBestRemainingLives() == null ? 0 : cityProgress.getBestRemainingLives());
            dto.put("challengeCount", cityProgress == null || cityProgress.getChallengeCount() == null ? 0 : cityProgress.getChallengeCount());
            dto.put("lastCompletedAt", cityProgress == null ? null : cityProgress.getLastCompletedAt());
            dto.put("done", done);
            dto.put("total", scenes.size());
            dto.put("scenes", sceneDtos);
            dto.put("bossStage", stageMode
                    ? buildBossStage(city, cityProgress, scenes, checkedSceneIds)
                    : null);
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

    public Map<String, Object> missions(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Checkin> checkins = checkinRepository.findByUserId(userId);
        List<UserProgress> progressRows = userProgressRepository.findByUserId(userId);

        int completedScenesToday = (int) checkins.stream()
                .filter(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .filter(checkin -> checkin.getCompletedAt() != null && !checkin.getCompletedAt().isBefore(todayStart))
                .count();
        int correctAnswersToday = (int) checkins.stream()
                .filter(checkin -> Boolean.TRUE.equals(checkin.getQuizCorrect()))
                .filter(checkin -> checkin.getCheckinTime() != null && !checkin.getCheckinTime().isBefore(todayStart))
                .count();
        int bossWinsToday = (int) progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getBossCompleted()))
                .filter(progress -> progress.getCompletedAt() != null && !progress.getCompletedAt().isBefore(todayStart))
                .count();

        List<Map<String, Object>> missionDtos = List.of(
                missionDto("complete-scenes", "完成景點", completedScenesToday, 2),
                missionDto("correct-answers", "答對題目", correctAnswersToday, 3),
                missionDto("boss-challenge", "Boss 挑戰", bossWinsToday, 1)
        );

        boolean completed = missionDtos.stream().allMatch(mission -> Boolean.TRUE.equals(mission.get("completed")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("rewardExp", 100);
        result.put("rewardCoins", 50);
        result.put("completed", completed);
        result.put("missions", missionDtos);
        return result;
    }

    public Map<String, Object> achievements(Long userId) {
        List<Checkin> completedCheckins = checkinRepository.findByUserIdAndCompletedTrue(userId);
        List<UserProgress> progressRows = userProgressRepository.findByUserId(userId);
        int completedScenes = completedCheckins.size();
        int completedCities = (int) progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getBossCompleted()))
                .count();
        int badges = (int) progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getBadgeUnlocked()))
                .count();
        boolean firstCityCompleted = progressRows.stream()
                .filter(progress -> progress.getCity() != null && progress.getCity().getUnlockOrder() != null)
                .filter(progress -> progress.getCity().getUnlockOrder() == 1)
                .anyMatch(progress -> Boolean.TRUE.equals(progress.getBossCompleted()));

        List<Map<String, Object>> achievementDtos = List.of(
                achievementDto("first-checkin", "初次探索", "完成第一次景點打卡", completedScenes >= 1),
                achievementDto("first-city", "台北征服者", "完成第一座城市", firstCityCompleted),
                achievementDto("three-correct", "三題連勝", "累積答對 3 題", completedScenes >= 3),
                achievementDto("badge-collector", "徽章收藏家", "收集 3 枚城市徽章", badges >= 3),
                achievementDto("taiwan-adventurer", "台灣冒險王", "完成全部 6 座城市", completedCities >= 6)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unlockedCount", achievementDtos.stream()
                .filter(achievement -> Boolean.TRUE.equals(achievement.get("unlocked")))
                .count());
        result.put("total", achievementDtos.size());
        result.put("badgeCount", badges);
        result.put("completedCities", completedCities);
        result.put("achievements", achievementDtos);
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

    private Map<String, Object> missionDto(String id, String label, int current, int target) {
        Map<String, Object> dto = new LinkedHashMap<>();
        int capped = Math.min(current, target);
        dto.put("id", id);
        dto.put("label", label);
        dto.put("current", capped);
        dto.put("target", target);
        dto.put("completed", capped >= target);
        dto.put("progressPercent", target == 0 ? 100 : (int) Math.round(capped * 100.0 / target));
        return dto;
    }

    private Map<String, Object> achievementDto(String id, String title, String description, boolean unlocked) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("title", title);
        dto.put("description", description);
        dto.put("unlocked", unlocked);
        return dto;
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

    private Map<String, Object> sceneDto(Long userId, Scene scene, boolean checked, boolean stageMode) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", scene.getId());
        dto.put("name", scene.getName());
        dto.put("type", scene.getType());
        dto.put("desc", scene.getDescription());
        dto.put("story", scene.getStory());
        dto.put("imageUrl", scene.getImageUrl());
        dto.put("rarity", scene.getRarity());
        dto.put("quizQuestion", scene.getQuizQuestion());
        dto.put("quizOptions", optionsDto(scene.getQuizOptionA(), scene.getQuizOptionB(), scene.getQuizOptionC(), scene.getQuizOptionD()));
        dto.put("quizExplanation", scene.getQuizExplanation());
        dto.put("expReward", scene.getExpReward());
        dto.put("coinReward", scene.getCoinReward());
        dto.put("checked", checked);
        SceneInteractionType interactionType;
        if (explorationMissionRegistry.findByTargetSceneId(scene.getId()).isPresent()) {
            interactionType = SceneInteractionType.EXPLORATION;
        } else if (imageRecognitionRegistry.findByTargetSceneId(scene.getId()).isPresent()) {
            interactionType = SceneInteractionType.IMAGE_RECOGNITION;
        } else {
            interactionType = SceneInteractionType.QUIZ;
        }
        dto.put("interactionType", interactionType.name());

        boolean stageConfigured = false;
        Integer stageOrder = null;
        String stageLabel = null;
        StageStatus stageStatus = null;
        if (stageMode) {
            landmarkStageRegistry.findByLandmarkId(scene.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "城市已啟用關卡模式，但景點缺少關卡設定"));
            stageConfigured = true;
            LandmarkStageStatus result = landmarkStageService.getStageStatus(userId, scene.getId());
            stageOrder = result.stageOrder();
            stageLabel = "第 " + result.stageOrder() + " 關";
            stageStatus = result.status();
        }

        dto.put("actionLabel", resolveActionLabel(checked, interactionType, stageStatus, stageConfigured));
        dto.put("stageOrder", stageOrder);
        dto.put("stageLabel", stageLabel);
        dto.put("stageStatus", stageStatus);
        dto.put("stageConfigured", stageConfigured);
        return dto;
    }

    private String resolveActionLabel(boolean checked, SceneInteractionType interactionType,
                                      StageStatus stageStatus, boolean stageConfigured) {
        if (stageConfigured) {
            if (stageStatus == StageStatus.LOCKED) {
                return "完成上一關後解鎖";
            }
            return checked ? "查看故事" : "開始挑戰";
        }

        if (checked) {
            return "查看景點故事";
        }
        return switch (interactionType) {
            case EXPLORATION -> "接旅行委託";
            case IMAGE_RECOGNITION -> "觀察景點照片";
            default -> "開始答題";
        };
    }

    private JourneyBossStageResponse buildBossStage(City city, UserProgress cityProgress,
                                                     List<Scene> scenes, Set<Long> checkedSceneIds) {
        boolean allLandmarksCompleted = scenes.stream()
                .allMatch(scene -> checkedSceneIds.contains(scene.getId()));
        boolean bossCompleted = cityProgress != null
                && Boolean.TRUE.equals(cityProgress.getBossCompleted());
        StageStatus status;
        if (bossCompleted) {
            status = StageStatus.COMPLETED;
        } else if (allLandmarksCompleted) {
            status = StageStatus.AVAILABLE;
        } else {
            status = StageStatus.LOCKED;
        }

        int stageOrder = landmarkStageRegistry.findByCityId(city.getId()).stream()
                .mapToInt(stage -> stage.stageOrder())
                .max()
                .orElse(0) + 1;

        return new JourneyBossStageResponse(
                city.getId(),
                city.getBossName(),
                stageOrder,
                "第 " + stageOrder + " 關",
                status,
                resolveBossActionLabel(status)
        );
    }

    private String resolveBossActionLabel(StageStatus status) {
        return switch (status) {
            case LOCKED -> "完成前三關後解鎖";
            case AVAILABLE -> "挑戰城市守護者";
            case COMPLETED -> "再次挑戰守護者";
        };
    }

    private Map<String, String> optionsDto(String optionA, String optionB, String optionC, String optionD) {
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
        if (optionD != null && !optionD.isBlank()) {
            options.add(optionD);
        }
        Collections.shuffle(options);

        Map<String, String> dto = new LinkedHashMap<>();
        String[] labels = {"A", "B", "C", "D"};
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
        String cityName = city.getName() == null || city.getName().isBlank()
                ? "城市"
                : city.getName();
        String icon = city.getBadgeIcon() == null || city.getBadgeIcon().isBlank()
                ? "🏅"
                : city.getBadgeIcon();
        String name = city.getBadgeName() == null || city.getBadgeName().isBlank()
                ? cityName + "徽章"
                : city.getBadgeName();
        return new CityBadge(icon, name);
    }

    private record CityBadge(String icon, String name) {
    }

    public record LevelInfo(int level, int currentLevelExp, int nextLevelExp, int levelProgressPercent) {
    }
}
