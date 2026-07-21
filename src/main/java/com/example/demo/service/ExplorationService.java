package com.example.demo.service;

import com.example.demo.repository.CheckinRepository;
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

@Service
public class ExplorationService {
    private static final Long TAINAN_CITY_ID = 3L;
    private static final int INITIAL_ACTIONS = 4;
    private static final Duration MISSION_DURATION = Duration.ofMinutes(30);
    private static final Duration CULTURE_CHALLENGE_DURATION = Duration.ofMinutes(2);
    private static final String CULTURE_QUESTION = "安平古堡最早與哪一個歐洲國家的統治有關？";
    private static final String CULTURE_ANSWER = "荷蘭";
    private static final List<String> CULTURE_OPTIONS = List.of("英國", "荷蘭", "法國", "葡萄牙");

    private static final List<InvestigationOption> INVESTIGATIONS = List.of(
            new InvestigationOption(ClueType.LOCAL, "詢問當地居民"),
            new InvestigationOption(ClueType.HISTORY, "查閱歷史文獻"),
            new InvestigationOption(ClueType.VISUAL, "觀察舊照片")
    );

    private static final Map<ClueType, String> CLUES = Map.of(
            ClueType.LOCAL, "這個地方位於台南安平一帶。",
            ClueType.HISTORY, "它曾是荷蘭人在台灣建立的重要據點。",
            ClueType.VISUAL, "可以看見紅磚城牆與古老遺跡。"
    );

    private static final ExplorationMission ANPING_MISSION = new ExplorationMission(
            "TAINAN-ANPING-01",
            "尋找海港旁的古老據點",
            "尋找一座與台灣早期海上歷史有關的景點。",
            List.of(
                    new SceneOption(7L, "赤崁樓"),
                    new SceneOption(8L, "安平古堡"),
                    new SceneOption(9L, "億載金城")
            ),
            8L
    );

    private final CheckinRepository checkinRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinService checkinService;
    private final Clock clock;
    private final Map<String, ExplorationState> explorationStates = new ConcurrentHashMap<>();

    @Autowired
    public ExplorationService(CheckinRepository checkinRepository,
                              UserProgressRepository userProgressRepository,
                              CheckinService checkinService) {
        this(checkinRepository, userProgressRepository, checkinService, Clock.systemUTC());
    }

    ExplorationService(CheckinRepository checkinRepository,
                       UserProgressRepository userProgressRepository,
                       CheckinService checkinService,
                       Clock clock) {
        this.checkinRepository = checkinRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinService = checkinService;
        this.clock = clock;
    }

