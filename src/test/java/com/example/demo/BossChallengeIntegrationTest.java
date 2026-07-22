package com.example.demo;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BossChallengeIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SceneRepository sceneRepository;
    @Autowired
    private CheckinRepository checkinRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private UserProgressRepository userProgressRepository;

    @Test
    void casualBossQuestionShouldExpireAfterTenSeconds() throws Exception {
        MvcResult result = startReadyBoss("boss-casual-player", "CASUAL");

        assertStartContract(result, "CASUAL", 10, 5);
        assertQuestionLifetime(result, 10);
    }

    @Test
    void normalBossQuestionShouldExpireAfterFiveSecondsWithoutFoodKey() throws Exception {
        MvcResult result = startReadyBoss("boss-normal-player", "NORMAL");

        assertStartContract(result, "NORMAL", 5, 3);
        assertQuestionLifetime(result, 5);
        JsonNode data = responseData(result);
        assertTrue(data.path("activeFood").isMissingNode());
        assertTrue(data.path("baseQuestionSeconds").isMissingNode());
        assertTrue(data.path("battle").isMissingNode());
    }

    @Test
    void extremeBossQuestionShouldExpireAfterThreeSeconds() throws Exception {
        MvcResult result = startReadyBoss("boss-extreme-player", "EXTREME");

        assertStartContract(result, "EXTREME", 3, 1);
        assertQuestionLifetime(result, 3);
    }

    @Test
    void bossFirstClearStillGrantsRewardsBadgeAndNextCity() throws Exception {
        String token = registerAndGetToken("boss-clear-player");
        User user = readyUser("boss-clear-player");
        int expBefore = user.getExp();
        int coinsBefore = user.getCoins();

        MvcResult firstStart = startBoss(token, "NORMAL");
        JsonNode firstQuestion = responseData(firstStart).path("question");
        String firstAnswer = correctBossAnswer(3L, firstQuestion.path("questionId").asText());
        mockMvc.perform(post("/api/cities/3/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(firstQuestion, firstAnswer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.earnedExp").value(312))
                .andExpect(jsonPath("$.data.earnedCoins").value(360));

        User rewarded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(expBefore + 312, rewarded.getExp());
        assertEquals(coinsBefore + 360, rewarded.getCoins());
        UserProgress tainan = userProgressRepository.findByUserIdAndCityId(user.getId(), 3L).orElseThrow();
        UserProgress kaohsiung = userProgressRepository.findByUserIdAndCityId(user.getId(), 4L).orElseThrow();
        assertTrue(tainan.getBossCompleted());
        assertTrue(tainan.getBadgeUnlocked());
        assertTrue(kaohsiung.getUnlocked());

        int rewardedExp = rewarded.getExp();
        int rewardedCoins = rewarded.getCoins();
        MvcResult repeatStart = startBoss(token, "NORMAL");
        JsonNode repeatQuestion = responseData(repeatStart).path("question");
        String repeatAnswer = correctBossAnswer(3L, repeatQuestion.path("questionId").asText());
        mockMvc.perform(post("/api/cities/3/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(repeatQuestion, repeatAnswer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.earnedExp").value(0))
                .andExpect(jsonPath("$.data.earnedCoins").value(0));

        User afterRepeat = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(rewardedExp, afterRepeat.getExp());
        assertEquals(rewardedCoins, afterRepeat.getCoins());
    }

    @Test
    void penghuBossCompletesJourneyWithoutCreatingNextCityOrDuplicateRewards() throws Exception {
        String token = registerAndGetToken("penghu-final-player");
        User user = readyPenghuUser("penghu-final-player");
        int progressCountBefore = userProgressRepository.findByUserId(user.getId()).size();
        int expBefore = user.getExp();
        int coinsBefore = user.getCoins();
        int bossPointsBefore = user.getBossPoints();

        MvcResult firstStart = startBoss(token, "NORMAL", 6L);
        JsonNode firstQuestion = responseData(firstStart).path("question");
        String firstAnswer = correctBossAnswer(6L, firstQuestion.path("questionId").asText());
        mockMvc.perform(post("/api/cities/6/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(firstQuestion, firstAnswer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.earnedExp").value(312))
                .andExpect(jsonPath("$.data.earnedCoins").value(360));

        User rewarded = userRepository.findById(user.getId()).orElseThrow();
        UserProgress penghu = userProgressRepository.findByUserIdAndCityId(user.getId(), 6L).orElseThrow();
        assertTrue(penghu.getBossCompleted());
        assertTrue(penghu.getBadgeUnlocked());
        assertEquals(expBefore + 312, rewarded.getExp());
        assertEquals(coinsBefore + 360, rewarded.getCoins());
        assertEquals(bossPointsBefore + 1, rewarded.getBossPoints());
        assertEquals(progressCountBefore, userProgressRepository.findByUserId(user.getId()).size());

        int rewardedExp = rewarded.getExp();
        int rewardedCoins = rewarded.getCoins();
        int rewardedBossPoints = rewarded.getBossPoints();
        MvcResult repeatStart = startBoss(token, "NORMAL", 6L);
        JsonNode repeatQuestion = responseData(repeatStart).path("question");
        String repeatAnswer = correctBossAnswer(6L, repeatQuestion.path("questionId").asText());
        mockMvc.perform(post("/api/cities/6/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(repeatQuestion, repeatAnswer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.earnedExp").value(0))
                .andExpect(jsonPath("$.data.earnedCoins").value(0));

        User afterRepeat = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(rewardedExp, afterRepeat.getExp());
        assertEquals(rewardedCoins, afterRepeat.getCoins());
        assertEquals(rewardedBossPoints, afterRepeat.getBossPoints());
        assertEquals(progressCountBefore, userProgressRepository.findByUserId(user.getId()).size());
    }

    @Test
    void hualienBossFirstClearUnlocksPenghuFirstStage() throws Exception {
        String token = registerAndGetToken("hualien-to-penghu");
        User user = readyHualienUser("hualien-to-penghu");

        MvcResult start = startBoss(token, "NORMAL", 5L);
        JsonNode question = responseData(start).path("question");
        String answer = correctBossAnswer(5L, question.path("questionId").asText());
        mockMvc.perform(post("/api/cities/5/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(question, answer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true));

        UserProgress penghu = userProgressRepository.findByUserIdAndCityId(user.getId(), 6L).orElseThrow();
        assertTrue(penghu.getUnlocked());
        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageStatus").value("LOCKED"));
    }

    @Test
    void directBossChallengeWithoutStartShouldBeRejected() throws Exception {
        String token = registerAndGetToken("boss-direct-player");
        readyUser("boss-direct-player");

        mockMvc.perform(post("/api/cities/3/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("questionId is required"));
    }

    @Test
    void battleResultRequiresOneTimePermitFromCompletedBossBattle() throws Exception {
        String token = registerAndGetToken("boss-record-player");
        readyUser("boss-record-player");

        mockMvc.perform(post("/api/cities/3/battle-result")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(battleResultRequest("forged")))
                .andExpect(status().isBadRequest());

        MvcResult start = startBoss(token, "NORMAL");
        JsonNode question = responseData(start).path("question");
        String answer = correctBossAnswer(3L, question.path("questionId").asText());
        MvcResult challenge = mockMvc.perform(post("/api/cities/3/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(question, answer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.battleResultToken").isString())
                .andReturn();
        String permit = responseData(challenge).path("battleResultToken").asText();

        String resultRequest = battleResultRequest(permit);
        mockMvc.perform(post("/api/cities/3/battle-result")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value("S"));

        mockMvc.perform(post("/api/cities/3/battle-result")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultRequest))
                .andExpect(status().isBadRequest());
    }

    private MvcResult startReadyBoss(String username, String difficulty) throws Exception {
        String token = registerAndGetToken(username);
        readyUser(username);
        return startBoss(token, difficulty);
    }

    private User readyUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setLevel(100);
        userRepository.save(user);
        completeCity(user, 3L, List.of(7L, 8L, 9L));
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private User readyPenghuUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setLevel(100);
        userRepository.save(user);
        completeCity(user, 6L, List.of(16L, 17L, 18L));
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private User readyHualienUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setLevel(100);
        userRepository.save(user);
        completeCity(user, 5L, List.of(13L, 14L, 15L));
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private void completeCity(User user, Long cityId, List<Long> sceneIds) {
        for (Long sceneId : sceneIds) {
            if (checkinRepository.existsByUserIdAndSceneId(user.getId(), sceneId)) {
                continue;
            }
            Scene scene = sceneRepository.findById(sceneId).orElseThrow();
            checkinRepository.save(Checkin.builder()
                    .user(user)
                    .scene(scene)
                    .quizCorrect(true)
                    .completed(true)
                    .earnedExp(0)
                    .earnedCoins(0)
                    .build());
        }
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(user.getId(), cityId)
                .orElseThrow();
        progress.setUnlocked(true);
        progress.setBossUnlocked(true);
        userProgressRepository.save(progress);
    }

    private MvcResult startBoss(String token, String difficulty) throws Exception {
        return startBoss(token, difficulty, 3L);
    }

    private MvcResult startBoss(String token, String difficulty, Long cityId) throws Exception {
        return mockMvc.perform(post("/api/cities/" + cityId + "/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"" + difficulty + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("守護者挑戰開始"))
                .andReturn();
    }

    private void assertStartContract(MvcResult result, String difficulty,
                                     int questionSeconds, int playerLives) throws Exception {
        JsonNode data = responseData(result);
        assertEquals(difficulty, data.path("difficulty").asText());
        assertEquals(questionSeconds, data.path("questionSeconds").asInt());
        assertEquals(playerLives, data.path("playerLives").asInt());
        assertEquals(0, data.path("combo").asInt());
        assertEquals(questionSeconds, data.path("question").path("seconds").asInt());
    }

    private String bossAnswerRequest(JsonNode question, String answerText) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "questionId", question.path("questionId").asText(),
                "answerText", answerText,
                "difficulty", "NORMAL"
        ));
    }

    private String battleResultRequest(String permit) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "battleResultToken", permit,
                "rank", "S",
                "maxCombo", 4,
                "remainingLives", 3,
                "correctAnswers", 4,
                "wrongAnswers", 0,
                "timeoutCount", 0,
                "earnedExp", 0,
                "earnedCoins", 0,
                "difficulty", "NORMAL"
        ));
    }

    private String correctBossAnswer(Long cityId, String questionId) {
        City city = cityRepository.findById(cityId).orElseThrow();
        if (questionId.equals("boss-" + cityId + "-fact")) {
            return switch (city.getBossCorrectAnswer()) {
                case "A" -> city.getBossOptionA();
                case "B" -> city.getBossOptionB();
                case "C" -> city.getBossOptionC();
                case "D" -> city.getBossOptionD();
                default -> throw new AssertionError("Unknown boss answer");
            };
        }
        if (questionId.equals("boss-" + cityId + "-badge")) {
            return city.getBadgeName();
        }
        if (questionId.equals("boss-" + cityId + "-guardian")) {
            return city.getBossName();
        }
        int sceneId = Integer.parseInt(questionId.substring(questionId.lastIndexOf('-') + 1));
        return sceneRepository.findById((long) sceneId).orElseThrow().getName();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void assertQuestionLifetime(MvcResult result, long expectedSeconds) throws Exception {
        JsonNode question = responseData(result).path("question");
        Instant issuedAt = Instant.parse(question.path("issuedAt").asText());
        Instant expiresAt = Instant.parse(question.path("expiresAt").asText());
        long seconds = Duration.between(issuedAt, expiresAt).toSeconds();
        assertTrue(seconds >= expectedSeconds - 1 && seconds <= expectedSeconds);
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
