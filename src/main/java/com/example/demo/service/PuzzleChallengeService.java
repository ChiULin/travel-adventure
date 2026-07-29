package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import com.example.demo.stage.LandmarkStageService;
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
import java.util.stream.IntStream;

@Service
public class PuzzleChallengeService {

    private static final int GRID_SIZE = 3;

    private final VisualChallengeRegistry registry;
    private final SceneRepository sceneRepository;
    private final CityRepository cityRepository;
    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinService checkinService;
    private final LandmarkStageRegistry stageRegistry;
    private final LandmarkStageService stageService;
    private final Clock clock;
    private final Map<String, ActivePuzzleChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, String> activeChallengeIds = new ConcurrentHashMap<>();
    private final Object issuanceMonitor = new Object();

    @Autowired
    public PuzzleChallengeService(
            VisualChallengeRegistry registry,
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            CheckinRepository checkinRepository,
            UserProgressRepository userProgressRepository,
            CheckinService checkinService,
            LandmarkStageRegistry stageRegistry,
            LandmarkStageService stageService
    ) {
        this(registry, sceneRepository, cityRepository, checkinRepository, userProgressRepository,
                checkinService, stageRegistry, stageService, Clock.systemUTC());
    }

    PuzzleChallengeService(
            VisualChallengeRegistry registry,
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            CheckinRepository checkinRepository,
            UserProgressRepository userProgressRepository,
            CheckinService checkinService,
            LandmarkStageRegistry stageRegistry,
            LandmarkStageService stageService,
            Clock clock
    ) {
        this.registry = registry;
        this.sceneRepository = sceneRepository;
        this.cityRepository = cityRepository;
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinService = checkinService;
        this.stageRegistry = stageRegistry;
        this.stageService = stageService;
        this.clock = clock;
    }

    public PuzzleChallengeView issue(
            Long userId,
            Long landmarkId,
            String difficultyName,
            String mysterySessionId
    ) {
        if (mysterySessionId == null || mysterySessionId.isBlank()) {
            throw new IllegalArgumentException("缺少神秘挑戰 Session");
        }
        sceneRepository.findById(landmarkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到景點"));
        LandmarkStageDefinition targetStage = stageRegistry.findByLandmarkId(landmarkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "這個景點沒有拼圖挑戰"));
        City targetCity = cityRepository.findById(targetStage.cityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到城市"));
        VisualChallengeDefinition definition = registry.findByStage(
                        targetCity.getUnlockOrder(), targetStage.stageOrder())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "這個景點沒有拼圖挑戰"));
        validateCityUnlocked(userId, targetCity.getId());
        stageService.validateStageAvailable(userId, landmarkId);
        validateSceneNotCompleted(userId, landmarkId);
        GameDifficulty difficulty = GameDifficulty.from(difficultyName);
        Instant now = clock.instant();
        String activeKey = activeKey(userId, landmarkId);

