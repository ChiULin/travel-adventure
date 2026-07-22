package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.dto.BattleResultRequest;
import com.example.demo.dto.BossStartResponse;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BossService {
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinRepository checkinRepository;
    private final SceneRepository sceneRepository;
    private final QuizQuestionService quizQuestionService;
    private final Map<String, BossBattleState> activeBattles = new ConcurrentHashMap<>();

    public BossService(CityRepository cityRepository, UserRepository userRepository, UserProgressRepository userProgressRepository,
                       CheckinRepository checkinRepository, SceneRepository sceneRepository,
                       QuizQuestionService quizQuestionService) {
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinRepository = checkinRepository;
        this.sceneRepository = sceneRepository;
        this.quizQuestionService = quizQuestionService;
    }

    public BossStartResponse startChallenge(Long userId, Long cityId,
                                            GameDifficulty requestedDifficulty) {
        bossContext(userId, cityId);
        GameDifficulty difficulty = requestedDifficulty == null
                ? GameDifficulty.CASUAL : requestedDifficulty;
        BossBattleState state = new BossBattleState(difficulty);
        activeBattles.put(battleKey(userId, cityId), state);

        Map<String, Object> question;
        try {
            question = quizQuestionService.randomBossQuestion(
                    userId, cityId, difficulty.name());
        } catch (RuntimeException exception) {
            activeBattles.remove(battleKey(userId, cityId), state);
            throw exception;
        }

        return new BossStartResponse(
                difficulty.name(),
                difficulty.seconds(),
                difficulty.lives(),
                0,
                question
        );
    }

    public boolean challenge(Long userId, Long cityId) {
        return challenge(userId, cityId, null);
    }

    public boolean challenge(Long userId, Long cityId, String selectedAnswer) {
        return challenge(userId, cityId, selectedAnswer, null);
    }

    @Transactional
    public boolean challenge(Long userId, Long cityId, String selectedAnswer, String selectedAnswerText) {
        return Boolean.TRUE.equals(challengeResult(userId, cityId, selectedAnswer, selectedAnswerText, null, null).get("win"));
    }

    @Transactional
    public Map<String, Object> challengeResult(Long userId, Long cityId, String selectedAnswer, String selectedAnswerText,
                                               String questionId, String difficultyName) {
        BossContext context = bossContext(userId, cityId);
        User user = context.user();
        City city = context.city();
        UserProgress progress = context.progress();

        BossBattleState battleState = null;
        if (questionId != null && !questionId.isBlank()) {
            battleState = activeBattles.remove(battleKey(userId, cityId));
            if (battleState == null) {
                throw new IllegalArgumentException("boss battle has not been started");
            }
            GameDifficulty submittedDifficulty = GameDifficulty.from(difficultyName);
            if (battleState.difficulty() != submittedDifficulty) {
                throw new IllegalArgumentException("boss difficulty does not match active battle");
            }
        }

        int userPower = (user.getLevel() == null ? 1 : user.getLevel()) * 10
                + ((user.getExp() == null ? 0 : user.getExp()) / 10);
        int bossPower = city.getBossPower() == null ? 0 : city.getBossPower();
        boolean answerCorrect = questionId == null || questionId.isBlank()
                ? bossAnswerCorrect(city, selectedAnswer, selectedAnswerText)
                : quizQuestionService.bossAnswerCorrect(userId, city, questionId, selectedAnswerText, difficultyName);
        boolean win = answerCorrect && userPower >= bossPower;
        GameDifficulty difficulty = battleState == null
                ? GameDifficulty.from(difficultyName) : battleState.difficulty();
        int earnedExp = 0;
        int earnedCoins = 0;

        if (win) {
            boolean firstClear = !Boolean.TRUE.equals(progress.getBadgeUnlocked());
            progress.setBossUnlocked(true);
            progress.setBossCompleted(true);
            progress.setBadgeUnlocked(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            userProgressRepository.save(progress);

            if (firstClear) {
                unlockNextCity(userId, city);
                earnedExp = difficulty.reward(260);
                earnedCoins = difficulty.reward(300);
                grantFirstClearRewards(user, earnedExp, earnedCoins);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("win", win);
        result.put("earnedExp", earnedExp);
        result.put("earnedCoins", earnedCoins);
        return result;
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
            return false;
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

        validateBattleResult(request, cityTotal, sceneRepository.findByCityId(cityId));

        String oldRank = progress.getBestRank();
        int oldBestCombo = progress.getBestCombo() == null ? 0 : progress.getBestCombo();
        int oldBestLives = progress.getBestRemainingLives() == null ? 0 : progress.getBestRemainingLives();
        String newRank = calculateRank(request);
        int maxCombo = valueOrZero(request.getMaxCombo());
        GameDifficulty difficulty = GameDifficulty.from(request.getDifficulty());
        int remainingLives = Math.max(0, Math.min(difficulty.lives(), valueOrZero(request.getRemainingLives())));

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

        checkinRepository.deleteIncompleteByUserIdAndCityId(userId, cityId);
        activeBattles.remove(battleKey(userId, cityId));
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
        GameDifficulty difficulty = GameDifficulty.from(request.getDifficulty());
        int remainingLives = Math.max(0, Math.min(difficulty.lives(), valueOrZero(request.getRemainingLives())));
        int wrongAnswers = Math.max(0, valueOrZero(request.getWrongAnswers()));
        int timeoutCount = Math.max(0, valueOrZero(request.getTimeoutCount()));
        if (remainingLives == difficulty.lives() && wrongAnswers == 0 && timeoutCount == 0) {
            return "S";
        }
        if (remainingLives >= Math.max(2, (int) Math.ceil(difficulty.lives() * 0.66))) {
            return "A";
        }
        if (remainingLives == 1) {
            return "B";
        }
        return "C";
    }

    private void validateBattleResult(BattleResultRequest request, long cityTotal, java.util.List<Scene> scenes) {
        if (request == null) {
            throw new IllegalArgumentException("battle result is required");
        }

        String requestedRank = request.getRank() == null ? "" : request.getRank().trim().toUpperCase();
        if (rankScore(requestedRank) == 0) {
            throw new IllegalArgumentException("invalid battle rank");
        }

        GameDifficulty difficulty = GameDifficulty.from(request.getDifficulty());
        int maxQuestions = Math.toIntExact(cityTotal) + 1;
        int maxCombo = valueOrZero(request.getMaxCombo());
        int remainingLives = valueOrZero(request.getRemainingLives());
        int correctAnswers = valueOrZero(request.getCorrectAnswers());
        int wrongAnswers = valueOrZero(request.getWrongAnswers());
        int timeoutCount = valueOrZero(request.getTimeoutCount());
        int failures = wrongAnswers + timeoutCount;

        if (maxCombo < 0 || maxCombo > maxQuestions
                || remainingLives < 0 || remainingLives > difficulty.lives()
                || correctAnswers < 0 || correctAnswers > maxQuestions
                || wrongAnswers < 0 || timeoutCount < 0 || failures > difficulty.lives()
                || remainingLives != Math.max(0, difficulty.lives() - failures)) {
            throw new IllegalArgumentException("invalid battle statistics");
        }

        String calculatedRank = calculateRank(request);
        if (!requestedRank.equals(calculatedRank)) {
            throw new IllegalArgumentException("battle rank does not match statistics");
        }

        int maximumExp = scenes.stream().mapToInt(scene -> difficulty.reward(valueOrZero(scene.getExpReward()))).sum()
                + difficulty.reward(260);
        int maximumCoins = scenes.stream().mapToInt(scene -> difficulty.reward(valueOrZero(scene.getCoinReward()))).sum()
                + difficulty.reward(300);
        int claimedExp = valueOrZero(request.getEarnedExp());
        int claimedCoins = valueOrZero(request.getEarnedCoins());
        if (claimedExp < 0 || claimedExp > maximumExp || claimedCoins < 0 || claimedCoins > maximumCoins) {
            throw new IllegalArgumentException("invalid claimed rewards");
        }
    }

    private void unlockNextCity(Long userId, City city) {
        cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                .filter(next -> next.getUnlockOrder() != null && city.getUnlockOrder() != null)
                .filter(next -> next.getUnlockOrder().equals(city.getUnlockOrder() + 1))
                .findFirst()
                .ifPresent(next -> userProgressRepository.findByUserIdAndCityId(userId, next.getId())
                        .ifPresent(nextProgress -> {
                            nextProgress.setUnlocked(true);
                            userProgressRepository.save(nextProgress);
                        }));
    }

    private void grantFirstClearRewards(User user, int expReward, int coinReward) {
        user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + coinReward);
        user.setExp((user.getExp() == null ? 0 : user.getExp()) + expReward);
        user.setBossPoints((user.getBossPoints() == null ? 0 : user.getBossPoints()) + 1);
        int level = JourneyStateService.calculateLevelInfo(user.getExp() == null ? 0 : user.getExp()).level();
        user.setLevel(level);
        user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
        userRepository.save(user);
    }

    private BossContext bossContext(Long userId, Long cityId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<City> optionalCity = cityRepository.findById(cityId);
        if (optionalUser.isEmpty() || optionalCity.isEmpty()) {
            throw new IllegalArgumentException("invalid user or city");
        }

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
        if (!Boolean.TRUE.equals(progress.getBossUnlocked())) {
            throw new IllegalArgumentException("city boss is not unlocked");
        }
        return new BossContext(optionalUser.get(), optionalCity.get(), progress);
    }

    private String battleKey(Long userId, Long cityId) {
        return "%d:%d".formatted(userId, cityId);
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

    private record BossContext(User user, City city, UserProgress progress) {
    }

    private record BossBattleState(GameDifficulty difficulty) {
    }
}
