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
