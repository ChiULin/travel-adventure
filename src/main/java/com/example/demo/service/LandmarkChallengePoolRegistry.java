package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LandmarkChallengePoolRegistry {

    private final Map<LandmarkStageKey, List<MysteryChallengeType>> pools = merge(
            Map.of(
            new LandmarkStageKey(1, 1),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(1, 2),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(1, 3),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(2, 1),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION
            ),
            new LandmarkStageKey(2, 2),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(2, 3),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(3, 1),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(3, 2),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(3, 3),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            )
            ),
            Map.of(
            new LandmarkStageKey(4, 1),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(4, 2),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(4, 3),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            )
            ),
            Map.of(
            new LandmarkStageKey(5, 1),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(5, 2),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            ),
            new LandmarkStageKey(5, 3),
            List.of(
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION,
                    MysteryChallengeType.PUZZLE
            )
            )
    );

    public List<MysteryChallengeType> getAvailableTypes(int cityOrder, int stageOrder) {
        return pools.getOrDefault(new LandmarkStageKey(cityOrder, stageOrder), List.of());
    }

    public boolean isEnabled(int cityOrder, int stageOrder) {
        return !getAvailableTypes(cityOrder, stageOrder).isEmpty();
    }

    public Map<LandmarkStageKey, List<MysteryChallengeType>> findAll() {
        return pools;
    }

    @SafeVarargs
    private static <K, V> Map<K, V> merge(Map<K, V>... maps) {
        Map<K, V> merged = new HashMap<>();
        for (Map<K, V> map : maps) {
            merged.putAll(map);
        }
        return Map.copyOf(merged);
    }
}
