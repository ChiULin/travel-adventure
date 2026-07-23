package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisualChallengeRegistryTest {

    @Test
    void taipeiTaichungAndTainanLandmarksShareStableVisualDefinitions() {
        VisualChallengeRegistry registry = new VisualChallengeRegistry();
        VisualChallengeDefinition taipei = registry.findRequired(new VisualChallengeKey(1, 1));
        VisualChallengeDefinition palace = registry.findRequired(new VisualChallengeKey(1, 2));
        VisualChallengeDefinition ximending =
                registry.findRequired(new VisualChallengeKey(1, 3));
        VisualChallengeDefinition gaomei =
                registry.findRequired(new VisualChallengeKey(2, 1));
        VisualChallengeDefinition opera =
                registry.findRequired(new VisualChallengeKey(2, 2));
        VisualChallengeDefinition rainbow =
                registry.findRequired(new VisualChallengeKey(2, 3));
        VisualChallengeDefinition chihkan =
                registry.findRequired(new VisualChallengeKey(3, 1));
        VisualChallengeDefinition anping =
                registry.findRequired(new VisualChallengeKey(3, 2));
        VisualChallengeDefinition confucius =
                registry.findRequired(new VisualChallengeKey(3, 3));

        assertEquals(9, registry.findAll().size());
        assertEquals("/images/challenges/taipei101-focus.jpg", taipei.focusImageUrl());
        assertEquals(taipei.focusImageUrl(), taipei.puzzleImageUrl());
        assertEquals("/images/challenges/palace-focus.jpg", palace.focusImageUrl());
        assertEquals("/images/challenges/palace-puzzle.jpg", palace.puzzleImageUrl());
        assertEquals("/images/challenges/ximending-focus.jpg", ximending.focusImageUrl());
        assertEquals("/images/challenges/ximending-puzzle.jpg", ximending.puzzleImageUrl());
        assertEquals(List.of(
                new VisualChallengeKey(1, 3),
                new VisualChallengeKey(2, 3),
                new VisualChallengeKey(4, 1),
                new VisualChallengeKey(6, 3)
        ), ximending.candidateStages());
        assertEquals("/images/challenges/gaomei-focus.jpg", gaomei.focusImageUrl());
        assertEquals("", gaomei.puzzleImageUrl());
        assertEquals("/images/challenges/opera-focus.jpg", opera.focusImageUrl());
        assertEquals("/images/challenges/opera-puzzle.jpg", opera.puzzleImageUrl());
        assertEquals("/images/challenges/rainbow-focus.jpg", rainbow.focusImageUrl());
        assertEquals("/images/challenges/rainbow-puzzle.jpg", rainbow.puzzleImageUrl());
        assertEquals("/images/challenges/chihkan-focus.jpg", chihkan.focusImageUrl());
        assertEquals("/images/challenges/chihkan-puzzle.jpg", chihkan.puzzleImageUrl());
        assertEquals("/images/challenges/anping-focus.jpg", anping.focusImageUrl());
        assertEquals("/images/challenges/anping-puzzle.jpg", anping.puzzleImageUrl());
        assertEquals("/images/challenges/confucius-focus.jpg", confucius.focusImageUrl());
        assertEquals("/images/challenges/confucius-puzzle.jpg", confucius.puzzleImageUrl());
        registry.findAll().forEach((key, definition) -> {
            assertEquals(4, definition.candidateStages().size());
            assertEquals(4, definition.candidateStages().stream().distinct().count());
            assertTrue(definition.candidateStages().contains(definition.correctStage()));
            assertEquals(key, definition.correctStage());
        });
    }

    @Test
    void startupValidationRejectsMissingPoolAsset() {
        VisualChallengeRegistry defaults = new VisualChallengeRegistry();
        Map<VisualChallengeKey, VisualChallengeDefinition> definitions =
                new HashMap<>(defaults.findAll());
        VisualChallengeKey key = new VisualChallengeKey(1, 1);
        VisualChallengeDefinition original = definitions.get(key);
        definitions.put(key, new VisualChallengeDefinition(
                original.challengeKey(),
                "",
                original.puzzleImageUrl(),
                original.focusPrompt(),
                original.cultureExplanation(),
                original.candidates(),
                original.correctStage()
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator(new VisualChallengeRegistry(definitions)).run(null));

        assertTrue(exception.getMessage().contains("缺少重點辨識圖片"));
    }

    @Test
    void startupValidationRejectsDuplicateCandidates() {
        VisualChallengeRegistry defaults = new VisualChallengeRegistry();
        Map<VisualChallengeKey, VisualChallengeDefinition> definitions =
                new HashMap<>(defaults.findAll());
        VisualChallengeKey key = new VisualChallengeKey(1, 1);
        VisualChallengeDefinition original = definitions.get(key);
        List<VisualCandidate> candidates = new ArrayList<>(original.candidates());
        candidates.set(3, candidates.get(0));
        definitions.put(key, new VisualChallengeDefinition(
                original.challengeKey(),
                original.focusImageUrl(),
                original.puzzleImageUrl(),
                original.focusPrompt(),
                original.cultureExplanation(),
                candidates,
                original.correctStage()
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator(new VisualChallengeRegistry(definitions)).run(null));

        assertTrue(exception.getMessage().contains("四個不重複"));
    }

    private GameRegistryValidator validator(VisualChallengeRegistry visualRegistry) {
        SceneRepository sceneRepository = mock(SceneRepository.class);
        when(sceneRepository.existsByIdAndCityId(anyLong(), anyLong())).thenReturn(true);
        when(sceneRepository.existsById(anyLong())).thenReturn(true);

        CityRepository cityRepository = mock(CityRepository.class);
        Map<Long, City> cities = Map.of(
                1L, City.builder().id(1L).unlockOrder(1).build(),
                2L, City.builder().id(2L).unlockOrder(2).build(),
                3L, City.builder().id(3L).unlockOrder(3).build(),
                4L, City.builder().id(4L).unlockOrder(4).build(),
                5L, City.builder().id(5L).unlockOrder(5).build(),
                6L, City.builder().id(6L).unlockOrder(6).build()
        );
        when(cityRepository.findAllByOrderByUnlockOrderAsc()).thenReturn(
                cities.values().stream()
                        .sorted(Comparator.comparingInt(City::getUnlockOrder))
                        .toList());

        ExplorationMissionRegistry explorationRegistry = mock(ExplorationMissionRegistry.class);
        when(explorationRegistry.findAll()).thenReturn(List.of());
        Resource resource = mock(Resource.class);
        when(resource.exists()).thenReturn(true);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);

        return new GameRegistryValidator(
                new LandmarkStageRegistry(),
                explorationRegistry,
                visualRegistry,
                new LandmarkChallengePoolRegistry(),
                sceneRepository,
                cityRepository,
                resourceLoader
        );
    }
}
