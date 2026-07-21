package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ExplorationMissionRegistry {
    private final Map<String, ExplorationMissionDefinition> missions;

    public ExplorationMissionRegistry() {
        List<ExplorationMissionDefinition> definitions = List.of(
                createAnpingMission(),
                createTaipei101Mission(),
                createTarokoMission()
        );
        Map<String, ExplorationMissionDefinition> indexed = new LinkedHashMap<>();
        definitions.forEach(mission -> indexed.put(mission.missionKey(), mission));
        this.missions = Map.copyOf(indexed);
    }

    public Optional<ExplorationMissionDefinition> findByKey(String missionKey) {
        return Optional.ofNullable(missions.get(missionKey));
    }

    public List<ExplorationMissionDefinition> findByCityId(Long cityId) {
        return missions.values().stream()
                .filter(mission -> mission.cityId().equals(cityId))
                .toList();
    }

    private ExplorationMissionDefinition createAnpingMission() {
        return new ExplorationMissionDefinition(
                "TAINAN-ANPING-01",
                3L,
                8L,
                "尋找海港旁的古老據點",
                "尋找一座與台灣早期海上歷史有關的景點。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問當地居民", "你從當地居民口中發現了新線索",
                                "這個地方位於台南安平一帶。"),
                        investigation(ClueType.HISTORY, "查閱歷史文獻", "你從歷史文獻中發現了新線索",
                                "它曾是荷蘭人在台灣建立的重要據點。"),
                        investigation(ClueType.VISUAL, "觀察舊照片", "你從舊照片中發現了新線索",
                                "可以看見紅磚城牆與古老遺跡。")
                ),
                List.of(7L, 8L, 9L),
                "安平古堡最早與哪一個歐洲國家的統治有關？",
                List.of("英國", "荷蘭", "法國", "葡萄牙"),
                "荷蘭"
        );
    }

    private ExplorationMissionDefinition createTaipei101Mission() {
        return new ExplorationMissionDefinition(
                "TAIPEI-101-01",
                1L,
                1L,
                "尋找節節高升的城市地標",
                "尋找一座以竹節為設計概念，曾是世界最高建築的城市地標。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問附近上班族", "你從附近上班族口中發現了新線索",
                                "它位於台北信義區，附近有大型商場與辦公大樓。"),
                        investigation(ClueType.HISTORY, "查看城市地圖", "你從城市地圖中發現了新線索",
                                "它鄰近市政府與世貿地區。"),
                        investigation(ClueType.VISUAL, "觀察城市輪廓", "你從城市輪廓中發現了新線索",
                                "建築外型由多個向上堆疊的節段組成。")
                ),
                List.of(1L, 2L, 3L),
                "這座城市地標的建築外型主要採用哪一種植物意象？",
                List.of("竹子", "梅花", "榕樹", "稻穗"),
                "竹子"
        );
    }

    private ExplorationMissionDefinition createTarokoMission() {
        return new ExplorationMissionDefinition(
                "HUALIEN-TAROKO-01",
                5L,
                13L,
                "尋找大理石峽谷",
                "尋找一處由河流長期切割山脈形成的峽谷景觀。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問山區旅客", "你從山區旅客口中發現了新線索",
                                "進入山區後，可以看到高聳岩壁。"),
                        investigation(ClueType.HISTORY, "查閱地形資料", "你從地形資料中發現了新線索",
                                "這片峽谷主要由立霧溪長期切割形成。"),
                        investigation(ClueType.VISUAL, "觀察峽谷照片", "你從峽谷照片中發現了新線索",
                                "照片中可見大理石岩壁、溪流與山間步道。")
                ),
                List.of(13L, 14L, 15L),
                "形成這片峽谷的主要河流是哪一條？",
                List.of("立霧溪", "淡水河", "濁水溪", "愛河"),
                "立霧溪"
        );
    }

    private InvestigationDefinition investigation(ClueType type, String actionName,
                                                   String resultMessage, String clueText) {
        return new InvestigationDefinition(type, actionName, resultMessage, clueText);
    }
}
