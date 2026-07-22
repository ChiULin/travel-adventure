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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BossFoodIntegrationTest {

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
    void unlockedBeefSoupAddsTwoServerValidatedSecondsOnlyToSelectedBattle() throws Exception {
        String token = registerAndGetToken("boss-food-player");
        User user = userRepository.findByUsername("boss-food-player").orElseThrow();
        user.setLevel(100);
        userRepository.save(user);
        completeCity(user, 3L, List.of(7L, 8L, 9L));

        mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("NORMAL", "TAINAN_BEEF_SOUP", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("尚未解鎖此美食"));

        mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("NORMAL", "FORGED_FOOD", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("找不到指定美食"));

        unlockBeefSoup(token);
        int expBeforeStarts = userRepository.findById(user.getId()).orElseThrow().getExp();
        int coinsBeforeStarts = userRepository.findById(user.getId()).orElseThrow().getCoins();

        MvcResult normalFood = mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("NORMAL", "TAINAN_BEEF_SOUP", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("守護者挑戰開始"))
                .andExpect(jsonPath("$.data.difficulty").value("NORMAL"))
                .andExpect(jsonPath("$.data.baseQuestionSeconds").value(5))
                .andExpect(jsonPath("$.data.questionSeconds").value(7))
                .andExpect(jsonPath("$.data.activeFood.foodKey").value("TAINAN_BEEF_SOUP"))
                .andExpect(jsonPath("$.data.activeFood.name").value("牛肉湯"))
                .andExpect(jsonPath("$.data.activeFood.effectType").value("EXTRA_TIME"))
                .andExpect(jsonPath("$.data.activeFood.effectValue").value(2))
                .andExpect(jsonPath("$.data.battle.playerLives").value(3))
                .andExpect(jsonPath("$.data.battle.combo").value(0))
                .andExpect(jsonPath("$.data.question.seconds").value(7))
                .andReturn();
        assertQuestionLifetime(normalFood, 9);

        mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("CASUAL", "TAINAN_BEEF_SOUP", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseQuestionSeconds").value(10))
                .andExpect(jsonPath("$.data.questionSeconds").value(12))
                .andExpect(jsonPath("$.data.question.seconds").value(12));

        mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("EXTREME", "TAINAN_BEEF_SOUP", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseQuestionSeconds").value(3))
                .andExpect(jsonPath("$.data.questionSeconds").value(5))
                .andExpect(jsonPath("$.data.question.seconds").value(5));

        MvcResult normalWithoutFood = mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("NORMAL", null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseQuestionSeconds").value(5))
                .andExpect(jsonPath("$.data.questionSeconds").value(5))
                .andExpect(jsonPath("$.data.activeFood").doesNotExist())
                .andExpect(jsonPath("$.data.question.seconds").value(5))
                .andReturn();
        assertQuestionLifetime(normalWithoutFood, 7);

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(expBeforeStarts, unchanged.getExp());
        assertEquals(coinsBeforeStarts, unchanged.getCoins());

        completeCity(user, 1L, List.of(1L, 2L, 3L));
        mockMvc.perform(post("/api/cities/1/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest("NORMAL", "TAINAN_BEEF_SOUP", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("此美食不能用於目前城市"));

        MvcResult firstClearQuestion = startBoss(token, "NORMAL", "TAINAN_BEEF_SOUP");
        JsonNode firstQuestion = responseData(firstClearQuestion).path("question");
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
        assertEquals(expBeforeStarts + 312, rewarded.getExp());
        assertEquals(coinsBeforeStarts + 360, rewarded.getCoins());

        MvcResult repeatQuestion = startBoss(token, "NORMAL", "TAINAN_BEEF_SOUP");
        JsonNode repeated = responseData(repeatQuestion).path("question");
        String repeatedAnswer = correctBossAnswer(3L, repeated.path("questionId").asText());
        mockMvc.perform(post("/api/cities/3/boss/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bossAnswerRequest(repeated, repeatedAnswer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.win").value(true))
                .andExpect(jsonPath("$.data.earnedExp").value(0))
                .andExpect(jsonPath("$.data.earnedCoins").value(0));

        User afterRepeat = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(rewarded.getExp(), afterRepeat.getExp());
        assertEquals(rewarded.getCoins(), afterRepeat.getCoins());
    }

    private void unlockBeefSoup(String token) throws Exception {
        MvcResult event = mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String questionId = objectMapper.readTree(event.getResponse().getContentAsString())
                .path("data").path("challenge").path("questionId").asText();
        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId": "%s",
                                  "answer": "將生牛肉薄片放入碗中，再沖入熱高湯"
                                }
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true));
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

    private String startRequest(String difficulty, String foodKey, boolean forgedEffect) {
        String foodValue = foodKey == null ? "null" : "\"" + foodKey + "\"";
        String forgedFields = forgedEffect ? ",\"effectValue\":999,\"extraSecondsPerQuestion\":999" : "";
        return "{\"difficulty\":\"" + difficulty + "\",\"foodKey\":" + foodValue + forgedFields + "}";
    }

    private MvcResult startBoss(String token, String difficulty, String foodKey) throws Exception {
        return mockMvc.perform(post("/api/cities/3/boss/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRequest(difficulty, foodKey, false)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String bossAnswerRequest(JsonNode question, String answerText) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "questionId", question.path("questionId").asText(),
                "answerText", answerText,
                "difficulty", "NORMAL",
                "foodKey", "TAINAN_BEEF_SOUP"
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
        JsonNode question = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("question");
        Instant issuedAt = Instant.parse(question.path("issuedAt").asText());
        Instant expiresAt = Instant.parse(question.path("expiresAt").asText());
        assertEquals(expectedSeconds, Duration.between(issuedAt, expiresAt).toSeconds());
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
