const SESSION_KEY = "travelAdventureApiSession";
    const TUTORIAL_KEY = "tutorialCompleted";
    const DIFFICULTIES = {
      CASUAL: { label: "休閒", seconds: 10, lives: 5, multiplier: 1 },
      NORMAL: { label: "一般", seconds: 5, lives: 3, multiplier: 1.2 },
      EXTREME: { label: "極限挑戰", seconds: 3, lives: 1, multiplier: 1.5 }
    };
    let selectedDifficulty = "NORMAL";
    let difficultyLocked = false;
    let activeQuizQuestion = null;
    let session = loadSession();
    let appState = null;
    let missionsState = null;
    let achievementsState = null;
    let activeCityId = null;
    let journeyView = "map";
    let logs = [];
    let quizTimerId = null;
    let quizTimerTarget = null;
    let activeSceneQuizId = null;
    let activeBossQuizCityId = null;
    let answerSubmitting = false;
    let answerCombo = 0;
    let cityLives = DIFFICULTIES[selectedDifficulty].lives;
    let cityFailedPending = false;
    let cityHadFailedAttempt = false;
    let cityBattleStats = emptyCityBattleStats();
    let finalEndingShown = false;
    let collectionState = null;
    let collectionTab = "landmarks";
    let selectedCollectionId = null;
    let explorationState = createExplorationState();
    let imageRecognitionState = createImageRecognitionState();

    function createExplorationState() {
      return {
        mission: null,
        cityId: null,
        remainingActions: 4,
        discoveredClues: [],
        wrongGuesses: 0,
        reasoningStarted: false,
        guessCorrect: false,
        guessedScene: null,
        cultureChallenge: null,
        challengeStarted: false,
        completion: null,
        feedback: null,
        error: null,
        loading: false,
        submitting: false
      };
    }

    function createImageRecognitionState() {
      return {
        questionId: null,
        cityId: null,
        sceneId: null,
        prompt: null,
        imageUrl: null,
        displayMode: null,
        initialBlurLevel: 0,
        currentBlurLevel: 0,
        difficulty: null,
        issuedAt: null,
        expiresAt: null,
        candidates: [],
        timerId: null,
        remainingSeconds: 0,
        loading: false,
        submitting: false,
        expired: false,
        result: null,
        error: null
      };
    }

    function emptyCityBattleStats() {
      return {
        correctAnswers: 0,
        wrongAnswers: 0,
        timeoutCount: 0,
        maxCombo: 0,
        earnedExp: 0,
        earnedCoins: 0
      };
    }

    function difficultyConfig() {
      return DIFFICULTIES[selectedDifficulty];
    }

    function scaledReward(value) {
      return Math.round(Number(value || 0) * difficultyConfig().multiplier);
    }

    function loadSession() {
      try {
        return JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
      } catch {
        return null;
      }
    }

    function saveSession(nextSession) {
      session = nextSession;
      if (nextSession) {
        localStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
      } else {
        localStorage.removeItem(SESSION_KEY);
      }
    }
