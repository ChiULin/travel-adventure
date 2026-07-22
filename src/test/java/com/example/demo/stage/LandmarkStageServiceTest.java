package com.example.demo.stage;

import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandmarkStageServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TAIPEI_CITY_ID = 1L;
    private static final Long TAIPEI_101_ID = 1L;
    private static final Long PALACE_MUSEUM_ID = 2L;
    private static final Long XIMENDING_ID = 3L;

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    private LandmarkStageService service;

    @BeforeEach
    void setUp() {
        service = new LandmarkStageService(
                new LandmarkStageRegistry(),
                checkinRepository,
                userProgressRepository
        );
    }

    @Test
    void unlockedTaipeiMakesTaipei101Available() {
        taipeiUnlocked(true);

        LandmarkStageStatus stage = service.getStageStatus(USER_ID, TAIPEI_101_ID);

        assertEquals(StageStatus.AVAILABLE, stage.status());
        assertEquals(1, stage.stageOrder());
    }

    @Test
    void palaceMuseumIsLockedBeforeTaipei101IsCompleted() {
        LandmarkStageStatus stage = service.getStageStatus(USER_ID, PALACE_MUSEUM_ID);

        assertEquals(StageStatus.LOCKED, stage.status());
    }

    @Test
    void palaceMuseumIsAvailableAfterTaipei101IsCompleted() {
        completed(TAIPEI_101_ID);

        LandmarkStageStatus stage = service.getStageStatus(USER_ID, PALACE_MUSEUM_ID);

        assertEquals(StageStatus.AVAILABLE, stage.status());
    }

    @Test
    void ximendingIsLockedBeforePalaceMuseumIsCompleted() {
        LandmarkStageStatus stage = service.getStageStatus(USER_ID, XIMENDING_ID);

        assertEquals(StageStatus.LOCKED, stage.status());
    }

    @Test
    void ximendingIsAvailableAfterPalaceMuseumIsCompleted() {
        completed(PALACE_MUSEUM_ID);

        LandmarkStageStatus stage = service.getStageStatus(USER_ID, XIMENDING_ID);

        assertEquals(StageStatus.AVAILABLE, stage.status());
    }

    @Test
    void completedLandmarkIsCompleted() {
        completed(TAIPEI_101_ID);

        LandmarkStageStatus stage = service.getStageStatus(USER_ID, TAIPEI_101_ID);

        assertEquals(StageStatus.COMPLETED, stage.status());
    }

    @Test
    void firstStageOfLockedCityIsLocked() {
        taipeiUnlocked(false);

        LandmarkStageStatus stage = service.getStageStatus(USER_ID, TAIPEI_101_ID);

        assertEquals(StageStatus.LOCKED, stage.status());
    }

    @Test
    void validatingLockedStageFails() {
        StageLockedException exception = assertThrows(
                StageLockedException.class,
                () -> service.validateStageAvailable(USER_ID, PALACE_MUSEUM_ID)
        );

        assertEquals("請先完成上一個景點關卡", exception.getMessage());
    }

    @Test
    void unconfiguredLandmarkKeepsExistingBehavior() {
        service.validateStageAvailable(USER_ID, 13L);
    }

    private void taipeiUnlocked(boolean unlocked) {
        when(userProgressRepository.findByUserIdAndCityId(USER_ID, TAIPEI_CITY_ID))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(unlocked).build()));
    }

    private void completed(Long landmarkId) {
        when(checkinRepository.existsByUserIdAndSceneIdAndCompletedTrue(eq(USER_ID), anyLong()))
                .thenAnswer(invocation -> landmarkId.equals(invocation.getArgument(1)));
    }
}
