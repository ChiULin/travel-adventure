package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PuzzleChallengeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class PuzzleChallengeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CheckinRepository checkinRepository;

    @Autowired
    private PuzzleChallengeService puzzleChallengeService;

    @Test
    void puzzleAnswerIsPlayerBoundOneTimeAndUnlocksPalaceOnlyWhenCorrect() throws Exception {
        String token = registerAndGetToken("puzzle-player");
        String otherToken = registerAndGetToken("puzzle-other");
        User user = userRepository.findByUsername("puzzle-player").orElseThrow();

        PuzzleChallengeService.PuzzleChallengeView first =
                puzzleChallengeService.issue(user.getId(), 1L, "NORMAL", "MC-integration-1");
        assertFalse(first.initialTileOrder().stream()
                .allMatch(tile -> first.initialTileOrder().get(tile) == tile));
        assertTrue(first.initialTileOrder().stream().distinct().count() == 9);
        assertTrue(first.candidates().size() == 4);
        var responseFields = java.util.Arrays.stream(
                        PuzzleChallengeService.PuzzleChallengeView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertFalse(responseFields.contains("correctLandmarkId"));
        assertFalse(responseFields.contains("correctStage"));

        mockMvc.perform(post("/api/puzzle-challenges/" + first.challengeId() + "/complete")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("這不是你的拼圖挑戰"));

        mockMvc.perform(post("/api/puzzle-challenges/" + first.challengeId() + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.completed").value(false));

        assertFalse(checkinRepository.existsByUserIdAndSceneId(user.getId(), 1L));
        assertPalaceLocked(token);

        PuzzleChallengeService.PuzzleChallengeView second =
                puzzleChallengeService.issue(user.getId(), 1L, "NORMAL", "MC-integration-2");
        assertNotEquals(first.challengeId(), second.challengeId());

        mockMvc.perform(post("/api/puzzle-challenges/" + second.challengeId() + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.sceneId").value(1))
                .andExpect(jsonPath("$.data.sceneName").value("台北101"))
                .andExpect(jsonPath("$.data.experienceGained").value(150))
                .andExpect(jsonPath("$.data.coinsGained").value(126));

        mockMvc.perform(post("/api/puzzle-challenges/" + second.challengeId() + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("拼圖挑戰不存在或已使用"));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].scenes[0].stageStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cities[0].scenes[1].stageStatus").value("AVAILABLE"));
    }

    private void assertPalaceLocked(String token) throws Exception {
        mockMvc.perform(get("/api/image-challenges/scenes/2?difficulty=NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("請先完成上一個景點關卡"));
    }

    private String answerRequest(Long selectedLandmarkId) {
        return "{\"selectedLandmarkId\":" + selectedLandmarkId + "}";
    }

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include token");
        }
        return matcher.group(1);
    }
}
