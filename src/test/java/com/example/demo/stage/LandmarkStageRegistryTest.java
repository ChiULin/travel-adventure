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
    private static final Long TAINAN_CITY_ID = 3L;
    private static final Long KAOHSIUNG_CITY_ID = 4L;
    private static final Long HUALIEN_CITY_ID = 5L;
    private static final Long PENGHU_CITY_ID = 6L;

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
    void taichungShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(TAICHUNG_CITY_ID));
    }

    @Test
    void taichungStagesShouldUseConfiguredLandmarkOrder() {
        var stages = registry.findByCityId(TAICHUNG_CITY_ID);

        assertEquals(3, stages.size());
        assertEquals(4L, stages.get(0).landmarkId());
        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(5L, stages.get(1).landmarkId());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(6L, stages.get(2).landmarkId());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void tainanShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(TAINAN_CITY_ID));
    }

    @Test
    void tainanStagesShouldUseConfiguredLandmarkOrder() {
        var stages = registry.findByCityId(TAINAN_CITY_ID);

        assertEquals(3, stages.size());
        assertEquals(7L, stages.get(0).landmarkId());
        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(8L, stages.get(1).landmarkId());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(9L, stages.get(2).landmarkId());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void kaohsiungShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(KAOHSIUNG_CITY_ID));
    }

    @Test
    void kaohsiungStagesShouldUseConfiguredLandmarkOrder() {
        var stages = registry.findByCityId(KAOHSIUNG_CITY_ID);

        assertEquals(3, stages.size());
        assertEquals(10L, stages.get(0).landmarkId());
        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(11L, stages.get(1).landmarkId());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(12L, stages.get(2).landmarkId());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void hualienShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(HUALIEN_CITY_ID));
    }

    @Test
    void hualienStagesShouldUseConfiguredLandmarkOrder() {
        var stages = registry.findByCityId(HUALIEN_CITY_ID);

        assertEquals(3, stages.size());
        assertEquals(13L, stages.get(0).landmarkId());
        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(14L, stages.get(1).landmarkId());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(15L, stages.get(2).landmarkId());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void penghuShouldBeFullyConfigured() {
        assertTrue(registry.isCityFullyConfigured(PENGHU_CITY_ID));
    }

    @Test
    void penghuStagesShouldUseConfiguredLandmarkOrder() {
        var stages = registry.findByCityId(PENGHU_CITY_ID);

        assertEquals(3, stages.size());
        assertEquals(16L, stages.get(0).landmarkId());
        assertEquals(1, stages.get(0).stageOrder());
        assertEquals(17L, stages.get(1).landmarkId());
        assertEquals(2, stages.get(1).stageOrder());
        assertEquals(18L, stages.get(2).landmarkId());
        assertEquals(3, stages.get(2).stageOrder());
    }

    @Test
    void unknownCityShouldNotBeFullyConfigured() {
        assertFalse(registry.isCityFullyConfigured(999L));
    }
}
