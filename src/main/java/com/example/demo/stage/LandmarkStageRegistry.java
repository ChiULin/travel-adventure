package com.example.demo.stage;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LandmarkStageRegistry {

    private static final long TAIPEI_CITY_ID = 1L;
    private static final long TAIPEI_101_ID = 1L;
    private static final long NATIONAL_PALACE_MUSEUM_ID = 2L;
    private static final long XIMENDING_ID = 3L;
    private static final long TAICHUNG_CITY_ID = 2L;
    private static final long GAOMEI_WETLANDS_ID = 4L;
    private static final long NATIONAL_TAICHUNG_THEATER_ID = 5L;
    private static final long RAINBOW_VILLAGE_ID = 6L;
    private static final long TAINAN_CITY_ID = 3L;
    private static final long CHIHKAN_TOWER_ID = 7L;
    private static final long ANPING_FORT_ID = 8L;
    private static final long TAINAN_CONFUCIUS_TEMPLE_ID = 9L;
    private static final long KAOHSIUNG_CITY_ID = 4L;
    private static final long PIER_2_ART_CENTER_ID = 10L;
    private static final long LOVE_RIVER_ID = 11L;
    private static final long DRAGON_TIGER_PAGODAS_ID = 12L;
    private static final long HUALIEN_CITY_ID = 5L;
    private static final long TAROKO_ID = 13L;
    private static final long QIXINGTAN_ID = 14L;
    private static final long QINGSHUI_CLIFF_ID = 15L;
    private static final long PENGHU_CITY_ID = 6L;
    private static final long DOUBLE_HEART_STONE_WEIR_ID = 16L;
    private static final long PENGHU_GREAT_BRIDGE_ID = 17L;
    private static final long PENGHU_FIREWORKS_FESTIVAL_ID = 18L;

    private final Map<Long, LandmarkStageDefinition> stagesByLandmarkId;

    public LandmarkStageRegistry() {
        List<LandmarkStageDefinition> definitions = List.of(
                new LandmarkStageDefinition(TAIPEI_CITY_ID, TAIPEI_101_ID, 1),
                new LandmarkStageDefinition(TAIPEI_CITY_ID, NATIONAL_PALACE_MUSEUM_ID, 2),
                new LandmarkStageDefinition(TAIPEI_CITY_ID, XIMENDING_ID, 3),
                new LandmarkStageDefinition(TAICHUNG_CITY_ID, GAOMEI_WETLANDS_ID, 1),
                new LandmarkStageDefinition(TAICHUNG_CITY_ID, NATIONAL_TAICHUNG_THEATER_ID, 2),
                new LandmarkStageDefinition(TAICHUNG_CITY_ID, RAINBOW_VILLAGE_ID, 3),
                new LandmarkStageDefinition(TAINAN_CITY_ID, CHIHKAN_TOWER_ID, 1),
                new LandmarkStageDefinition(TAINAN_CITY_ID, ANPING_FORT_ID, 2),
                new LandmarkStageDefinition(TAINAN_CITY_ID, TAINAN_CONFUCIUS_TEMPLE_ID, 3),
                new LandmarkStageDefinition(KAOHSIUNG_CITY_ID, PIER_2_ART_CENTER_ID, 1),
                new LandmarkStageDefinition(KAOHSIUNG_CITY_ID, LOVE_RIVER_ID, 2),
                new LandmarkStageDefinition(KAOHSIUNG_CITY_ID, DRAGON_TIGER_PAGODAS_ID, 3),
                new LandmarkStageDefinition(HUALIEN_CITY_ID, TAROKO_ID, 1),
                new LandmarkStageDefinition(HUALIEN_CITY_ID, QIXINGTAN_ID, 2),
                new LandmarkStageDefinition(HUALIEN_CITY_ID, QINGSHUI_CLIFF_ID, 3),
                new LandmarkStageDefinition(PENGHU_CITY_ID, DOUBLE_HEART_STONE_WEIR_ID, 1),
                new LandmarkStageDefinition(PENGHU_CITY_ID, PENGHU_GREAT_BRIDGE_ID, 2),
                new LandmarkStageDefinition(PENGHU_CITY_ID, PENGHU_FIREWORKS_FESTIVAL_ID, 3)
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

    public boolean isCityFullyConfigured(Long cityId) {
        List<LandmarkStageDefinition> stages = findByCityId(cityId);

        if (stages.size() != 3) {
            return false;
        }

        return stages.get(0).stageOrder() == 1
                && stages.get(1).stageOrder() == 2
                && stages.get(2).stageOrder() == 3;
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
