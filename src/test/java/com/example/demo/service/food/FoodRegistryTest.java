package com.example.demo.service.food;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FoodRegistryTest {

    @Autowired
    private FoodRegistry foodRegistry;

    @Test
    void shouldFindTainanSignatureFood() {
        FoodDefinition food = foodRegistry
                .findSignatureFoodByCityId(3L)
                .orElseThrow();

        assertEquals("TAINAN_BEEF_SOUP", food.foodKey());
        assertEquals("牛肉湯", food.name());
    }

    @Test
    void shouldUseExtraTimeEffect() {
        FoodDefinition food = foodRegistry
                .findByFoodKey("TAINAN_BEEF_SOUP")
                .orElseThrow();

        assertEquals(FoodEffectType.EXTRA_TIME, food.effectType());
        assertEquals(2, food.effectValue());
    }

    @Test
    void shouldHaveFourChallengeOptions() {
        FoodDefinition food = foodRegistry
                .findByFoodKey("TAINAN_BEEF_SOUP")
                .orElseThrow();

        assertEquals(4, food.challengeOptions().size());
    }

    @Test
    void correctAnswerShouldExistInOptions() {
        FoodDefinition food = foodRegistry
                .findByFoodKey("TAINAN_BEEF_SOUP")
                .orElseThrow();

        assertTrue(food.challengeOptions().contains(food.correctAnswer()));
    }

    @Test
    void unknownCityShouldReturnEmptyList() {
        assertTrue(foodRegistry.findByCityId(999L).isEmpty());
    }
}
