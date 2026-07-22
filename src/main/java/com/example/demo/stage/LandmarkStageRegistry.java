package com.example.demo.stage;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LandmarkStageRegistry {

    private final Map<Long, LandmarkStageDefinition> stagesByLandmarkId;

    public LandmarkStageRegistry() {
        List<LandmarkStageDefinition> definitions = List.of(
                new LandmarkStageDefinition(1L, 1L, 1),
                new LandmarkStageDefinition(1L, 2L, 2),
                new LandmarkStageDefinition(1L, 3L, 3)
        );

        validate(definitions);

        this.stagesByLandmarkId = definitions.stream()
                .collect(Collectors.toUnmodifiableMap(
                        LandmarkStageDefinition::landmarkId,
                        Function.identity()
                ));
    }

    public Optional<LandmarkStageDefinition> findByLandmarkId(Long landmarkId) {
        return Optional.ofNullable(stagesByLandmarkId.get(landmarkId));
    }

    public List<LandmarkStageDefinition> findByCityId(Long cityId) {
        return stagesByLandmarkId.values().stream()
                .filter(stage -> stage.cityId().equals(cityId))
                .sorted((a, b) -> Integer.compare(a.stageOrder(), b.stageOrder()))
                .toList();
    }

    public List<LandmarkStageDefinition> findAll() {
        return List.copyOf(stagesByLandmarkId.values());
    }

    private void validate(List<LandmarkStageDefinition> definitions) {
        long uniqueLandmarks = definitions.stream()
                .map(LandmarkStageDefinition::landmarkId)
                .distinct()
                .count();

        if (uniqueLandmarks != definitions.size()) {
            throw new IllegalStateException("同一景點不可重複設定關卡");
        }

        long uniqueOrders = definitions.stream()
                .map(stage -> stage.cityId() + ":" + stage.stageOrder())
                .distinct()
                .count();

        if (uniqueOrders != definitions.size()) {
            throw new IllegalStateException("同一城市不可有重複關卡順序");
        }

        definitions.forEach(stage -> {
            if (stage.stageOrder() < 1) {
                throw new IllegalStateException("關卡順序必須從 1 開始");
            }
        });
    }
}
