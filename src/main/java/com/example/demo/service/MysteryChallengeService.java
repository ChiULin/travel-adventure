package com.example.demo.service;

import com.example.demo.entity.Scene;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import com.example.demo.stage.LandmarkStageService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

@Service
public class MysteryChallengeService {

    private final SceneRepository sceneRepository;
    private final CityRepository cityRepository;
    private final LandmarkStageRegistry stageRegistry;
    private final LandmarkStageService stageService;
    private final LandmarkChallengePoolRegistry challengePoolRegistry;
    private final QuizQuestionService quizQuestionService;
    private final ExplorationService explorationService;
    private final ImageRecognitionService imageRecognitionService;
    private final PuzzleChallengeService puzzleChallengeService;
    private final Clock clock;
    private final IntUnaryOperator randomIndex;
    private final Map<String, ActiveMysteryChallenge> activeChallenges = new ConcurrentHashMap<>();
    private final Map<String, MysteryChallengeType> previousChallengeTypes = new ConcurrentHashMap<>();
    private final Object challengeMonitor = new Object();

    @Autowired
    public MysteryChallengeService(
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            LandmarkStageRegistry stageRegistry,
            LandmarkStageService stageService,
            LandmarkChallengePoolRegistry challengePoolRegistry,
            QuizQuestionService quizQuestionService,
            ExplorationService explorationService,
            ImageRecognitionService imageRecognitionService,
            PuzzleChallengeService puzzleChallengeService
    ) {
        this(sceneRepository, cityRepository, stageRegistry, stageService, challengePoolRegistry,
                quizQuestionService, explorationService, imageRecognitionService,
                puzzleChallengeService, Clock.systemUTC(),
                bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    MysteryChallengeService(
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            LandmarkStageRegistry stageRegistry,
            LandmarkStageService stageService,
            LandmarkChallengePoolRegistry challengePoolRegistry,
            QuizQuestionService quizQuestionService,
            ExplorationService explorationService,
            ImageRecognitionService imageRecognitionService,
            PuzzleChallengeService puzzleChallengeService,
            Clock clock
    ) {
        this(sceneRepository, cityRepository, stageRegistry, stageService, challengePoolRegistry,
                quizQuestionService, explorationService, imageRecognitionService,
                puzzleChallengeService, clock,
                bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    MysteryChallengeService(
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            LandmarkStageRegistry stageRegistry,
            LandmarkStageService stageService,
            LandmarkChallengePoolRegistry challengePoolRegistry,
            QuizQuestionService quizQuestionService,
            ExplorationService explorationService,
            ImageRecognitionService imageRecognitionService,
            PuzzleChallengeService puzzleChallengeService,
            Clock clock,
            IntUnaryOperator randomIndex
    ) {
        this.sceneRepository = sceneRepository;
        this.cityRepository = cityRepository;
        this.stageRegistry = stageRegistry;
        this.stageService = stageService;
        this.challengePoolRegistry = challengePoolRegistry;
        this.quizQuestionService = quizQuestionService;
        this.explorationService = explorationService;
        this.imageRecognitionService = imageRecognitionService;
        this.puzzleChallengeService = puzzleChallengeService;
        this.clock = clock;
        this.randomIndex = randomIndex;
    }

    @Transactional(readOnly = true)
    public MysteryChallengeResponse start(Long userId, Long landmarkId, String difficultyName) {
        Scene scene = sceneRepository.findById(landmarkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到景點"));
        LandmarkStageDefinition stage = stageRegistry.findByLandmarkId(landmarkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "這個景點尚未啟用神秘挑戰"));
        stageService.validateStageAvailable(userId, landmarkId);
        GameDifficulty difficulty = GameDifficulty.from(difficultyName);
        int cityOrder = cityRepository.findById(stage.cityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到城市"))
                .getUnlockOrder();
        List<MysteryChallengeType> candidates =
                challengePoolRegistry.getAvailableTypes(cityOrder, stage.stageOrder());
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "這個景點尚未啟用神秘挑戰");
        }

        String key = challengeKey(userId, landmarkId);
        synchronized (challengeMonitor) {
            Instant now = clock.instant();
            ActiveMysteryChallenge current = activeChallenges.get(key);
            if (current != null && !current.consumed() && now.isBefore(current.expiresAt())) {
                return response(current);
            }

            MysteryChallengeType selected = selectChallengeType(key, candidates);
            String sessionId = "MC-" + UUID.randomUUID();
            IssuedChildChallenge child = issueChildChallenge(
                    selected, userId, landmarkId, difficulty.name(), sessionId);
            ActiveMysteryChallenge created = new ActiveMysteryChallenge(
                    sessionId,
                    userId,
                    stage.cityId(),
                    landmarkId,
                    difficulty,
                    selected,
                    child.challengeId(),
                    now,
                    child.expiresAt(),
                    false,
                    child.challengeData()
            );
            activeChallenges.put(key, created);
            previousChallengeTypes.put(key, selected);
            return response(created);
        }
    }

    public void markConsumed(Long userId, String childChallengeId) {
        if (childChallengeId == null || childChallengeId.isBlank()) {
            return;
        }
        activeChallenges.forEach((key, challenge) -> {
            if (challenge.userId().equals(userId)
                    && challenge.childChallengeId().equals(childChallengeId)
                    && !challenge.consumed()) {
                activeChallenges.replace(key, challenge, challenge.consume());
            }
        });
    }

    @Scheduled(fixedDelayString = "${game.mystery-challenge.cleanup-interval-ms:60000}")
    public void removeExpiredChallenges() {
        Instant now = clock.instant();
        activeChallenges.entrySet().removeIf(entry ->
                entry.getValue().consumed() || !now.isBefore(entry.getValue().expiresAt()));
    }

    private MysteryChallengeType selectChallengeType(
            String key,
            List<MysteryChallengeType> candidates
    ) {
        MysteryChallengeType previous = previousChallengeTypes.get(key);
        List<MysteryChallengeType> selectable = candidates.size() > 1
                ? candidates.stream().filter(type -> type != previous).toList()
                : candidates;
        return selectable.get(randomIndex.applyAsInt(selectable.size()));
    }

    private IssuedChildChallenge issueChildChallenge(
            MysteryChallengeType type,
            Long userId,
            Long landmarkId,
            String difficulty,
            String mysterySessionId
    ) {
        return switch (type) {
            case QUIZ -> {
                Map<String, Object> question =
                        quizQuestionService.randomSceneQuestion(userId, landmarkId, difficulty);
                yield new IssuedChildChallenge(
                        String.valueOf(question.get("questionId")),
                        (Instant) question.get("expiresAt"),
                        question
                );
            }
            case EXPLORATION -> {
                ExplorationService.ExplorationMissionView mission =
                        explorationService.missionForScene(userId, landmarkId);
                yield new IssuedChildChallenge(mission.missionId(), mission.expiresAt(), mission);
            }
            case IMAGE_RECOGNITION -> {
                ImageRecognitionService.ImageChallengeView challenge =
                        imageRecognitionService.issue(userId, landmarkId, difficulty);
                yield new IssuedChildChallenge(
                        challenge.questionId(), challenge.expiresAt(), challenge);
            }
            case PUZZLE -> {
                PuzzleChallengeService.PuzzleChallengeView challenge =
                        puzzleChallengeService.issue(
                                userId, landmarkId, difficulty, mysterySessionId);
                yield new IssuedChildChallenge(
                        challenge.challengeId(), challenge.expiresAt(), challenge);
            }
        };
    }

    private MysteryChallengeResponse response(ActiveMysteryChallenge challenge) {
        return new MysteryChallengeResponse(
                challenge.sessionId(),
                challenge.landmarkId(),
                challenge.challengeType(),
                challengeTitle(challenge.challengeType()),
                challenge.difficulty(),
                challenge.expiresAt(),
                challenge.challengeData()
        );
    }

    private String challengeTitle(MysteryChallengeType type) {
        return switch (type) {
            case EXPLORATION -> "神秘旅人的委託";
            case IMAGE_RECOGNITION -> "重點圖片辨識";
            case QUIZ -> "城市居民的文化考驗";
            case PUZZLE -> "景點拼圖";
        };
    }

    private String challengeKey(Long userId, Long landmarkId) {
        return "%d:%d".formatted(userId, landmarkId);
    }

    private record IssuedChildChallenge(
            String challengeId,
            Instant expiresAt,
            Object challengeData
    ) {
    }

    public record ActiveMysteryChallenge(
            String sessionId,
            Long userId,
            Long cityId,
            Long landmarkId,
            GameDifficulty difficulty,
            MysteryChallengeType challengeType,
            String childChallengeId,
            Instant createdAt,
            Instant expiresAt,
            boolean consumed,
            Object challengeData
    ) {
        ActiveMysteryChallenge consume() {
            return new ActiveMysteryChallenge(
                    sessionId, userId, cityId, landmarkId, difficulty,
                    challengeType, childChallengeId, createdAt, expiresAt, true, challengeData);
        }
    }

    public record MysteryChallengeResponse(
            String challengeSessionId,
            Long landmarkId,
            MysteryChallengeType challengeType,
            String challengeTitle,
            GameDifficulty difficulty,
            Instant expiresAt,
            Object challengeData
    ) {
    }
}
