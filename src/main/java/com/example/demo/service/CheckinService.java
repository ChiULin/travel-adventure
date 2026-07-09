package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CheckinService {
    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;

    public CheckinService(CheckinRepository checkinRepository, UserRepository userRepository, SceneRepository sceneRepository) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.sceneRepository = sceneRepository;
    }

    public Checkin checkin(Long userId, Long sceneId) {
        Optional<User> ou = userRepository.findById(userId);
        Optional<Scene> os = sceneRepository.findById(sceneId);
        if (ou.isEmpty() || os.isEmpty()) {
            throw new IllegalArgumentException("invalid user or scene");
        }

        User user = ou.get();
        Scene scene = os.get();
        if (checkinRepository.existsByUserIdAndSceneId(userId, sceneId)) {
            throw new IllegalArgumentException("scene already checked in");
        }

        Checkin c = Checkin.builder()
                .user(user)
                .scene(scene)
                .checkinTime(LocalDateTime.now())
                .build();
        checkinRepository.save(c);

        user.setExp((user.getExp() == null ? 0 : user.getExp()) + (scene.getExpReward() == null ? 0 : scene.getExpReward()));
        user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + (scene.getCoinReward() == null ? 0 : scene.getCoinReward()));
        int level = (user.getExp() == null ? 0 : user.getExp()) / 220 + 1;
        user.setLevel(level);
        user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
        userRepository.save(user);

        return c;
    }
}
