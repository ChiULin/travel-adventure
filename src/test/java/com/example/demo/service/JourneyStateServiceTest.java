package com.example.demo.service;

import com.example.demo.dto.JourneyBossStageResponse;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import com.example.demo.stage.LandmarkStageService;
import com.example.demo.stage.LandmarkStageStatus;
import com.example.demo.stage.StageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JourneyStateServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CITY_ID = 99L;

    @Mock private UserRepository userRepository;
    @Mock private CityRepository cityRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private UserProgressRepository userProgressRepository;
    @Mock private ExplorationMissionRegistry explorationMissionRegistry;
    @Mock private VisualChallengeRegistry visualChallengeRegistry;
    @Mock private LandmarkStageRegistry landmarkStageRegistry;
    @Mock private LandmarkStageService landmarkStageService;
    @Mock private LandmarkChallengePoolRegistry landmarkChallengePoolRegistry;

    private JourneyStateService service;

    @BeforeEach
    void setUp() {
        service = new JourneyStateService(
                userRepository, cityRepository, sceneRepository, checkinRepository,
                userProgressRepository, explorationMissionRegistry, visualChallengeRegistry,
                landmarkStageRegistry, landmarkStageService, landmarkChallengePoolRegistry);
    }

    @Test
    void fullyConfiguredArbitraryCityUsesStageOrderAndCreatesBossStage() {
        City city = city();
        Scene third = scene(903L, city);
        Scene first = scene(901L, city);
        Scene second = scene(902L, city);
        stubJourney(city, List.of(third, first, second));
        when(landmarkStageRegistry.isCityFullyConfigured(CITY_ID)).thenReturn(true);
        stubStage(first, 1);
        stubStage(second, 2);
        stubStage(third, 3);
        when(landmarkStageRegistry.findByCityId(CITY_ID)).thenReturn(List.of(
                definition(first, 1), definition(second, 2), definition(third, 3)));

        Map<String, Object> state = service.state(USER_ID);
        Map<String, Object> cityResponse = firstCity(state);
        List<Map<String, Object>> scenes = scenes(cityResponse);

        assertEquals(List.of(901L, 902L, 903L),
                scenes.stream().map(scene -> (Long) scene.get("id")).toList());
        JourneyBossStageResponse bossStage = assertInstanceOf(
                JourneyBossStageResponse.class, cityResponse.get("bossStage"));
        assertEquals(4, bossStage.stageOrder());
        assertFalse((Boolean) state.get("journeyCompleted"));
        assertEquals(0, state.get("completedCityCount"));
        assertEquals(1, state.get("totalCityCount"));
        assertEquals(0, state.get("completedLandmarkCount"));
        assertEquals(3, state.get("totalLandmarkCount"));
        assertEquals(0, state.get("badgeCount"));
        assertEquals(1, state.get("totalBadgeCount"));
        verify(landmarkStageRegistry).isCityFullyConfigured(CITY_ID);
    }

    @Test
    void unconfiguredCityKeepsRepositoryOrderAndHasNoStageMetadataOrBossStage() {
        City city = city();
        Scene third = scene(903L, city);
        Scene first = scene(901L, city);
        Scene second = scene(902L, city);
        stubJourney(city, List.of(third, first, second));
        when(landmarkStageRegistry.isCityFullyConfigured(CITY_ID)).thenReturn(false);

        Map<String, Object> cityResponse = firstCity(service.state(USER_ID));
        List<Map<String, Object>> scenes = scenes(cityResponse);

        assertEquals(List.of(903L, 901L, 902L),
                scenes.stream().map(scene -> (Long) scene.get("id")).toList());
        assertFalse((Boolean) scenes.getFirst().get("stageConfigured"));
        assertNull(scenes.getFirst().get("stageOrder"));
        assertNull(cityResponse.get("bossStage"));
        assertEquals("🏅", cityResponse.get("badgeIcon"));
        assertEquals("測試城徽章", cityResponse.get("badgeName"));
        verify(landmarkStageRegistry, never()).findByLandmarkId(903L);
    }

    private void stubJourney(City city, List<Scene> scenes) {
        User user = User.builder().id(USER_ID).username("generic-player").build();
        UserProgress progress = UserProgress.builder().user(user).city(city).unlocked(true).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(cityRepository.findAllByOrderByUnlockOrderAsc()).thenReturn(List.of(city));
        when(userProgressRepository.findByUserId(USER_ID)).thenReturn(List.of(progress));
        when(checkinRepository.findByUserIdAndCompletedTrue(USER_ID)).thenReturn(List.of());
        when(sceneRepository.findByCityId(CITY_ID)).thenReturn(scenes);
        scenes.forEach(scene -> {
            when(explorationMissionRegistry.findByTargetSceneId(scene.getId())).thenReturn(Optional.empty());
        });
    }

    private void stubStage(Scene scene, int order) {
        LandmarkStageDefinition definition = definition(scene, order);
        when(landmarkStageRegistry.findByLandmarkId(scene.getId())).thenReturn(Optional.of(definition));
        when(landmarkStageService.getStageStatus(USER_ID, scene.getId())).thenReturn(
                new LandmarkStageStatus(scene.getId(), CITY_ID, order, StageStatus.AVAILABLE));
    }

    private LandmarkStageDefinition definition(Scene scene, int order) {
        return new LandmarkStageDefinition(CITY_ID, scene.getId(), order);
    }

    private City city() {
        return City.builder()
                .id(CITY_ID)
                .name("測試城")
                .unlockOrder(1)
                .bossName("測試守護者")
                .build();
    }

    private Scene scene(Long id, City city) {
        return Scene.builder().id(id).name("景點 " + id).city(city).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstCity(Map<String, Object> state) {
        return ((List<Map<String, Object>>) state.get("cities")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> scenes(Map<String, Object> city) {
        return (List<Map<String, Object>>) city.get("scenes");
    }
}
