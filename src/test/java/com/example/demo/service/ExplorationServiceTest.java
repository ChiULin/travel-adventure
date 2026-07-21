package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.SceneRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExplorationServiceTest {

    @Test
    void scheduledCleanupRemovesExpiredMissionState() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        CheckinService checkinService = mock(CheckinService.class);
        SceneRepository sceneRepository = sceneRepository();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService,
                new ExplorationMissionRegistry(), sceneRepository, clock);

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
        SceneRepository sceneRepository = sceneRepository();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService,
                new ExplorationMissionRegistry(), sceneRepository, clock);

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
        SceneRepository sceneRepository = sceneRepository();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, checkinService,
                new ExplorationMissionRegistry(), sceneRepository, clock);

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

    @Test
    void registryProvidesSixCompleteCitySpecificMissions() {
        ExplorationMissionRegistry registry = new ExplorationMissionRegistry();

        assertEquals("TAIPEI-101-01", registry.findByCityId(1L).getFirst().missionKey());
        assertEquals("TAICHUNG-GAOMEI-01", registry.findByCityId(2L).getFirst().missionKey());
        assertEquals("TAINAN-ANPING-01", registry.findByCityId(3L).getFirst().missionKey());
        assertEquals("KAOHSIUNG-PIER2-01", registry.findByCityId(4L).getFirst().missionKey());
        assertEquals("HUALIEN-TAROKO-01", registry.findByCityId(5L).getFirst().missionKey());
        assertEquals("PENGHU-DOUBLE-HEART-01", registry.findByCityId(6L).getFirst().missionKey());
        assertEquals(6, registry.findAll().size());

        List.of(1L, 2L, 3L, 4L, 5L, 6L).forEach(cityId -> {
            ExplorationMissionDefinition mission = registry.findByCityId(cityId).getFirst();
            assertEquals(3, mission.investigations().size());
            assertEquals(3, mission.investigations().stream()
                    .map(InvestigationDefinition::type).distinct().count());
            assertEquals(3, mission.candidateSceneIds().size());
            assertTrue(mission.candidateSceneIds().contains(mission.targetSceneId()));
            assertTrue(mission.cultureOptions().contains(mission.cultureAnswer()));
        });
    }

    @Test
    void duplicateMissionKeyFailsDuringRegistryCreation() {
        ExplorationMissionDefinition mission = new ExplorationMissionRegistry().findByCityId(1L).getFirst();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new ExplorationMissionRegistry(List.of(mission, mission)));

        assertEquals("探索任務代碼重複：TAIPEI-101-01", exception.getMessage());
    }

    @Test
    void unsupportedCityDoesNotReturnAnotherCityMission() {
        ExplorationService service = new ExplorationService(
                mock(CheckinRepository.class), mock(UserProgressRepository.class), mock(CheckinService.class),
                new ExplorationMissionRegistry(), sceneRepository(), Clock.systemUTC());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.randomMission(1L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("這座城市目前沒有探索任務", exception.getReason());
    }

    @Test
    void lockedCityMissionIsRejected() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, mock(CheckinService.class),
                new ExplorationMissionRegistry(), sceneRepository(), Clock.systemUTC());
        when(progressRepository.findByUserIdAndCityId(1L, 1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.randomMission(1L, 1L));

        assertEquals("城市尚未解鎖", exception.getMessage());
    }

    @Test
    void completedTargetSceneIsExcludedFromRandomMission() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, mock(CheckinService.class),
                new ExplorationMissionRegistry(), sceneRepository(), Clock.systemUTC());
        when(progressRepository.findByUserIdAndCityId(1L, 1L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 1L))
                .thenReturn(Optional.of(Checkin.builder().completed(true).build()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.randomMission(1L, 1L));

        assertEquals("目前沒有未完成的探索委託", exception.getMessage());
    }

    @Test
    void playerCanKeepIndependentMissionStateForDifferentCities() {
        CheckinRepository checkinRepository = mock(CheckinRepository.class);
        UserProgressRepository progressRepository = mock(UserProgressRepository.class);
        ExplorationService service = new ExplorationService(
                checkinRepository, progressRepository, mock(CheckinService.class),
                new ExplorationMissionRegistry(), sceneRepository(), Clock.systemUTC());
        when(progressRepository.findByUserIdAndCityId(1L, 1L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(progressRepository.findByUserIdAndCityId(1L, 5L))
                .thenReturn(Optional.of(UserProgress.builder().unlocked(true).build()));
        when(checkinRepository.findByUserIdAndSceneId(1L, 1L)).thenReturn(Optional.empty());
        when(checkinRepository.findByUserIdAndSceneId(1L, 13L)).thenReturn(Optional.empty());

        var taipei = service.randomMission(1L, 1L);
        service.investigate(1L, taipei.missionId(), "LOCAL");
        var hualien = service.randomMission(1L, 5L);
        service.investigate(1L, hualien.missionId(), "VISUAL");

        assertEquals(ClueType.LOCAL, service.randomMission(1L, 1L).discoveredClues().getFirst().type());
        assertEquals(ClueType.VISUAL, service.randomMission(1L, 5L).discoveredClues().getFirst().type());
        assertEquals(3, service.randomMission(1L, 1L).remainingActions());
        assertEquals(3, service.randomMission(1L, 5L).remainingActions());
    }

    private SceneRepository sceneRepository() {
        SceneRepository sceneRepository = mock(SceneRepository.class);
        when(sceneRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            java.util.Map<Long, String> names = java.util.Map.ofEntries(
                    java.util.Map.entry(1L, "台北101"), java.util.Map.entry(2L, "國立故宮博物院"),
                    java.util.Map.entry(3L, "西門町"), java.util.Map.entry(4L, "高美濕地"),
                    java.util.Map.entry(5L, "國家歌劇院"), java.util.Map.entry(6L, "彩虹眷村"),
                    java.util.Map.entry(7L, "赤崁樓"), java.util.Map.entry(8L, "安平古堡"),
                    java.util.Map.entry(9L, "台南孔廟"), java.util.Map.entry(10L, "駁二藝術特區"),
                    java.util.Map.entry(11L, "愛河"), java.util.Map.entry(12L, "龍虎塔"),
                    java.util.Map.entry(13L, "太魯閣"), java.util.Map.entry(14L, "七星潭"),
                    java.util.Map.entry(15L, "清水斷崖"), java.util.Map.entry(16L, "雙心石滬"),
                    java.util.Map.entry(17L, "澎湖跨海大橋"), java.util.Map.entry(18L, "澎湖花火節"));
            java.util.ArrayList<com.example.demo.entity.Scene> scenes = new java.util.ArrayList<>();
            ids.forEach(id -> scenes.add(com.example.demo.entity.Scene.builder().id(id).name(names.get(id)).build()));
            return scenes;
        });
        return sceneRepository;
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
