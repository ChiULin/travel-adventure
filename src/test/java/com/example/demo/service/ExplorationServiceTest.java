package com.example.demo.service;

import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExplorationServiceTest {

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

        var guess = service.submitGuess(1L, "TAINAN-ANPING-01", 8L);
        clock.advance(Duration.ofMinutes(3));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.complete(1L, "TAINAN-ANPING-01", guess.challenge().questionId(), "荷蘭", "NORMAL"));
        assertEquals("文化挑戰已過期，請重新推理", exception.getMessage());
        verifyNoInteractions(checkinService);
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
