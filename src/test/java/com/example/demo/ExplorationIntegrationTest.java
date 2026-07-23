package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExplorationMissionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class ExplorationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Autowired
    private CheckinRepository checkinRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private ExplorationMissionRegistry missionRegistry;

    @Test
    void tainanExplorationIssuanceRequiresMysteryEntry() throws Exception {
        String token = registerAndGetToken("exploration-test");
        User user = userRepository.findByUsername("exploration-test").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));

        unlockCity(user.getId(), 3L);
        saveCompletedCheckin(user, 7L);

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));
        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 8L)) {
            throw new AssertionError("Legacy exploration issuance must not create a checkin");
        }
    }

    @Test
    void legacyTaipeiAndHualienExplorationsRequireMysteryEntry() throws Exception {
        String token = registerAndGetToken("multi-explore");
        User user = userRepository.findByUsername("multi-explore").orElseThrow();
        unlockCity(user.getId(), 5L);

        mockMvc.perform(get("/api/explorations/cities/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));

        mockMvc.perform(get("/api/explorations/cities/5/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));

        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 1L)
                || checkinRepository.existsByUserIdAndSceneId(user.getId(), 13L)) {
            throw new AssertionError("Legacy mystery-only exploration must not create a checkin");
        }
    }

    @Test
    void everyConfiguredCityExplorationRequiresMysteryEntry() throws Exception {
        String token = registerAndGetToken("remaining-cities");
        User user = userRepository.findByUsername("remaining-cities").orElseThrow();
        unlockCity(user.getId(), 2L);
        unlockCity(user.getId(), 4L);
        unlockCity(user.getId(), 5L);
        unlockCity(user.getId(), 6L);

        mockMvc.perform(get("/api/journey/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[1].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[3].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[4].scenes[0].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[5].scenes[0].interactionType").value("MYSTERY"));

        mockMvc.perform(get("/api/explorations/cities/4/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));
        mockMvc.perform(get("/api/explorations/cities/5/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));
        mockMvc.perform(get("/api/explorations/cities/6/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("此城市的探索已啟用未知挑戰，請由未知挑戰入口開始"));

        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 10L)
                || checkinRepository.existsByUserIdAndSceneId(user.getId(), 13L)
                || checkinRepository.existsByUserIdAndSceneId(user.getId(), 16L)) {
            throw new AssertionError("Legacy mystery-only exploration must not create a checkin");
        }
    }

    @Test
    void everyMissionTargetAndCandidateBelongsToDeclaredCity() {
        missionRegistry.findAll().forEach(mission -> {
            java.util.Set<Long> citySceneIds = sceneRepository.findByCityId(mission.cityId()).stream()
                    .map(com.example.demo.entity.Scene::getId)
                    .collect(java.util.stream.Collectors.toSet());
            mission.candidateSceneIds().forEach(sceneId -> {
                if (!citySceneIds.contains(sceneId)) {
                    throw new AssertionError(mission.missionKey() + " contains a scene from another city");
                }
            });
        });
    }

    private void assertMission(String token, Long cityId, String missionId,
                               Long targetSceneId, String targetSceneName) throws Exception {
        mockMvc.perform(get("/api/explorations/cities/" + cityId + "/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value(missionId))
                .andExpect(jsonPath("$.data.cityId").value(cityId))
                .andExpect(jsonPath("$.data.availableInvestigations", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[0].sceneId").value(targetSceneId))
                .andExpect(jsonPath("$.data.candidates[0].name").value(targetSceneName))
                .andExpect(jsonPath("$.data.correctSceneId").doesNotExist())
                .andExpect(jsonPath("$.data.cultureAnswer").doesNotExist());
    }

    private void completeMission(String token, String missionId, String questionId,
                                 String answer, Long sceneId, String sceneName) throws Exception {
        mockMvc.perform(post("/api/explorations/" + missionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(questionId, answer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(sceneId))
                .andExpect(jsonPath("$.data.sceneName").value(sceneName));
    }

    private String submitCorrectGuess(String token, String missionId, Long sceneId, String sceneName) throws Exception {
        return mockMvc.perform(post("/api/explorations/" + missionId + "/guess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneId\":" + sceneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("推理成功，請完成文化挑戰"))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.missionId").value(missionId))
                .andExpect(jsonPath("$.data.sceneId").value(sceneId))
                .andExpect(jsonPath("$.data.sceneName").value(sceneName))
                .andExpect(jsonPath("$.data.challenge.questionId").isString())
                .andExpect(jsonPath("$.data.challenge.question").isString())
                .andExpect(jsonPath("$.data.challenge.options", hasSize(4)))
                .andExpect(jsonPath("$.data.challenge.expiresAt").isString())
                .andExpect(jsonPath("$.data.challenge.answer").doesNotExist())
                .andExpect(jsonPath("$.data.challenge.correctAnswer").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extract(body, "token");
    }


    private void unlockCity(Long userId, Long cityId) {
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(userId, cityId).orElseThrow();
        progress.setUnlocked(true);
        userProgressRepository.save(progress);
    }

    private void saveCompletedCheckin(User user, Long sceneId) {
        checkinRepository.save(com.example.demo.entity.Checkin.builder()
                .user(user)
                .scene(sceneRepository.findById(sceneId).orElseThrow())
                .quizCorrect(true)
                .completed(true)
                .earnedExp(0)
                .earnedCoins(0)
                .build());
    }

    private String completeRequest(String questionId, String answer) {
        return "{\"questionId\":\"" + questionId + "\",\"answer\":\"" + answer
                + "\",\"difficulty\":\"NORMAL\"}";
    }

    private String extract(String json, String property) {
        Matcher matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include " + property);
        }
        return matcher.group(1);
    }
}
