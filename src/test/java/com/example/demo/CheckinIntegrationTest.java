package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.Scene;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.QuizQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CheckinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SceneRepository sceneRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private QuizQuestionService quizQuestionService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkinWithoutJwtShouldReturnUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(post("/api/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("請先登入"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void checkinWithUnknownSceneShouldReturnNotFound() throws Exception {
        String token = registerAndGetToken("checkinTester01");

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 999999
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("scene not found"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void checkinShouldUpdateLevelProgress() throws Exception {
        String token = registerAndGetToken("levelTester01");

        answerScene(token, 1L, true)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.level").value(2))
                .andExpect(jsonPath("$.data.user.experience").value(125))
                .andExpect(jsonPath("$.data.user.currentLevelExp").value(25))
                .andExpect(jsonPath("$.data.user.nextLevelExp").value(200))
                .andExpect(jsonPath("$.data.user.levelProgressPercent").value(12));
    }

    @Test
    void wrongAnswerShouldNotCompleteCheckinOrGrantReward() throws Exception {
        String token = registerAndGetToken("quizTester01");

        answerScene(token, 1L, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("答案錯誤"))
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.correct").value(false));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.experience").value(0))
                .andExpect(jsonPath("$.data.cities[0].done").value(0));

        answerScene(token, 1L, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void answerTextShouldBeAcceptedWhenOptionLabelIsShuffled() throws Exception {
        String token = registerAndGetToken("shuffleTester01");

        answerScene(token, 1L, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.experience").value(125))
                .andExpect(jsonPath("$.data.cities[0].done").value(1));
    }

    @Test
    void missionsAndAchievementsShouldUseExistingProgress() throws Exception {
        String token = registerAndGetToken("missionTester01");

        answerScene(token, 1L, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mockMvc.perform(get("/api/journey/missions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardExp").value(100))
                .andExpect(jsonPath("$.data.rewardCoins").value(50))
                .andExpect(jsonPath("$.data.missions[0].id").value("complete-scenes"))
                .andExpect(jsonPath("$.data.missions[0].current").value(1))
                .andExpect(jsonPath("$.data.missions[1].id").value("correct-answers"))
                .andExpect(jsonPath("$.data.missions[1].current").value(1));

        mockMvc.perform(get("/api/journey/achievements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievements[0].id").value("first-checkin"))
                .andExpect(jsonPath("$.data.achievements[0].unlocked").value(true));
    }

    @Test
    void mysteryLandmarkShouldRejectLegacyRandomQuizEndpoint() throws Exception {
        String token = registerAndGetToken("randomQuizTester01");

        mockMvc.perform(get("/api/quizzes/landmarks/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("此景點已啟用未知挑戰，請由未知挑戰入口開始"));
    }

    @Test
    void directCheckinWithoutIssuedQuestionShouldBeRejected() throws Exception {
        String token = registerAndGetToken("directBlock01");

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneId\":1,\"answer\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("questionId is required"));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].done").value(0));
    }

    @Test
    void lockedStageCannotBeCompletedWithForgedQuestionId() throws Exception {
        String token = registerAndGetToken("lockedStage01");

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 2,
                                  "answerText": "forged",
                                  "questionId": "scene-2-fact",
                                  "difficulty": "CASUAL"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].done").value(0));
    }

    private ResultActions answerScene(String token, Long sceneId, boolean correct) throws Exception {
        Long userId = userRepository.findByUsername(jwtUtil.getSubject(token))
                .orElseThrow()
                .getId();
        java.util.Map<String, Object> question = transactionTemplate.execute(status ->
                quizQuestionService.randomSceneQuestion(userId, sceneId, "CASUAL"));
        String questionId = String.valueOf(question.get("questionId"));
        String answerText = correct ? correctSceneAnswer(sceneId, questionId)
                : "definitely-not-the-correct-answer";
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "sceneId", sceneId,
                "answerText", answerText,
                "questionId", questionId,
                "difficulty", "CASUAL"));
        return mockMvc.perform(post("/api/checkins")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private String correctSceneAnswer(Long sceneId, String questionId) {
        Scene scene = sceneRepository.findById(sceneId).orElseThrow();
        if (questionId.endsWith("-city")) {
            return cityRepository.findById(scene.getCity().getId()).orElseThrow().getName();
        }
        if (questionId.endsWith("-identify")) {
            return scene.getName();
        }
        return switch (scene.getQuizCorrectAnswer()) {
            case "A" -> scene.getQuizOptionA();
            case "B" -> scene.getQuizOptionB();
            case "C" -> scene.getQuizOptionC();
            case "D" -> scene.getQuizOptionD();
            default -> throw new AssertionError("Unknown scene answer");
        };
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());

        return jsonNode.path("data").path("token").asText();
    }
}
