package com.example.demo.stage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LandmarkStageRegistryTest {

    private static final Long TAIPEI_CITY_ID = 1L;
    private static final Long TAICHUNG_CITY_ID = 2L;

    @Autowired
    private LandmarkStageRegistry registry;

    @Test
    void shouldReturnThreeTaipeiStages() {
        var stages = registry.findByCityId(1L);

        assertEquals(3, stages.size());
    }

    @Test
    void taipeiStagesShouldBeOrdered() {
        var stages = registry.findByCityId(1L);

        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void shouldFindStageByLandmarkId() {
        var stage = registry.findByLandmarkId(1L).orElseThrow();

        assertEquals(1, stage.stageOrder());
    }

    @Test
    void unknownLandmarkShouldReturnEmpty() {
        assertTrue(registry.findByLandmarkId(999L).isEmpty());
    }

    @Test
    void taipeiShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(TAIPEI_CITY_ID));
    }

    @Test
    void unconfiguredCityShouldNotBeFullyConfigured() {
        assertFalse(registry.isCityFullyConfigured(TAICHUNG_CITY_ID));
    }

    @Test
    void unknownCityShouldNotBeFullyConfigured() {
        assertFalse(registry.isCityFullyConfigured(999L));
    }
}
