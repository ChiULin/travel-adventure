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
class FoodEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SceneRepository sceneRepository;
    @Autowired
    private CheckinRepository checkinRepository;

    @Test
    void beefSoupEventIsCheckinGatedPlayerBoundAndOneTime() throws Exception {
        String token = registerAndGetToken("food-player");
        User user = userRepository.findByUsername("food-player").orElseThrow();

        mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("完成台南 2 個景點後解鎖特色美食"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.requiredCheckins").value(2))
                .andExpect(jsonPath("$.data.completedCheckins").value(0))
                .andExpect(jsonPath("$.data.remainingCheckins").value(2))
                .andExpect(jsonPath("$.data.challenge").doesNotExist());

        saveCheckin(user, 7L, true);
        saveCheckin(user, 1L, true);
        mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.completedCheckins").value(1))
                .andExpect(jsonPath("$.data.remainingCheckins").value(1));

        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest("food-q-forged", "任意答案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("美食文化題不存在或已使用"));

        saveCheckin(user, 8L, true);
        String availableBody = mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("台南特色美食事件已解鎖"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.claimed").value(false))
                .andExpect(jsonPath("$.data.foodKey").value("TAINAN_BEEF_SOUP"))
                .andExpect(jsonPath("$.data.cityName").value("台南"))
                .andExpect(jsonPath("$.data.name").value("牛肉湯"))
                .andExpect(jsonPath("$.data.effect.type").value("EXTRA_TIME"))
                .andExpect(jsonPath("$.data.effect.value").value(2))
                .andExpect(jsonPath("$.data.challenge.questionId").isString())
                .andExpect(jsonPath("$.data.challenge.options", hasSize(4)))
                .andExpect(jsonPath("$.data.correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.data.fullDescription").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String firstQuestionId = extract(availableBody, "questionId");

        String repeatedBody = mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (!firstQuestionId.equals(extract(repeatedBody, "questionId"))) {
            throw new AssertionError("同城市應沿用尚未過期的美食題目");
        }

        String otherToken = registerAndGetToken("food-other");
        User other = userRepository.findByUsername("food-other").orElseThrow();
        saveCheckin(other, 7L, true);
        saveCheckin(other, 8L, true);
        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(firstQuestionId, correctAnswer())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("這不是你的美食文化題"));

        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(firstQuestionId, "將牛肉裹粉後油炸")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("答案不正確，可以重新挑戰"))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.foodKey").doesNotExist());

        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(firstQuestionId, correctAnswer())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("美食文化題不存在或已使用"));

        String secondBody = mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondQuestionId = extract(secondBody, "questionId");
        if (firstQuestionId.equals(secondQuestionId)) {
            throw new AssertionError("答錯後必須取得新的美食題目");
        }

        mockMvc.perform(post("/api/cities/4/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(secondQuestionId, correctAnswer())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("美食文化題不屬於這座城市"));

        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(secondQuestionId, correctAnswer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("成功解鎖台南牛肉湯"))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.foodKey").value("TAINAN_BEEF_SOUP"))
                .andExpect(jsonPath("$.data.name").value("牛肉湯"))
                .andExpect(jsonPath("$.data.effectType").value("EXTRA_TIME"))
                .andExpect(jsonPath("$.data.effectValue").value(2))
                .andExpect(jsonPath("$.data.explanation").isString());

        mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("台南牛肉湯已解鎖"))
                .andExpect(jsonPath("$.data.claimed").value(true))
                .andExpect(jsonPath("$.data.challenge").doesNotExist());

        mockMvc.perform(get("/api/cities/1/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("這座城市目前沒有特色美食事件"));
    }

    @Test
    void eligibilityIsRecheckedWhenClaiming() throws Exception {
        String token = registerAndGetToken("food-eligibility");
        User user = userRepository.findByUsername("food-eligibility").orElseThrow();
        saveCheckin(user, 7L, true);
        saveCheckin(user, 8L, true);
        String body = mockMvc.perform(get("/api/cities/3/food")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = extract(body, "questionId");
        checkinRepository.deleteAll(checkinRepository.findByUserId(user.getId()));

        mockMvc.perform(post("/api/cities/3/food/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequest(questionId, correctAnswer())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("尚未達成美食事件解鎖條件"));
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

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "token");
    }

    private String claimRequest(String questionId, String answer) {
        return "{\"questionId\":\"" + questionId + "\",\"answer\":\"" + answer + "\"}";
    }

    private String correctAnswer() {
        return "將生牛肉薄片放入碗中，再沖入熱高湯";
    }

    private String extract(String json, String property) {
        Matcher matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include " + property);
        }
        return matcher.group(1);
    }
}
