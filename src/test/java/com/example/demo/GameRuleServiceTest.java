package com.example.demo;

import com.example.demo.dto.BattleResultRequest;
import com.example.demo.entity.Checkin;
import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProgress;
import com.example.demo.repository.CheckinRepository;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.repository.UserProgressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.BossService;
import com.example.demo.service.CheckinService;
import com.example.demo.service.JourneyStateService;
import com.example.demo.service.QuizQuestionService;
import com.example.demo.stage.LandmarkStageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRuleServiceTest {

    @Mock
    private CheckinRepository checkinRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private UserProgressRepository userProgressRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private JourneyStateService journeyStateService;
    @Mock
    private QuizQuestionService quizQuestionService;
    @Mock
    private LandmarkStageService landmarkStageService;
    private CheckinService checkinService;
    private BossService bossService;

    @BeforeEach
    void setUp() {
        checkinService = new CheckinService(checkinRepository, userRepository, sceneRepository,
                userProgressRepository, journeyStateService, quizQuestionService, landmarkStageService);
        bossService = new BossService(cityRepository, userRepository, userProgressRepository,
                checkinRepository, sceneRepository, quizQuestionService);
    }

    @Test
    void completedLandmarkCannotGrantRewardTwice() {
        User user = user();
        Scene scene = scene(city());
        UserProgress progress = progress(user, scene.getCity(), true, false);
        Checkin completed = Checkin.builder().user(user).scene(scene).completed(true).build();
        stubCheckinContext(user, scene, progress);
        when(checkinRepository.findByUserIdAndSceneId(1L, 10L)).thenReturn(Optional.of(completed));

        assertThrows(IllegalArgumentException.class,
                () -> checkinService.checkin(1L, 10L, "A", null));

        verify(checkinRepository, never()).save(any(Checkin.class));
        verify(userRepository, never()).save(any(User.class));
        assertEquals(0, user.getExp());
        assertEquals(0, user.getCoins());
    }

    @Test
    void lockedCityCannotSubmitLandmarkAnswer() {
        User user = user();
        Scene scene = scene(city());
        stubCheckinContext(user, scene, progress(user, scene.getCity(), false, false));

        assertThrows(IllegalArgumentException.class,
                () -> checkinService.checkin(1L, 10L, "A", null));

        verify(checkinRepository, never()).save(any(Checkin.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void bossCannotBeChallengedBeforeAllLandmarksAreCompleted() {
        User user = user();
        City city = city();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userProgressRepository.findByUserIdAndCityId(1L, 1L))
                .thenReturn(Optional.of(progress(user, city, true, false)));
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(1L, 1L)).thenReturn(2L);
        when(sceneRepository.countByCityId(1L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class,
                () -> bossService.challenge(1L, 1L, "A", null));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void directBossChallengeWithoutIssuedQuestionIsRejected() {
        User user = user();
        user.setLevel(10);
        City city = city();
        UserProgress progress = progress(user, city, true, true);
        progress.setBossCompleted(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userProgressRepository.findByUserIdAndCityId(1L, 1L)).thenReturn(Optional.of(progress));
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(1L, 1L)).thenReturn(3L);
        when(sceneRepository.countByCityId(1L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class,
                () -> bossService.challenge(1L, 1L, "A", null));

        assertEquals(0, user.getExp());
        assertEquals(0, user.getCoins());
        assertEquals(0, user.getBossPoints());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void forgedRewardsAndIllegalRankAreRejected() {
        User user = user();
        City city = city();
        UserProgress progress = progress(user, city, true, true);
        progress.setBossCompleted(true);
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userProgressRepository.findByUserIdAndCityId(1L, 1L)).thenReturn(Optional.of(progress));
        when(checkinRepository.countByUserIdAndSceneCityIdAndCompletedTrue(1L, 1L)).thenReturn(3L);
        when(sceneRepository.countByCityId(1L)).thenReturn(3L);
        when(sceneRepository.findByCityId(1L)).thenReturn(List.of(scene(city), scene(city), scene(city)));

        BattleResultRequest illegalRank = validBattleResult();
        illegalRank.setRank("X");
        assertThrows(IllegalArgumentException.class,
                () -> bossService.recordBattleResult(1L, 1L, illegalRank));

        BattleResultRequest forgedRewards = validBattleResult();
        forgedRewards.setEarnedExp(999_999);
        forgedRewards.setEarnedCoins(999_999);
        assertThrows(IllegalArgumentException.class,
                () -> bossService.recordBattleResult(1L, 1L, forgedRewards));

        verify(userProgressRepository, never()).save(any(UserProgress.class));
    }

    @Test
    void normalDifficultyAppliesServerSideRewardMultiplier() {
        User user = user();
        Scene scene = scene(city());
        UserProgress progress = progress(user, scene.getCity(), true, false);
        stubCheckinContext(user, scene, progress);
        when(checkinRepository.findByUserIdAndSceneId(1L, 10L)).thenReturn(Optional.empty());
        when(quizQuestionService.sceneAnswerCorrect(1L, scene, "scene-10-city", "correct", "NORMAL")).thenReturn(true);
        when(checkinRepository.save(any(Checkin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Checkin result = checkinService.checkin(1L, 10L, "A", "correct", "scene-10-city", "NORMAL");

        assertEquals(120, result.getEarnedExp());
        assertEquals(60, result.getEarnedCoins());
        assertEquals(120, user.getExp());
        assertEquals(60, user.getCoins());
        verify(userRepository).save(user);
    }

    private void stubCheckinContext(User user, Scene scene, UserProgress progress) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sceneRepository.findById(10L)).thenReturn(Optional.of(scene));
        when(userProgressRepository.findByUserIdAndCityId(1L, 1L)).thenReturn(Optional.of(progress));
    }

    private BattleResultRequest validBattleResult() {
        BattleResultRequest request = new BattleResultRequest();
        request.setRank("S");
        request.setMaxCombo(4);
        request.setRemainingLives(3);
        request.setCorrectAnswers(4);
        request.setWrongAnswers(0);
        request.setTimeoutCount(0);
        request.setEarnedExp(0);
        request.setEarnedCoins(0);
        return request;
    }

    private User user() {
        return User.builder().id(1L).level(1).exp(0).coins(0).bossPoints(0).build();
    }

    private City city() {
        return City.builder().id(1L).unlockOrder(1).bossPower(0).bossCorrectAnswer("A")
                .bossOptionA("correct").build();
    }

    private Scene scene(City city) {
        return Scene.builder().id(10L).city(city).quizCorrectAnswer("A").quizOptionA("correct")
                .expReward(100).coinReward(50).build();
    }

    private UserProgress progress(User user, City city, boolean unlocked, boolean badgeUnlocked) {
        return UserProgress.builder().user(user).city(city).unlocked(unlocked).bossUnlocked(true)
                .bossCompleted(false).badgeUnlocked(badgeUnlocked).bestCombo(0).bestRemainingLives(0)
                .challengeCount(0).build();
    }
}
