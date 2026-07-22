package com.example.demo.service;

import com.example.demo.entity.Scene;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImageRecognitionService {
    private final ImageRecognitionRegistry registry;
    private final SceneRepository sceneRepository;
    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinService checkinService;
    private final Clock clock;
    private final Map<String, PendingImageChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, String> activeChallengeIds = new ConcurrentHashMap<>();
    private final Object issuanceMonitor = new Object();

    @Autowired
    public ImageRecognitionService(ImageRecognitionRegistry registry,
                                   SceneRepository sceneRepository,
                                   CheckinRepository checkinRepository,
                                   UserProgressRepository userProgressRepository,
                                   CheckinService checkinService) {
        this(registry, sceneRepository, checkinRepository, userProgressRepository,
                checkinService, Clock.systemUTC());
    }

    ImageRecognitionService(ImageRecognitionRegistry registry,
                            SceneRepository sceneRepository,
                            CheckinRepository checkinRepository,
                            UserProgressRepository userProgressRepository,
                            CheckinService checkinService,
                            Clock clock) {
        this.registry = registry;
        this.sceneRepository = sceneRepository;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinService = checkinService;
        this.clock = clock;
    }

    public ImageChallengeView issue(Long userId, Long sceneId, String difficultyName) {
        ImageRecognitionDefinition definition = registry.findByTargetSceneId(sceneId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "這個景點沒有圖片辨識挑戰"));
        validateCityUnlocked(userId, definition.cityId());
        validateSceneNotCompleted(userId, definition.targetSceneId());
        GameDifficulty difficulty = GameDifficulty.from(difficultyName);
        Instant now = clock.instant();
        String activeKey = activeKey(userId, sceneId);

        synchronized (issuanceMonitor) {
            String existingId = activeChallengeIds.get(activeKey);
            PendingImageChallenge existing = existingId == null ? null : challenges.get(existingId);
            if (existing != null && !existing.used && now.isBefore(existing.expiresAt)
                    && existing.difficulty == difficulty) {
                return challengeView(existing);
            }
            removeChallenge(existing);

            List<SceneOption> candidates = candidateOptions(definition, difficulty);
            Instant expiresAt = now.plusSeconds(difficulty.seconds());
            PendingImageChallenge challenge = new PendingImageChallenge(
                    "IMG-" + UUID.randomUUID(), userId, definition, difficulty,
                    now, expiresAt, candidates);
            challenges.put(challenge.questionId, challenge);
            activeChallengeIds.put(activeKey, challenge.questionId);
            return challengeView(challenge);
        }
    }

    public ImageChallengeResult complete(Long userId, String questionId, Long selectedSceneId,
                                         String difficultyName) {
        PendingImageChallenge challenge = challenges.get(questionId);
        if (challenge == null || challenge.used) {
            throw new IllegalArgumentException("圖片辨識題目不存在或已使用");
        }
        synchronized (challenge) {
            if (challenge.used || challenges.get(questionId) != challenge) {
                throw new IllegalArgumentException("圖片辨識題目不存在或已使用");
            }
            if (!challenge.userId.equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "這不是你的圖片辨識題目");
            }
            if (!clock.instant().isBefore(challenge.expiresAt)) {
                consume(challenge);
                throw new ResponseStatusException(HttpStatus.GONE, "圖片辨識題目已過期");
            }
            GameDifficulty submittedDifficulty = GameDifficulty.from(difficultyName);
            if (submittedDifficulty != challenge.difficulty) {
                throw new IllegalArgumentException("圖片辨識難度與簽發時不符");
            }
            SceneOption selected = challenge.candidates.stream()
                    .filter(candidate -> candidate.sceneId().equals(selectedSceneId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("選擇的景點不在候選清單中"));

            consume(challenge);
            boolean correct = challenge.definition.targetSceneId().equals(selectedSceneId);
            if (!correct) {
                return ImageChallengeResult.incorrect(selected);
            }

            CheckinService.ExplorationCheckinResult checkin = checkinService.completeExploration(
                    userId, challenge.definition.targetSceneId(), challenge.difficulty.name());
            return new ImageChallengeResult(
                    true,
                    true,
                    checkin.sceneId(),
                    checkin.sceneName(),
                    challenge.definition.cultureExplanation(),
                    checkin.experienceGained(),
                    checkin.coinsGained(),
                    checkin.levelUp(),
                    checkin.cityBossUnlocked()
            );
        }
    }

    @Scheduled(fixedDelayString = "${game.image-recognition.cleanup-interval-ms:60000}")
    public void removeExpiredChallenges() {
        Instant now = clock.instant();
        challenges.values().forEach(challenge -> {
            if (!now.isBefore(challenge.expiresAt)) {
                synchronized (challenge) {
                    if (!now.isBefore(challenge.expiresAt)) {
                        consume(challenge);
                    }
                }
            }
        });
    }

    private ImageChallengeView challengeView(PendingImageChallenge challenge) {
        return new ImageChallengeView(
                challenge.questionId,
                challenge.definition.cityId(),
                challenge.definition.prompt(),
                challenge.definition.imageUrl(),
                displayMode(challenge.difficulty),
                blurLevel(challenge.difficulty),
                challenge.difficulty.name(),
                challenge.difficulty.seconds(),
                challenge.issuedAt,
                challenge.expiresAt,
                challenge.candidates
        );
    }

    private List<SceneOption> candidateOptions(ImageRecognitionDefinition definition,
                                               GameDifficulty difficulty) {
        int candidateCount = difficulty == GameDifficulty.CASUAL ? 3 : 4;
        List<Long> selectedIds = new ArrayList<>();
        selectedIds.add(definition.targetSceneId());
        List<Long> distractors = new ArrayList<>(definition.candidateSceneIds().stream()
                .filter(sceneId -> !sceneId.equals(definition.targetSceneId()))
                .toList());
        Collections.shuffle(distractors);
        selectedIds.addAll(distractors.stream().limit(candidateCount - 1L).toList());

        Map<Long, Scene> scenesById = sceneRepository.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity()));
        List<SceneOption> candidates = selectedIds.stream().map(sceneId -> {
            Scene scene = scenesById.get(sceneId);
            if (scene == null) {
                throw new IllegalStateException("圖片辨識候選景點設定不完整：" + definition.challengeKey());
            }
            return new SceneOption(scene.getId(), scene.getName());
        }).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        return List.copyOf(candidates);
    }

    private String displayMode(GameDifficulty difficulty) {
        return difficulty == GameDifficulty.CASUAL ? "FULL" : "BLUR";
    }

    private int blurLevel(GameDifficulty difficulty) {
        return switch (difficulty) {
            case CASUAL -> 1;
            case NORMAL -> 6;
            case EXTREME -> 12;
        };
    }

    private void validateCityUnlocked(Long userId, Long cityId) {
        boolean unlocked = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .orElse(false);
        if (!unlocked) {
            throw new IllegalArgumentException("城市尚未解鎖");
        }
    }

    private void validateSceneNotCompleted(Long userId, Long sceneId) {
        boolean completed = checkinRepository.findByUserIdAndSceneId(userId, sceneId)
                .map(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .orElse(false);
        if (completed) {
            throw new IllegalArgumentException("這個景點已經完成打卡");
        }
    }

    private void consume(PendingImageChallenge challenge) {
        challenge.used = true;
        challenges.remove(challenge.questionId, challenge);
        activeChallengeIds.remove(activeKey(challenge.userId, challenge.definition.targetSceneId()),
                challenge.questionId);
    }

    private void removeChallenge(PendingImageChallenge challenge) {
        if (challenge == null) {
            return;
        }
        synchronized (challenge) {
            consume(challenge);
        }
    }

    private String activeKey(Long userId, Long sceneId) {
        return "%d:%d".formatted(userId, sceneId);
    }

    private static final class PendingImageChallenge {
        private final String questionId;
        private final Long userId;
        private final ImageRecognitionDefinition definition;
        private final GameDifficulty difficulty;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final List<SceneOption> candidates;
        private boolean used;

        private PendingImageChallenge(String questionId, Long userId,
                                      ImageRecognitionDefinition definition,
                                      GameDifficulty difficulty, Instant issuedAt,
                                      Instant expiresAt, List<SceneOption> candidates) {
            this.questionId = questionId;
            this.userId = userId;
            this.definition = definition;
            this.difficulty = difficulty;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.candidates = candidates;
        }
    }

    public record SceneOption(Long sceneId, String name) {
    }

    public record ImageChallengeView(
            String questionId,
            Long cityId,
            String prompt,
            String imageUrl,
            String displayMode,
            int blurLevel,
            String difficulty,
            int seconds,
            Instant issuedAt,
            Instant expiresAt,
            List<SceneOption> candidates
    ) {
    }

    public record ImageChallengeResult(
            boolean correct,
            boolean completed,
            Long sceneId,
            String sceneName,
            String cultureExplanation,
            int experienceGained,
            int coinsGained,
            boolean levelUp,
            boolean cityBossUnlocked
    ) {
        private static ImageChallengeResult incorrect(SceneOption selected) {
            return new ImageChallengeResult(
                    false, false, selected.sceneId(), selected.name(),
                    null, 0, 0, false, false);
        }
    }
}
