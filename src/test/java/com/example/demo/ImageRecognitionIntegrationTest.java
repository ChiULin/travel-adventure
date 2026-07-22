package com.example.demo;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
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
class ImageRecognitionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SceneRepository sceneRepository;
    @Autowired
    private CheckinRepository checkinRepository;

    @Test
    void imageChallengeIsPlayerBoundOneTimeAndCompletesCheckin() throws Exception {
        String token = registerAndGetToken("image-player");
        String otherToken = registerAndGetToken("image-other");
        User user = userRepository.findByUsername("image-player").orElseThrow();
        completeOtherTaipeiScenes(user);

        mockMvc.perform(get("/api/journey/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[1].interactionType").value("IMAGE_RECOGNITION"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("觀察景點照片"));

        String firstBody = mockMvc.perform(get("/api/image-challenges/scenes/2?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("取得圖片辨識挑戰成功"))
                .andExpect(jsonPath("$.data.questionId").isString())
                .andExpect(jsonPath("$.data.cityId").value(1))
                .andExpect(jsonPath("$.data.imageUrl").value("/images/landmarks/national-palace-museum.png"))
                .andExpect(jsonPath("$.data.displayMode").value("BLUR"))
                .andExpect(jsonPath("$.data.blurLevel").value(6))
                .andExpect(jsonPath("$.data.difficulty").value("NORMAL"))
                .andExpect(jsonPath("$.data.candidates", hasSize(4)))
                .andExpect(jsonPath("$.data.targetSceneId").doesNotExist())
                .andExpect(jsonPath("$.data.correctSceneId").doesNotExist())
                .andExpect(jsonPath("$.data.cultureExplanation").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String firstQuestionId = extract(firstBody, "questionId");

        mockMvc.perform(post("/api/image-challenges/" + firstQuestionId + "/complete")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(2L, "NORMAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("這不是你的圖片辨識題目"));

        mockMvc.perform(post("/api/image-challenges/" + firstQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(999L, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("選擇的景點不在候選清單中"));

        mockMvc.perform(post("/api/image-challenges/" + firstQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(1L, "NORMAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.cultureExplanation").isEmpty());
        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 2L)) {
            throw new AssertionError("Wrong image answer must not create checkin");
        }

        mockMvc.perform(post("/api/image-challenges/" + firstQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(2L, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("圖片辨識題目不存在或已使用"));

        String secondBody = mockMvc.perform(get("/api/image-challenges/scenes/2?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondQuestionId = extract(secondBody, "questionId");
        if (firstQuestionId.equals(secondQuestionId)) {
            throw new AssertionError("Consumed challenge must not be reused");
        }

        mockMvc.perform(post("/api/image-challenges/" + secondQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(2L, "CASUAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("圖片辨識難度與簽發時不符"));

        mockMvc.perform(post("/api/image-challenges/" + secondQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(2L, "NORMAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("辨識成功，完成景點打卡"))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(2))
                .andExpect(jsonPath("$.data.sceneName").value("國立故宮博物院"))
                .andExpect(jsonPath("$.data.cultureExplanation").isString())
                .andExpect(jsonPath("$.data.experienceGained").value(156))
                .andExpect(jsonPath("$.data.coinsGained").value(132))
                .andExpect(jsonPath("$.data.cityBossUnlocked").value(true));

        var completed = checkinRepository.findByUserIdAndSceneId(user.getId(), 2L).orElseThrow();
        if (!Boolean.TRUE.equals(completed.getCompleted())) {
            throw new AssertionError("Correct image answer must complete checkin");
        }

        mockMvc.perform(get("/api/journey/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].done").value(3))
                .andExpect(jsonPath("$.data.cities[0].bossUnlocked").value(true))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("查看景點故事"));

        mockMvc.perform(get("/api/image-challenges/scenes/2?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("這個景點已經完成打卡"));
    }

    private void completeOtherTaipeiScenes(User user) {
        for (Long sceneId : java.util.List.of(1L, 3L)) {
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
    }

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "token");
    }

    private String answerRequest(Long sceneId, String difficulty) {
        return "{\"sceneId\":" + sceneId + ",\"difficulty\":\"" + difficulty + "\"}";
    }

    private String extract(String json, String property) {
        Matcher matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include " + property);
        }
        return matcher.group(1);
    }
}
