package com.example.demo.config;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import org.springframework.boot.CommandLineRunner;

import java.util.List;
import java.util.Map;

public class LandmarkDataInitializer implements CommandLineRunner {
    private final CityRepository cityRepository;
    private final SceneRepository sceneRepository;
    private final CheckinRepository checkinRepository;

    public LandmarkDataInitializer(CityRepository cityRepository, SceneRepository sceneRepository,
                                   CheckinRepository checkinRepository) {
        this.cityRepository = cityRepository;
        this.sceneRepository = sceneRepository;
        this.checkinRepository = checkinRepository;
    }

    @Override
    public void run(String... args) {
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        if (cities.size() < 4) {
            return;
        }

        removeScene("台中逢甲夜市");
        removeScene("逢甲夜市");

        migrateJiufenToGaomei(cities.get(1));
        migrateScene(
                cities.get(3),
                "龍虎塔",
                new Landmark(3, "旗津", "海港風景",
                        "搭渡輪前往高雄港邊沙洲，漫步海岸、燈塔與老街，感受南方海風與港都日常。",
                        "/images/landmarks/cijin.webp", 3, 155, 135)
        );

        if (cities.size() > 4) {
            migrateScene(
                    cities.get(4),
                    "太魯閣峽谷",
                    new Landmark(4, "阿美文化村", "原住民文化",
                            "走進花蓮阿美族文化場域，認識木雕、圖騰、歌舞與山海生活記憶。",
                            "/images/landmarks/amis-cultural-village.webp", 3, 165, 140)
            );
        }

        List<Landmark> landmarks = List.of(
                new Landmark(0, "台北 101", "城市地標",
                        "登上臺灣代表性的摩天大樓，從高空俯瞰臺北盆地與城市天際線。",
                        "/images/landmarks/taipei-101.webp", 2, 130, 110),
                new Landmark(0, "中正紀念堂", "歷史建築",
                        "漫步自由廣場，欣賞白牆藍瓦的紀念建築與臺北城市風景。",
                        "/images/landmarks/chiang-kai-shek-memorial.webp", 2, 125, 105),
                new Landmark(1, "臺中國家歌劇院", "現代建築",
                        "走進由曲牆與洞穴空間展開的現代建築，感受藝術與城市交會。",
                        "/images/landmarks/taichung-theater.webp", 2, 140, 120),
                new Landmark(1, "高美濕地", "自然景觀",
                        "沿著木棧道走向夕陽與風車，觀察潮間帶生態與壯闊海景。",
                        "/images/landmarks/gaomei-wetlands.webp", 2, 135, 115),
                new Landmark(2, "赤崁樓", "歷史古蹟",
                        "探訪臺南古城核心，感受亭閣、石碑與廟埕交織出的歷史層次。",
                        "/images/landmarks/chihkan-tower.webp", 2, 140, 120),
                new Landmark(2, "安平古堡", "歷史古蹟",
                        "穿梭紅磚城牆與老樹之間，閱讀臺南數百年的海港歷史。",
                        "/images/landmarks/anping-fort.webp", 2, 145, 125),
                new Landmark(3, "旗津", "海港風景",
                        "搭渡輪前往高雄港邊沙洲，漫步海岸、燈塔與老街，感受南方海風與港都日常。",
                        "/images/landmarks/cijin.webp", 3, 155, 135),
                new Landmark(3, "駁二藝術特區", "藝術空間",
                        "走進由舊倉庫改造的創意街區，探索展覽、裝置藝術與港邊風景。",
                        "/images/landmarks/pier-2-art-center.webp", 2, 145, 125),
                new Landmark(4, "阿美文化村", "原住民文化",
                        "走進花蓮阿美族文化場域，認識木雕、圖騰、歌舞與山海生活記憶。",
                        "/images/landmarks/amis-cultural-village.webp", 3, 165, 140),
                new Landmark(5, "雙心石滬", "海島地景",
                        "在潮汐之間尋找澎湖最浪漫的石滬線條，收藏海島經典風景。",
                        "/images/landmarks/twin-hearts-stone-weir.webp", 3, 165, 145)
        );

        landmarks.stream()
                .filter(landmark -> landmark.cityIndex() < cities.size())
                .forEach(landmark -> upsert(cities.get(landmark.cityIndex()), landmark));

        fillMissingImages();
    }

