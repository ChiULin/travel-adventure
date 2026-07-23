package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExplorationService;
import com.example.demo.service.MysteryChallengeService;
import com.example.demo.service.MysteryChallengeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class MysteryChallengeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MysteryChallengeService mysteryChallengeService;

    @Test
    void taipei101MysteryChallengeReturnsSameActiveSession() throws Exception {
        String token = registerAndGetToken("mystery-session");

        String first = startMystery(token, 1L);
        String second = startMystery(token, 1L);

        assertEquals(extract(first, "challengeSessionId"), extract(second, "challengeSessionId"));
        assertEquals(extract(first, "challengeType"), extract(second, "challengeType"));
    }

    @Test
    void consumedChallengeAvoidsThePreviousType() throws Exception {
        registerAndGetToken("mystery-repeat");
        User user = userRepository.findByUsername("mystery-repeat").orElseThrow();

        MysteryChallengeService.MysteryChallengeResponse first =
                mysteryChallengeService.start(user.getId(), 1L, "NORMAL");
        mysteryChallengeService.markConsumed(user.getId(), childChallengeId(first));
        MysteryChallengeService.MysteryChallengeResponse second =
                mysteryChallengeService.start(user.getId(), 1L, "NORMAL");

        assertNotEquals(first.challengeSessionId(), second.challengeSessionId());
        assertNotEquals(first.challengeType(), second.challengeType());
    }

    @Test
    void lockedStageStillReturnsConflictBeforePoolLookup() throws Exception {
        String token = registerAndGetToken("mystery-locked");

        mockMvc.perform(post("/api/mystery-challenges/landmarks/2/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"NORMAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("請先完成上一個景點關卡"));
    }

    @Test
    void taipei101PoolUsesOnlyPreparedChallengeTypes() throws Exception {
        String token = registerAndGetToken("mystery-pool-player");
        String response = startMystery(token, 1L);
        String type = extract(response, "challengeType");

        assertTrue(type.equals("QUIZ") || type.equals("EXPLORATION"));
        assertNotEquals(MysteryChallengeType.IMAGE_RECOGNITION.name(), type);
    }

    private String childChallengeId(MysteryChallengeService.MysteryChallengeResponse response) {
        if (response.challengeType() == MysteryChallengeType.EXPLORATION) {
            return ((ExplorationService.ExplorationMissionView) response.challengeData()).missionId();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> question = (Map<String, Object>) response.challengeData();
        return String.valueOf(question.get("questionId"));
    }

    private String startMystery(String token, Long landmarkId) throws Exception {
        return mockMvc.perform(post("/api/mystery-challenges/landmarks/" + landmarkId + "/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"NORMAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString())
                .andExpect(jsonPath("$.data.landmarkId").value(landmarkId))
                .andExpect(jsonPath("$.data.challengeType").isString())
                .andExpect(jsonPath("$.data.challengeData").isMap())
                .andReturn().getResponse().getContentAsString();
    }

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "token");
    }

    private String extract(String json, String property) {
        Matcher matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include " + property);
        }
        return matcher.group(1);
    }
}
