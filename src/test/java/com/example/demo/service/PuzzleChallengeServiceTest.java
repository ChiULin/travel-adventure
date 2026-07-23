package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.stage.LandmarkStageRegistry;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PuzzleChallengeServiceTest {

    private CheckinRepository checkinRepository;
    private UserProgressRepository progressRepository;
    private CheckinService checkinService;
    private LandmarkStageService stageService;
    private MutableClock clock;
    private PuzzleChallengeService service;

    @BeforeEach
    void setUp() {
        checkinRepository = mock(CheckinRepository.class);
        progressRepository = mock(UserProgressRepository.class);
        checkinService = mock(CheckinService.class);
        stageService = mock(LandmarkStageService.class);
        SceneRepository sceneRepository = mock(SceneRepository.class);
        CityRepository cityRepository = mock(CityRepository.class);
        LandmarkStageRegistry stageRegistry = new LandmarkStageRegistry();
        clock = new MutableClock(Instant.parse("2026-07-23T12:00:00Z"));

        Map<Long, City> cities = Map.of(
                1L, City.builder().id(1L).unlockOrder(1).build(),
                2L, City.builder().id(2L).unlockOrder(2).build(),
                3L, City.builder().id(3L).unlockOrder(3).build(),
                4L, City.builder().id(4L).unlockOrder(4).build(),
                5L, City.builder().id(5L).unlockOrder(5).build(),
                6L, City.builder().id(6L).unlockOrder(6).build()
        );
        Map<Long, String> sceneNames = Map.of(
                1L, "台北101",
                2L, "國立故宮博物院",
                5L, "國家歌劇院",
                17L, "澎湖跨海大橋"
        );
        when(cityRepository.findAllByOrderByUnlockOrderAsc()).thenReturn(
                cities.values().stream()
                        .sorted(Comparator.comparingInt(City::getUnlockOrder))
                        .toList());
        when(cityRepository.findById(1L)).thenReturn(Optional.of(cities.get(1L)));
        when(sceneRepository.findById(1L)).thenReturn(Optional.of(
                Scene.builder().id(1L).name("台北101").city(cities.get(1L)).build()));
        when(sceneRepository.findById(2L)).thenReturn(Optional.of(
                Scene.builder().id(2L).name("國立故宮博物院").city(cities.get(1L)).build()));
        when(sceneRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Scene> scenes = new ArrayList<>();
            ids.forEach(id -> scenes.add(Scene.builder()
                    .id(id)
                    .name(sceneNames.get(id))
                    .build()));
            return scenes;
        });
        when(progressRepository.findByUserIdAndCityId(anyLong(), eq(1L)))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(anyLong(), eq(1L)))
                .thenReturn(Optional.empty());

        service = new PuzzleChallengeService(
                new VisualChallengeRegistry(),
                sceneRepository,
                cityRepository,
                checkinRepository,
                progressRepository,
                checkinService,
                stageRegistry,
                stageService,
                clock
        );
    }

    @Test
    void activeSessionReusesTileOrderAndDifficultyControlsTime() {
        var casual = service.issue(1L, 1L, "CASUAL", "MC-casual");
        var repeated = service.issue(1L, 1L, "EXTREME", "MC-ignored");
        var normal = service.issue(2L, 1L, "NORMAL", "MC-normal");
        var extreme = service.issue(3L, 1L, "EXTREME", "MC-extreme");

        assertEquals(casual.challengeId(), repeated.challengeId());
        assertEquals(casual.initialTileOrder(), repeated.initialTileOrder());
        assertEquals(9, casual.initialTileOrder().stream().distinct().count());
        assertFalse(isSolved(casual.initialTileOrder()));
        assertEquals(60, casual.seconds());
        assertEquals(45, normal.seconds());
        assertEquals(30, extreme.seconds());
        assertEquals(3, normal.gridSize());
        assertEquals(4, normal.candidates().size());
        assertNotEquals(casual.challengeId(), normal.challengeId());
    }

    @Test
    void palaceUsesSharedVisualRegistryAndReusesItsSession() {
        var first = service.issue(1L, 2L, "NORMAL", "MC-palace");
        var repeated = service.issue(1L, 2L, "NORMAL", "MC-palace-repeated");

        assertEquals(first.challengeId(), repeated.challengeId());
        assertEquals(first.initialTileOrder(), repeated.initialTileOrder());
        assertEquals("/images/challenges/palace-puzzle.jpg", first.imageUrl());
        assertEquals(4, first.candidates().size());
        assertTrue(first.candidates().stream()
                .anyMatch(candidate -> candidate.landmarkId().equals(2L)));
    }

    @Test
    void everyIssuedPuzzleStartsUnsolved() {
        for (long userId = 10L; userId < 110L; userId++) {
            var challenge = service.issue(userId, 1L, "NORMAL", "MC-" + userId);
            assertFalse(isSolved(challenge.initialTileOrder()));
        }
    }

    @Test
    void wrongAnswerConsumesChallengeWithoutCheckin() {
        var challenge = service.issue(1L, 1L, "NORMAL", "MC-wrong");
        Long wrongLandmarkId = challenge.candidates().stream()
                .map(PuzzleChallengeService.LandmarkOption::landmarkId)
                .filter(landmarkId -> !landmarkId.equals(1L))
                .findFirst()
                .orElseThrow();

        var result = service.complete(1L, challenge.challengeId(), wrongLandmarkId);

        assertFalse(result.correct());
        assertFalse(result.completed());
        assertThrows(IllegalArgumentException.class,
                () -> service.complete(1L, challenge.challengeId(), 1L));
        verifyNoInteractions(checkinService);
    }

    @Test
    void correctAnswerChecksInOnceAndDuplicateSubmissionIsRejected() {
        var challenge = service.issue(1L, 1L, "NORMAL", "MC-correct");
        when(checkinService.completeExploration(1L, 1L, "NORMAL"))
                .thenReturn(new CheckinService.ExplorationCheckinResult(
                        1L, "台北101", 120, 96, false, false));

        var result = service.complete(1L, challenge.challengeId(), 1L);

        assertTrue(result.correct());
        assertTrue(result.completed());
        assertEquals(120, result.experienceGained());
        assertThrows(IllegalArgumentException.class,
                () -> service.complete(1L, challenge.challengeId(), 1L));
        verify(checkinService).completeExploration(1L, 1L, "NORMAL");
    }

    @Test
    void expiredAndOtherPlayersCannotSubmit() {
        var challenge = service.issue(1L, 1L, "EXTREME", "MC-owner");

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> service.complete(2L, challenge.challengeId(), 1L));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        clock.advanceSeconds(31);
        ResponseStatusException expired = assertThrows(ResponseStatusException.class,
                () -> service.complete(1L, challenge.challengeId(), 1L));
        assertEquals(HttpStatus.GONE, expired.getStatusCode());
        verifyNoInteractions(checkinService);
    }

    @Test
    void lockedStageDoesNotCreatePuzzleState() {
        doThrow(new StageLockedException("請先完成上一個景點關卡"))
                .when(stageService).validateStageAvailable(1L, 1L);

        assertThrows(StageLockedException.class,
                () -> service.issue(1L, 1L, "NORMAL", "MC-locked"));
        clock.advanceSeconds(1);
        doNothing().when(stageService).validateStageAvailable(1L, 1L);

        var challenge = service.issue(1L, 1L, "NORMAL", "MC-unlocked");

        assertEquals(clock.instant(), challenge.issuedAt());
        verifyNoInteractions(checkinService);
    }

    private boolean isSolved(List<Integer> order) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index) != index) {
                return false;
            }
        }
        return true;
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
