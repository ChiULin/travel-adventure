package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PuzzleChallengeRegistry {

    private final List<PuzzleChallengeDefinition> definitions = List.of(
            new PuzzleChallengeDefinition(
                    "TAIPEI-101-PUZZLE",
                    new LandmarkStageKey(1, 1),
                    "/images/challenges/taipei101-focus.jpg",
                    List.of(
                            new LandmarkStageKey(1, 1),
                            new LandmarkStageKey(1, 2),
                            new LandmarkStageKey(2, 2),
                            new LandmarkStageKey(4, 3)
                    ),
                    new LandmarkStageKey(1, 1)
            )
    );

    public Optional<PuzzleChallengeDefinition> findByStage(int cityOrder, int stageOrder) {
        LandmarkStageKey stageKey = new LandmarkStageKey(cityOrder, stageOrder);
        return definitions.stream()
                .filter(definition -> definition.targetStage().equals(stageKey))
                .findFirst();
    }

    public List<PuzzleChallengeDefinition> findAll() {
        return definitions;
    }
}
