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
    let logs = [];
    let quizTimerId = null;
    let quizTimerTarget = null;
    let activeSceneQuizId = null;
    let activeBossQuizCityId = null;
    let answerCombo = 0;
    let cityLives = DIFFICULTIES[selectedDifficulty].lives;
    let cityFailedPending = false;
    let cityHadFailedAttempt = false;
    let cityBattleStats = emptyCityBattleStats();
    let finalEndingShown = false;
    let collectionState = null;
    let collectionTab = "landmarks";
    let selectedCollectionId = null;
    let explorationMission = null;
    let explorationResult = null;
    let explorationError = null;
    let explorationLoading = false;
    let explorationSubmitting = false;

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
