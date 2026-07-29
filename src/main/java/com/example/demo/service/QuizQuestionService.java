package com.example.demo.service;

import com.example.demo.entity.City;
import com.example.demo.entity.Scene;
import com.example.demo.repository.CityRepository;
import com.example.demo.repository.SceneRepository;
import com.example.demo.stage.LandmarkStageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QuizQuestionService {
    private static final Duration LAST_QUESTION_RETENTION = Duration.ofMinutes(10);

    private final SceneRepository sceneRepository;
    private final CityRepository cityRepository;
    private final LandmarkStageService stageService;
    private final Map<String, IssuedQuestion> issuedQuestions = new ConcurrentHashMap<>();
    private final Map<String, LastQuestion> lastQuestionIds = new ConcurrentHashMap<>();
    private final int maxPendingQuestionsPerPlayer;
    private final Clock clock;
    private final Object issuanceMonitor = new Object();

    @Autowired
    public QuizQuestionService(
            SceneRepository sceneRepository,
            CityRepository cityRepository,
            LandmarkStageService stageService,
            @Value("${game.quiz.max-pending-per-player:5}") int maxPendingQuestionsPerPlayer) {
        this(sceneRepository, cityRepository, stageService,
                maxPendingQuestionsPerPlayer, Clock.systemUTC());
    }

    QuizQuestionService(SceneRepository sceneRepository, CityRepository cityRepository,
                        LandmarkStageService stageService,
                        int maxPendingQuestionsPerPlayer, Clock clock) {
        if (maxPendingQuestionsPerPlayer < 1) {
            throw new IllegalArgumentException("max pending questions per player must be positive");
        }
        this.sceneRepository = sceneRepository;
        this.cityRepository = cityRepository;
        this.stageService = stageService;
        this.maxPendingQuestionsPerPlayer = maxPendingQuestionsPerPlayer;
        this.clock = clock;
    }

    public Map<String, Object> randomSceneQuestion(Long userId, Long sceneId, String difficultyName) {
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("scene not found"));
        stageService.validateStageAvailable(userId, sceneId);
        List<Question> questions = sceneQuestions(scene);
        return issueQuestion(userId, sceneKey(userId, sceneId), questions, difficultyName, true);
    }

    public Map<String, Object> randomBossQuestion(Long userId, Long cityId, String difficultyName) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new IllegalArgumentException("city not found"));
        List<Question> questions = bossQuestions(city);
        return issueQuestion(userId, bossKey(userId, cityId), questions, difficultyName, false);
    }

    public boolean sceneAnswerCorrect(Long userId, Scene scene, String questionId, String answerText, String difficultyName) {
        validateIssuedQuestion(sceneKey(userId, scene.getId()), questionId, difficultyName);
        return answerCorrect(sceneQuestions(scene), questionId, answerText);
    }

    public boolean bossAnswerCorrect(Long userId, City city, String questionId, String answerText, String difficultyName) {
        validateIssuedQuestion(bossKey(userId, city.getId()), questionId, difficultyName);
        return answerCorrect(bossQuestions(city), questionId, answerText);
    }

    private Map<String, Object> issueQuestion(Long userId, String key, List<Question> questions,
                                              String difficultyName, boolean includeSubmissionGrace) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("question bank is empty");
        }
        GameDifficulty difficulty = GameDifficulty.from(difficultyName);
        Instant now = clock.instant();
        IssuedQuestion issued;
        Question selected;

        synchronized (issuanceMonitor) {
            removeExpiredState(now);
            if (!issuedQuestions.containsKey(key) && pendingQuestionCount(userId) >= maxPendingQuestionsPerPlayer) {
                throw new IllegalArgumentException("too many pending questions");
            }

            LastQuestion previous = lastQuestionIds.get(key);
            String previousId = previous == null ? null : previous.questionId();
            List<Question> candidates = questions.size() < 2 ? questions : questions.stream()
                    .filter(question -> !question.id().equals(previousId)).toList();
            selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

            long expirySeconds = difficulty.seconds() + (includeSubmissionGrace ? 2L : 0L);
            Instant expiresAt = now.plusSeconds(expirySeconds);
            issued = new IssuedQuestion(selected.id(), userId, difficulty, now, expiresAt);
            issuedQuestions.put(key, issued);
            lastQuestionIds.put(key, new LastQuestion(selected.id(), now.plus(LAST_QUESTION_RETENTION)));
        }

        Map<String, Object> dto = questionDto(selected);
        dto.put("difficulty", difficulty.name());
        dto.put("seconds", difficulty.seconds());
        dto.put("lives", difficulty.lives());
        dto.put("rewardPercent", difficulty.rewardPercent());
        dto.put("issuedAt", issued.issuedAt());
        dto.put("expiresAt", issued.expiresAt());
        return dto;
    }

    private void validateIssuedQuestion(String key, String questionId, String difficultyName) {
        IssuedQuestion issued = issuedQuestions.remove(key);
        GameDifficulty difficulty = GameDifficulty.from(difficultyName);
        if (issued == null || !issued.questionId().equals(questionId) || issued.difficulty() != difficulty) {
            throw new IllegalArgumentException("question challenge does not match");
        }
        if (!clock.instant().isBefore(issued.expiresAt())) {
            throw new IllegalArgumentException("question time expired");
        }
    }

    @Scheduled(fixedDelayString = "${game.quiz.cleanup-interval-ms:60000}")
    public void removeExpiredQuestions() {
        synchronized (issuanceMonitor) {
            removeExpiredState(clock.instant());
        }
    }

    private void removeExpiredState(Instant now) {
        issuedQuestions.forEach((key, issued) -> {
            if (!now.isBefore(issued.expiresAt())) {
                issuedQuestions.remove(key, issued);
            }
        });
        lastQuestionIds.forEach((key, previous) -> {
            if (!now.isBefore(previous.expiresAt())) {
                lastQuestionIds.remove(key, previous);
            }
        });
    }

    private long pendingQuestionCount(Long userId) {
        return issuedQuestions.values().stream()
                .filter(question -> question.userId().equals(userId))
                .count();
    }

    private String sceneKey(Long userId, Long sceneId) {
        return "scene:%d:%d".formatted(userId, sceneId);
    }

    private String bossKey(Long userId, Long cityId) {
        return "boss:%d:%d".formatted(userId, cityId);
    }

    private boolean answerCorrect(List<Question> questions, String questionId, String answerText) {
        if (questionId == null || answerText == null || answerText.isBlank()) {
            return false;
        }
        return questions.stream()
                .filter(question -> question.id().equals(questionId))
                .findFirst()
                .map(question -> question.correctText().trim().equals(answerText.trim()))
                .orElseThrow(() -> new IllegalArgumentException("invalid question"));
    }

    private List<Question> sceneQuestions(Scene scene) {
        List<Scene> allScenes = sceneRepository.findAll();
        List<City> allCities = cityRepository.findAllByOrderByUnlockOrderAsc();
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("scene-%d-fact".formatted(scene.getId()), scene.getQuizQuestion(),
                options(scene.getQuizOptionA(), scene.getQuizOptionB(), scene.getQuizOptionC(), scene.getQuizOptionD()),
                sceneOptionText(scene), scene.getQuizExplanation()));
        questions.add(new Question("scene-%d-city".formatted(scene.getId()),
                "「%s」位於哪一座城市？".formatted(scene.getName()),
                choices(scene.getCity().getName(), allCities.stream().map(City::getName).toList()),
                scene.getCity().getName(), "這個景點屬於「%s」。".formatted(scene.getCity().getName())));
        questions.add(new Question("scene-%d-identify".formatted(scene.getId()),
                "根據介紹判斷景點：%s".formatted(shortDescription(scene)),
                choices(scene.getName(), allScenes.stream().map(Scene::getName).toList()),
                scene.getName(), scene.getQuizExplanation()));
        return questions.stream().filter(Question::valid).toList();
    }

    private List<Question> bossQuestions(City city) {
        List<City> cities = cityRepository.findAllByOrderByUnlockOrderAsc();
        List<Scene> cityScenes = sceneRepository.findByCityId(city.getId());
        List<Scene> allScenes = sceneRepository.findAll();
        List<Question> questions = new ArrayList<>();
        questions.add(new Question("boss-%d-fact".formatted(city.getId()), city.getBossQuestion(),
                options(city.getBossOptionA(), city.getBossOptionB(), city.getBossOptionC(), city.getBossOptionD()),
                bossOptionText(city), "守護者考驗的是你對城市故事的理解。"));
        questions.add(new Question("boss-%d-badge".formatted(city.getId()),
                "完成「%s」後會獲得哪一枚城市徽章？".formatted(city.getName()),
                choices(city.getBadgeName(), cities.stream().map(City::getBadgeName).toList()),
                city.getBadgeName(), "這是「%s」的城市徽章。".formatted(city.getName())));
        questions.add(new Question("boss-%d-guardian".formatted(city.getId()),
                "哪一位守護者鎮守「%s」？".formatted(city.getName()),
                choices(city.getBossName(), cities.stream().map(City::getBossName).toList()),
                city.getBossName(), "「%s」是這座城市的守護者。".formatted(city.getBossName())));
        if (!cityScenes.isEmpty()) {
            Scene selected = cityScenes.get(0);
            questions.add(new Question("boss-%d-landmark-%d".formatted(city.getId(), selected.getId()),
                    "下列哪個景點屬於「%s」？".formatted(city.getName()),
                    choices(selected.getName(), allScenes.stream().map(Scene::getName).toList()),
                    selected.getName(), "「%s」是%s的景點。".formatted(selected.getName(), city.getName())));
            Scene storyScene = cityScenes.size() > 1 ? cityScenes.get(1) : selected;
            questions.add(new Question("boss-%d-story-%d".formatted(city.getId(), storyScene.getId()),
                    "哪個景點最符合這段線索：%s".formatted(shortDescription(storyScene)),
                    choices(storyScene.getName(), allScenes.stream().map(Scene::getName).toList()),
                    storyScene.getName(), storyScene.getQuizExplanation()));
        }
        return questions.stream().filter(Question::valid).toList();
    }

    private Map<String, Object> questionDto(Question question) {
        List<String> shuffled = new ArrayList<>(question.options());
        Collections.shuffle(shuffled);
        Map<String, String> options = new LinkedHashMap<>();
        String[] labels = {"A", "B", "C", "D"};
        for (int index = 0; index < shuffled.size() && index < labels.length; index++) {
            options.put(labels[index], shuffled.get(index));
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("questionId", question.id());
        dto.put("question", question.text());
        dto.put("options", options);
        dto.put("explanation", question.explanation());
        return dto;
    }

    private List<String> choices(String correct, List<String> candidates) {
        List<String> distractors = new ArrayList<>(candidates.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !value.equals(correct)).distinct().toList());
        Collections.shuffle(distractors);
        List<String> choices = new ArrayList<>();
        choices.add(correct);
        choices.addAll(distractors.stream().limit(3).toList());
        Collections.shuffle(choices);
        return choices;
    }

    private List<String> options(String... values) {
        return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private String sceneOptionText(Scene scene) {
        return optionText(scene.getQuizCorrectAnswer(), scene.getQuizOptionA(), scene.getQuizOptionB(),
                scene.getQuizOptionC(), scene.getQuizOptionD());
    }

    private String bossOptionText(City city) {
        return optionText(city.getBossCorrectAnswer(), city.getBossOptionA(), city.getBossOptionB(),
                city.getBossOptionC(), city.getBossOptionD());
    }

    private String optionText(String answer, String a, String b, String c, String d) {
        return switch (answer == null ? "" : answer.trim().toUpperCase()) {
            case "A" -> a;
            case "B" -> b;
            case "C" -> c;
            case "D" -> d;
            default -> null;
        };
    }

    private String shortDescription(Scene scene) {
        String value = scene.getDescription();
        if (value == null || value.isBlank()) {
            value = scene.getStory();
        }
        if (value == null || value.isBlank()) {
            return scene.getName();
        }
        return value.length() > 90 ? value.substring(0, 90) + "..." : value;
    }

    private record Question(String id, String text, List<String> options, String correctText, String explanation) {
        private boolean valid() {
            return text != null && !text.isBlank() && correctText != null && !correctText.isBlank()
                    && options != null && options.size() >= 4 && options.contains(correctText);
        }
    }

    private record IssuedQuestion(String questionId, Long userId, GameDifficulty difficulty,
                                  Instant issuedAt, Instant expiresAt) {
    }

    private record LastQuestion(String questionId, Instant expiresAt) {
    }
}