    public ExplorationMissionView randomMission(Long userId, Long cityId) {
        if (!TAINAN_CITY_ID.equals(cityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目前只有台南探索試作任務");
        }
        validateCityUnlocked(userId);
        validateSceneNotCompleted(userId);

        String key = stateKey(userId, ANPING_MISSION.missionId());
        Instant now = clock.instant();
        ExplorationState state = explorationStates.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.expiresAt)) {
                return new ExplorationState(userId, ANPING_MISSION, now, now.plus(MISSION_DURATION));
            }
            return existing;
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
                    CLUES.get(clueType),
                    alreadyDiscovered,
                    state.remainingActions,
                    discoveredClueViews(state),
                    true
            );
        }
    }

    public ExplorationGuessResult submitGuess(Long userId, String missionId, Long sceneId) {
        ExplorationState state = activeState(userId, missionId);
        validateSceneNotCompleted(userId);
        SceneOption selected = state.mission.candidates().stream()
                .filter(candidate -> candidate.sceneId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("候選景點不在這次委託中"));

        synchronized (state) {
            if (state.completed) {
                throw new IllegalArgumentException("探索任務已完成");
            }
            boolean correct = state.mission.correctSceneId().equals(sceneId);
            if (state.reasoningCompleted) {
                if (!correct) {
                    throw new IllegalArgumentException("景點推理已完成");
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
                throw new IllegalArgumentException("請先取得文化挑戰");
            }
            if (!pending.questionId().equals(questionId)) {
                throw new IllegalArgumentException("文化挑戰題目不符");
            }
            if (!clock.instant().isBefore(pending.expiresAt())) {
                state.pendingChallenge = null;
                throw new ResponseStatusException(HttpStatus.GONE, "文化挑戰已過期，請重新取得題目");
            }

            state.pendingChallenge = null;
            if (!pending.correctAnswer().equals(normalize(answer))) {
                return ExplorationCompletionResult.incorrect(state);
            }

            CheckinService.ExplorationCheckinResult checkin = checkinService.completeExploration(
                    userId, state.mission.correctSceneId(), difficulty);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "請先取得旅行委託");
        }
        if (!clock.instant().isBefore(state.expiresAt)) {
            explorationStates.remove(key, state);
            throw new ResponseStatusException(HttpStatus.GONE, "旅行委託已過期，請重新取得");
        }
        return state;
    }

    private ExplorationMission mission(String missionId) {
        if (!ANPING_MISSION.missionId().equals(missionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索任務");
        }
        return ANPING_MISSION;
    }

    private ExplorationMissionView missionView(ExplorationState state) {
        return new ExplorationMissionView(
                state.mission.missionId(),
                state.mission.title(),
                state.mission.description(),
                state.remainingActions,
                INVESTIGATIONS,
                discoveredClueViews(state),
                state.mission.candidates(),
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
                state.mission.missionId(),
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
            return challengeView(pending);
        }
        return issueCultureChallenge(state);
    }

    private CultureChallengeView issueCultureChallenge(ExplorationState state) {
        PendingCultureChallenge pending = new PendingCultureChallenge(
                UUID.randomUUID().toString(), CULTURE_ANSWER,
                clock.instant().plus(CULTURE_CHALLENGE_DURATION));
        state.pendingChallenge = pending;
        state.cultureChallengeIssued = true;
        return challengeView(pending);
    }

    private CultureChallengeView challengeView(PendingCultureChallenge pending) {
        return new CultureChallengeView(
                pending.questionId(), CULTURE_QUESTION, CULTURE_OPTIONS, pending.expiresAt());
    }

    private List<ClueView> discoveredClueViews(ExplorationState state) {
        List<ClueView> result = new ArrayList<>();
        for (ClueType type : ClueType.values()) {
            if (state.discoveredClues.contains(type)) {
                result.add(new ClueView(type, CLUES.get(type)));
            }
        }
        return List.copyOf(result);
    }

    private void revealAllCluesWhenActionsExhausted(ExplorationState state) {
        if (state.remainingActions == 0) {
            state.actionsExhausted = true;
            state.discoveredClues.addAll(List.of(ClueType.values()));
        }
    }

    private void validateInvestigationOpen(ExplorationState state) {
        if (state.completed) {
            throw new IllegalArgumentException("探索任務已完成");
        }
        if (state.reasoningCompleted) {
            throw new IllegalArgumentException("景點推理已完成");
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

    private void validateSceneNotCompleted(Long userId) {
        boolean completed = checkinRepository.findByUserIdAndSceneId(userId, ANPING_MISSION.correctSceneId())
                .map(checkin -> Boolean.TRUE.equals(checkin.getCompleted()))
                .orElse(false);
        if (completed) {
            throw new IllegalArgumentException("目前沒有未完成的探索委託");
        }
    }

    private void validateCityUnlocked(Long userId) {
        boolean unlocked = userProgressRepository.findByUserIdAndCityId(userId, TAINAN_CITY_ID)
                .map(progress -> Boolean.TRUE.equals(progress.getUnlocked()))
                .orElse(false);
        if (!unlocked) {
            throw new IllegalArgumentException("台南尚未解鎖");
        }
    }

    private String stateKey(Long userId, String missionId) {
        return "%d:%s".formatted(userId, missionId);
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.trim();
    }

    public enum ClueType {
        LOCAL,
        HISTORY,
        VISUAL
    }

    private record ExplorationMission(
            String missionId,
            String title,
            String description,
            List<SceneOption> candidates,
            Long correctSceneId
    ) {
    }

    private record PendingCultureChallenge(
            String questionId,
            String correctAnswer,
            Instant expiresAt
    ) {
    }

    private static final class ExplorationState {
        private final Long userId;
        private final ExplorationMission mission;
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

        private ExplorationState(Long userId, ExplorationMission mission, Instant createdAt, Instant expiresAt) {
            this.userId = userId;
            this.mission = mission;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    public record ExplorationMissionView(
            String missionId,
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
        private static ExplorationCompletionResult incorrect(ExplorationState state) {
            String sceneName = state.mission.candidates().stream()
                    .filter(candidate -> candidate.sceneId().equals(state.mission.correctSceneId()))
                    .map(SceneOption::name)
                    .findFirst()
                    .orElse("景點");
            return new ExplorationCompletionResult(
                    false,
                    false,
                    state.mission.correctSceneId(),
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
