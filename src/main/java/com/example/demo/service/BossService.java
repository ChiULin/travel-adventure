package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.dto.BattleResultRequest;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class BossService {
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinRepository checkinRepository;
    private final SceneRepository sceneRepository;

    public BossService(CityRepository cityRepository, UserRepository userRepository, UserProgressRepository userProgressRepository,
                       CheckinRepository checkinRepository, SceneRepository sceneRepository) {
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinRepository = checkinRepository;
        this.sceneRepository = sceneRepository;
    }

    public boolean challenge(Long userId, Long cityId) {
        return challenge(userId, cityId, null);
    }

    public boolean challenge(Long userId, Long cityId, String selectedAnswer) {
        return challenge(userId, cityId, selectedAnswer, null);
    }

    public boolean challenge(Long userId, Long cityId, String selectedAnswer, String selectedAnswerText) {
        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<City> optionalCity = cityRepository.findById(cityId);
        if (optionalUser.isEmpty() || optionalCity.isEmpty()) {
            throw new IllegalArgumentException("invalid user or city");
        }

        User user = optionalUser.get();
        City city = optionalCity.get();
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not unlocked"));
        if (!Boolean.TRUE.equals(progress.getUnlocked())) {
            throw new IllegalArgumentException("city not unlocked");
        }

        long cityDone = checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(userId, cityId);
        long cityTotal = sceneRepository.countByCityId(cityId);
        if (cityTotal == 0 || cityDone < cityTotal) {
            throw new IllegalArgumentException("complete all city scenes first");
        }

        int userPower = (user.getLevel() == null ? 1 : user.getLevel()) * 10
                + ((user.getExp() == null ? 0 : user.getExp()) / 10);
        int bossPower = city.getBossPower() == null ? 0 : city.getBossPower();
        boolean answerCorrect = bossAnswerCorrect(city, selectedAnswer, selectedAnswerText);
        boolean win = answerCorrect && userPower >= bossPower;

        if (win) {
            progress.setBossUnlocked(true);
            progress.setBossCompleted(true);
            progress.setBadgeUnlocked(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            userProgressRepository.save(progress);

            cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                    .filter(next -> next.getUnlockOrder() != null && city.getUnlockOrder() != null)
                    .filter(next -> next.getUnlockOrder().equals(city.getUnlockOrder() + 1))
                    .findFirst()
                    .ifPresent(next -> userProgressRepository.findByUserIdAndCityId(userId, next.getId())
                            .ifPresent(nextProgress -> {
                                nextProgress.setUnlocked(true);
                                userProgressRepository.save(nextProgress);
                            }));

            user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + 300);
            user.setExp((user.getExp() == null ? 0 : user.getExp()) + 260);
            user.setBossPoints((user.getBossPoints() == null ? 0 : user.getBossPoints()) + 1);
            int level = JourneyStateService.calculateLevelInfo(user.getExp() == null ? 0 : user.getExp()).level();
            user.setLevel(level);
            user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
            userRepository.save(user);
        }

        return win;
    }

    private boolean bossAnswerCorrect(City city, String selectedAnswer, String selectedAnswerText) {
        String correctAnswer = normalizeAnswer(city.getBossCorrectAnswer());
        String answer = normalizeAnswer(selectedAnswer);
        if (correctAnswer == null || correctAnswer.isBlank()) {
            return true;
        }
        String correctText = optionText(city, correctAnswer);
        String answerText = normalizeText(selectedAnswerText);
        if (answerText != null && correctText != null) {
            return answerText.equals(normalizeText(correctText));
        }
        if (answer == null || answer.isBlank()) {
            return true;
        }
        return correctAnswer.equals(answer);
    }

    private String optionText(City city, String answer) {
        return switch (answer) {
            case "A" -> city.getBossOptionA();
            case "B" -> city.getBossOptionB();
            case "C" -> city.getBossOptionC();
            case "D" -> city.getBossOptionD();
            default -> null;
        };
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        return answer.trim().toUpperCase();
    }

    private String normalizeText(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        return answerText.trim();
    }

    public Map<String, Object> recordBattleResult(Long userId, Long cityId, BattleResultRequest request) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not found"));
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not unlocked"));
        if (!Boolean.TRUE.equals(progress.getBossCompleted())) {
            throw new IllegalArgumentException("city boss is not completed");
        }

        long cityDone = checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(userId, cityId);
        long cityTotal = sceneRepository.countByCityId(cityId);
        if (cityTotal == 0 || cityDone < cityTotal) {
            throw new IllegalArgumentException("complete all city scenes first");
        }

        String oldRank = progress.getBestRank();
        int oldBestCombo = progress.getBestCombo() == null ? 0 : progress.getBestCombo();
        int oldBestLives = progress.getBestRemainingLives() == null ? 0 : progress.getBestRemainingLives();
        String newRank = calculateRank(request);
        int maxCombo = Math.max(0, valueOrZero(request.getMaxCombo()));
        int remainingLives = Math.max(0, Math.min(3, valueOrZero(request.getRemainingLives())));

        boolean newRecord = false;
        if (rankScore(newRank) > rankScore(oldRank)) {
            progress.setBestRank(newRank);
            newRecord = true;
        }
        if (maxCombo > oldBestCombo) {
            progress.setBestCombo(maxCombo);
            newRecord = true;
        }
        if (remainingLives > oldBestLives) {
            progress.setBestRemainingLives(remainingLives);
            newRecord = true;
        }
        progress.setChallengeCount((progress.getChallengeCount() == null ? 0 : progress.getChallengeCount()) + 1);
        progress.setLastCompletedAt(LocalDateTime.now());
        userProgressRepository.save(progress);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rank", newRank);
        result.put("bestRank", progress.getBestRank());
        result.put("bestCombo", progress.getBestCombo());
        result.put("bestRemainingLives", progress.getBestRemainingLives());
        result.put("challengeCount", progress.getChallengeCount());
        result.put("lastCompletedAt", progress.getLastCompletedAt());
        result.put("newRecord", newRecord);
        result.put("cityName", city.getName());
        return result;
    }

    public Map<String, Object> restartCity(Long userId, Long cityId) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not found"));
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not unlocked"));
        if (!Boolean.TRUE.equals(progress.getUnlocked())) {
            throw new IllegalArgumentException("city not unlocked");
        }

        checkinRepository.deleteByUserIdAndCityId(userId, cityId);
        progress.setBossUnlocked(false);
        progress.setBossCompleted(false);
        progress.setCompletedAt(null);
        userProgressRepository.save(progress);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityId", city.getId());
        result.put("cityName", city.getName());
        result.put("bestRank", progress.getBestRank());
        result.put("bestCombo", progress.getBestCombo() == null ? 0 : progress.getBestCombo());
        result.put("restarted", true);
        return result;
    }

    private String calculateRank(BattleResultRequest request) {
        int remainingLives = Math.max(0, Math.min(3, valueOrZero(request.getRemainingLives())));
        int wrongAnswers = Math.max(0, valueOrZero(request.getWrongAnswers()));
        int timeoutCount = Math.max(0, valueOrZero(request.getTimeoutCount()));
        if (remainingLives == 3 && wrongAnswers == 0 && timeoutCount == 0) {
            return "S";
        }
        if (remainingLives == 2) {
            return "A";
        }
        if (remainingLives == 1) {
            return "B";
        }
        return "C";
    }

    private int rankScore(String rank) {
        return switch (rank == null ? "" : rank.trim().toUpperCase()) {
            case "S" -> 4;
            case "A" -> 3;
            case "B" -> 2;
            case "C" -> 1;
            default -> 0;
        };
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
