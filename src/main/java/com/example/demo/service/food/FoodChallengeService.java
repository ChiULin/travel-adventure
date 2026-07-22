package com.example.demo.service.food;

import com.example.demo.repository.CheckinRepository;
import com.example.demo.service.food.dto.FoodChallengeResponse;
import com.example.demo.service.food.dto.FoodClaimRequest;
import com.example.demo.service.food.dto.FoodClaimResponse;
import com.example.demo.service.food.dto.FoodEffectResponse;
import com.example.demo.service.food.dto.FoodEventResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FoodChallengeService {

    static final int REQUIRED_CHECKINS = 2;
    static final Duration CHALLENGE_DURATION = Duration.ofMinutes(2);

    private final FoodRegistry foodRegistry;
    private final CheckinRepository checkinRepository;
    private final Clock clock;
    private final Random random;
    private final Map<String, IssuedFoodChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, String> activeChallengeIds = new ConcurrentHashMap<>();
    private final Set<String> claimedFoods = ConcurrentHashMap.newKeySet();
    private final Object issuanceMonitor = new Object();

    @Autowired
    public FoodChallengeService(FoodRegistry foodRegistry,
                                CheckinRepository checkinRepository) {
        this(foodRegistry, checkinRepository, Clock.systemUTC(), new Random());
    }

    FoodChallengeService(FoodRegistry foodRegistry,
                         CheckinRepository checkinRepository,
                         Clock clock,
                         Random random) {
        this.foodRegistry = foodRegistry;
        this.checkinRepository = checkinRepository;
        this.clock = clock;
        this.random = random;
    }

    public FoodEventResponse getFoodEvent(Long userId, Long cityId) {
        FoodDefinition food = signatureFood(cityId);
        long completedCheckins = completedCheckins(userId, cityId);
        if (completedCheckins < REQUIRED_CHECKINS) {
            removeActiveChallenge(userId, cityId);
            return FoodEventResponse.unavailable(REQUIRED_CHECKINS, completedCheckins);
        }

        boolean claimed = claimedFoods.contains(claimedKey(userId, food.foodKey()));
        FoodChallengeResponse challenge = claimed ? null : issueChallenge(userId, food);
        return new FoodEventResponse(
                true,
                claimed,
                REQUIRED_CHECKINS,
                completedCheckins,
                0,
                food.foodKey(),
                food.cityName(),
                food.name(),
                food.shortDescription(),
                effect(food),
                challenge
        );
    }

    public FoodClaimResponse claim(Long userId, Long cityId, FoodClaimRequest request) {
        IssuedFoodChallenge challenge = challenges.get(request.questionId());
        if (challenge == null || challenge.used()) {
            throw new IllegalArgumentException("美食文化題不存在或已使用");
        }

        synchronized (challenge) {
            if (challenge.used() || challenges.get(request.questionId()) != challenge) {
                throw new IllegalArgumentException("美食文化題不存在或已使用");
            }
            if (!challenge.userId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "這不是你的美食文化題");
            }
            if (!challenge.cityId().equals(cityId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "美食文化題不屬於這座城市");
            }
            if (!clock.instant().isBefore(challenge.expiresAt())) {
                consume(challenge);
                throw new ResponseStatusException(HttpStatus.GONE, "美食文化題已過期");
            }
            if (completedCheckins(userId, cityId) < REQUIRED_CHECKINS) {
                consume(challenge);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "尚未達成美食事件解鎖條件");
            }

            FoodDefinition food = signatureFood(cityId);
            if (!food.foodKey().equals(challenge.foodKey())) {
                consume(challenge);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "美食文化題資料不一致");
            }

            consume(challenge);
            if (!challenge.correctAnswer().equals(request.answer())) {
                return FoodClaimResponse.incorrect();
            }

            claimedFoods.add(claimedKey(userId, food.foodKey()));
            FoodEffectResponse effect = effect(food);
            return new FoodClaimResponse(
                    true,
                    food.foodKey(),
                    food.name(),
                    effect.type(),
                    effect.value(),
                    effect.description(),
                    food.explanation()
            );
        }
    }

    @Scheduled(fixedDelayString = "${game.food.cleanup-interval-ms:60000}")
    public void removeExpiredChallenges() {
        Instant now = clock.instant();
        challenges.values().forEach(challenge -> {
            if (!now.isBefore(challenge.expiresAt())) {
                synchronized (challenge) {
                    if (!now.isBefore(challenge.expiresAt())) {
                        consume(challenge);
                    }
                }
            }
        });
    }

    private FoodChallengeResponse issueChallenge(Long userId, FoodDefinition food) {
        Instant now = clock.instant();
        String activeKey = activeKey(userId, food.cityId());
        synchronized (issuanceMonitor) {
            String existingId = activeChallengeIds.get(activeKey);
            IssuedFoodChallenge existing = existingId == null ? null : challenges.get(existingId);
            if (existing != null && !existing.used() && now.isBefore(existing.expiresAt())) {
                return challengeResponse(existing);
            }
            removeChallenge(existing);

            ArrayList<String> shuffledOptions = new ArrayList<>(food.challengeOptions());
            Collections.shuffle(shuffledOptions, random);
            IssuedFoodChallenge challenge = new IssuedFoodChallenge(
                    "food-q-" + UUID.randomUUID(),
                    userId,
                    food.cityId(),
                    food.foodKey(),
                    food.correctAnswer(),
                    food.challengeQuestion(),
                    shuffledOptions,
                    now.plus(CHALLENGE_DURATION)
            );
            challenges.put(challenge.questionId(), challenge);
            activeChallengeIds.put(activeKey, challenge.questionId());
            return challengeResponse(challenge);
        }
    }

    private FoodChallengeResponse challengeResponse(IssuedFoodChallenge challenge) {
        return new FoodChallengeResponse(
                challenge.questionId(),
                challenge.question(),
                challenge.options(),
                challenge.expiresAt()
        );
    }

    private FoodEffectResponse effect(FoodDefinition food) {
        String description = switch (food.effectType()) {
            case EXTRA_TIME -> "城市 Boss 戰每題額外增加 %d 秒".formatted(food.effectValue());
            case EXTRA_HP -> "城市 Boss 戰額外增加 %d 點生命".formatted(food.effectValue());
            case FIRST_TURN_DAMAGE -> "城市 Boss 戰首回合造成 %d 點傷害".formatted(food.effectValue());
            case BLOCK_ONE_DAMAGE -> "城市 Boss 戰抵擋 %d 次傷害".formatted(food.effectValue());
            case PRESERVE_COMBO -> "城市 Boss 戰保留 %d 次 Combo".formatted(food.effectValue());
            case REMOVE_ONE_WRONG_OPTION -> "城市 Boss 戰移除 %d 個錯誤選項".formatted(food.effectValue());
        };
        return new FoodEffectResponse(food.effectType().name(), food.effectValue(), description);
    }

    private FoodDefinition signatureFood(Long cityId) {
        return foodRegistry.findSignatureFoodByCityId(cityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "這座城市目前沒有特色美食事件"));
    }

    private long completedCheckins(Long userId, Long cityId) {
        return checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(userId, cityId);
    }

    private void removeActiveChallenge(Long userId, Long cityId) {
        String questionId = activeChallengeIds.get(activeKey(userId, cityId));
        removeChallenge(questionId == null ? null : challenges.get(questionId));
    }

    private void removeChallenge(IssuedFoodChallenge challenge) {
        if (challenge == null) {
            return;
        }
        synchronized (challenge) {
            consume(challenge);
        }
    }

    private void consume(IssuedFoodChallenge challenge) {
        challenge.markUsed();
        challenges.remove(challenge.questionId(), challenge);
        activeChallengeIds.remove(activeKey(challenge.userId(), challenge.cityId()), challenge.questionId());
    }

    private String activeKey(Long userId, Long cityId) {
        return "%d:%d".formatted(userId, cityId);
    }

    private String claimedKey(Long userId, String foodKey) {
        return "%d:%s".formatted(userId, foodKey);
    }
}
