package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class VisualChallengeRegistry {

    private final Map<VisualChallengeKey, VisualChallengeDefinition> definitions;

    public VisualChallengeRegistry() {
        this(merge(
                Map.of(
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
                ),
                new VisualChallengeKey(3, 1),
                new VisualChallengeDefinition(
                        "TAINAN-CHIKHAN-TOWER-VISUAL",
                        "/images/challenges/chihkan-focus.jpg",
                        "/images/challenges/chihkan-puzzle.jpg",
                        "觀察紅瓦、飛簷、紅磚與石碑局部，找出正確的臺南古蹟。",
                        "赤崁樓前身為普羅民遮城，紅瓦樓閣與歷史遺構見證臺南城市發展。",
                        List.of(
                                candidate(3, 1),
                                candidate(3, 2),
                                candidate(3, 3),
                                candidate(1, 2)
                        ),
                        new VisualChallengeKey(3, 1)
                ),
                new VisualChallengeKey(3, 2),
                new VisualChallengeDefinition(
                        "TAINAN-ANPING-FORT-VISUAL",
                        "/images/challenges/anping-focus.jpg",
                        "/images/challenges/anping-puzzle.jpg",
                        "觀察紅磚古牆、樹根與瞭望塔局部，找出正確的臺南古蹟。",
                        "安平古堡與熱蘭遮城歷史密切相關，古牆、遺跡與瞭望塔保存港城記憶。",
                        List.of(
                                candidate(3, 2),
                                candidate(3, 1),
                                candidate(3, 3),
                                candidate(4, 3)
                        ),
                        new VisualChallengeKey(3, 2)
                ),
                new VisualChallengeKey(3, 3),
                new VisualChallengeDefinition(
                        "TAINAN-CONFUCIUS-TEMPLE-VISUAL",
                        "/images/challenges/confucius-focus.jpg",
                        "/images/challenges/confucius-puzzle.jpg",
                        "觀察紅色門牆、傳統屋簷與石坊局部，找出正確的臺南古蹟。",
                        "臺南孔廟是臺灣第一座孔廟，以紅牆、古建築與教育傳統聞名。",
                        List.of(
                                candidate(3, 3),
                                candidate(3, 1),
                                candidate(3, 2),
                                candidate(1, 2)
                        ),
                        new VisualChallengeKey(3, 3)
                )
                ),
                Map.of(
                        new VisualChallengeKey(4, 1),
                        new VisualChallengeDefinition(
                                "KAOHSIUNG-PIER2-VISUAL",
                                "/images/challenges/pier2-focus.jpg",
                                "/images/challenges/pier2-puzzle.jpg",
                                "觀察紅磚倉庫、鐵窗、舊鐵道與公共藝術局部，找出正確景點。",
                                "駁二藝術特區由港區舊倉庫轉型，保留鐵道與工業建築並融入公共藝術。",
                                List.of(
                                        candidate(4, 1),
                                        candidate(2, 3),
                                        candidate(1, 3),
                                        candidate(2, 2)
                                ),
                                new VisualChallengeKey(4, 1)
                        ),
                        new VisualChallengeKey(4, 2),
                        new VisualChallengeDefinition(
                                "KAOHSIUNG-LOVE-RIVER-VISUAL",
                                "/images/challenges/love-river-focus.jpg",
                                "/images/challenges/love-river-puzzle.jpg",
                                "觀察河岸步道、橋梁與夜間燈光倒影，找出正確景點。",
                                "愛河是高雄重要城市水岸，河岸步道、橋梁與夜景承載市民生活記憶。",
                                List.of(
                                        candidate(4, 2),
                                        candidate(2, 1),
                                        candidate(5, 2),
                                        candidate(6, 2)
                                ),
                                new VisualChallengeKey(4, 2)
                        ),
                        new VisualChallengeKey(4, 3),
                        new VisualChallengeDefinition(
                                "KAOHSIUNG-DRAGON-TIGER-PAGODAS-VISUAL",
                                "/images/challenges/dragon-tiger-focus.jpg",
                                "/images/challenges/dragon-tiger-puzzle.jpg",
                                "觀察龍虎入口、七層塔身、彩繪屋簷與九曲橋局部，找出正確景點。",
                                "龍虎塔以雙塔、龍虎入口與九曲橋構成蓮池潭代表性的傳統建築景觀。",
                                List.of(
                                        candidate(4, 3),
                                        candidate(3, 1),
                                        candidate(3, 3),
                                        candidate(1, 2)
                                ),
                                new VisualChallengeKey(4, 3)
                        )
                ),
                Map.of(
                        new VisualChallengeKey(5, 1),
                        new VisualChallengeDefinition(
                                "HUALIEN-TAROKO-VISUAL",
                                "/images/challenges/taroko-focus.jpg",
                                "/images/challenges/taroko-puzzle.jpg",
                                "觀察岩壁、河谷、步道與地形特徵的組合，找出正確景點。",
                                "太魯閣以大理石峽谷、立霧溪與沿山步道呈現花蓮壯闊的河谷地形。",
                                List.of(
                                        candidate(5, 1),
                                        candidate(5, 3),
                                        candidate(5, 2),
                                        candidate(2, 1)
                                ),
                                new VisualChallengeKey(5, 1)
                        ),
                        new VisualChallengeKey(5, 2),
                        new VisualChallengeDefinition(
                                "HUALIEN-QIXINGTAN-VISUAL",
                                "/images/challenges/qixingtan-focus.jpg",
                                "/images/challenges/qixingtan-puzzle.jpg",
                                "觀察礫石、海浪、海岸弧線與遠方山勢的組合，找出正確景點。",
                                "七星潭以礫石海灘、弧形海岸及中央山脈與太平洋相接的景觀聞名。",
                                List.of(
                                        candidate(5, 2),
                                        candidate(2, 1),
                                        candidate(4, 2),
                                        candidate(6, 1)
                                ),
                                new VisualChallengeKey(5, 2)
                        ),
                        new VisualChallengeKey(5, 3),
                        new VisualChallengeDefinition(
                                "HUALIEN-QINGSHUI-CLIFF-VISUAL",
                                "/images/challenges/qingshui-focus.jpg",
                                "/images/challenges/qingshui-puzzle.jpg",
                                "觀察高聳岩壁、海岸道路與深淺海色的組合，找出正確景點。",
                                "清水斷崖由高山岩壁直接落入太平洋，形成花蓮代表性的壯闊海岸地形。",
                                List.of(
                                        candidate(5, 3),
                                        candidate(5, 1),
                                        candidate(5, 2),
                                        candidate(6, 2)
                                ),
                                new VisualChallengeKey(5, 3)
                        )
                ),
                Map.of(
                        new VisualChallengeKey(6, 1),
                        new VisualChallengeDefinition(
                                "PENGHU-DOUBLE-HEART-WEIR-VISUAL",
                                "/images/challenges/double-heart-focus.jpg",
                                "/images/challenges/double-heart-puzzle.jpg",
                                "觀察石牆排列、海水與潮間帶形狀的組合，找出正確景點。",
                                "雙心石滬以玄武岩堆砌潮間帶石牆，呈現澎湖傳統海洋生活智慧。",
                                List.of(
                                        candidate(6, 1),
                                        candidate(5, 2),
                                        candidate(2, 1),
                                        candidate(4, 2)
                                ),
                                new VisualChallengeKey(6, 1)
                        ),
                        new VisualChallengeKey(6, 2),
                        new VisualChallengeDefinition(
                                "PENGHU-SEA-BRIDGE-VISUAL",
                                "/images/challenges/penghu-bridge-focus.jpg",
                                "/images/challenges/penghu-bridge-puzzle.jpg",
                                "觀察橋梁結構、海面及島嶼之間的連接方式，找出正確景點。",
                                "澎湖跨海大橋跨越海域連接島嶼，是群島交通與海洋景觀的重要地標。",
                                List.of(
                                        candidate(6, 2),
                                        candidate(4, 2),
                                        candidate(5, 3),
                                        candidate(2, 1)
                                ),
                                new VisualChallengeKey(6, 2)
                        ),
                        new VisualChallengeKey(6, 3),
                        new VisualChallengeDefinition(
                                "PENGHU-FIREWORKS-FESTIVAL-VISUAL",
                                "/images/challenges/fireworks-focus.jpg",
                                "/images/challenges/fireworks-puzzle.jpg",
                                "觀察夜間活動、海面及煙火背景的組合，找出正確景點。",
                                "澎湖花火節結合海島港灣、舞臺活動與海面煙火，形成代表性的夜間盛會。",
                                List.of(
                                        candidate(6, 3),
                                        candidate(1, 3),
                                        candidate(4, 2),
                                        candidate(4, 1)
                                ),
                                new VisualChallengeKey(6, 3)
                        )
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

    @SafeVarargs
    private static <K, V> Map<K, V> merge(Map<K, V>... maps) {
        Map<K, V> merged = new HashMap<>();
        for (Map<K, V> map : maps) {
            merged.putAll(map);
        }
        return Map.copyOf(merged);
    }
}
