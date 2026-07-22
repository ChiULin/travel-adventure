package com.example.demo;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class StageChallengeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private CheckinRepository checkinRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Test
    void taipeiChallengeApisEnforceStageOrderBeforeIssuingState() throws Exception {
        String token = registerAndGetToken("stage-api-player");
        User user = userRepository.findByUsername("stage-api-player").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("TAIPEI-101-01"));

        assertLocked(get("/api/image-challenges/scenes/2?difficulty=NORMAL"), token);
        assertLocked(get("/api/quizzes/landmarks/3/random?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 2L);
        assertNoCheckin(user.getId(), 3L);

        saveCheckin(user, 1L, true);

        mockMvc.perform(get("/api/image-challenges/scenes/2?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertLocked(get("/api/quizzes/landmarks/3/random?difficulty=NORMAL"), token);

        saveCheckin(user, 2L, true);

        mockMvc.perform(get("/api/quizzes/landmarks/3/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertNoCheckin(user.getId(), 3L);
    }

    @Test
    void anotherPlayersCheckinDoesNotUnlockNextStage() throws Exception {
        registerAndGetToken("stage-api-owner");
        String otherToken = registerAndGetToken("stage-api-other");
        User owner = userRepository.findByUsername("stage-api-owner").orElseThrow();

        saveCheckin(owner, 1L, true);

        assertLocked(get("/api/image-challenges/scenes/2?difficulty=NORMAL"), otherToken);
    }

    @Test
    void incompleteCheckinDoesNotUnlockNextStage() throws Exception {
        String token = registerAndGetToken("stage-incomplete");
        User user = userRepository.findByUsername("stage-incomplete").orElseThrow();

        saveCheckin(user, 1L, false);

        assertLocked(get("/api/image-challenges/scenes/2?difficulty=NORMAL"), token);
    }

    @Test
    void unconfiguredCityKeepsExistingExplorationBehavior() throws Exception {
        String token = registerAndGetToken("stage-compatible");
        User user = userRepository.findByUsername("stage-compatible").orElseThrow();
        var progress = userProgressRepository.findByUserIdAndCityId(user.getId(), 3L).orElseThrow();
        progress.setUnlocked(true);
        userProgressRepository.save(progress);

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("TAINAN-ANPING-01"));
    }

    private void assertLocked(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token
    ) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("請先完成上一個景點關卡"));
    }

    private void saveCheckin(User user, Long sceneId, boolean completed) {
        Scene scene = sceneRepository.findById(sceneId).orElseThrow();
        checkinRepository.save(Checkin.builder()
                .user(user)
                .scene(scene)
                .quizCorrect(completed)
                .completed(completed)
                .earnedExp(0)
                .earnedCoins(0)
                .build());
    }

    private void assertNoCheckin(Long userId, Long sceneId) {
        if (checkinRepository.existsByUserIdAndSceneId(userId, sceneId)) {
            throw new AssertionError("Locked challenge created a checkin for scene " + sceneId);
        }
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
