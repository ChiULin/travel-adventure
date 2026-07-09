package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class BossService {
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final UserProgressRepository userProgressRepository;
    private final CheckinRepository checkinRepository;
    private final SceneRepository sceneRepository;

    public BossService(CityRepository cityRepository, UserRepository userRepository, UserProgressRepository userProgressRepository,
                       CheckinRepository checkinRepository, SceneRepository sceneRepository) {
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
        this.userProgressRepository = userProgressRepository;
        this.checkinRepository = checkinRepository;
        this.sceneRepository = sceneRepository;
    }

    public boolean challenge(Long userId, Long cityId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<City> optionalCity = cityRepository.findById(cityId);
        if (optionalUser.isEmpty() || optionalCity.isEmpty()) {
            throw new IllegalArgumentException("invalid user or city");
        }

        User user = optionalUser.get();
        City city = optionalCity.get();
        long cityDone = checkinRepository.countByUserIdAndSceneCityId(userId, cityId);
        long cityTotal = sceneRepository.countByCityId(cityId);
        if (cityTotal == 0 || cityDone < cityTotal) {
            throw new IllegalArgumentException("complete all city scenes first");
        }

        int userPower = (user.getLevel() == null ? 1 : user.getLevel()) * 10
                + ((user.getExp() == null ? 0 : user.getExp()) / 10);
        int bossPower = city.getBossPower() == null ? 0 : city.getBossPower();
        boolean win = userPower >= bossPower;

        if (win) {
            UserProgress progress = userProgressRepository.findByUserId(userId).orElseGet(() -> UserProgress.builder()
                    .user(user)
                    .unlockedCityIdsJson("1")
                    .defeatedBossCityIdsJson("")
                    .discoveredHiddenSceneIdsJson("")
                    .build());
            Set<Long> defeated = JourneyStateService.csvToLongSet(progress.getDefeatedBossCityIdsJson());
            defeated.add(cityId);
            progress.setDefeatedBossCityIdsJson(JourneyStateService.longSetToCsv(defeated));

            Set<Long> unlocked = JourneyStateService.csvToLongSet(progress.getUnlockedCityIdsJson());
            cityRepository.findAllByOrderByUnlockOrderAsc().stream()
                    .filter(next -> next.getUnlockOrder() != null && city.getUnlockOrder() != null)
                    .filter(next -> next.getUnlockOrder().equals(city.getUnlockOrder() + 1))
                    .findFirst()
                    .ifPresent(next -> unlocked.add(next.getId()));
            progress.setUnlockedCityIdsJson(JourneyStateService.longSetToCsv(unlocked));
            userProgressRepository.save(progress);

            user.setCoins((user.getCoins() == null ? 0 : user.getCoins()) + 300);
            user.setExp((user.getExp() == null ? 0 : user.getExp()) + 260);
            user.setBossPoints((user.getBossPoints() == null ? 0 : user.getBossPoints()) + 1);
            int level = (user.getExp() == null ? 0 : user.getExp()) / 220 + 1;
            user.setLevel(level);
            user.setTitle(level >= 4 ? "City Explorer" : "New Traveler");
            userRepository.save(user);
        }

        return win;
    }
}
