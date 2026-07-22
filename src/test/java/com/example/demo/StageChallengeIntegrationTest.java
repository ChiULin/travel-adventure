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

    @Test
    void newPlayerJourneyReturnsOrderedConfiguredStageMetadata() throws Exception {
        String token = registerAndGetToken("journey-new-user");

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].id").value(1))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageLabel").value("第 1 關"))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].actionLabel").value("開始挑戰"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].id").value(2))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageLabel").value("第 2 關"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("完成上一關後解鎖"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].id").value(3))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageLabel").value("第 3 關"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].actionLabel").value("完成上一關後解鎖"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.cityId").value(1))
                .andExpect(jsonPath("$.data.cities[0].bossStage.bossName").value("台北守護者"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageLabel").value("第 4 關"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.actionLabel").value("完成前三關後解鎖"))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].id").value(4))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].interactionType").value("EXPLORATION"))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].id").value(5))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].id").value(6))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].bossStage.cityId").value(2))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageConfigured").value(false))
                .andExpect(jsonPath("$.data.cities[2].bossStage").value((Object) null))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageConfigured").value(false))
                .andExpect(jsonPath("$.data.cities[3].bossStage").value((Object) null))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageConfigured").value(false))
                .andExpect(jsonPath("$.data.cities[4].bossStage").value((Object) null))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageConfigured").value(false))
                .andExpect(jsonPath("$.data.cities[5].bossStage").value((Object) null));
    }

    @Test
    void taichungChallengeApisEnforceStageOrder() throws Exception {
        String token = registerAndGetToken("taichung-api-player");
        User user = userRepository.findByUsername("taichung-api-player").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/2/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        unlockCity(user, 2L);

        mockMvc.perform(get("/api/explorations/cities/2/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("TAICHUNG-GAOMEI-01"));

        assertLocked(get("/api/quizzes/landmarks/5/random?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 5L);

        saveCheckin(user, 4L, true);

        mockMvc.perform(get("/api/quizzes/landmarks/5/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertLocked(get("/api/quizzes/landmarks/6/random?difficulty=NORMAL"), token);

        saveCheckin(user, 5L, true);

        mockMvc.perform(get("/api/quizzes/landmarks/6/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertNoCheckin(user.getId(), 6L);
    }

    @Test
    void taichungJourneyUnlocksStagesAndBossInOrder() throws Exception {
        String token = registerAndGetToken("taichung-journey");
        User user = userRepository.findByUsername("taichung-journey").orElseThrow();

        unlockCity(user, 2L);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[1].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 4L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[1].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("LOCKED"));

        saveCheckin(user, 5L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 6L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageStatus").value("AVAILABLE"));
    }

    @Test
    void anotherPlayersTaichungCheckinDoesNotUnlockNextStage() throws Exception {
        registerAndGetToken("taichung-owner");
        String otherToken = registerAndGetToken("taichung-other");
        User owner = userRepository.findByUsername("taichung-owner").orElseThrow();
        User other = userRepository.findByUsername("taichung-other").orElseThrow();
        unlockCity(other, 2L);
        saveCheckin(owner, 4L, true);

        assertLocked(get("/api/quizzes/landmarks/5/random?difficulty=NORMAL"), otherToken);
    }

    @Test
    void incompleteTaichungCheckinDoesNotUnlockNextStage() throws Exception {
        String token = registerAndGetToken("taichung-incomplete");
        User user = userRepository.findByUsername("taichung-incomplete").orElseThrow();
        unlockCity(user, 2L);
        saveCheckin(user, 4L, false);

        assertLocked(get("/api/quizzes/landmarks/5/random?difficulty=NORMAL"), token);
    }

    @Test
    void journeyUnlocksPalaceMuseumAfterTaipei101Completion() throws Exception {
        String token = registerAndGetToken("journey-one-user");
        User user = userRepository.findByUsername("journey-one-user").orElseThrow();
        saveCheckin(user, 1L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].actionLabel").value("查看故事"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("開始挑戰"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("LOCKED"));
    }

    @Test
    void journeyUnlocksXimendingAfterPalaceMuseumCompletion() throws Exception {
        String token = registerAndGetToken("journey-two-user");
        User user = userRepository.findByUsername("journey-two-user").orElseThrow();
        saveCheckin(user, 1L, true);
        saveCheckin(user, 2L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("查看故事"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].actionLabel").value("開始挑戰"));
    }

    @Test
    void onePlayersCompletionDoesNotChangeAnotherPlayersJourney() throws Exception {
        registerAndGetToken("journey-owner");
        String otherToken = registerAndGetToken("journey-other");
        User owner = userRepository.findByUsername("journey-owner").orElseThrow();
        saveCheckin(owner, 1L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageStatus").value("LOCKED"));
    }

    @Test
    void taipeiBossStageBecomesAvailableOnlyAfterAllThreeStagesComplete() throws Exception {
        String token = registerAndGetToken("journey-boss-ready");
        User user = userRepository.findByUsername("journey-boss-ready").orElseThrow();
        saveCheckin(user, 1L, true);
        saveCheckin(user, 2L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.actionLabel").value("完成前三關後解鎖"));

        saveCheckin(user, 3L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.actionLabel").value("挑戰城市守護者"));
    }

    @Test
    void incompleteCheckinDoesNotCountTowardTaipeiBossStage() throws Exception {
        String token = registerAndGetToken("boss-incomplete");
        User user = userRepository.findByUsername("boss-incomplete").orElseThrow();
        saveCheckin(user, 1L, true);
        saveCheckin(user, 2L, true);
        saveCheckin(user, 3L, false);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("LOCKED"));
    }

    @Test
    void completedTaipeiBossStageSupportsAnotherChallenge() throws Exception {
        String token = registerAndGetToken("boss-completed");
        User user = userRepository.findByUsername("boss-completed").orElseThrow();
        saveCheckin(user, 1L, true);
        saveCheckin(user, 2L, true);
        saveCheckin(user, 3L, true);
        var progress = userProgressRepository.findByUserIdAndCityId(user.getId(), 1L).orElseThrow();
        progress.setBossCompleted(true);
        progress.setBadgeUnlocked(true);
        userProgressRepository.save(progress);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].bossStage.actionLabel").value("再次挑戰守護者"));
    }

    @Test
    void onePlayersTaipeiBossProgressDoesNotAffectAnotherPlayer() throws Exception {
        registerAndGetToken("journey-boss-owner");
        String otherToken = registerAndGetToken("journey-boss-other");
        User owner = userRepository.findByUsername("journey-boss-owner").orElseThrow();
        saveCheckin(owner, 1L, true);
        saveCheckin(owner, 2L, true);
        saveCheckin(owner, 3L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].bossStage.stageStatus").value("LOCKED"));
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

    private void unlockCity(User user, Long cityId) {
        var progress = userProgressRepository.findByUserIdAndCityId(user.getId(), cityId).orElseThrow();
        progress.setUnlocked(true);
        userProgressRepository.save(progress);
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
