package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class VisualChallengeRegistry {

    private final Map<VisualChallengeKey, VisualChallengeDefinition> definitions;

    public VisualChallengeRegistry() {
        this(Map.of(
                new VisualChallengeKey(1, 1),
                new VisualChallengeDefinition(
                        "TAIPEI-101-VISUAL",
                        "/images/challenges/taipei101-focus.jpg",
                        "/images/challenges/taipei101-focus.jpg",
                        "旅行筆記中出現了一張景點局部照片，觀察建築輪廓與外牆特徵。",
                        "臺北 101 的竹節式外觀與層層收分設計，融合傳統意象與現代摩天建築技術。",
                        List.of(
                                candidate(1, 1),
                                candidate(2, 2),
                                candidate(4, 3),
                                candidate(6, 2)
                        ),
                        new VisualChallengeKey(1, 1)
                ),
                new VisualChallengeKey(1, 2),
                new VisualChallengeDefinition(
                        "TAIPEI-PALACE-MUSEUM-VISUAL",
                        "/images/challenges/palace-focus.jpg",
                        "/images/challenges/palace-puzzle.jpg",
                        "觀察青綠屋瓦、屋脊裝飾與層疊屋簷，找出正確的臺灣景點。",
                        "國立故宮博物院以傳統宮殿式建築與典藏大量中華文物聞名。",
                        List.of(
                                candidate(1, 2),
                                candidate(1, 1),
                                candidate(2, 2),
                                candidate(1, 3)
                        ),
                        new VisualChallengeKey(1, 2)
                ),
                new VisualChallengeKey(1, 3),
                new VisualChallengeDefinition(
                        "TAIPEI-XIMENDING-VISUAL",
                        "/images/challenges/ximending-focus.jpg",
                        "/images/challenges/ximending-puzzle.jpg",
                        "觀察紅磚、拱窗與八角屋頂等街區建築特徵，找出正確的臺灣景點。",
                        "西門町融合紅樓歷史建築、徒步商圈與街頭文化，是臺北代表性的娛樂文化街區。",
                        List.of(
                                candidate(1, 3),
                                candidate(2, 3),
                                candidate(4, 1),
                                candidate(6, 3)
                        ),
                        new VisualChallengeKey(1, 3)
                ),
                new VisualChallengeKey(2, 1),
                new VisualChallengeDefinition(
                        "TAICHUNG-GAOMEI-WETLANDS-VISUAL",
                        "/images/challenges/gaomei-focus.jpg",
                        "",
                        "觀察木棧道、潮間帶水面、夕陽倒影與風力發電機，找出正確的臺灣景點。",
                        "高美濕地擁有潮間帶生態、木棧道與沿岸風機，是臺中著名的夕陽景觀。",
                        List.of(
                                candidate(2, 1),
                                candidate(5, 2),
                                candidate(5, 3),
                                candidate(6, 1)
                        ),
                        new VisualChallengeKey(2, 1)
                ),
                new VisualChallengeKey(2, 2),
                new VisualChallengeDefinition(
                        "TAICHUNG-NATIONAL-THEATER-VISUAL",
                        "/images/challenges/opera-focus.jpg",
                        "/images/challenges/opera-puzzle.jpg",
                        "觀察曲面牆、洞穴式開口與流動的建築輪廓，找出正確的臺灣景點。",
                        "臺中國家歌劇院以曲牆、孔洞與無樑板的洞穴式空間聞名。",
                        List.of(
                                candidate(2, 2),
                                candidate(1, 1),
                                candidate(4, 1),
                                candidate(1, 2)
                        ),
                        new VisualChallengeKey(2, 2)
                ),
                new VisualChallengeKey(2, 3),
                new VisualChallengeDefinition(
                        "TAICHUNG-RAINBOW-VILLAGE-VISUAL",
                        "/images/challenges/rainbow-focus.jpg",
                        "/images/challenges/rainbow-puzzle.jpg",
                        "觀察牆面上的人物、動物與鮮明手繪圖案，找出正確的臺灣景點。",
                        "彩虹眷村以充滿人物、動物與幾何圖案的彩繪巷弄保存眷村記憶。",
                        List.of(
                                candidate(2, 3),
                                candidate(1, 3),
                                candidate(4, 1),
                                candidate(6, 3)
                        ),
                        new VisualChallengeKey(2, 3)
                )
        ));
    }

    VisualChallengeRegistry(
            Map<VisualChallengeKey, VisualChallengeDefinition> definitions
    ) {
        this.definitions = Map.copyOf(definitions);
    }

    public Optional<VisualChallengeDefinition> findByStage(int cityOrder, int stageOrder) {
        return Optional.ofNullable(definitions.get(new VisualChallengeKey(cityOrder, stageOrder)));
    }

    public VisualChallengeDefinition findRequired(VisualChallengeKey key) {
        VisualChallengeDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalStateException(key + " 缺少視覺挑戰設定");
        }
        return definition;
    }

    public Map<VisualChallengeKey, VisualChallengeDefinition> findAll() {
        return definitions;
    }

    private static VisualCandidate candidate(int cityOrder, int stageOrder) {
        return new VisualCandidate(new VisualChallengeKey(cityOrder, stageOrder));
    }
}
