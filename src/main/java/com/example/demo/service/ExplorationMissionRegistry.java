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
        this(List.of(
                createTaipei101Mission(),
                createGaomeiWetlandsMission(),
                createAnpingMission(),
                createPier2Mission(),
                createTarokoMission(),
                createDoubleHeartStoneWeirMission()
        ));
    }

    ExplorationMissionRegistry(List<ExplorationMissionDefinition> definitions) {
        Map<String, ExplorationMissionDefinition> indexed = new LinkedHashMap<>();
        definitions.forEach(mission -> {
            validateMission(mission);
            if (indexed.putIfAbsent(mission.missionKey(), mission) != null) {
                throw new IllegalStateException("探索任務代碼重複：" + mission.missionKey());
            }
        });
        this.missions = Map.copyOf(indexed);
    }

    public Optional<ExplorationMissionDefinition> findByKey(String missionKey) {
        return Optional.ofNullable(missions.get(missionKey));
    }

    public ExplorationMissionDefinition getRequired(String missionKey) {
        ExplorationMissionDefinition mission = missions.get(missionKey);
        if (mission == null) {
            throw new IllegalArgumentException("找不到探索任務：" + missionKey);
        }
        return mission;
    }

    public List<ExplorationMissionDefinition> findByCityId(Long cityId) {
        return missions.values().stream()
                .filter(mission -> mission.cityId().equals(cityId))
                .toList();
    }

    public Optional<ExplorationMissionDefinition> findByTargetSceneId(Long sceneId) {
        return missions.values().stream()
                .filter(mission -> mission.targetSceneId().equals(sceneId))
                .findFirst();
    }

    public List<ExplorationMissionDefinition> findAll() {
        return List.copyOf(missions.values());
    }

    private void validateMission(ExplorationMissionDefinition mission) {
        if (mission.cityId() == null || mission.targetSceneId() == null) {
            throw new IllegalStateException(mission.missionKey() + " 缺少城市或目標景點");
        }
        if (mission.investigations().size() != 3
                || mission.investigations().stream().map(InvestigationDefinition::type).distinct().count() != 3) {
            throw new IllegalStateException(mission.missionKey() + " 必須具有三種不同調查");
        }
        if (mission.candidateSceneIds().size() < 3
                || mission.candidateSceneIds().stream().distinct().count() != mission.candidateSceneIds().size()) {
            throw new IllegalStateException(mission.missionKey() + " 至少需要三個不同候選景點");
        }
        if (!mission.candidateSceneIds().contains(mission.targetSceneId())) {
            throw new IllegalStateException(mission.missionKey() + " 的候選景點未包含正確景點");
        }
        if (mission.cultureOptions().size() < 4
                || !mission.cultureOptions().contains(mission.cultureAnswer())) {
            throw new IllegalStateException(mission.missionKey() + " 的文化答案或選項設定錯誤");
        }
    }

    private static ExplorationMissionDefinition createTaipei101Mission() {
        return mission(
                "TAIPEI-101-01", 1L, 1L,
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
                List.of("竹子", "梅花", "榕樹", "稻穗"), "竹子");
    }

    private static ExplorationMissionDefinition createGaomeiWetlandsMission() {
        return mission(
                "TAICHUNG-GAOMEI-01", 2L, 4L,
                "尋找風車旁的潮間帶",
                "尋找一處能看見夕陽、風車與大片潮間帶的海岸景觀。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問攝影旅人", "你從攝影旅人口中發現了新線索",
                                "許多人會在傍晚來這裡拍攝夕陽。"),
                        investigation(ClueType.HISTORY, "查看自然資料", "你從自然資料中發現了新線索",
                                "這裡具有豐富的潮間帶生態。"),
                        investigation(ClueType.VISUAL, "觀察海岸照片", "你從海岸照片中發現了新線索",
                                "遠方可以看到成排的風力發電機。")
                ),
                List.of(4L, 5L, 6L),
                "高美濕地最具代表性的自然環境是什麼？",
                List.of("高山森林", "潮間帶", "珊瑚礁", "火山地形"), "潮間帶");
    }

    private static ExplorationMissionDefinition createAnpingMission() {
        return mission(
                "TAINAN-ANPING-01", 3L, 8L,
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
                List.of("英國", "荷蘭", "法國", "葡萄牙"), "荷蘭");
    }

    private static ExplorationMissionDefinition createPier2Mission() {
        return mission(
                "KAOHSIUNG-PIER2-01", 4L, 10L,
                "尋找港邊的藝術倉庫",
                "尋找一處由港口舊倉庫轉型而成，充滿藝術裝置的文化園區。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問港區居民", "你從港區居民口中發現了新線索",
                                "過去這裡是港口貨物倉儲區。"),
                        investigation(ClueType.HISTORY, "查閱城市資料", "你從城市資料中發現了新線索",
                                "現在經常舉辦展覽及文化活動。"),
                        investigation(ClueType.VISUAL, "觀察園區照片", "你從園區照片中發現了新線索",
                                "可以看到倉庫、鐵道與大型藝術裝置。")
                ),
                List.of(10L, 11L, 12L),
                "駁二藝術特區主要由哪一類空間改造而成？",
                List.of("軍事碉堡", "港口倉庫", "傳統市場", "學校校舍"), "港口倉庫");
    }

    private static ExplorationMissionDefinition createTarokoMission() {
        return mission(
                "HUALIEN-TAROKO-01", 5L, 13L,
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
                List.of("立霧溪", "淡水河", "濁水溪", "愛河"), "立霧溪");
    }

    private static ExplorationMissionDefinition createDoubleHeartStoneWeirMission() {
        return mission(
                "PENGHU-DOUBLE-HEART-01", 6L, 16L,
                "尋找海上的雙心石牆",
                "尋找一座由石塊堆砌、外觀如兩顆愛心的傳統捕魚設施。",
                List.of(
                        investigation(ClueType.LOCAL, "詢問島上漁民", "你從島上漁民口中發現了新線索",
                                "這種設施利用海水漲退潮捕魚。"),
                        investigation(ClueType.HISTORY, "查閱地方文化", "你從地方文化資料中發現了新線索",
                                "它展現澎湖先民運用海洋環境的智慧。"),
                        investigation(ClueType.VISUAL, "觀察空拍照片", "你從空拍照片中發現了新線索",
                                "石牆從空中看起來像兩顆相連的心。")
                ),
                List.of(16L, 17L, 18L),
                "石滬主要利用什麼自然現象捕魚？",
                List.of("季風", "潮汐", "地震", "海水蒸發"), "潮汐");
    }

    private static ExplorationMissionDefinition mission(
            String key, Long cityId, Long targetSceneId, String title, String description,
            List<InvestigationDefinition> investigations, List<Long> candidateSceneIds,
            String cultureQuestion, List<String> cultureOptions, String cultureAnswer) {
        return new ExplorationMissionDefinition(
                key, cityId, targetSceneId, title, description, investigations,
                candidateSceneIds, cultureQuestion, cultureOptions, cultureAnswer);
    }

    private static InvestigationDefinition investigation(ClueType type, String actionName,
                                                         String resultMessage, String clueText) {
        return new InvestigationDefinition(type, actionName, resultMessage, clueText);
    }
}
