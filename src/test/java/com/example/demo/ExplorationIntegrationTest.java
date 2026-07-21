package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
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

    @Test
    void cultureChallengeCreatesCheckinOnlyAfterValidOneTimeAnswer() throws Exception {
        String token = registerAndGetToken("exploration-test");
        String otherToken = registerAndGetToken("exploration-other");
        User user = userRepository.findByUsername("exploration-test").orElseThrow();

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("城市尚未解鎖"));

        unlockTainan(user.getId());

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("取得旅行委託成功"))
                .andExpect(jsonPath("$.data.missionId").value("TAINAN-ANPING-01"))
                .andExpect(jsonPath("$.data.remainingActions").value(4))
                .andExpect(jsonPath("$.data.availableInvestigations", hasSize(3)))
                .andExpect(jsonPath("$.data.discoveredClues", hasSize(0)))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[1].sceneId").value(8))
                .andExpect(jsonPath("$.data.candidates[1].name").value("安平古堡"))
                .andExpect(jsonPath("$.data.clues").doesNotExist())
                .andExpect(jsonPath("$.data.correctSceneId").doesNotExist());

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/investigate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"HISTORY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("你從歷史文獻中發現了新線索"))
                .andExpect(jsonPath("$.data.clueType").value("HISTORY"))
                .andExpect(jsonPath("$.data.alreadyDiscovered").value(false))
                .andExpect(jsonPath("$.data.remainingActions").value(3))
                .andExpect(jsonPath("$.data.discoveredClues", hasSize(1)));

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/investigate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"HISTORY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("這項線索已經調查過"))
                .andExpect(jsonPath("$.data.alreadyDiscovered").value(true))
                .andExpect(jsonPath("$.data.remainingActions").value(3));

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/guess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sceneId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("這個地點與目前線索不完全吻合"))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.remainingActions").value(2))
                .andExpect(jsonPath("$.data.wrongGuesses").value(1))
                .andExpect(jsonPath("$.data.canContinue").value(true))
                .andExpect(jsonPath("$.data.challenge").isEmpty());

        String firstChallengeBody = submitCorrectGuess(
                token, "TAINAN-ANPING-01", 8L, "安平古堡");
        String firstQuestionId = extract(firstChallengeBody, "questionId");

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/complete")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(firstQuestionId, "荷蘭")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(firstQuestionId, "英國")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("文化挑戰答案錯誤"))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.completed").value(false));
        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 8L)) {
            throw new AssertionError("Wrong culture answer must not create a checkin");
        }

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(firstQuestionId, "荷蘭")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        String secondChallengeBody = submitCorrectGuess(
                token, "TAINAN-ANPING-01", 8L, "安平古堡");
        String secondQuestionId = extract(secondChallengeBody, "questionId");
        if (firstQuestionId.equals(secondQuestionId)) {
            throw new AssertionError("A retried challenge must receive a new question id");
        }

        mockMvc.perform(post("/api/explorations/TAINAN-ANPING-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(secondQuestionId, "荷蘭")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("探索完成，成功打卡安平古堡"))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(8))
                .andExpect(jsonPath("$.data.sceneName").value("安平古堡"))
                .andExpect(jsonPath("$.data.explorationGrade").value("B"))
                .andExpect(jsonPath("$.data.cluesUsed").value(1))
                .andExpect(jsonPath("$.data.wrongGuesses").value(1))
                .andExpect(jsonPath("$.data.experienceGained").value(174))
                .andExpect(jsonPath("$.data.coinsGained").value(150))
                .andExpect(jsonPath("$.data.levelUp").value(true))
                .andExpect(jsonPath("$.data.cityBossUnlocked").value(false));

        var completed = checkinRepository.findByUserIdAndSceneId(user.getId(), 8L).orElseThrow();
        if (!Boolean.TRUE.equals(completed.getCompleted())) {
            throw new AssertionError("Correct culture answer must complete the checkin");
        }

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("目前沒有未完成的探索委託"));
    }

    @Test
    void cityMissionsRemainIsolatedAndCompleteTheirOwnScenes() throws Exception {
        String token = registerAndGetToken("multi-explore");
        User user = userRepository.findByUsername("multi-explore").orElseThrow();
        unlockCity(user.getId(), 5L);

        mockMvc.perform(get("/api/explorations/cities/1/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("TAIPEI-101-01"))
                .andExpect(jsonPath("$.data.cityId").value(1))
                .andExpect(jsonPath("$.data.availableInvestigations", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[0].sceneId").value(1))
                .andExpect(jsonPath("$.data.candidates[1].sceneId").value(2))
                .andExpect(jsonPath("$.data.candidates[2].sceneId").value(3))
                .andExpect(jsonPath("$.data.correctSceneId").doesNotExist())
                .andExpect(jsonPath("$.data.cultureAnswer").doesNotExist());

        mockMvc.perform(get("/api/explorations/cities/5/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionId").value("HUALIEN-TAROKO-01"))
                .andExpect(jsonPath("$.data.cityId").value(5))
                .andExpect(jsonPath("$.data.availableInvestigations", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[0].sceneId").value(13))
                .andExpect(jsonPath("$.data.candidates[1].sceneId").value(14))
                .andExpect(jsonPath("$.data.candidates[2].sceneId").value(15))
                .andExpect(jsonPath("$.data.correctSceneId").doesNotExist())
                .andExpect(jsonPath("$.data.cultureAnswer").doesNotExist());

        mockMvc.perform(post("/api/explorations/TAIPEI-101-01/guess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneId\":13}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("選擇的地點不在本次候選清單中"));

        String taipeiChallenge = submitCorrectGuess(token, "TAIPEI-101-01", 1L, "台北101");
        String tarokoChallenge = submitCorrectGuess(token, "HUALIEN-TAROKO-01", 13L, "太魯閣");
        String taipeiQuestionId = extract(taipeiChallenge, "questionId");
        String tarokoQuestionId = extract(tarokoChallenge, "questionId");

        mockMvc.perform(post("/api/explorations/HUALIEN-TAROKO-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(taipeiQuestionId, "立霧溪")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文化挑戰題目無效"));

        mockMvc.perform(post("/api/explorations/TAIPEI-101-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(taipeiQuestionId, "竹子")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(1))
                .andExpect(jsonPath("$.data.sceneName").value("台北101"));

        mockMvc.perform(post("/api/explorations/HUALIEN-TAROKO-01/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeRequest(tarokoQuestionId, "立霧溪")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(13))
                .andExpect(jsonPath("$.data.sceneName").value("太魯閣"));

        if (!checkinRepository.existsByUserIdAndSceneId(user.getId(), 1L)
                || !checkinRepository.existsByUserIdAndSceneId(user.getId(), 13L)) {
            throw new AssertionError("Each mission must create a checkin for its own target scene");
        }
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

    private void unlockTainan(Long userId) {
        unlockCity(userId, 3L);
    }

    private void unlockCity(Long userId, Long cityId) {
        UserProgress progress = userProgressRepository.findByUserIdAndCityId(userId, cityId).orElseThrow();
        progress.setUnlocked(true);
        userProgressRepository.save(progress);
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
