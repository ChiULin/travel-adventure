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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExplorationService {
    private static final int INITIAL_ACTIONS = 4;
    private static final Duration MISSION_DURATION = Duration.ofMinutes(30);
    private static final Duration CULTURE_CHALLENGE_DURATION = Duration.ofMinutes(2);

    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinService checkinService;
    private final ExplorationMissionRegistry missionRegistry;
    private final SceneRepository sceneRepository;
    private final Clock clock;
    private final Map<String, ExplorationState> explorationStates = new ConcurrentHashMap<>();

    @Autowired
    public ExplorationService(CheckinRepository checkinRepository,
                              UserProgressRepository userProgressRepository,
                              CheckinService checkinService,
                              ExplorationMissionRegistry missionRegistry,
                              SceneRepository sceneRepository) {
        this(checkinRepository, userProgressRepository, checkinService,
                missionRegistry, sceneRepository, Clock.systemUTC());
    }

    ExplorationService(CheckinRepository checkinRepository,
                       UserProgressRepository userProgressRepository,
                       CheckinService checkinService,
                       ExplorationMissionRegistry missionRegistry,
                       SceneRepository sceneRepository,
                       Clock clock) {
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinService = checkinService;
        this.missionRegistry = missionRegistry;
        this.sceneRepository = sceneRepository;
        this.clock = clock;
    }

    public ExplorationMissionView randomMission(Long userId, Long cityId) {
        List<ExplorationMissionDefinition> cityMissions = missionRegistry.findByCityId(cityId);
        if (cityMissions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "這座城市目前沒有探索任務");
        }
        validateCityUnlocked(userId, cityId);

        List<ExplorationMissionDefinition> available = cityMissions.stream()
                .filter(mission -> !sceneCompleted(userId, mission.targetSceneId()))
                .toList();
        if (available.isEmpty()) {
            throw new IllegalArgumentException("目前沒有未完成的探索委託");
        }

        Instant now = clock.instant();
        ExplorationState existing = available.stream()
                .map(mission -> explorationStates.get(stateKey(userId, mission.missionKey())))
                .filter(state -> state != null && !state.completed && now.isBefore(state.expiresAt))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            synchronized (existing) {
                return missionView(existing);
            }
        }

        ExplorationMissionDefinition selected = available.get(
                ThreadLocalRandom.current().nextInt(available.size()));
        String key = stateKey(userId, selected.missionKey());
        ExplorationState state = explorationStates.compute(key, (ignored, current) -> {
            if (current == null || current.completed || !now.isBefore(current.expiresAt)) {
                return new ExplorationState(userId, selected, now, now.plus(MISSION_DURATION));
            }
            return current;
        });
        synchronized (state) {
            return missionView(state);
        }
    }

    public InvestigationResult investigate(Long userId, String missionId, String actionName) {
        ExplorationState state = activeState(userId, missionId);
        ClueType clueType = clueType(actionName);
        synchronized (state) {
            validateInvestigationOpen(state);
            InvestigationDefinition investigation = investigation(state.mission, clueType);
            boolean alreadyDiscovered = state.discoveredClues.contains(clueType);
            if (!alreadyDiscovered) {
                if (state.remainingActions > 0) {
                    state.remainingActions--;
                }
                state.discoveredClues.add(clueType);
                revealAllCluesWhenActionsExhausted(state);
            }
            return new InvestigationResult(
                    clueType,
                    investigation.clueText(),
                    investigation.resultMessage(),
                    alreadyDiscovered,
                    state.remainingActions,
                    discoveredClueViews(state),
                    true
            );
        }
    }

    public ExplorationGuessResult submitGuess(Long userId, String missionId, Long sceneId) {
        ExplorationState state = activeState(userId, missionId);
        validateSceneNotCompleted(userId, state.mission);
        SceneOption selected = candidateOptions(state.mission).stream()
                .filter(candidate -> candidate.sceneId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("選擇的地點不在本次候選清單中"));

        synchronized (state) {
            if (state.completed) {
                throw new IllegalArgumentException("旅行委託已經完成");
            }
            boolean correct = state.mission.targetSceneId().equals(sceneId);
            if (state.reasoningCompleted) {
                if (!correct) {
                    throw new IllegalArgumentException("景點推理已經完成");
                }
                CultureChallengeView challenge = currentOrNewCultureChallenge(state);
                return guessResult(state, selected, true, challenge);
            }

            if (!correct) {
                state.wrongGuesses++;
                if (state.remainingActions > 0) {
                    state.remainingActions--;
                }
                revealAllCluesWhenActionsExhausted(state);
                return guessResult(state, selected, false, null);
            }

            state.reasoningCompleted = true;
            CultureChallengeView challenge = issueCultureChallenge(state);
            return guessResult(state, selected, true, challenge);
        }
    }

    public ExplorationCompletionResult complete(Long userId, String missionId, String questionId,
                                                String answer, String difficulty) {
        ExplorationState state = activeState(userId, missionId);
        synchronized (state) {
            if (!state.reasoningCompleted) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "請先完成景點推理");
            }
            PendingCultureChallenge pending = state.pendingChallenge;
            if (pending == null) {
                throw new IllegalArgumentException("請先取得文化挑戰題目");
            }
            if (!pending.questionId().equals(questionId)) {
                throw new IllegalArgumentException("文化挑戰題目無效");
            }
            if (!clock.instant().isBefore(pending.expiresAt())) {
                state.pendingChallenge = null;
                throw new ResponseStatusException(HttpStatus.GONE, "文化挑戰已過期，請重新取得題目");
            }

            state.pendingChallenge = null;
            if (!pending.correctAnswer().equals(normalize(answer))) {
                return ExplorationCompletionResult.incorrect(state, targetSceneName(state.mission));
            }

            CheckinService.ExplorationCheckinResult checkin = checkinService.completeExploration(
                    userId, state.mission.targetSceneId(), difficulty);
            state.completed = true;
            return new ExplorationCompletionResult(
                    true,
                    true,
                    checkin.sceneId(),
                    checkin.sceneName(),
                    explorationGrade(state),
                    state.discoveredClues.size(),
                    state.wrongGuesses,
                    checkin.experienceGained(),
                    checkin.coinsGained(),
                    checkin.levelUp(),
                    checkin.cityBossUnlocked()
            );
        }
    }

    @Scheduled(fixedDelayString = "${game.exploration.cleanup-interval-ms:60000}")
    public void removeExpiredStates() {
        Instant now = clock.instant();
        explorationStates.forEach((key, state) -> {
            synchronized (state) {
                if (!now.isBefore(state.expiresAt)) {
                    explorationStates.remove(key, state);
                } else if (state.pendingChallenge != null
                        && !now.isBefore(state.pendingChallenge.expiresAt())) {
                    state.pendingChallenge = null;
                }
            }
        });
    }

    private ExplorationState activeState(Long userId, String missionId) {
        mission(missionId);
        String key = stateKey(userId, missionId);
        ExplorationState state = explorationStates.get(key);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "請先接取自己的旅行委託");
        }
        if (!clock.instant().isBefore(state.expiresAt)) {
            explorationStates.remove(key, state);
            throw new ResponseStatusException(HttpStatus.GONE, "旅行委託已過期，請重新接取");
        }
        return state;
    }

    private ExplorationMissionDefinition mission(String missionId) {
        return missionRegistry.findByKey(missionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到旅行委託"));
    }

    private ExplorationMissionView missionView(ExplorationState state) {
        return new ExplorationMissionView(
                state.mission.missionKey(),
                state.mission.cityId(),
                state.mission.title(),
                state.mission.description(),
                state.remainingActions,
                state.mission.investigations().stream()
                        .map(definition -> new InvestigationOption(definition.type(), definition.actionName()))
                        .toList(),
                discoveredClueViews(state),
                candidateOptions(state.mission),
                state.wrongGuesses,
                true,
                state.createdAt,
                state.expiresAt
        );
    }

    private ExplorationGuessResult guessResult(ExplorationState state, SceneOption selected,
                                               boolean correct, CultureChallengeView challenge) {
        return new ExplorationGuessResult(
                correct,
                state.mission.missionKey(),
                selected.sceneId(),
                selected.name(),
                state.remainingActions,
                state.wrongGuesses,
                !correct,
                discoveredClueViews(state),
                challenge
        );
    }

    private CultureChallengeView currentOrNewCultureChallenge(ExplorationState state) {
        PendingCultureChallenge pending = state.pendingChallenge;
        if (pending != null && clock.instant().isBefore(pending.expiresAt())) {
            return challengeView(state.mission, pending);
        }
        return issueCultureChallenge(state);
    }

    private CultureChallengeView issueCultureChallenge(ExplorationState state) {
        PendingCultureChallenge pending = new PendingCultureChallenge(
                UUID.randomUUID().toString(), state.mission.cultureAnswer(),
                clock.instant().plus(CULTURE_CHALLENGE_DURATION));
        state.pendingChallenge = pending;
        state.cultureChallengeIssued = true;
        return challengeView(state.mission, pending);
    }

    private CultureChallengeView challengeView(ExplorationMissionDefinition mission,
                                               PendingCultureChallenge pending) {
        return new CultureChallengeView(
                pending.questionId(), mission.cultureQuestion(), mission.cultureOptions(), pending.expiresAt());
    }

    private List<SceneOption> candidateOptions(ExplorationMissionDefinition mission) {
        Map<Long, Scene> scenesById = sceneRepository.findAllById(mission.candidateSceneIds()).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity()));
        return mission.candidateSceneIds().stream()
                .map(sceneId -> {
                    Scene scene = scenesById.get(sceneId);
                    if (scene == null) {
                        throw new IllegalStateException("探索任務候選景點設定不完整：" + mission.missionKey());
                    }
                    return new SceneOption(scene.getId(), scene.getName());
                })
                .toList();
    }

    private String targetSceneName(ExplorationMissionDefinition mission) {
        return candidateOptions(mission).stream()
                .filter(candidate -> candidate.sceneId().equals(mission.targetSceneId()))
                .map(SceneOption::name)
                .findFirst()
                .orElse("景點");
    }

    private List<ClueView> discoveredClueViews(ExplorationState state) {
        List<ClueView> result = new ArrayList<>();
        for (ClueType type : ClueType.values()) {
            if (state.discoveredClues.contains(type)) {
                result.add(new ClueView(type, investigation(state.mission, type).clueText()));
            }
        }
        return List.copyOf(result);
    }

    private InvestigationDefinition investigation(ExplorationMissionDefinition mission, ClueType type) {
        return mission.investigations().stream()
                .filter(definition -> definition.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("探索任務缺少調查設定：" + type));
    }

    private void revealAllCluesWhenActionsExhausted(ExplorationState state) {
        if (state.remainingActions == 0) {
            state.actionsExhausted = true;
            state.discoveredClues.addAll(List.of(ClueType.values()));
        }
    }

    private void validateInvestigationOpen(ExplorationState state) {
        if (state.completed) {
            throw new IllegalArgumentException("旅行委託已經完成");
        }
        if (state.reasoningCompleted) {
            throw new IllegalArgumentException("景點推理已經完成");
        }
    }

    private ClueType clueType(String value) {
        try {
            return ClueType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支援的調查行動");
        }
    }

    private String explorationGrade(ExplorationState state) {
        int cluesUsed = state.discoveredClues.size();
        if (state.actionsExhausted || state.wrongGuesses >= 2) {
            return "C";
        }
        if (cluesUsed >= 3 || state.wrongGuesses == 1) {
            return "B";
        }
        if (cluesUsed == 2) {
            return "A";
        }
        return "S";
    }

    private void validateSceneNotCompleted(Long userId, ExplorationMissionDefinition mission) {
        if (sceneCompleted(userId, mission.targetSceneId())) {
            throw new IllegalArgumentException("這個景點已經完成探索");
        }
    }

    private boolean sceneCompleted(Long userId, Long sceneId) {
        return checkinRepository.findByUserIdAndSceneId(userId, sceneId)
                .map(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .orElse(false);
    }

    private void validateCityUnlocked(Long userId, Long cityId) {
        boolean unlocked = userProgressRepository.findByUserIdAndCityId(userId, cityId)
                .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .orElse(false);
        if (!unlocked) {
            throw new IllegalArgumentException("城市尚未解鎖");
        }
    }

    private String stateKey(Long userId, String missionId) {
        return "%d:%s".formatted(userId, missionId);
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.trim();
    }

    private record PendingCultureChallenge(
            String questionId,
            String correctAnswer,
            Instant expiresAt
    ) {
    }

    private static final class ExplorationState {
        private final Long userId;
        private final ExplorationMissionDefinition mission;
        private final Instant createdAt;
        private final Instant expiresAt;
        private int remainingActions = INITIAL_ACTIONS;
        private final Set<ClueType> discoveredClues = new LinkedHashSet<>();
        private int wrongGuesses;
        private boolean reasoningCompleted;
        private boolean cultureChallengeIssued;
        private boolean completed;
        private boolean actionsExhausted;
        private PendingCultureChallenge pendingChallenge;

        private ExplorationState(Long userId, ExplorationMissionDefinition mission,
                                 Instant createdAt, Instant expiresAt) {
            this.userId = userId;
            this.mission = mission;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    public record ExplorationMissionView(
            String missionId,
            Long cityId,
            String title,
            String description,
            int remainingActions,
            List<InvestigationOption> availableInvestigations,
            List<ClueView> discoveredClues,
            List<SceneOption> candidates,
            int wrongGuesses,
            boolean canGuess,
            Instant createdAt,
            Instant expiresAt
    ) {
    }

    public record InvestigationOption(ClueType type, String name) {
    }

    public record ClueView(ClueType type, String text) {
    }

    public record SceneOption(Long sceneId, String name) {
    }

    public record InvestigationResult(
            ClueType clueType,
            String clue,
            String resultMessage,
            boolean alreadyDiscovered,
            int remainingActions,
            List<ClueView> discoveredClues,
            boolean canGuess
    ) {
    }

    public record CultureChallengeView(
            String questionId,
            String question,
            List<String> options,
            Instant expiresAt
    ) {
    }

    public record ExplorationGuessResult(
            boolean correct,
            String missionId,
            Long sceneId,
            String sceneName,
            int remainingActions,
            int wrongGuesses,
            boolean canContinue,
            List<ClueView> discoveredClues,
            CultureChallengeView challenge
    ) {
    }

    public record ExplorationCompletionResult(
            boolean correct,
            boolean completed,
            Long sceneId,
            String sceneName,
            String explorationGrade,
            int cluesUsed,
            int wrongGuesses,
            int experienceGained,
            int coinsGained,
            boolean levelUp,
            boolean cityBossUnlocked
    ) {
        private static ExplorationCompletionResult incorrect(ExplorationState state, String sceneName) {
            return new ExplorationCompletionResult(
                    false,
                    false,
                    state.mission.targetSceneId(),
                    sceneName,
                    null,
                    state.discoveredClues.size(),
                    state.wrongGuesses,
                    0,
                    0,
                    false,
                    false
            );
        }
    }
}
