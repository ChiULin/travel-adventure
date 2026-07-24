package com.example.demo;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ImageRecognitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Autowired
    private ImageRecognitionService imageRecognitionService;

    @Test
    void taipei101FocusImageChallengeHidesAnswerAndUnlocksPalaceOnlyWhenCorrect() throws Exception {
        String token = registerAndGetToken("taipei-focus-player");
        User user = userRepository.findByUsername("taipei-focus-player").orElseThrow();

        ClassPathResource focusImage =
                new ClassPathResource("static/images/challenges/taipei101-focus.jpg");
        if (!focusImage.exists()) {
            throw new AssertionError("Taipei 101 focus image must exist");
        }

        ImageRecognitionService.ImageChallengeView first =
                imageRecognitionService.issue(user.getId(), 1L, "NORMAL");
        assertEquals("/images/challenges/taipei101-focus.jpg", first.imageUrl());
        assertEquals(4, first.candidates().size());
        assertPublicViewHidesAnswer();
        String firstQuestionId = first.questionId();
        mockMvc.perform(post("/api/image-challenges/" + firstQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(5L, "NORMAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.completed").value(false));

        if (checkinRepository.existsByUserIdAndSceneId(user.getId(), 1L)) {
            throw new AssertionError("Wrong focus-image answer must not create checkin");
        }
        assertPalaceLocked(token);

        ImageRecognitionService.ImageChallengeView second =
                imageRecognitionService.issue(user.getId(), 1L, "NORMAL");
        String secondQuestionId = second.questionId();

        mockMvc.perform(post("/api/image-challenges/" + secondQuestionId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(1L, "NORMAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(1))
                .andExpect(jsonPath("$.data.sceneName").value("台北101"));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("AVAILABLE"));
    }

    @Test
    void imageChallengeIsPlayerBoundOneTimeAndCompletesCheckin() throws Exception {
        String token = registerAndGetToken("image-player");
        String otherToken = registerAndGetToken("image-other");
        User user = userRepository.findByUsername("image-player").orElseThrow();
        completeOtherTaipeiScenes(user);

        mockMvc.perform(get("/api/journey/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[1].interactionType").value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("開始未知挑戰"));

        ImageRecognitionService.ImageChallengeView first =
                imageRecognitionService.issue(user.getId(), 2L, "NORMAL");
        assertEquals(1L, first.cityId());
        assertEquals("/images/challenges/palace-focus.jpg", first.imageUrl());
        assertEquals("BLUR", first.displayMode());
        assertEquals(6, first.blurLevel());
        assertEquals("NORMAL", first.difficulty());
        assertEquals(4, first.candidates().size());
        assertPublicViewHidesAnswer();
        String firstQuestionId = first.questionId();

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

        ImageRecognitionService.ImageChallengeView second =
                imageRecognitionService.issue(user.getId(), 2L, "NORMAL");
        String secondQuestionId = second.questionId();
        assertNotEquals(firstQuestionId, secondQuestionId);

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
                .andExpect(jsonPath("$.data.cities[0].scenes[1].actionLabel").value("查看故事"));

        assertThrows(IllegalArgumentException.class,
                () -> imageRecognitionService.issue(user.getId(), 2L, "NORMAL"));
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

    private void assertPalaceLocked(String token) throws Exception {
        mockMvc.perform(post("/api/mystery-challenges/landmarks/2/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"NORMAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("請先完成上一個景點關卡"));
    }

    private void assertPublicViewHidesAnswer() {
        var fields = java.util.Arrays.stream(
                        ImageRecognitionService.ImageChallengeView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertFalse(fields.contains("targetSceneId"));
        assertFalse(fields.contains("correctSceneId"));
        assertFalse(fields.contains("cultureExplanation"));
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
