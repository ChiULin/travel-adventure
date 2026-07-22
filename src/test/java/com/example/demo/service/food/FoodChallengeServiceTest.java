package com.example.demo.service.food;

import com.example.demo.repository.CheckinRepository;
import com.example.demo.service.food.dto.FoodClaimRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoodChallengeServiceTest {

    private CheckinRepository checkinRepository;
    private MutableClock clock;
    private FoodChallengeService service;

    @BeforeEach
    void setUp() {
        checkinRepository = mock(CheckinRepository.class);
        clock = new MutableClock(Instant.parse("2026-07-22T11:00:00Z"));
        service = new FoodChallengeService(
                new FoodRegistry(), checkinRepository, clock, new Random(0));
    }

    @Test
    void eventRequiresTwoCompletedCheckins() {
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(10L, 3L))
                .thenReturn(1L);

        var event = service.getFoodEvent(10L, 3L);

        assertFalse(event.available());
        assertEquals(2, event.requiredCheckins());
        assertEquals(1, event.completedCheckins());
        assertEquals(1, event.remainingCheckins());
        assertNull(event.challenge());
    }

    @Test
    void availableEventReusesOneShuffledTwoMinuteChallenge() {
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(10L, 3L))
                .thenReturn(2L);

        var first = service.getFoodEvent(10L, 3L);
        var second = service.getFoodEvent(10L, 3L);

        assertTrue(first.available());
        assertEquals(first.challenge().questionId(), second.challenge().questionId());
        assertEquals(4, first.challenge().options().size());
        assertNotEquals(new FoodRegistry().findByFoodKey("TAINAN_BEEF_SOUP")
                .orElseThrow().challengeOptions(), first.challenge().options());
        assertEquals(clock.instant().plus(Duration.ofMinutes(2)), first.challenge().expiresAt());
    }

    @Test
    void expiredChallengeCannotBeClaimed() {
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(10L, 3L))
                .thenReturn(2L);
        String questionId = service.getFoodEvent(10L, 3L).challenge().questionId();
        clock.advance(Duration.ofMinutes(2));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.claim(10L, 3L,
                        new FoodClaimRequest(questionId, "任意答案")));

        assertEquals(410, exception.getStatusCode().value());
        assertThrows(IllegalArgumentException.class,
                () -> service.claim(10L, 3L,
                        new FoodClaimRequest(questionId, "任意答案")));
    }

    @Test
    void wrongAnswerConsumesChallengeWithoutUnlocking() {
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(10L, 3L))
                .thenReturn(2L);
        String questionId = service.getFoodEvent(10L, 3L).challenge().questionId();

        var result = service.claim(10L, 3L,
                new FoodClaimRequest(questionId, "將牛肉裹粉後油炸"));

        assertFalse(result.correct());
        assertThrows(IllegalArgumentException.class,
                () -> service.claim(10L, 3L,
                        new FoodClaimRequest(questionId, "將生牛肉薄片放入碗中，再沖入熱高湯")));
        assertFalse(service.getFoodEvent(10L, 3L).claimed());
    }

    @Test
    void challengeIsPlayerBoundAndCorrectAnswerUnlocksForSession() {
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(10L, 3L))
                .thenReturn(2L);
        String questionId = service.getFoodEvent(10L, 3L).challenge().questionId();

        ResponseStatusException crossPlayer = assertThrows(ResponseStatusException.class,
                () -> service.claim(11L, 3L,
                        new FoodClaimRequest(questionId, "將生牛肉薄片放入碗中，再沖入熱高湯")));
        assertEquals(403, crossPlayer.getStatusCode().value());

        var result = service.claim(10L, 3L,
                new FoodClaimRequest(questionId, "將生牛肉薄片放入碗中，再沖入熱高湯"));

        assertTrue(result.correct());
        assertEquals("TAINAN_BEEF_SOUP", result.foodKey());
        assertEquals("EXTRA_TIME", result.effectType());
        assertEquals(2, result.effectValue());
        var claimedEvent = service.getFoodEvent(10L, 3L);
        assertTrue(claimedEvent.claimed());
        assertNull(claimedEvent.challenge());
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
