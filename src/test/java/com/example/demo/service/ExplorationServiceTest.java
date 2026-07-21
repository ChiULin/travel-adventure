package com.example.demo.service;

import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExplorationServiceTest {

    @Test
    void scheduledCleanupRemovesExpiredMissionState() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        CheckinService checkinService = mock(CheckinService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService, clock);

        when(progressRepository.findByUserIdAndCityId(1L, 3L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 8L)).thenReturn(Optional.empty());

        var first = service.randomMission(1L, 3L);
        service.investigate(1L, first.missionId(), "LOCAL");
        clock.advance(Duration.ofMinutes(31));
        service.removeExpiredStates();
        var restarted = service.randomMission(1L, 3L);

        assertNotEquals(first.createdAt(), restarted.createdAt());
        assertEquals(4, restarted.remainingActions());
        assertTrue(restarted.discoveredClues().isEmpty());
    }

    @Test
    void expiredCultureChallengeIsRejectedWithoutCreatingCheckin() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        CheckinService checkinService = mock(CheckinService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService, clock);

        when(progressRepository.findByUserIdAndCityId(1L, 3L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 8L)).thenReturn(Optional.empty());

        service.randomMission(1L, 3L);
        var guess = service.submitGuess(1L, "TAINAN-ANPING-01", 8L);
        clock.advance(Duration.ofMinutes(3));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.complete(1L, "TAINAN-ANPING-01", guess.challenge().questionId(), "荷蘭", "NORMAL"));
        assertEquals(HttpStatus.GONE, exception.getStatusCode());
        assertEquals("文化挑戰已過期，請重新取得題目", exception.getReason());
        verifyNoInteractions(checkinService);
    }

    @Test
    void repeatedInvestigationIsFreeAndExhaustedActionsRevealAllCluesWithGradeC() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        CheckinService checkinService = mock(CheckinService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService, clock);

        when(progressRepository.findByUserIdAndCityId(1L, 3L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 8L)).thenReturn(Optional.empty());
        when(checkinService.completeExploration(1L, 8L, "NORMAL"))
                .thenReturn(new CheckinService.ExplorationCheckinResult(8L, "安平古堡", 174, 150, true, false));

        var mission = service.randomMission(1L, 3L);
        assertEquals(4, mission.remainingActions());
        assertTrue(mission.discoveredClues().isEmpty());

        var local = service.investigate(1L, mission.missionId(), "LOCAL");
        assertFalse(local.alreadyDiscovered());
        assertEquals(3, local.remainingActions());

        var repeated = service.investigate(1L, mission.missionId(), "LOCAL");
        assertTrue(repeated.alreadyDiscovered());
        assertEquals(3, repeated.remainingActions());

        var wrongGuess = service.submitGuess(1L, mission.missionId(), 7L);
        assertEquals(2, wrongGuess.remainingActions());
        assertEquals(1, wrongGuess.wrongGuesses());

        service.investigate(1L, mission.missionId(), "HISTORY");
        var exhausted = service.investigate(1L, mission.missionId(), "VISUAL");
        assertEquals(0, exhausted.remainingActions());
        assertEquals(3, exhausted.discoveredClues().size());

        var correctGuess = service.submitGuess(1L, mission.missionId(), 8L);
        var completed = service.complete(
                1L, mission.missionId(), correctGuess.challenge().questionId(), "荷蘭", "NORMAL");
        assertEquals("C", completed.explorationGrade());
        assertEquals(3, completed.cluesUsed());
        assertEquals(1, completed.wrongGuesses());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
