package com.example.demo.config;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LandmarkDataInitializer implements CommandLineRunner {
    private final CityRepository cityRepository;
    private final SceneRepository sceneRepository;

    public LandmarkDataInitializer(CityRepository cityRepository, SceneRepository sceneRepository) {
        this.cityRepository = cityRepository;
        this.sceneRepository = sceneRepository;
    }

    @Override
    public void run(String... args) {
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        if (cities.size() < 4) {
            return;
        }

        migrateJiufenToGaomei(cities.get(1));

        List<Landmark> landmarks = List.of(
                new Landmark(0, "台北 101", "現代地標",
                        "登上臺灣代表性的摩天大樓，從高空俯瞰臺北盆地與城市天際線。",
                        "/images/landmarks/taipei-101.png", 2, 130, 110),
                new Landmark(0, "中正紀念堂", "歷史建築",
                        "漫步自由廣場，欣賞白牆藍瓦的紀念建築與臺北城市風景。",
                        "/images/landmarks/chiang-kai-shek-memorial.png", 2, 125, 105),
                new Landmark(1, "臺中國家歌劇院", "建築藝術",
                        "走進以曲牆與洞穴空間聞名的現代建築，感受藝術與城市交會。",
                        "/images/landmarks/taichung-theater.png", 2, 140, 120),
                new Landmark(1, "高美濕地", "自然生態",
                        "沿著木棧道走入潮間帶，在風車與水面倒影之間欣賞夕陽。",
                        "/images/landmarks/gaomei-wetlands.png", 2, 135, 115),
                new Landmark(2, "安平古堡", "歷史古蹟",
                        "穿梭紅磚城牆與老樹之間，閱讀臺南數百年的海港歷史。",
                        "/images/landmarks/anping-fort.png", 2, 145, 125),
                new Landmark(2, "赤崁樓", "歷史古蹟",
                        "探索紅瓦樓閣與古老碑林，感受臺南府城深厚的歷史層次。",
                        "/images/landmarks/chihkan-tower.png", 2, 140, 120),
                new Landmark(3, "龍虎塔", "宗教建築",
                        "造訪蓮池潭畔色彩鮮明的龍虎雙塔，收藏高雄經典湖景。",
                        "/images/landmarks/dragon-tiger-pagodas.png", 3, 155, 135),
                new Landmark(3, "駁二藝術特區", "藝術園區",
                        "穿梭港邊倉庫、裝置藝術與輕軌，探索高雄活力十足的創意港區。",
                        "/images/landmarks/pier-2-art-center.png", 2, 145, 125),
                new Landmark(4, "太魯閣峽谷", "自然景觀",
                        "走進壯麗的大理石峽谷，沿著湛藍溪流探索花蓮群山與險峻岩壁。",
                        "/images/landmarks/taroko-gorge.png", 3, 165, 140),
                new Landmark(5, "雙心石滬", "海島文化",
                        "從高處欣賞七美海岸的雙心造型石滬，認識澎湖傳統潮間帶漁法。",
                        "/images/landmarks/twin-hearts-stone-weir.png", 3, 165, 145)
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
            scene.setType("自然生態");
            scene.setDescription("沿著木棧道走入潮間帶，在風車與水面倒影之間欣賞夕陽。");
            scene.setImageUrl("/images/landmarks/gaomei-wetlands.png");
            scene.setRarity(2);
            scene.setExpReward(135);
            scene.setCoinReward(115);
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
                Map.entry("淡水老街", "/images/landmarks/tamsui-old-street.png"),
                Map.entry("台中逢甲夜市", "/images/landmarks/fengjia-night-market.png"),
                Map.entry("台中彩虹眷村", "/images/landmarks/rainbow-village.png"),
                Map.entry("台南神農街", "/images/landmarks/shennong-street.png"),
                Map.entry("蓮池潭", "/images/landmarks/dragon-tiger-pagodas.png"),
                Map.entry("太魯閣國家公園", "/images/landmarks/taroko-gorge.png"),
                Map.entry("七星潭", "/images/landmarks/qixingtan-beach.png"),
                Map.entry("澎湖跨海大橋", "/images/landmarks/penghu-great-bridge.png"),
                Map.entry("澎湖花火節", "/images/landmarks/penghu-fireworks-festival.png")
        );

        imageBySceneName.forEach((sceneName, imageUrl) ->
                sceneRepository.findFirstByName(sceneName).ifPresent(scene -> {
                    if (scene.getImageUrl() == null || scene.getImageUrl().isBlank()) {
                        scene.setImageUrl(imageUrl);
                        sceneRepository.save(scene);
                    }
                }));
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
