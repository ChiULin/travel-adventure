package com.example.demo.service;

import com.example.demo.entity.Scene;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.stage.LandmarkStageService;
import com.example.demo.stage.StageLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageRecognitionServiceTest {
    private CheckinRepository checkinRepository;
    private UserProgressRepository progressRepository;
    private CheckinService checkinService;
    private LandmarkStageService stageService;
    private MutableClock clock;
    private ImageRecognitionService service;

    @BeforeEach
    void setUp() {
        checkinRepository = mock(CheckinRepository.class);
        progressRepository = mock(UserProgressRepository.class);
        checkinService = mock(CheckinService.class);
        SceneRepository sceneRepository = mock(SceneRepository.class);
        clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        stageService = mock(LandmarkStageService.class);
        when(progressRepository.findByUserIdAndCityId(1L, 1L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 2L)).thenReturn(Optional.empty());
        when(sceneRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            Map<Long, String> names = Map.of(
                    1L, "台北101", 2L, "國立故宮博物院", 3L, "西門町", 5L, "國家歌劇院");
            ArrayList<Scene> scenes = new ArrayList<>();
            ids.forEach(id -> scenes.add(Scene.builder().id(id).name(names.get(id)).build()));
            return scenes;
        });
        service = new ImageRecognitionService(
                new ImageRecognitionRegistry(), sceneRepository, checkinRepository,
                progressRepository, checkinService, clock, stageService);
    }

    @Test
    void activeChallengeIsReusedAndDifficultyControlsPresentation() {
        var first = service.issue(1L, 2L, "NORMAL");
        var repeated = service.issue(1L, 2L, "NORMAL");

        assertEquals(first.questionId(), repeated.questionId());
        assertEquals("BLUR", first.displayMode());
        assertEquals(6, first.blurLevel());
        assertEquals(4, first.candidates().size());
        assertEquals(clock.instant().plusSeconds(5), first.expiresAt());

        var casual = service.issue(1L, 2L, "CASUAL");
        assertNotEquals(first.questionId(), casual.questionId());
        assertEquals("FULL", casual.displayMode());
        assertEquals(1, casual.blurLevel());
        assertEquals(3, casual.candidates().size());
    }

    @Test
    void expiredChallengeIsRejectedWithoutCheckin() {
        var challenge = service.issue(1L, 2L, "CASUAL");
        clock.advanceSeconds(10);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.complete(1L, challenge.questionId(), 2L, "CASUAL"));

        assertEquals(HttpStatus.GONE, exception.getStatusCode());
        verifyNoInteractions(checkinService);
    }

    @Test
    void wrongAnswerConsumesChallengeWithoutCheckin() {
        var challenge = service.issue(1L, 2L, "NORMAL");

        var result = service.complete(1L, challenge.questionId(), 1L, "NORMAL");

        assertFalse(result.correct());
        assertFalse(result.completed());
        assertThrows(IllegalArgumentException.class,
                () -> service.complete(1L, challenge.questionId(), 2L, "NORMAL"));
        verifyNoInteractions(checkinService);
    }

    @Test
    void forgedDifficultyDoesNotCompleteOrConsumeValidChallenge() {
        var challenge = service.issue(1L, 2L, "NORMAL");
        when(checkinService.completeExploration(1L, 2L, "NORMAL"))
                .thenReturn(new CheckinService.ExplorationCheckinResult(
                        2L, "國立故宮博物院", 156, 132, false, false));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.complete(1L, challenge.questionId(), 2L, "CASUAL"));
        var completed = service.complete(1L, challenge.questionId(), 2L, "NORMAL");

        assertEquals("圖片辨識難度與簽發時不符", exception.getMessage());
        assertEquals(156, completed.experienceGained());
        assertEquals(132, completed.coinsGained());
    }

    @Test
    void lockedStageDoesNotCreateImageChallengeState() {
        doThrow(new StageLockedException("請先完成上一個景點關卡"))
                .when(stageService).validateStageAvailable(1L, 2L);

        assertThrows(StageLockedException.class, () -> service.issue(1L, 2L, "NORMAL"));
        clock.advanceSeconds(1);
        doNothing().when(stageService).validateStageAvailable(1L, 2L);

        var challenge = service.issue(1L, 2L, "NORMAL");

        assertEquals(clock.instant(), challenge.issuedAt());
        verifyNoInteractions(checkinService);
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
            return ZoneOffset.UTC;
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
