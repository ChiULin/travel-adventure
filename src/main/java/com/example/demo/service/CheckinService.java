package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CheckinService {
    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final JourneyStateService journeyStateService;

    public CheckinService(CheckinRepository checkinRepository, UserRepository userRepository, SceneRepository sceneRepository,
                          JourneyStateService journeyStateService) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.sceneRepository = sceneRepository;
        this.journeyStateService = journeyStateService;
    }

    public Checkin checkin(Long userId, Long sceneId) {
        return checkin(userId, sceneId, null);
    }

    public Checkin checkin(Long userId, Long sceneId, String selectedAnswer) {
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
        Optional<Checkin> existing = checkinRepository.findByUserIdAndSceneId(userId, sceneId);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getCompleted())) {
            throw new IllegalArgumentException("scene already checked in");
        }

        Checkin c = existing.orElseGet(() -> Checkin.builder()
                .user(user)
                .scene(scene)
                .quizCorrect(false)
                .completed(false)
                .earnedExp(0)
                .earnedCoins(0)
                .build());
        c.setCheckinTime(LocalDateTime.now());
        c.setSelectedAnswer(normalizeAnswer(selectedAnswer));

        boolean correct = isCorrect(scene, selectedAnswer);
        c.setQuizCorrect(correct);
        if (!correct) {
            return checkinRepository.save(c);
        }

        int expReward = scene.getExpReward() == null ? 0 : scene.getExpReward();
        int coinReward = scene.getCoinReward() == null ? 0 : scene.getCoinReward();
        c.setCompleted(true);
        c.setEarnedExp(expReward);
        c.setEarnedCoins(coinReward);
        c.setCompletedAt(LocalDateTime.now());
        checkinRepository.save(c);

        user.setExp((user.getExp() == null ? 0 : user.getExp()) + expReward);
        user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + coinReward);
        int level = JourneyStateService.calculateLevelInfo(user.getExp() == null ? 0 : user.getExp()).level();
        user.setLevel(level);
        user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
        userRepository.save(user);
        journeyStateService.updateBossUnlock(userId, scene.getCity().getId());

        return c;
    }

    private boolean isCorrect(Scene scene, String selectedAnswer) {
        String correctAnswer = normalizeAnswer(scene.getQuizCorrectAnswer());
        String answer = normalizeAnswer(selectedAnswer);
        if (correctAnswer == null || correctAnswer.isBlank()) {
            return true;
        }
        if (answer == null || answer.isBlank()) {
            return true;
        }
        return correctAnswer.equals(answer);
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        return answer.trim().toUpperCase();
    }
}
