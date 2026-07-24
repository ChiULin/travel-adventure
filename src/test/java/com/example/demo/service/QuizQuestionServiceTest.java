package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageService;
import com.example.demo.stage.StageLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizQuestionServiceTest {
    private SceneRepository sceneRepository;
    private CityRepository cityRepository;
    private LandmarkStageService stageService;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        sceneRepository = mock(SceneRepository.class);
        cityRepository = mock(CityRepository.class);
        stageService = mock(LandmarkStageService.class);
        clock = new MutableClock(Instant.parse("2026-07-21T00:00:00Z"));

        City city = mock(City.class);
        when(city.getName()).thenReturn("Test City");
        when(sceneRepository.findById(anyLong())).thenAnswer(invocation -> {
            long sceneId = invocation.getArgument(0);
            return Optional.of(scene(sceneId, city));
        });
        when(sceneRepository.findAll()).thenReturn(List.of());
        when(cityRepository.findAllByOrderByUnlockOrderAsc()).thenReturn(List.of());
    }

    @Test
    void issuedQuestionContainsIssueAndExpiryTimes() {
        QuizQuestionService service = serviceWithLimit(5);

        Map<String, Object> question = service.randomSceneQuestion(1L, 1L, "CASUAL");

        assertEquals(clock.instant(), question.get("issuedAt"));
        assertEquals(clock.instant().plusSeconds(12), question.get("expiresAt"));
    }

    @Test
    void submittedQuestionIsConsumedImmediately() {
        QuizQuestionService service = serviceWithLimit(5);
        Scene scene = scene(1L, city());
        Map<String, Object> question = service.randomSceneQuestion(1L, 1L, "CASUAL");
        String questionId = question.get("questionId").toString();

        assertTrue(service.sceneAnswerCorrect(1L, scene, questionId, "Correct", "CASUAL"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.sceneAnswerCorrect(1L, scene, questionId, "Correct", "CASUAL"));
        assertEquals("question challenge does not match", exception.getMessage());
    }

    @Test
    void expiredQuestionIsRejectedAndConsumed() {
        QuizQuestionService service = serviceWithLimit(5);
        Scene scene = scene(1L, city());
        Map<String, Object> question = service.randomSceneQuestion(1L, 1L, "CASUAL");
        clock.advanceSeconds(12);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.sceneAnswerCorrect(1L, scene, question.get("questionId").toString(), "Correct", "CASUAL"));
        assertEquals("question time expired", exception.getMessage());
    }

    @Test
    void pendingLimitIsReleasedAfterScheduledCleanup() {
        QuizQuestionService service = serviceWithLimit(2);
        service.randomSceneQuestion(1L, 1L, "CASUAL");
        service.randomSceneQuestion(1L, 2L, "CASUAL");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.randomSceneQuestion(1L, 3L, "CASUAL"));
        assertEquals("too many pending questions", exception.getMessage());

        clock.advanceSeconds(12);
        service.removeExpiredQuestions();
        Map<String, Object> next = service.randomSceneQuestion(1L, 3L, "CASUAL");
        assertEquals("scene-3-fact", next.get("questionId"));
    }

    @Test
    void bossQuestionTimeComesOnlyFromDifficulty() {
        QuizQuestionService service = serviceWithLimit(5);
        City bossCity = mock(City.class);
        when(bossCity.getId()).thenReturn(3L);
        when(bossCity.getName()).thenReturn("台南");
        when(bossCity.getBossQuestion()).thenReturn("Boss question");
        when(bossCity.getBossOptionA()).thenReturn("Correct");
        when(bossCity.getBossOptionB()).thenReturn("Wrong B");
        when(bossCity.getBossOptionC()).thenReturn("Wrong C");
        when(bossCity.getBossOptionD()).thenReturn("Wrong D");
        when(bossCity.getBossCorrectAnswer()).thenReturn("A");
        when(cityRepository.findById(3L)).thenReturn(Optional.of(bossCity));
        when(sceneRepository.findByCityId(3L)).thenReturn(List.of());

        Map<String, Object> normal = service.randomBossQuestion(1L, 3L, "NORMAL");

        assertEquals(5, normal.get("seconds"));
        assertEquals(clock.instant().plusSeconds(5), normal.get("expiresAt"));
    }

    @Test
    void lockedStageDoesNotCreateQuizQuestionState() {
        QuizQuestionService service = serviceWithLimit(5);
        doThrow(new StageLockedException("請先完成上一個景點關卡"))
                .when(stageService).validateStageAvailable(1L, 3L);

        assertThrows(StageLockedException.class,
                () -> service.randomSceneQuestion(1L, 3L, "NORMAL"));
        clock.advanceSeconds(1);
        doNothing().when(stageService).validateStageAvailable(1L, 3L);

        Map<String, Object> question = service.randomSceneQuestion(1L, 3L, "NORMAL");

        assertEquals(clock.instant(), question.get("issuedAt"));
    }

    private QuizQuestionService serviceWithLimit(int limit) {
        return new QuizQuestionService(sceneRepository, cityRepository, stageService, limit, clock);
    }

    private Scene scene(long id, City city) {
        Scene scene = mock(Scene.class);
        when(scene.getId()).thenReturn(id);
        when(scene.getName()).thenReturn("Scene " + id);
        when(scene.getCity()).thenReturn(city);
        when(scene.getQuizQuestion()).thenReturn("Question " + id);
        when(scene.getQuizOptionA()).thenReturn("Correct");
        when(scene.getQuizOptionB()).thenReturn("Wrong B");
        when(scene.getQuizOptionC()).thenReturn("Wrong C");
        when(scene.getQuizOptionD()).thenReturn("Wrong D");
        when(scene.getQuizCorrectAnswer()).thenReturn("A");
        when(scene.getQuizExplanation()).thenReturn("Explanation");
        return scene;
    }

    private City city() {
        City city = mock(City.class);
        when(city.getName()).thenReturn("Test City");
        return city;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
