package com.example.demo.service.food;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FoodRegistry {

    private final Map<Long, List<FoodDefinition>> foodsByCity;

    public FoodRegistry() {
        FoodDefinition tainanBeefSoup = createTainanBeefSoup();

        this.foodsByCity = Map.of(
                tainanBeefSoup.cityId(),
                List.of(tainanBeefSoup)
        );

        validateAll();
    }

    public List<FoodDefinition> findByCityId(Long cityId) {
        return foodsByCity.getOrDefault(cityId, List.of());
    }

    public Optional<FoodDefinition> findSignatureFoodByCityId(Long cityId) {
        return findByCityId(cityId).stream()
                .filter(FoodDefinition::signatureFood)
                .findFirst();
    }

    public Optional<FoodDefinition> findByFoodKey(String foodKey) {
        return foodsByCity.values().stream()
                .flatMap(List::stream)
                .filter(food -> food.foodKey().equals(foodKey))
                .findFirst();
    }

    private FoodDefinition createTainanBeefSoup() {
        return new FoodDefinition(
                "TAINAN_BEEF_SOUP",
                3L,
                "台南",
                "牛肉湯",
                "溫體牛肉片沖入熱高湯，是台南代表性的早晨美食。",
                "台南牛肉湯常使用當日現宰直送的溫體牛。"
                        + "店家將生牛肉薄片放入碗中，再直接沖入熱高湯川燙，"
                        + "肉質粉嫩鮮甜，常搭配薑絲與甘甜醬油膏享用。",
                true,
                FoodEffectType.EXTRA_TIME,
                2,
                "台南牛肉湯常見的特色做法是什麼？",
                List.of(
                        "將生牛肉薄片放入碗中，再沖入熱高湯",
                        "將牛肉裹粉後油炸",
                        "以冷湯長時間浸泡",
                        "加入起司後放入烤箱焗烤"
                ),
                "將生牛肉薄片放入碗中，再沖入熱高湯",
                "台南牛肉湯以溫體牛肉和熱高湯快速沖燙為特色，"
                        + "能保留牛肉柔嫩與鮮甜的口感。"
        );
    }

    private void validateAll() {
        foodsByCity.values().stream()
                .flatMap(List::stream)
                .forEach(this::validate);
    }

    private void validate(FoodDefinition food) {
        if (food.foodKey() == null || food.foodKey().isBlank()) {
            throw new IllegalStateException("foodKey 不可空白");
        }

        if (food.cityId() == null) {
            throw new IllegalStateException(
                    food.foodKey() + " 的 cityId 不可為空"
            );
        }

        if (food.name() == null || food.name().isBlank()) {
            throw new IllegalStateException(
                    food.foodKey() + " 的美食名稱不可空白"
            );
        }

        if (food.challengeOptions() == null
                || food.challengeOptions().size() != 4) {
            throw new IllegalStateException(
                    food.foodKey() + " 的文化題必須有 4 個選項"
            );
        }

        if (!food.challengeOptions().contains(food.correctAnswer())) {
            throw new IllegalStateException(
                    food.foodKey() + " 的正確答案不在選項中"
            );
        }

        if (food.effectValue() <= 0) {
            throw new IllegalStateException(
                    food.foodKey() + " 的效果值必須大於 0"
            );
        }
    }
}
