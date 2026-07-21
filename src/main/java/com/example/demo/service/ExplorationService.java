package com.example.demo.service;

import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExplorationService {
    private static final Long TAINAN_CITY_ID = 3L;
    private static final Duration CULTURE_CHALLENGE_DURATION = Duration.ofMinutes(2);
    private static final String CULTURE_QUESTION = "安平古堡最早與哪一個歐洲國家的統治有關？";
    private static final String CULTURE_ANSWER = "荷蘭";
    private static final List<String> CULTURE_OPTIONS = List.of("英國", "荷蘭", "法國", "葡萄牙");

    private static final ExplorationMission ANPING_MISSION = new ExplorationMission(
            "TAINAN-ANPING-01",
            "尋找海港旁的古老城堡",
            "找出符合旅行委託的景點。",
            List.of(
                    "這座景點位於台南安平一帶。",
                    "它曾是荷蘭人在台灣的重要據點。",
                    "現場可看到紅磚城牆與古老遺跡。"
            ),
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
    private final Map<String, PendingCultureChallenge> pendingChallenges = new ConcurrentHashMap<>();

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
            throw new IllegalArgumentException("目前只有台南探索試作任務");
        }
        validateCityUnlocked(userId);
        validateSceneNotCompleted(userId);
        return ANPING_MISSION.toView();
    }

    public ExplorationGuessResult submitGuess(Long userId, String missionId, Long sceneId) {
        ExplorationMission mission = mission(missionId);
        validateCityUnlocked(userId);
        validateSceneNotCompleted(userId);
        SceneOption selected = mission.candidates().stream()
                .filter(candidate -> candidate.sceneId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("候選景點不在這次委託中"));

        boolean correct = mission.correctSceneId().equals(sceneId);
        CultureChallengeView challenge = null;
        String key = challengeKey(userId, missionId);
        if (correct) {
            challenge = issueCultureChallenge(userId, mission);
        } else {
            pendingChallenges.remove(key);
        }
        return new ExplorationGuessResult(correct, missionId, selected.sceneId(), selected.name(), challenge);
    }

    public ExplorationCompletionResult complete(Long userId, String missionId, String questionId,
                                                String answer, String difficulty) {
        ExplorationMission mission = mission(missionId);
        String key = challengeKey(userId, missionId);
        PendingCultureChallenge pending = pendingChallenges.get(key);
        if (pending == null) {
            throw new IllegalArgumentException("請先完成景點推理");
        }
        if (!pending.questionId().equals(questionId)) {
            throw new IllegalArgumentException("文化挑戰題目不符");
        }
        if (!clock.instant().isBefore(pending.expiresAt())) {
            pendingChallenges.remove(key, pending);
            throw new IllegalArgumentException("文化挑戰已過期，請重新推理");
        }
        if (!pendingChallenges.remove(key, pending)) {
            throw new IllegalArgumentException("文化挑戰已提交");
        }
        if (!pending.correctAnswer().equals(normalize(answer))) {
            return ExplorationCompletionResult.incorrect(mission);
        }

        CheckinService.ExplorationCheckinResult checkin = checkinService.completeExploration(
                userId, mission.correctSceneId(), difficulty);
        return new ExplorationCompletionResult(
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

    private CultureChallengeView issueCultureChallenge(Long userId, ExplorationMission mission) {
        Instant expiresAt = clock.instant().plus(CULTURE_CHALLENGE_DURATION);
        PendingCultureChallenge pending = new PendingCultureChallenge(
                UUID.randomUUID().toString(), userId, mission.missionId(), CULTURE_ANSWER, expiresAt);
        pendingChallenges.put(challengeKey(userId, mission.missionId()), pending);
        return new CultureChallengeView(
                pending.questionId(), CULTURE_QUESTION, CULTURE_OPTIONS, pending.expiresAt());
    }

    private ExplorationMission mission(String missionId) {
        if (!ANPING_MISSION.missionId().equals(missionId)) {
            throw new IllegalArgumentException("找不到探索任務");
        }
        return ANPING_MISSION;
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

    private String challengeKey(Long userId, String missionId) {
        return "%d:%s".formatted(userId, missionId);
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.trim();
    }

    private record ExplorationMission(
            String missionId,
            String title,
            String description,
            List<String> clues,
            List<SceneOption> candidates,
            Long correctSceneId
    ) {
        private ExplorationMissionView toView() {
            return new ExplorationMissionView(missionId, title, description, clues, candidates);
        }
    }

    private record PendingCultureChallenge(
            String questionId,
            Long userId,
            String missionId,
            String correctAnswer,
            Instant expiresAt
    ) {
    }

    public record ExplorationMissionView(
            String missionId,
            String title,
            String description,
            List<String> clues,
            List<SceneOption> candidates
    ) {
    }

    public record SceneOption(Long sceneId, String name) {
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
            CultureChallengeView challenge
    ) {
    }

    public record ExplorationCompletionResult(
            boolean correct,
            boolean completed,
            Long sceneId,
            String sceneName,
            int experienceGained,
            int coinsGained,
            boolean levelUp,
            boolean cityBossUnlocked
    ) {
        private static ExplorationCompletionResult incorrect(ExplorationMission mission) {
            String sceneName = mission.candidates().stream()
                    .filter(candidate -> candidate.sceneId().equals(mission.correctSceneId()))
                    .map(SceneOption::name)
                    .findFirst()
                    .orElse("景點");
            return new ExplorationCompletionResult(
                    false, false, mission.correctSceneId(), sceneName, 0, 0, false, false);
        }
    }
}