        synchronized (issuanceMonitor) {
            String existingId = activeChallengeIds.get(activeKey);
            ActivePuzzleChallenge existing =
                    existingId == null ? null : challenges.get(existingId);
            if (existing != null && !existing.consumed() && now.isBefore(existing.expiresAt())) {
                return challengeView(existing);
            }
            removeChallenge(existing);

            List<LandmarkOption> candidates = candidateOptions(definition);
            Long correctLandmarkId = resolveSceneIds(List.of(definition.correctStage()))
                    .get(definition.correctStage());
            Instant expiresAt = now.plusSeconds(timeLimit(difficulty));
            ActivePuzzleChallenge challenge = new ActivePuzzleChallenge(
                    "PUZ-" + UUID.randomUUID(),
                    mysterySessionId,
                    userId,
                    targetCity.getId(),
                    landmarkId,
                    correctLandmarkId,
                    difficulty,
                    GRID_SIZE,
                    shuffledTileOrder(),
                    now,
                    expiresAt,
                    false,
                    definition,
                    candidates
            );
            challenges.put(challenge.challengeId(), challenge);
            activeChallengeIds.put(activeKey, challenge.challengeId());
            return challengeView(challenge);
        }
    }

    public PuzzleChallengeResult complete(
            Long userId,
            String challengeId,
            Long selectedLandmarkId
    ) {
        ActivePuzzleChallenge challenge = challenges.get(challengeId);
        if (challenge == null || challenge.consumed()) {
            throw new IllegalArgumentException("拼圖挑戰不存在或已使用");
        }
        synchronized (challenge) {
            if (challenge.consumed() || challenges.get(challengeId) != challenge) {
                throw new IllegalArgumentException("拼圖挑戰不存在或已使用");
            }
            if (!challenge.userId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "這不是你的拼圖挑戰");
            }
            if (!clock.instant().isBefore(challenge.expiresAt())) {
                consume(challenge);
                throw new ResponseStatusException(HttpStatus.GONE, "拼圖挑戰已過期");
            }
            LandmarkOption selected = challenge.candidates().stream()
                    .filter(candidate -> candidate.landmarkId().equals(selectedLandmarkId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("選擇的景點不在候選清單中"));

            consume(challenge);
            if (!challenge.correctLandmarkId().equals(selectedLandmarkId)) {
                return PuzzleChallengeResult.incorrect(selected);
            }

            CheckinService.ExplorationCheckinResult checkin = checkinService.completeExploration(
                    userId, challenge.landmarkId(), challenge.difficulty().name());
            return new PuzzleChallengeResult(
                    true,
                    true,
                    checkin.sceneId(),
                    checkin.sceneName(),
                    checkin.experienceGained(),
                    checkin.coinsGained(),
                    checkin.levelUp(),
                    checkin.cityBossUnlocked()
            );
        }
    }

    @Scheduled(fixedDelayString = "${game.puzzle.cleanup-interval-ms:60000}")
    public void removeExpiredChallenges() {
        Instant now = clock.instant();
        challenges.values().forEach(challenge -> {
            if (challenge.consumed() || !now.isBefore(challenge.expiresAt())) {
                synchronized (challenge) {
                    if (challenge.consumed() || !now.isBefore(challenge.expiresAt())) {
                        consume(challenge);
                    }
                }
            }
        });
    }

    private PuzzleChallengeView challengeView(ActivePuzzleChallenge challenge) {
        return new PuzzleChallengeView(
                challenge.challengeId(),
                challenge.definition().puzzleImageUrl(),
                challenge.gridSize(),
                challenge.initialTileOrder(),
                challenge.difficulty().name(),
                timeLimit(challenge.difficulty()),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.candidates()
        );
    }

    private List<Integer> shuffledTileOrder() {
        List<Integer> order = IntStream.range(0, GRID_SIZE * GRID_SIZE)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        do {
            Collections.shuffle(order);
        } while (isSolved(order));
        return List.copyOf(order);
    }

    private boolean isSolved(List<Integer> order) {
        return IntStream.range(0, order.size()).allMatch(index -> order.get(index) == index);
    }

    private int timeLimit(GameDifficulty difficulty) {
        return switch (difficulty) {
            case CASUAL -> 60;
            case NORMAL -> 45;
            case EXTREME -> 30;
        };
    }

    private List<LandmarkOption> candidateOptions(VisualChallengeDefinition definition) {
        Map<VisualChallengeKey, Long> sceneIdsByStage = resolveSceneIds(definition.candidateStages());
        List<Long> sceneIds = definition.candidateStages().stream()
                .map(sceneIdsByStage::get)
                .toList();
        Map<Long, Scene> scenesById = sceneRepository.findAllById(sceneIds).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity()));
        List<LandmarkOption> candidates = sceneIds.stream()
                .map(sceneId -> {
                    Scene scene = scenesById.get(sceneId);
                    if (scene == null) {
                        throw new IllegalStateException(
                                "拼圖候選景點設定不完整：" + definition.challengeKey());
                    }
                    return new LandmarkOption(scene.getId(), scene.getName());
                })
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        return List.copyOf(candidates);
    }

    private Map<VisualChallengeKey, Long> resolveSceneIds(List<VisualChallengeKey> stageKeys) {
        Map<Integer, City> citiesByOrder = cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                .collect(Collectors.toMap(City::getUnlockOrder, Function.identity()));
        return stageKeys.stream().collect(Collectors.toMap(
                Function.identity(),
                stageKey -> {
                    City city = citiesByOrder.get(stageKey.cityOrder());
                    if (city == null) {
                        throw new IllegalStateException("拼圖候選城市設定不完整：" + stageKey.cityOrder());
                    }
                    return stageRegistry.findByCityId(city.getId()).stream()
                            .filter(stage -> stage.stageOrder() == stageKey.stageOrder())
                            .map(LandmarkStageDefinition::landmarkId)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "拼圖候選關卡設定不完整：" + stageKey));
                }
        ));
    }

    private void validateCityUnlocked(Long userId, Long cityId) {
        boolean unlocked = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .orElse(false);
        if (!unlocked) {
            throw new IllegalArgumentException("城市尚未解鎖");
        }
    }

    private void validateSceneNotCompleted(Long userId, Long landmarkId) {
        boolean completed = checkinRepository.findByUserIdAndSceneId(userId, landmarkId)
                .map(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .orElse(false);
        if (completed) {
            throw new IllegalArgumentException("這個景點已經完成打卡");
        }
    }

    private void consume(ActivePuzzleChallenge challenge) {
        ActivePuzzleChallenge consumed = challenge.consume();
        challenges.replace(challenge.challengeId(), challenge, consumed);
        challenges.remove(challenge.challengeId(), consumed);
        activeChallengeIds.remove(activeKey(challenge.userId(), challenge.landmarkId()),
                challenge.challengeId());
    }

    private void removeChallenge(ActivePuzzleChallenge challenge) {
        if (challenge == null) {
            return;
        }
        synchronized (challenge) {
            consume(challenge);
        }
    }

    private String activeKey(Long userId, Long landmarkId) {
        return "%d:%d".formatted(userId, landmarkId);
    }

    public record LandmarkOption(Long landmarkId, String name) {
    }

    public record PuzzleChallengeView(
            String challengeId,
            String imageUrl,
            int gridSize,
            List<Integer> initialTileOrder,
            String difficulty,
            int seconds,
            Instant issuedAt,
            Instant expiresAt,
            List<LandmarkOption> candidates
    ) {
    }

    public record PuzzleChallengeResult(
            boolean correct,
            boolean completed,
            Long sceneId,
            String sceneName,
            int experienceGained,
            int coinsGained,
            boolean levelUp,
            boolean cityBossUnlocked
    ) {
        private static PuzzleChallengeResult incorrect(LandmarkOption selected) {
            return new PuzzleChallengeResult(
                    false, false, selected.landmarkId(), selected.name(),
                    0, 0, false, false);
        }
    }

    public record ActivePuzzleChallenge(
            String challengeId,
            String mysterySessionId,
            Long userId,
            Long cityId,
            Long landmarkId,
            Long correctLandmarkId,
            GameDifficulty difficulty,
            int gridSize,
            List<Integer> initialTileOrder,
            Instant createdAt,
            Instant expiresAt,
            boolean consumed,
            VisualChallengeDefinition definition,
            List<LandmarkOption> candidates
    ) {
        private ActivePuzzleChallenge consume() {
            return new ActivePuzzleChallenge(
                    challengeId, mysterySessionId, userId, cityId, landmarkId,
                    correctLandmarkId, difficulty, gridSize, initialTileOrder,
                    createdAt, expiresAt, true, definition, candidates);
        }
    }
}
