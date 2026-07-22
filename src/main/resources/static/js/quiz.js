function stopQuizTimer() {
      if (quizTimerId) {
        clearInterval(quizTimerId);
      }
      quizTimerId = null;
      quizTimerTarget = null;
    }

    function updateQuizTimers(seconds) {
      document.querySelectorAll("[data-quiz-timer]").forEach(timer => {
        timer.textContent = `剩餘 ${seconds} 秒`;
        timer.classList.toggle("warning", seconds <= 2);
      });
    }

    function startQuizTimer(type, targetId) {
      stopQuizTimer();
      if (overlayIsOpen()) return;
      quizTimerTarget = { type, targetId };
      let seconds = Number(activeQuizQuestion?.seconds || difficultyConfig().seconds);
      updateQuizTimers(seconds);
      quizTimerId = setInterval(() => {
        seconds -= 1;
        updateQuizTimers(Math.max(seconds, 0));
        if (seconds <= 0) {
          handleQuizTimeout();
        }
      }, 1000);
    }

    function startActiveQuizTimer() {
      if (activeSceneQuizId && document.querySelector(`[data-scene-id="${activeSceneQuizId}"]:not(:disabled)`)) {
        startQuizTimer("scene", activeSceneQuizId);
        return;
      }
      if (activeBossQuizCityId && document.querySelector(`[data-boss-city-id="${activeBossQuizCityId}"]:not(:disabled)`)) {
        startQuizTimer("boss", activeBossQuizCityId);
        return;
      }
      stopQuizTimer();
    }

    async function startSceneQuiz(sceneId) {
      if (cityLives <= 0) {
        showCityFailedResult();
        return;
      }
      try {
        activeQuizQuestion = await api(`/api/quizzes/landmarks/${sceneId}/random?difficulty=${selectedDifficulty}`);
        difficultyLocked = true;
        activeSceneQuizId = sceneId;
        activeBossQuizCityId = null;
        renderCityDetail(activeCityId);
      } catch (error) {
        addLog(error.message);
      }
    }

    async function startBossQuiz(cityId) {
      if (cityLives <= 0) {
        showCityFailedResult();
        return;
      }
      try {
        const battle = await api(`/api/cities/${cityId}/boss/start`, {
          method: "POST",
          body: JSON.stringify({ difficulty: selectedDifficulty, foodKey: null })
        });
        activeQuizQuestion = battle.question;
        difficultyLocked = true;
        activeBossQuizCityId = cityId;
        activeSceneQuizId = null;
        renderCityDetail(activeCityId);
      } catch (error) {
        addLog(error.message);
      }
    }

    function disableVisibleQuizOptions() {
      document.querySelectorAll(".quiz-option").forEach(button => {
        button.disabled = true;
      });
    }

    function handleQuizTimeout() {
      const target = quizTimerTarget;
      stopQuizTimer();
      disableVisibleQuizOptions();
      if (target?.type === "scene") {
        const city = activeCity();
        const scene = city?.scenes?.find(item => item.id === target.targetId);
        activeSceneQuizId = null;
        activeQuizQuestion = null;
        cityBattleStats.timeoutCount += 1;
        registerWrongAnswer();
        if (cityLives <= 0) {
          showCityFailedResult();
          addLog(`${city?.name || "城市"}生命歸零，關卡失敗。`);
          return;
        }
        showWrongAnswerResult(scene, "時間到！");
        addLog(`${scene?.name || "題目"}時間到，探索失敗。`);
        return;
      }
      if (target?.type === "boss") {
        const city = activeCity();
        activeBossQuizCityId = null;
        activeQuizQuestion = null;
        cityBattleStats.timeoutCount += 1;
        registerWrongAnswer();
        if (cityLives <= 0) {
          showCityFailedResult();
          addLog(`${city?.name || "城市"}生命歸零，關卡失敗。`);
          return;
        }
        showBossFailedResult(city, "時間到！");
        addLog(`${city?.bossName || "守護者"}時間到，挑戰失敗。`);
      }
    }

async function checkin(sceneId, answer, answerText) {
      stopQuizTimer();
      activeSceneQuizId = null;
      const questionId = activeQuizQuestion?.questionId;
      activeQuizQuestion = null;
      const city = activeCity();
      const scene = city?.scenes?.find(item => item.id === sceneId);
      try {
        const result = await api("/api/checkins", {
          method: "POST",
          body: JSON.stringify({ sceneId, answer, answerText, questionId, difficulty: selectedDifficulty })
        });
        if (!result.ok) {
          registerWrongAnswer();
          if (cityLives <= 0) {
            showCityFailedResult();
            addLog(`${city?.name || "城市"}生命歸零，關卡失敗。`);
            return;
          }
          showWrongAnswerResult(scene);
          addLog("答錯了，再想想看。");
          return;
        }
        const rewardedScene = {
          ...scene,
          expReward: result.earnedExp ?? scaledReward(scene?.expReward),
          coinReward: result.earnedCoins ?? scaledReward(scene?.coinReward)
        };
        registerCorrectAnswer(rewardedScene);
        if (city && scene) {
          showSceneResult(city, rewardedScene);
        }
        addLog(`${scene?.name || "景點"}探索完成。`);
        await refreshState();
      } catch (error) {
        addLog(error.message);
      }
    }
