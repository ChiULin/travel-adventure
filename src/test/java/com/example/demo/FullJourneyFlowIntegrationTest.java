package com.example.demo;

import com.example.demo.dto.BattleResultRequest;
import com.example.demo.dto.BossStartResponse;
import com.example.demo.entity.Checkin;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.BossService;
import com.example.demo.service.CheckinService;
import com.example.demo.service.GameDifficulty;
import com.example.demo.service.QuizQuestionService;
import com.example.demo.service.UserService;
import com.example.demo.stage.StageLockedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FullJourneyFlowIntegrationTest {

    private static final List<Long> CITY_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L);
    private static final List<List<Long>> CITY_SCENE_IDS = List.of(
            List.of(1L, 2L, 3L),
            List.of(4L, 5L, 6L),
            List.of(7L, 8L, 9L),
            List.of(10L, 11L, 12L),
            List.of(13L, 14L, 15L),
            List.of(16L, 17L, 18L)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private SceneRepository sceneRepository;
    @Autowired
    private CheckinRepository checkinRepository;
    @Autowired
    private UserProgressRepository userProgressRepository;
    @Autowired
    private QuizQuestionService quizQuestionService;
    @Autowired
    private CheckinService checkinService;
    @Autowired
    private BossService bossService;

    @Test
    void playerShouldCompleteAllSixCitiesInOrder() throws Exception {
        PlayerSession player = register("full-journey-main");
        Long userId = player.user().getId();

        assertEquals(6, userProgressRepository.findByUserId(userId).size());
        assertCityState(player.token(), 1L, true,
                "AVAILABLE", "LOCKED", "LOCKED", "LOCKED");
        for (Long cityId : CITY_IDS.subList(1, CITY_IDS.size())) {
            assertCityState(player.token(), cityId, false,
                    "LOCKED", "LOCKED", "LOCKED", "LOCKED");
        }
        assertThrows(StageLockedException.class, () -> completeScene(userId, 4L));

        for (int cityIndex = 0; cityIndex < CITY_IDS.size(); cityIndex++) {
            Long cityId = CITY_IDS.get(cityIndex);
            List<Long> sceneIds = CITY_SCENE_IDS.get(cityIndex);

            assertCityState(player.token(), cityId, true,
                    "AVAILABLE", "LOCKED", "LOCKED", "LOCKED");
            assertThrows(IllegalArgumentException.class,
                    () -> bossService.startChallenge(userId, cityId, GameDifficulty.NORMAL));
            assertThrows(StageLockedException.class, () -> completeScene(userId, sceneIds.get(1)));

            completeScene(userId, sceneIds.get(0));
            assertCityState(player.token(), cityId, true,
                    "COMPLETED", "AVAILABLE", "LOCKED", "LOCKED");
            assertThrows(StageLockedException.class, () -> completeScene(userId, sceneIds.get(2)));

            completeScene(userId, sceneIds.get(1));
            assertCityState(player.token(), cityId, true,
                    "COMPLETED", "COMPLETED", "AVAILABLE", "LOCKED");
            assertThrows(IllegalArgumentException.class,
                    () -> bossService.startChallenge(userId, cityId, GameDifficulty.NORMAL));

            completeScene(userId, sceneIds.get(2));
            assertCityState(player.token(), cityId, true,
                    "COMPLETED", "COMPLETED", "COMPLETED", "AVAILABLE");

            User beforeBoss = reloadUser(userId);
            int expBeforeBoss = beforeBoss.getExp();
            int coinsBeforeBoss = beforeBoss.getCoins();
            int bossPointsBeforeBoss = beforeBoss.getBossPoints();
            BattleStats stats = cityId.equals(6L) ? BattleStats.firstPenghuClear() : BattleStats.perfect();
            BossCompletion completion = completeBoss(userId, cityId, stats);

            assertTrue((Boolean) completion.challenge().get("win"));
            assertEquals(312, completion.challenge().get("earnedExp"));
            assertEquals(360, completion.challenge().get("earnedCoins"));
            User afterBoss = reloadUser(userId);
            assertEquals(expBeforeBoss + 312, afterBoss.getExp());
            assertEquals(coinsBeforeBoss + 360, afterBoss.getCoins());
            assertEquals(bossPointsBeforeBoss + 1, afterBoss.getBossPoints());
            assertCityState(player.token(), cityId, true,
                    "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");

            UserProgress completedProgress = progress(userId, cityId);
            assertTrue(completedProgress.getBossCompleted());
            assertTrue(completedProgress.getBadgeUnlocked());
            assertEquals(1, completedProgress.getChallengeCount());

            if (cityIndex + 1 < CITY_IDS.size()) {
                Long nextCityId = CITY_IDS.get(cityIndex + 1);
                assertTrue(progress(userId, nextCityId).getUnlocked());
                assertCityState(player.token(), nextCityId, true,
                        "AVAILABLE", "LOCKED", "LOCKED", "LOCKED");
            }
        }

        assertEquals(18, checkinRepository.findByUserIdAndCompletedTrue(userId).size());
        assertEquals(6, userProgressRepository.findByUserId(userId).size());
        assertTrue(userProgressRepository.findByUserId(userId).stream()
                .allMatch(progress -> Boolean.TRUE.equals(progress.getBossCompleted())
                        && Boolean.TRUE.equals(progress.getBadgeUnlocked())));

        User beforeReplay = reloadUser(userId);
        int expBeforeReplay = beforeReplay.getExp();
        int coinsBeforeReplay = beforeReplay.getCoins();
        int bossPointsBeforeReplay = beforeReplay.getBossPoints();
        int progressCountBeforeReplay = userProgressRepository.findByUserId(userId).size();
        UserProgress penghuBeforeReplay = progress(userId, 6L);
        assertEquals("B", penghuBeforeReplay.getBestRank());
        assertEquals(1, penghuBeforeReplay.getBestCombo());

        BossCompletion replay = completeBoss(userId, 6L, BattleStats.perfect());

        assertTrue((Boolean) replay.challenge().get("win"));
        assertEquals(0, replay.challenge().get("earnedExp"));
        assertEquals(0, replay.challenge().get("earnedCoins"));
        User afterReplay = reloadUser(userId);
        assertEquals(expBeforeReplay, afterReplay.getExp());
        assertEquals(coinsBeforeReplay, afterReplay.getCoins());
        assertEquals(bossPointsBeforeReplay, afterReplay.getBossPoints());
        assertEquals(progressCountBeforeReplay, userProgressRepository.findByUserId(userId).size());

        UserProgress penghuAfterReplay = progress(userId, 6L);
        assertTrue(penghuAfterReplay.getBadgeUnlocked());
        assertEquals(2, penghuAfterReplay.getChallengeCount());
        assertEquals("S", penghuAfterReplay.getBestRank());
        assertEquals(4, penghuAfterReplay.getBestCombo());
        assertEquals(3, penghuAfterReplay.getBestRemainingLives());

        JsonNode completedJourney = journey(player.token());
        assertEquals(18, completedJourney.path("checkedSceneIds").size());
        assertEquals(6, completedJourney.path("defeatedBossCityIds").size());
    }

    @Test
    void onePlayersProgressShouldNotUnlockAnotherPlayersJourney() throws Exception {
        PlayerSession playerA = register("full-journey-a");
        PlayerSession playerB = register("full-journey-b");

        completeScene(playerA.user().getId(), 1L);
        completeScene(playerA.user().getId(), 2L);
        completeScene(playerA.user().getId(), 3L);
        completeBoss(playerA.user().getId(), 1L, BattleStats.perfect());

        assertCityState(playerA.token(), 1L, true,
                "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
        assertCityState(playerA.token(), 2L, true,
                "AVAILABLE", "LOCKED", "LOCKED", "LOCKED");

        assertCityState(playerB.token(), 1L, true,
                "AVAILABLE", "LOCKED", "LOCKED", "LOCKED");
        assertCityState(playerB.token(), 2L, false,
                "LOCKED", "LOCKED", "LOCKED", "LOCKED");
        assertEquals(0, checkinRepository.findByUserIdAndCompletedTrue(playerB.user().getId()).size());
        assertFalse(progress(playerB.user().getId(), 2L).getUnlocked());
    }

    private PlayerSession register(String username) {
        User user = userService.register(username, "Password123");
        return new PlayerSession(user, jwtUtil.generateToken(user.getUsername()));
    }

    private Checkin completeScene(Long userId, Long sceneId) {
        Scene scene = sceneRepository.findById(sceneId).orElseThrow();
        Map<String, Object> question = quizQuestionService.randomSceneQuestion(
                userId, sceneId, GameDifficulty.CASUAL.name());
        String questionId = (String) question.get("questionId");
        String answerText = correctSceneAnswer(scene, questionId);
        Checkin checkin = checkinService.checkin(
                userId,
                sceneId,
                null,
                answerText,
                questionId,
                GameDifficulty.CASUAL.name()
        );
        assertTrue(checkin.getCompleted());
        return checkin;
    }

    private BossCompletion completeBoss(Long userId, Long cityId, BattleStats stats) {
        BossStartResponse start = bossService.startChallenge(userId, cityId, GameDifficulty.NORMAL);
        String questionId = (String) start.question().get("questionId");
        String answerText = correctBossAnswer(cityId, questionId);
        Map<String, Object> challenge = bossService.challengeResult(
                userId,
                cityId,
                null,
                answerText,
                questionId,
                GameDifficulty.NORMAL.name()
        );

        BattleResultRequest request = new BattleResultRequest();
        request.setBattleResultToken((String) challenge.get("battleResultToken"));
        request.setRank(stats.rank());
        request.setMaxCombo(stats.maxCombo());
        request.setRemainingLives(stats.remainingLives());
        request.setCorrectAnswers(stats.correctAnswers());
        request.setWrongAnswers(stats.wrongAnswers());
        request.setTimeoutCount(stats.timeoutCount());
        request.setEarnedExp(0);
        request.setEarnedCoins(0);
        request.setDifficulty(GameDifficulty.NORMAL.name());
        Map<String, Object> battleResult = bossService.recordBattleResult(userId, cityId, request);
        return new BossCompletion(challenge, battleResult);
    }

    private String correctSceneAnswer(Scene scene, String questionId) {
        if (questionId.endsWith("-fact")) {
            return optionText(
                    scene.getQuizCorrectAnswer(),
                    scene.getQuizOptionA(),
                    scene.getQuizOptionB(),
                    scene.getQuizOptionC(),
                    scene.getQuizOptionD()
            );
        }
        if (questionId.endsWith("-city")) {
            return scene.getCity().getName();
        }
        if (questionId.endsWith("-identify")) {
            return scene.getName();
        }
        throw new AssertionError("Unknown scene question: " + questionId);
    }

    private String correctBossAnswer(Long cityId, String questionId) {
        City city = cityRepository.findById(cityId).orElseThrow();
        if (questionId.equals("boss-" + cityId + "-fact")) {
            return optionText(
                    city.getBossCorrectAnswer(),
                    city.getBossOptionA(),
                    city.getBossOptionB(),
                    city.getBossOptionC(),
                    city.getBossOptionD()
            );
        }
        if (questionId.equals("boss-" + cityId + "-badge")) {
            return city.getBadgeName();
        }
        if (questionId.equals("boss-" + cityId + "-guardian")) {
            return city.getBossName();
        }
        long sceneId = Long.parseLong(questionId.substring(questionId.lastIndexOf('-') + 1));
        return sceneRepository.findById(sceneId).orElseThrow().getName();
    }

    private String optionText(String answer, String optionA, String optionB, String optionC, String optionD) {
        return switch (answer == null ? "" : answer.trim().toUpperCase()) {
            case "A" -> optionA;
            case "B" -> optionB;
            case "C" -> optionC;
            case "D" -> optionD;
            default -> throw new AssertionError("Unknown answer option: " + answer);
        };
    }

    private void assertCityState(String token, Long cityId, boolean unlocked,
                                 String first, String second, String third, String boss) throws Exception {
        JsonNode city = city(journey(token), cityId);
        assertEquals(unlocked, city.path("unlocked").asBoolean(), "city " + cityId + " unlocked");
        assertEquals(3, city.path("scenes").size(), "city " + cityId + " scene count");
        assertStage(city.path("scenes").get(0), 1, first);
        assertStage(city.path("scenes").get(1), 2, second);
        assertStage(city.path("scenes").get(2), 3, third);
        assertEquals(4, city.path("bossStage").path("stageOrder").asInt());
        assertEquals(boss, city.path("bossStage").path("stageStatus").asText());
    }

    private void assertStage(JsonNode stage, int order, String status) {
        assertTrue(stage.path("stageConfigured").asBoolean());
        assertEquals(order, stage.path("stageOrder").asInt());
        assertEquals(status, stage.path("stageStatus").asText());
    }

    private JsonNode journey(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode city(JsonNode journey, Long cityId) {
        for (JsonNode city : journey.path("cities")) {
            if (city.path("id").asLong() == cityId) {
                return city;
            }
        }
        throw new AssertionError("Journey did not contain city " + cityId);
    }

    private User reloadUser(Long userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private UserProgress progress(Long userId, Long cityId) {
        return userProgressRepository.findByUserIdAndCityId(userId, cityId).orElseThrow();
    }

    private record PlayerSession(User user, String token) {
    }

    private record BossCompletion(Map<String, Object> challenge, Map<String, Object> battleResult) {
    }

    private record BattleStats(
            String rank,
            int maxCombo,
            int remainingLives,
            int correctAnswers,
            int wrongAnswers,
            int timeoutCount
    ) {
        private static BattleStats perfect() {
            return new BattleStats("S", 4, 3, 4, 0, 0);
        }

        private static BattleStats firstPenghuClear() {
            return new BattleStats("B", 1, 1, 2, 2, 0);
        }
    }
}
