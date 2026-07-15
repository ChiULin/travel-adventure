package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void checkinWithoutJwtShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneId": 1
                                }
                                """))
                .andExpect(status().isForbidden());
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
                .andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.user.level").value(2))
                .andExpect(jsonPath("$.user.experience").value(125))
                .andExpect(jsonPath("$.user.currentLevelExp").value(25))
                .andExpect(jsonPath("$.user.nextLevelExp").value(200))
                .andExpect(jsonPath("$.user.levelProgressPercent").value(12));
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
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.correct").value(false));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.experience").value(0))
                .andExpect(jsonPath("$.cities[0].done").value(0));

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
                .andExpect(jsonPath("$.ok").value(true));
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
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.experience").value(125))
                .andExpect(jsonPath("$.cities[0].done").value(1));
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
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/journey/missions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardExp").value(100))
                .andExpect(jsonPath("$.rewardCoins").value(50))
                .andExpect(jsonPath("$.missions[0].id").value("complete-scenes"))
                .andExpect(jsonPath("$.missions[0].current").value(1))
                .andExpect(jsonPath("$.missions[1].id").value("correct-answers"))
                .andExpect(jsonPath("$.missions[1].current").value(1));

        mockMvc.perform(get("/api/journey/achievements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements[0].id").value("first-checkin"))
                .andExpect(jsonPath("$.achievements[0].unlocked").value(true));
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
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());

        return jsonNode.get("token").asText();
    }
}
