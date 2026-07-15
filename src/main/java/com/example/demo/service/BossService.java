package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

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
        boolean answerCorrect = bossAnswerCorrect(city, selectedAnswer);
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

    private boolean bossAnswerCorrect(City city, String selectedAnswer) {
        String correctAnswer = normalizeAnswer(city.getBossCorrectAnswer());
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
