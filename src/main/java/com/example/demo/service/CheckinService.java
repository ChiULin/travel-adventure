package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CheckinService {
    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final UserProgressRepository userProgressRepository;
    private final JourneyStateService journeyStateService;
    private final QuizQuestionService quizQuestionService;

    public CheckinService(CheckinRepository checkinRepository, UserRepository userRepository, SceneRepository sceneRepository,
                          UserProgressRepository userProgressRepository, JourneyStateService journeyStateService,
                          QuizQuestionService quizQuestionService) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.sceneRepository = sceneRepository;
        this.userProgressRepository = userProgressRepository;
        this.journeyStateService = journeyStateService;
        this.quizQuestionService = quizQuestionService;
    }

    public Checkin checkin(Long userId, Long sceneId) {
        return checkin(userId, sceneId, null);
    }

    public Checkin checkin(Long userId, Long sceneId, String selectedAnswer) {
        return checkin(userId, sceneId, selectedAnswer, null);
    }

    @Transactional
    public Checkin checkin(Long userId, Long sceneId, String selectedAnswer, String selectedAnswerText) {
        return checkin(userId, sceneId, selectedAnswer, selectedAnswerText, null, null);
    }

    @Transactional
    public Checkin checkin(Long userId, Long sceneId, String selectedAnswer, String selectedAnswerText,
                           String questionId, String difficultyName) {
        CheckinContext context = prepareCheckin(userId, sceneId);
        Checkin checkin = context.checkin();
        Scene scene = context.scene();
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setSelectedAnswer(normalizeAnswer(selectedAnswer));

        boolean correct = questionId == null || questionId.isBlank()
                ? isCorrect(scene, selectedAnswer, selectedAnswerText)
                : quizQuestionService.sceneAnswerCorrect(userId, scene, questionId, selectedAnswerText, difficultyName);
        checkin.setQuizCorrect(correct);
        if (!correct) {
            return checkinRepository.save(checkin);
        }

        return completeCheckin(userId, context, GameDifficulty.from(difficultyName));
    }

    @Transactional
    public ExplorationCheckinResult completeExploration(Long userId, Long sceneId, String difficultyName) {
        CheckinContext context = prepareCheckin(userId, sceneId);
        int previousLevel = context.user().getLevel() == null ? 1 : context.user().getLevel();
        context.checkin().setCheckinTime(LocalDateTime.now());
        context.checkin().setSelectedAnswer(null);
        context.checkin().setQuizCorrect(true);

        Checkin completed = completeCheckin(userId, context, GameDifficulty.from(difficultyName));
        boolean bossUnlocked = userProgressRepository.findByUserIdAndCityId(userId, context.scene().getCity().getId())
                .map(progress -> Boolean.TRUE.equals(progress.getBossUnlocked()))
                .orElse(false);
        int currentLevel = context.user().getLevel() == null ? previousLevel : context.user().getLevel();
        return new ExplorationCheckinResult(
                context.scene().getId(),
                context.scene().getName(),
                completed.getEarnedExp() == null ? 0 : completed.getEarnedExp(),
                completed.getEarnedCoins() == null ? 0 : completed.getEarnedCoins(),
                currentLevel > previousLevel,
                bossUnlocked
        );
    }

    private CheckinContext prepareCheckin(Long userId, Long sceneId) {
        Optional<User> ou = userRepository.findById(userId);
        Optional<Scene> os = sceneRepository.findById(sceneId);
        if (ou.isEmpty()) {
            throw new IllegalArgumentException("invalid user");
        }
        if (os.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "scene not found");
        }

        User user = ou.get();
        Scene scene = os.get();
        boolean cityUnlocked = userProgressRepository.findByUserIdAndCityId(userId, scene.getCity().getId())
                .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .orElse(false);
        if (!cityUnlocked) {
            throw new IllegalArgumentException("city not unlocked");
        }
        Optional<Checkin> existing = checkinRepository.findByUserIdAndSceneId(userId, sceneId);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getCompleted())) {
            throw new IllegalArgumentException("scene already checked in");
        }

        Checkin checkin = existing.orElseGet(() -> Checkin.builder()
                .user(user)
                .scene(scene)
                .quizCorrect(false)
                .completed(false)
                .earnedExp(0)
                .earnedCoins(0)
                .build());
        return new CheckinContext(user, scene, checkin);
    }

    private Checkin completeCheckin(Long userId, CheckinContext context, GameDifficulty difficulty) {
        User user = context.user();
        Scene scene = context.scene();
        Checkin checkin = context.checkin();
        int expReward = difficulty.reward(scene.getExpReward() == null ? 0 : scene.getExpReward());
        int coinReward = difficulty.reward(scene.getCoinReward() == null ? 0 : scene.getCoinReward());
        checkin.setCompleted(true);
        checkin.setEarnedExp(expReward);
        checkin.setEarnedCoins(coinReward);
        checkin.setCompletedAt(LocalDateTime.now());
        checkinRepository.save(checkin);

        user.setExp((user.getExp() == null ? 0 : user.getExp()) + expReward);
        user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + coinReward);
        int level = JourneyStateService.calculateLevelInfo(user.getExp() == null ? 0 : user.getExp()).level();
        user.setLevel(level);
        user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
        userRepository.save(user);
        journeyStateService.updateBossUnlock(userId, scene.getCity().getId());

        return checkin;
    }

    private boolean isCorrect(Scene scene, String selectedAnswer, String selectedAnswerText) {
        String correctAnswer = normalizeAnswer(scene.getQuizCorrectAnswer());
        String answer = normalizeAnswer(selectedAnswer);
        if (correctAnswer == null || correctAnswer.isBlank()) {
            return true;
        }
        String correctText = optionText(scene, correctAnswer);
        String answerText = normalizeText(selectedAnswerText);
        if (answerText != null && correctText != null) {
            return answerText.equals(normalizeText(correctText));
        }
        if (answer == null || answer.isBlank()) {
            return false;
        }
        return correctAnswer.equals(answer);
    }

    private String optionText(Scene scene, String answer) {
        return switch (answer) {
            case "A" -> scene.getQuizOptionA();
            case "B" -> scene.getQuizOptionB();
            case "C" -> scene.getQuizOptionC();
            case "D" -> scene.getQuizOptionD();
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

    private record CheckinContext(User user, Scene scene, Checkin checkin) {
    }

    public record ExplorationCheckinResult(
            Long sceneId,
            String sceneName,
            int experienceGained,
            int coinsGained,
            boolean levelUp,
            boolean cityBossUnlocked
    ) {
    }
}
