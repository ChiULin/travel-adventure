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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MysteryChallengeServiceTest {

    @Test
    void imageRecognitionCanBeWrappedAndReusedByMysterySession() {
        SceneRepository sceneRepository = mock(SceneRepository.class);
        CityRepository cityRepository = mock(CityRepository.class);
        LandmarkStageRegistry stageRegistry = mock(LandmarkStageRegistry.class);
        LandmarkStageService stageService = mock(LandmarkStageService.class);
        QuizQuestionService quizService = mock(QuizQuestionService.class);
        ExplorationService explorationService = mock(ExplorationService.class);
        ImageRecognitionService imageService = mock(ImageRecognitionService.class);
        City taipei = City.builder().id(1L).unlockOrder(1).build();
        Scene taipei101 = Scene.builder().id(1L).city(taipei).build();
        Instant issuedAt = Instant.parse("2026-07-23T12:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(5);
        ImageRecognitionService.ImageChallengeView imageChallenge =
                new ImageRecognitionService.ImageChallengeView(
                        "IMG-focus", 1L,
                        "觀察局部照片", "/images/challenges/taipei101-focus.jpg",
                        "BLUR", 6, "NORMAL", 5, issuedAt, expiresAt,
                        List.of(
                                new ImageRecognitionService.SceneOption(1L, "台北101"),
                                new ImageRecognitionService.SceneOption(5L, "國家歌劇院"),
                                new ImageRecognitionService.SceneOption(12L, "龍虎塔"),
                                new ImageRecognitionService.SceneOption(17L, "澎湖跨海大橋")
                        )
                );

        when(sceneRepository.findById(1L)).thenReturn(Optional.of(taipei101));
        when(stageRegistry.findByLandmarkId(1L))
                .thenReturn(Optional.of(new LandmarkStageDefinition(1L, 1L, 1)));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(taipei));
        when(imageService.issue(7L, 1L, "NORMAL")).thenReturn(imageChallenge);

        MysteryChallengeService service = new MysteryChallengeService(
                sceneRepository,
                cityRepository,
                stageRegistry,
                stageService,
                new LandmarkChallengePoolRegistry(),
                quizService,
                explorationService,
                imageService,
                Clock.fixed(issuedAt, ZoneOffset.UTC),
                bound -> bound - 1
        );

        MysteryChallengeService.MysteryChallengeResponse first =
                service.start(7L, 1L, "NORMAL");
        MysteryChallengeService.MysteryChallengeResponse repeated =
                service.start(7L, 1L, "NORMAL");

        assertEquals(MysteryChallengeType.IMAGE_RECOGNITION, first.challengeType());
        assertEquals("重點圖片辨識", first.challengeTitle());
        assertEquals(first.challengeSessionId(), repeated.challengeSessionId());
        assertSame(imageChallenge, first.challengeData());
        verify(imageService).issue(7L, 1L, "NORMAL");
        verifyNoInteractions(quizService, explorationService);
    }
}