    private void migrateJiufenToGaomei(City taichung) {
        sceneRepository.findFirstByName("九份老街").ifPresent(scene -> {
            scene.setCity(taichung);
            scene.setName("高美濕地");
            scene.setType("自然景觀");
            scene.setDescription("沿著木棧道走向夕陽與風車，觀察潮間帶生態與壯闊海景。");
            scene.setImageUrl("/images/landmarks/gaomei-wetlands.webp");
            scene.setRarity(2);
            scene.setExpReward(135);
            scene.setCoinReward(115);
            scene.setIsHidden(false);
            sceneRepository.save(scene);
        });
    }

    private void migrateScene(City city, String oldName, Landmark replacement) {
        sceneRepository.findFirstByName(oldName).ifPresent(scene -> {
            scene.setCity(city);
            scene.setName(replacement.name());
            scene.setType(replacement.type());
            scene.setDescription(replacement.description());
            scene.setImageUrl(replacement.imageUrl());
            scene.setRarity(replacement.rarity());
            scene.setExpReward(replacement.expReward());
            scene.setCoinReward(replacement.coinReward());
            scene.setIsHidden(false);
            sceneRepository.save(scene);
        });
    }

    private void upsert(City city, Landmark landmark) {
        Scene scene = sceneRepository.findFirstByName(landmark.name()).orElseGet(Scene::new);
        scene.setCity(city);
        scene.setName(landmark.name());
        scene.setType(landmark.type());
        scene.setDescription(landmark.description());
        scene.setImageUrl(landmark.imageUrl());
        scene.setRarity(landmark.rarity());
        scene.setExpReward(landmark.expReward());
        scene.setCoinReward(landmark.coinReward());
        scene.setIsHidden(false);
        sceneRepository.save(scene);
    }

    private void fillMissingImages() {
        Map<String, String> imageBySceneName = Map.ofEntries(
                Map.entry("淡水老街", "/images/landmarks/tamsui-old-street.webp"),
                Map.entry("台中彩虹眷村", "/images/landmarks/rainbow-village.webp"),
                Map.entry("安平古堡", "/images/landmarks/anping-fort.webp"),
                Map.entry("台南神農街", "/images/landmarks/shennong-street.webp"),
                Map.entry("蓮池潭", "/images/landmarks/dragon-tiger-pagodas.webp"),
                Map.entry("旗津", "/images/landmarks/cijin.webp"),
                Map.entry("太魯閣國家公園", "/images/landmarks/taroko-gorge.webp"),
                Map.entry("阿美文化村", "/images/landmarks/amis-cultural-village.webp"),
                Map.entry("七星潭", "/images/landmarks/qixingtan-beach.webp"),
                Map.entry("澎湖跨海大橋", "/images/landmarks/penghu-great-bridge.webp"),
                Map.entry("澎湖花火節", "/images/landmarks/penghu-fireworks-festival.webp")
        );

        imageBySceneName.forEach((sceneName, imageUrl) ->
                sceneRepository.findFirstByName(sceneName).ifPresent(scene -> {
                    if (!imageUrl.equals(scene.getImageUrl())) {
                        scene.setImageUrl(imageUrl);
                        sceneRepository.save(scene);
                    }
                }));
    }

    private void removeScene(String sceneName) {
        sceneRepository.findFirstByName(sceneName).ifPresent(scene -> {
            checkinRepository.deleteBySceneId(scene.getId());
            sceneRepository.delete(scene);
        });
    }

    private record Landmark(
            int cityIndex,
            String name,
            String type,
            String description,
            String imageUrl,
            int rarity,
            int expReward,
            int coinReward
    ) {
    }
}
