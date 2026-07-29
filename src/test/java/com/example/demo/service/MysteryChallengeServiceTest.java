package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageDefinition;
import com.example.demo.stage.LandmarkStageRegistry;
import com.example.demo.stage.LandmarkStageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MysteryChallengeServiceTest {

    @Test
    void puzzleCanBeWrappedAndReusedByMysterySession() {
        SceneRepository sceneRepository = mock(SceneRepository.class);
        CityRepository cityRepository = mock(CityRepository.class);
        LandmarkStageRegistry stageRegistry = mock(LandmarkStageRegistry.class);
        LandmarkStageService stageService = mock(LandmarkStageService.class);
        QuizQuestionService quizService = mock(QuizQuestionService.class);
        ExplorationService explorationService = mock(ExplorationService.class);
        ImageRecognitionService imageService = mock(ImageRecognitionService.class);
        PuzzleChallengeService puzzleService = mock(PuzzleChallengeService.class);
        City taipei = City.builder().id(1L).unlockOrder(1).build();
        Scene taipei101 = Scene.builder().id(1L).city(taipei).build();
        Instant issuedAt = Instant.parse("2026-07-23T12:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(45);
        PuzzleChallengeService.PuzzleChallengeView puzzleChallenge =
                new PuzzleChallengeService.PuzzleChallengeView(
                        "PUZ-focus",
                        "/images/challenges/taipei101-focus.jpg",
                        3,
                        List.of(4, 0, 7, 2, 8, 1, 5, 3, 6),
                        "NORMAL",
                        45,
                        issuedAt,
                        expiresAt,
                        List.of(
                                new PuzzleChallengeService.LandmarkOption(1L, "台北101"),
                                new PuzzleChallengeService.LandmarkOption(2L, "國立故宮博物院"),
                                new PuzzleChallengeService.LandmarkOption(5L, "國家歌劇院"),
                                new PuzzleChallengeService.LandmarkOption(12L, "龍虎塔")
                        )
                );

        when(sceneRepository.findById(1L)).thenReturn(Optional.of(taipei101));
        when(stageRegistry.findByLandmarkId(1L))
                .thenReturn(Optional.of(new LandmarkStageDefinition(1L, 1L, 1)));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(taipei));
        when(puzzleService.issue(eq(7L), eq(1L), eq("NORMAL"), anyString()))
                .thenReturn(puzzleChallenge);

        MysteryChallengeService service = new MysteryChallengeService(
                sceneRepository,
                cityRepository,
                stageRegistry,
                stageService,
                new LandmarkChallengePoolRegistry(),
                quizService,
                explorationService,
                imageService,
                puzzleService,
                Clock.fixed(issuedAt, ZoneOffset.UTC),
                bound -> bound - 1
        );

        MysteryChallengeService.MysteryChallengeResponse first =
                service.start(7L, 1L, "NORMAL");
        MysteryChallengeService.MysteryChallengeResponse repeated =
                service.start(7L, 1L, "NORMAL");

        assertEquals(MysteryChallengeType.PUZZLE, first.challengeType());
        assertEquals("景點拼圖", first.challengeTitle());
        assertEquals(first.challengeSessionId(), repeated.challengeSessionId());
        assertSame(puzzleChallenge, first.challengeData());
        verify(puzzleService).issue(7L, 1L, "NORMAL", first.challengeSessionId());
        verifyNoInteractions(quizService, explorationService, imageService);

        ImageRecognitionService.ImageChallengeView imageChallenge =
                new ImageRecognitionService.ImageChallengeView(
                        "IMG-after-puzzle", 1L,
                        "觀察局部照片", "/images/challenges/taipei101-focus.jpg",
                        "BLUR", 6, "NORMAL", 5, issuedAt, issuedAt.plusSeconds(5),
                        List.of(
                                new ImageRecognitionService.SceneOption(1L, "台北101"),
                                new ImageRecognitionService.SceneOption(2L, "國立故宮博物院"),
                                new ImageRecognitionService.SceneOption(5L, "國家歌劇院"),
                                new ImageRecognitionService.SceneOption(12L, "龍虎塔")
                        )
                );
        when(imageService.issue(7L, 1L, "NORMAL")).thenReturn(imageChallenge);

        service.markConsumed(7L, puzzleChallenge.challengeId());
        MysteryChallengeService.MysteryChallengeResponse next =
                service.start(7L, 1L, "NORMAL");

        assertNotEquals(first.challengeSessionId(), next.challengeSessionId());
        assertEquals(MysteryChallengeType.IMAGE_RECOGNITION, next.challengeType());
    }
}
