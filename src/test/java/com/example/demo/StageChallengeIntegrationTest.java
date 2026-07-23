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
    void taipeiMysteryStagesRejectLegacyIssuanceApis() throws Exception {
        String token = registerAndGetToken("stage-api-player");
        User user = userRepository.findByUsername("stage-api-player").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));

        assertMysteryOnly(get("/api/image-challenges/scenes/1?difficulty=NORMAL"), token);
        assertMysteryOnly(get("/api/image-challenges/scenes/2?difficulty=NORMAL"), token);
        assertMysteryOnly(get("/api/quizzes/landmarks/3/random?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 2L);
        assertNoCheckin(user.getId(), 3L);
    }

    private void assertMysteryOnly(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token
    ) throws Exception {
        assertMysteryOnly(
                request,
                token,
                "此景點已啟用未知挑戰，請由未知挑戰入口開始"
        );
    }

    private void assertMysteryOnly(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token,
            String message
    ) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void anotherPlayersCheckinDoesNotUnlockNextStage() throws Exception {
        registerAndGetToken("stage-api-owner");
        String otherToken = registerAndGetToken("stage-api-other");
        User owner = userRepository.findByUsername("stage-api-owner").orElseThrow();

        saveCheckin(owner, 1L, true);

        assertLocked(mysteryStart(2L), otherToken);
    }

    @Test
    void incompleteCheckinDoesNotUnlockNextStage() throws Exception {
        String token = registerAndGetToken("stage-incomplete");
        User user = userRepository.findByUsername("stage-incomplete").orElseThrow();

        saveCheckin(user, 1L, false);

        assertLocked(mysteryStart(2L), token);
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
                .andExpect(jsonPath("$.data.cities[0].scenes[0].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[0].scenes[0].actionLabel").value("開始未知挑戰"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].id").value(2))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageLabel").value("第 2 關"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("完成上一關後解鎖"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].id").value(3))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageLabel").value("第 3 關"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[0].scenes[2].interactionType").value("MYSTERY"))
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
                .andExpect(jsonPath("$.data.cities[1].scenes[0].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].id").value(5))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[1].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].id").value(6))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[1].scenes[2].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[1].bossStage.cityId").value(2))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[1].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].id").value(7))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].id").value(8))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].id").value(9))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].bossStage.cityId").value(3))
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].id").value(10))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].id").value(11))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].id").value(12))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[3].bossStage.cityId").value(4))
                .andExpect(jsonPath("$.data.cities[3].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[3].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].id").value(13))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].id").value(14))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].id").value(15))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].mysteryChallengeEnabled").value(true))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[4].bossStage.cityId").value(5))
                .andExpect(jsonPath("$.data.cities[4].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[4].bossStage.stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].id").value(16))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageConfigured").value(true))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageOrder").value(1))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].interactionType").value("EXPLORATION"))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].id").value(17))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageOrder").value(2))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].interactionType").value("QUIZ"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].id").value(18))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageOrder").value(3))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].interactionType").value("QUIZ"))
                .andExpect(jsonPath("$.data.cities[5].bossStage.cityId").value(6))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageOrder").value(4))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageStatus").value("LOCKED"));
    }

    @Test
    void taichungMysteryApisEnforceCityAndStageOrder() throws Exception {
        String token = registerAndGetToken("taichung-api-player");
        User user = userRepository.findByUsername("taichung-api-player").orElseThrow();

        assertLocked(mysteryStart(4L), token);
        unlockCity(user, 2L);

        mockMvc.perform(mysteryStart(4L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.landmarkId").value(4));

        assertMysteryOnly(get("/api/explorations/cities/2/random"), token,
                "此城市的探索已啟用未知挑戰，請由未知挑戰入口開始");
        assertMysteryOnly(get("/api/quizzes/landmarks/5/random?difficulty=NORMAL"), token,
                "此景點已啟用未知挑戰，請由未知挑戰入口開始");
        assertMysteryOnly(get("/api/image-challenges/scenes/6?difficulty=NORMAL"), token,
                "此景點已啟用未知挑戰，請由未知挑戰入口開始");
        assertLocked(mysteryStart(5L), token);
        assertNoCheckin(user.getId(), 5L);

        saveCheckin(user, 4L, true);

        mockMvc.perform(mysteryStart(5L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.landmarkId").value(5));
        assertLocked(mysteryStart(6L), token);

        saveCheckin(user, 5L, true);

        mockMvc.perform(mysteryStart(6L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.landmarkId").value(6));
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

        assertLocked(mysteryStart(5L), otherToken);
    }

    @Test
    void incompleteTaichungCheckinDoesNotUnlockNextStage() throws Exception {
        String token = registerAndGetToken("taichung-incomplete");
        User user = userRepository.findByUsername("taichung-incomplete").orElseThrow();
        unlockCity(user, 2L);
        saveCheckin(user, 4L, false);

        assertLocked(mysteryStart(5L), token);
    }

    @Test
    void tainanMysteryApisEnforceCityAndStageOrder() throws Exception {
        String token = registerAndGetToken("tainan-api-player");
        User user = userRepository.findByUsername("tainan-api-player").orElseThrow();

        assertLocked(mysteryStart(7L), token);
        unlockCity(user, 3L);

        mockMvc.perform(mysteryStart(7L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());

        assertLocked(mysteryStart(8L), token);
        assertMysteryOnly(get("/api/explorations/cities/3/random"), token,
                "此城市的探索已啟用未知挑戰，請由未知挑戰入口開始");
        assertMysteryOnly(get("/api/quizzes/landmarks/7/random?difficulty=NORMAL"), token);
        assertMysteryOnly(get("/api/image-challenges/scenes/7?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 8L);

        saveCheckin(user, 7L, true);

        mockMvc.perform(mysteryStart(8L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());
        assertLocked(mysteryStart(9L), token);

        saveCheckin(user, 8L, true);

        mockMvc.perform(mysteryStart(9L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());
        assertNoCheckin(user.getId(), 9L);
    }

    @Test
    void tainanJourneyUnlocksStagesAndBossInOrder() throws Exception {
        String token = registerAndGetToken("tainan-journey");
        User user = userRepository.findByUsername("tainan-journey").orElseThrow();

        unlockCity(user, 3L);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 7L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageStatus").value("LOCKED"));

        saveCheckin(user, 8L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 9L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].scenes[2].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageStatus").value("AVAILABLE"));
    }

    @Test
    void anotherPlayersTainanCheckinDoesNotUnlockAnpingFort() throws Exception {
        registerAndGetToken("tainan-owner");
        String otherToken = registerAndGetToken("tainan-other");
        User owner = userRepository.findByUsername("tainan-owner").orElseThrow();
        User other = userRepository.findByUsername("tainan-other").orElseThrow();
        unlockCity(other, 3L);
        saveCheckin(owner, 7L, true);

        assertLocked(mysteryStart(8L), otherToken);
    }

    @Test
    void incompleteChihkanTowerCheckinDoesNotUnlockAnpingFort() throws Exception {
        String token = registerAndGetToken("tainan-incomplete");
        User user = userRepository.findByUsername("tainan-incomplete").orElseThrow();
        unlockCity(user, 3L);
        saveCheckin(user, 7L, false);

        assertLocked(mysteryStart(8L), token);
    }

    @Test
    void kaohsiungMysteryApisEnforceCityAndStageOrder() throws Exception {
        String token = registerAndGetToken("kaohsiung-api-player");
        User user = userRepository.findByUsername("kaohsiung-api-player").orElseThrow();

        assertLocked(mysteryStart(10L), token);
        unlockCity(user, 4L);

        mockMvc.perform(mysteryStart(10L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());

        assertLocked(mysteryStart(11L), token);
        assertMysteryOnly(get("/api/explorations/cities/4/random"), token,
                "此城市的探索已啟用未知挑戰，請由未知挑戰入口開始");
        assertMysteryOnly(get("/api/quizzes/landmarks/10/random?difficulty=NORMAL"), token);
        assertMysteryOnly(get("/api/image-challenges/scenes/10?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 11L);

        saveCheckin(user, 10L, true);

        mockMvc.perform(mysteryStart(11L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());
        assertLocked(mysteryStart(12L), token);

        saveCheckin(user, 11L, true);

        mockMvc.perform(mysteryStart(12L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeSessionId").isString());
        assertNoCheckin(user.getId(), 12L);
    }

    @Test
    void kaohsiungJourneyUnlocksStagesAndBossInOrder() throws Exception {
        String token = registerAndGetToken("kaohsiung-journey");
        User user = userRepository.findByUsername("kaohsiung-journey").orElseThrow();

        unlockCity(user, 4L);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[3].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 10L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[3].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageStatus").value("LOCKED"));

        saveCheckin(user, 11L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[3].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[3].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 12L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[3].scenes[2].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[3].bossStage.stageStatus").value("AVAILABLE"));
    }

    @Test
    void anotherPlayersKaohsiungCheckinDoesNotUnlockLoveRiver() throws Exception {
        registerAndGetToken("kaohsiung-owner");
        String otherToken = registerAndGetToken("kaohsiung-other");
        User owner = userRepository.findByUsername("kaohsiung-owner").orElseThrow();
        User other = userRepository.findByUsername("kaohsiung-other").orElseThrow();
        unlockCity(other, 4L);
        saveCheckin(owner, 10L, true);

        assertLocked(mysteryStart(11L), otherToken);
    }

    @Test
    void incompletePier2CheckinDoesNotUnlockLoveRiver() throws Exception {
        String token = registerAndGetToken("kaohsiung-incomplete");
        User user = userRepository.findByUsername("kaohsiung-incomplete").orElseThrow();
        unlockCity(user, 4L);
        saveCheckin(user, 10L, false);

        assertLocked(mysteryStart(11L), token);
    }

    @Test
    void hualienMysteryApisEnforceCityAndStageOrder() throws Exception {
        String token = registerAndGetToken("hualien-api-player");
        User user = userRepository.findByUsername("hualien-api-player").orElseThrow();

        assertLocked(mysteryStart(13L), token);
        assertNoCheckin(user.getId(), 13L);

        unlockCity(user, 5L);

        mockMvc.perform(get("/api/explorations/cities/5/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/quizzes/landmarks/13/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/image-challenges/scenes/13")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(mysteryStart(13L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertLocked(mysteryStart(14L), token);
        assertNoCheckin(user.getId(), 14L);

        saveCheckin(user, 13L, true);

        mockMvc.perform(mysteryStart(14L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertLocked(mysteryStart(15L), token);

        saveCheckin(user, 14L, true);

        mockMvc.perform(mysteryStart(15L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertNoCheckin(user.getId(), 15L);
    }

    @Test
    void hualienJourneyUnlocksStagesAndBossInOrder() throws Exception {
        String token = registerAndGetToken("hualien-journey");
        User user = userRepository.findByUsername("hualien-journey").orElseThrow();

        unlockCity(user, 5L);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[4].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 13L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[4].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageStatus").value("LOCKED"));

        saveCheckin(user, 14L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[4].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[4].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 15L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[4].scenes[2].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[4].bossStage.stageStatus").value("AVAILABLE"));
    }

    @Test
    void anotherPlayersHualienCheckinDoesNotUnlockQixingtan() throws Exception {
        registerAndGetToken("hualien-owner");
        String otherToken = registerAndGetToken("hualien-other");
        User owner = userRepository.findByUsername("hualien-owner").orElseThrow();
        User other = userRepository.findByUsername("hualien-other").orElseThrow();
        unlockCity(other, 5L);
        saveCheckin(owner, 13L, true);

        assertLocked(mysteryStart(14L), otherToken);
    }

    @Test
    void incompleteTarokoCheckinDoesNotUnlockQixingtan() throws Exception {
        String token = registerAndGetToken("hualien-incomplete");
        User user = userRepository.findByUsername("hualien-incomplete").orElseThrow();
        unlockCity(user, 5L);
        saveCheckin(user, 13L, false);

        assertLocked(mysteryStart(14L), token);
    }

    @Test
    void penghuChallengeApisEnforceStageOrderWithoutCreatingLockedExplorationState() throws Exception {
        String token = registerAndGetToken("penghu-api-player");
        User user = userRepository.findByUsername("penghu-api-player").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/6/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/explorations/PENGHU-DOUBLE-HEART-01/investigate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"LOCAL\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/explorations/PENGHU-DOUBLE-HEART-01/guess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneId\":16}"))
                .andExpect(status().is4xxClientError());
        assertNoCheckin(user.getId(), 16L);

        unlockCity(user, 6L);

        mockMvc.perform(get("/api/explorations/cities/6/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("PENGHU-DOUBLE-HEART-01"));

        assertLocked(get("/api/quizzes/landmarks/17/random?difficulty=NORMAL"), token);
        assertNoCheckin(user.getId(), 17L);

        saveCheckin(user, 16L, true);

        mockMvc.perform(get("/api/quizzes/landmarks/17/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertLocked(get("/api/quizzes/landmarks/18/random?difficulty=NORMAL"), token);

        saveCheckin(user, 17L, true);

        mockMvc.perform(get("/api/quizzes/landmarks/18/random?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").isString());
        assertNoCheckin(user.getId(), 18L);
    }

    @Test
    void penghuJourneyUnlocksStagesAndBossInOrder() throws Exception {
        String token = registerAndGetToken("penghu-journey");
        User user = userRepository.findByUsername("penghu-journey").orElseThrow();

        unlockCity(user, 6L);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("LOCKED"))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 16L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[5].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("LOCKED"));

        saveCheckin(user, 17L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[5].scenes[1].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageStatus").value("LOCKED"));

        saveCheckin(user, 18L, true);

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[5].scenes[2].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[5].bossStage.stageStatus").value("AVAILABLE"));
    }

    @Test
    void anotherPlayersPenghuCheckinDoesNotUnlockGreatBridge() throws Exception {
        registerAndGetToken("penghu-owner");
        String otherToken = registerAndGetToken("penghu-other");
        User owner = userRepository.findByUsername("penghu-owner").orElseThrow();
        User other = userRepository.findByUsername("penghu-other").orElseThrow();
        unlockCity(other, 6L);
        saveCheckin(owner, 16L, true);

        assertLocked(get("/api/quizzes/landmarks/17/random?difficulty=NORMAL"), otherToken);
    }

    @Test
    void incompleteStoneWeirCheckinDoesNotUnlockGreatBridge() throws Exception {
        String token = registerAndGetToken("penghu-incomplete");
        User user = userRepository.findByUsername("penghu-incomplete").orElseThrow();
        unlockCity(user, 6L);
        saveCheckin(user, 16L, false);

        assertLocked(get("/api/quizzes/landmarks/17/random?difficulty=NORMAL"), token);
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
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("開始未知挑戰"))
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
                .andExpect(jsonPath("$.data.cities[0].scenes[2].actionLabel").value("開始未知挑戰"));
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mysteryStart(
            Long landmarkId
    ) {
        return post("/api/mystery-challenges/landmarks/" + landmarkId + "/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"NORMAL\"}");
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
