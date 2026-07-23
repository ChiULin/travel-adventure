package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LandmarkChallengePoolRegistry {

    private final Map<LandmarkStageKey, List<MysteryChallengeType>> pools = Map.of(
            new LandmarkStageKey(1, 1),
            List.of(
                    MysteryChallengeType.EXPLORATION,
                    MysteryChallengeType.QUIZ,
                    MysteryChallengeType.IMAGE_RECOGNITION
            )
    );

    public List<MysteryChallengeType> getAvailableTypes(int cityOrder, int stageOrder) {
        return pools.getOrDefault(new LandmarkStageKey(cityOrder, stageOrder), List.of());
    }

    public boolean isEnabled(int cityOrder, int stageOrder) {
        return !getAvailableTypes(cityOrder, stageOrder).isEmpty();
    }
}
