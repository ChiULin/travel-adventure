package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CheckinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1,
                                  "answer": "A"
                                }
                                """))
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

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1,
                                  "answer": "B"
                                }
                                """))
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

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1,
                                  "answer": "A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void answerTextShouldBeAcceptedWhenOptionLabelIsShuffled() throws Exception {
        String token = registerAndGetToken("shuffleTester01");

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1,
                                  "answer": "C",
                                  "answerText": "節節高升"
                                }
                                """))
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

        mockMvc.perform(post("/api/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1,
                                  "answer": "A"
                                }
                                """))
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
    void randomLandmarkQuestionShouldReturnQuestionIdAndFourOptions() throws Exception {
        String token = registerAndGetToken("randomQuizTester01");

        MvcResult first = mockMvc.perform(get("/api/quizzes/landmarks/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionId").isString())
                .andExpect(jsonPath("$.data.question").isString())
                .andExpect(jsonPath("$.data.issuedAt").isString())
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andExpect(jsonPath("$.data.options.A").isString())
                .andExpect(jsonPath("$.data.options.B").isString())
                .andExpect(jsonPath("$.data.options.C").isString())
                .andExpect(jsonPath("$.data.options.D").isString())
                .andReturn();

        MvcResult second = mockMvc.perform(get("/api/quizzes/landmarks/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String firstQuestionId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("questionId").asText();
        String secondQuestionId = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("questionId").asText();
        assertNotEquals(firstQuestionId, secondQuestionId);
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
