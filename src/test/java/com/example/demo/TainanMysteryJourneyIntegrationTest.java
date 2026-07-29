package com.example.demo;

import com.example.demo.dto.BossStartResponse;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BossService;
import com.example.demo.service.CheckinService;
import com.example.demo.service.GameDifficulty;
import com.example.demo.service.ImageRecognitionService;
import com.example.demo.service.LandmarkChallengePoolRegistry;
import com.example.demo.service.MysteryChallengeService;
import com.example.demo.service.MysteryChallengeType;
import com.example.demo.service.PuzzleChallengeService;
import com.example.demo.stage.StageLockedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class TainanMysteryJourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProgressRepository userProgressRepository;
    @Autowired private CheckinRepository checkinRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private MysteryChallengeService mysteryChallengeService;
    @Autowired private LandmarkChallengePoolRegistry challengePoolRegistry;
    @Autowired private CheckinService checkinService;
    @Autowired private ImageRecognitionService imageRecognitionService;
    @Autowired private PuzzleChallengeService puzzleChallengeService;
    @Autowired private BossService bossService;

    @Test
    void tainanRequiresTaichungClearAndKeepsSessionsPlayerBound() throws Exception {
        String token = registerAndGetToken("tn-myst-owner");
        registerAndGetToken("tn-myst-other");
        User owner = user("tn-myst-owner");
        User other = user("tn-myst-other");

        assertThrows(StageLockedException.class,
                () -> mysteryChallengeService.start(owner.getId(), 7L, "NORMAL"));

        unlockTainan(owner);
        unlockTainan(other);
        MysteryChallengeService.MysteryChallengeResponse first =
                mysteryChallengeService.start(owner.getId(), 7L, "NORMAL");
        MysteryChallengeService.MysteryChallengeResponse repeated =
                mysteryChallengeService.start(owner.getId(), 7L, "NORMAL");
        MysteryChallengeService.MysteryChallengeResponse isolated =
                mysteryChallengeService.start(other.getId(), 7L, "NORMAL");

        assertEquals(first.challengeSessionId(), repeated.challengeSessionId());
        assertEquals(first.challengeType(), repeated.challengeType());
        assertEquals(first.challengeData(), repeated.challengeData());
        assertNotEquals(first.challengeSessionId(), isolated.challengeSessionId());
        assertEquals(List.of(
                MysteryChallengeType.QUIZ,
                MysteryChallengeType.IMAGE_RECOGNITION,
                MysteryChallengeType.PUZZLE
        ), challengePoolRegistry.getAvailableTypes(3, 1));
        assertEquals(List.of(
                MysteryChallengeType.EXPLORATION,
                MysteryChallengeType.QUIZ,
                MysteryChallengeType.IMAGE_RECOGNITION,
                MysteryChallengeType.PUZZLE
        ), challengePoolRegistry.getAvailableTypes(3, 2));
        assertEquals(challengePoolRegistry.getAvailableTypes(3, 1),
                challengePoolRegistry.getAvailableTypes(3, 3));

        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].scenes[0].interactionType")
                        .value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].scenes[0].actionLabel")
                        .value("開始未知挑戰"))
                .andExpect(jsonPath("$.data.cities[2].scenes[1].interactionType")
                        .value("MYSTERY"))
                .andExpect(jsonPath("$.data.cities[2].scenes[2].interactionType")
                        .value("MYSTERY"));
    }

    @Test
    void legacyTainanIssuanceEndpointsCannotBypassMystery() throws Exception {
        String token = registerAndGetToken("tn-legacy");
        unlockTainan(user("tn-legacy"));

        mockMvc.perform(get("/api/explorations/cities/3/random")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        for (Long landmarkId : List.of(7L, 8L, 9L)) {
            mockMvc.perform(get("/api/quizzes/landmarks/" + landmarkId + "/random")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            mockMvc.perform(get("/api/image-challenges/scenes/" + landmarkId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void threeMysteryLandmarksUnlockTainanBossAndFirstClearUnlocksKaohsiung()
            throws Exception {
        String token = registerAndGetToken("tn-full");
        User user = user("tn-full");
        unlockTainan(user);

        completeMystery(user.getId(), mysteryChallengeService.start(
                user.getId(), 7L, "NORMAL"));
        assertStageState(token, 0, "COMPLETED");
        assertStageState(token, 1, "AVAILABLE");

        completeMystery(user.getId(), mysteryChallengeService.start(
                user.getId(), 8L, "NORMAL"));
        assertStageState(token, 1, "COMPLETED");
        assertStageState(token, 2, "AVAILABLE");

        MysteryChallengeService.MysteryChallengeResponse failed =
                mysteryChallengeService.start(user.getId(), 9L, "NORMAL");
        failMystery(user.getId(), failed);
        assertFalse(checkinRepository.existsByUserIdAndSceneIdAndCompletedTrue(
                user.getId(), 9L));
        assertBossState(token, "LOCKED");

        completeMystery(user.getId(), mysteryChallengeService.start(
                user.getId(), 9L, "NORMAL"));
        assertStageState(token, 2, "COMPLETED");
        assertBossState(token, "AVAILABLE");

        BossStartResponse boss = bossService.startChallenge(
                user.getId(), 3L, GameDifficulty.NORMAL);
        String questionId = String.valueOf(boss.question().get("questionId"));
        Map<String, Object> result = bossService.challengeResult(
                user.getId(),
                3L,
                null,
                correctBossAnswer(3L, questionId),
                questionId,
                GameDifficulty.NORMAL.name()
        );

        assertTrue((Boolean) result.get("win"));
        assertTrue(userProgressRepository.findByUserIdAndCityId(user.getId(), 4L)
                .orElseThrow()
                .getUnlocked());
    }

    private void completeMystery(
            Long userId,
            MysteryChallengeService.MysteryChallengeResponse challenge
    ) {
        String childId;
        switch (challenge.challengeType()) {
            case EXPLORATION -> {
                childId = ((com.example.demo.service.ExplorationService.ExplorationMissionView)
                        challenge.challengeData()).missionId();
                checkinService.completeExploration(
                        userId, challenge.landmarkId(), challenge.difficulty().name());
            }
            case QUIZ -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> question = (Map<String, Object>) challenge.challengeData();
                childId = String.valueOf(question.get("questionId"));
                checkinService.checkin(
                        userId,
                        challenge.landmarkId(),
                        null,
                        correctSceneAnswer(challenge.landmarkId(), childId),
                        childId,
                        challenge.difficulty().name()
                );
            }
            case IMAGE_RECOGNITION -> {
                ImageRecognitionService.ImageChallengeView image =
                        (ImageRecognitionService.ImageChallengeView) challenge.challengeData();
                childId = image.questionId();
                imageRecognitionService.complete(
                        userId,
                        childId,
                        challenge.landmarkId(),
                        challenge.difficulty().name()
                );
            }
            case PUZZLE -> {
                PuzzleChallengeService.PuzzleChallengeView puzzle =
                        (PuzzleChallengeService.PuzzleChallengeView) challenge.challengeData();
                childId = puzzle.challengeId();
                puzzleChallengeService.complete(
                        userId, childId, challenge.landmarkId());
            }
            default -> throw new AssertionError("Unsupported mystery type");
        }
        mysteryChallengeService.markConsumed(userId, childId);
    }

    private void failMystery(
            Long userId,
            MysteryChallengeService.MysteryChallengeResponse challenge
    ) {
        String childId;
        switch (challenge.challengeType()) {
            case QUIZ -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> question = (Map<String, Object>) challenge.challengeData();
                childId = String.valueOf(question.get("questionId"));
                checkinService.checkin(
                        userId,
                        challenge.landmarkId(),
                        null,
                        "definitely-wrong",
                        childId,
                        challenge.difficulty().name()
                );
            }
            case IMAGE_RECOGNITION -> {
                ImageRecognitionService.ImageChallengeView image =
                        (ImageRecognitionService.ImageChallengeView) challenge.challengeData();
                childId = image.questionId();
                Long wrong = image.candidates().stream()
                        .map(ImageRecognitionService.SceneOption::sceneId)
                        .filter(sceneId -> !sceneId.equals(challenge.landmarkId()))
                        .findFirst()
                        .orElseThrow();
                imageRecognitionService.complete(
                        userId, childId, wrong, challenge.difficulty().name());
            }
            case PUZZLE -> {
                PuzzleChallengeService.PuzzleChallengeView puzzle =
                        (PuzzleChallengeService.PuzzleChallengeView) challenge.challengeData();
                childId = puzzle.challengeId();
                Long wrong = puzzle.candidates().stream()
                        .map(PuzzleChallengeService.LandmarkOption::landmarkId)
                        .filter(landmarkId -> !landmarkId.equals(challenge.landmarkId()))
                        .findFirst()
                        .orElseThrow();
                puzzleChallengeService.complete(userId, childId, wrong);
            }
            default -> throw new AssertionError("Stage 3 must not select exploration");
        }
        mysteryChallengeService.markConsumed(userId, childId);
    }

    private String correctSceneAnswer(Long sceneId, String questionId) {
        Scene scene = sceneRepository.findById(sceneId).orElseThrow();
        if (questionId.endsWith("-fact")) {
            return optionText(
                    scene.getQuizCorrectAnswer(),
                    scene.getQuizOptionA(),
                    scene.getQuizOptionB(),
                    scene.getQuizOptionC(),
                    scene.getQuizOptionD()
            );
        }
        if (questionId.endsWith("-city")) {
            return cityRepository.findById(scene.getCity().getId()).orElseThrow().getName();
        }
        return scene.getName();
    }

    private String correctBossAnswer(Long cityId, String questionId) {
        City city = cityRepository.findById(cityId).orElseThrow();
        if (questionId.equals("boss-" + cityId + "-fact")) {
            return optionText(
                    city.getBossCorrectAnswer(),
                    city.getBossOptionA(),
                    city.getBossOptionB(),
                    city.getBossOptionC(),
                    city.getBossOptionD()
            );
        }
        if (questionId.equals("boss-" + cityId + "-badge")) {
            return city.getBadgeName();
        }
        if (questionId.equals("boss-" + cityId + "-guardian")) {
            return city.getBossName();
        }
        long sceneId = Long.parseLong(questionId.substring(questionId.lastIndexOf('-') + 1));
        return sceneRepository.findById(sceneId).orElseThrow().getName();
    }

    private String optionText(String answer, String a, String b, String c, String d) {
        return switch (answer == null ? "" : answer.trim().toUpperCase()) {
            case "A" -> a;
            case "B" -> b;
            case "C" -> c;
            case "D" -> d;
            default -> throw new AssertionError("Unknown answer option");
        };
    }

    private void assertStageState(String token, int stageIndex, String expected)
            throws Exception {
        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.cities[2].scenes[" + stageIndex + "].stageStatus")
                        .value(expected));
    }

    private void assertBossState(String token, String expected) throws Exception {
        mockMvc.perform(get("/api/journey/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[2].bossStage.stageStatus")
                        .value(expected));
    }

    private void unlockTainan(User user) {
        var taichung = userProgressRepository.findByUserIdAndCityId(user.getId(), 2L)
                .orElseThrow();
        taichung.setBossCompleted(true);
        taichung.setBadgeUnlocked(true);
        userProgressRepository.save(taichung);
        var tainan = userProgressRepository.findByUserIdAndCityId(user.getId(), 3L)
                .orElseThrow();
        tainan.setUnlocked(true);
        userProgressRepository.save(tainan);
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"correct-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Response did not include token");
        }
        return matcher.group(1);
    }
}
