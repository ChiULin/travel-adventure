function renderBattleStatus() {
      const comboElement = document.getElementById("combo-display");
      const livesElement = document.getElementById("lives-display");
      if (comboElement) {
        comboElement.textContent = `🔥 Combo ×${answerCombo}`;
        comboElement.classList.toggle("hot", answerCombo > 0);
      }
      if (livesElement) {
        livesElement.textContent = cityLives > 0 ? "❤️".repeat(cityLives) : "💔 生命歸零";
        livesElement.classList.toggle("empty", cityLives <= 0);
      }
    }

    function showBattleFloat(text) {
      const float = document.createElement("div");
      float.className = "battle-float";
      float.textContent = text;
      document.body.appendChild(float);
      setTimeout(() => float.remove(), 1000);
    }

    function flashCombo() {
      const comboElement = document.getElementById("combo-display");
      if (!comboElement) return;
      comboElement.classList.remove("hot");
      void comboElement.offsetWidth;
      comboElement.classList.add("hot");
    }

    function shakeLives() {
      const livesElement = document.getElementById("lives-display");
      if (!livesElement) return;
      livesElement.classList.remove("damaged");
      void livesElement.offsetWidth;
      livesElement.classList.add("damaged");
    }

    function flyExpReward(scene) {
      const summary = document.getElementById("player-summary");
      if (!summary || !scene) return;
      const rect = summary.getBoundingClientRect();
      const fly = document.createElement("div");
      fly.className = "exp-fly";
      fly.textContent = `EXP +${scene.expReward || 0}`;
      fly.style.left = `${rect.left + rect.width - 86}px`;
      fly.style.top = `${rect.top + 56}px`;
      document.body.appendChild(fly);
      setTimeout(() => fly.remove(), 900);
    }

    function registerCorrectAnswer(scene) {
      answerCombo += 1;
      cityBattleStats.correctAnswers += 1;
      cityBattleStats.maxCombo = Math.max(cityBattleStats.maxCombo, answerCombo);
      cityBattleStats.earnedExp += scene?.expReward || 0;
      cityBattleStats.earnedCoins += scene?.coinReward || 0;
      renderBattleStatus();
      flashCombo();
      flyExpReward(scene);
      showBattleFloat(answerCombo >= 3 ? "🔥 PERFECT COMBO" : "Combo +1");
    }

    function registerWrongAnswer() {
      answerCombo = 0;
      cityBattleStats.wrongAnswers += 1;
      cityLives = Math.max(0, cityLives - 1);
      renderBattleStatus();
      shakeLives();
    }

    function resetCityBattleStats(options = {}) {
      cityBattleStats = emptyCityBattleStats();
      if (!options.keepFailedAttempt) {
        cityHadFailedAttempt = false;
      }
    }

    function resetLocalBattleState() {
      activeSceneQuizId = null;
      activeBossQuizCityId = null;
      activeQuizQuestion = null;
      difficultyLocked = false;
      answerCombo = 0;
      cityLives = difficultyConfig().lives;
      resetCityBattleStats();
      stopQuizTimer();
    }

    function calculateBattleRank() {
      if (
        !cityHadFailedAttempt &&
        cityLives === difficultyConfig().lives &&
        cityBattleStats.wrongAnswers === 0 &&
        cityBattleStats.timeoutCount === 0
      ) {
        return "S";
      }
      if (cityHadFailedAttempt) return "C";
      if (cityLives >= Math.max(2, Math.ceil(difficultyConfig().lives * 0.66))) return "A";
      if (cityLives >= 1) return "B";
      return "C";
    }

    function showCityFailedResult() {
      activeSceneQuizId = null;
      activeBossQuizCityId = null;
      cityFailedPending = true;
      cityHadFailedAttempt = true;
      stopQuizTimer();
      showResultCard(`
        <h2>城市關卡失敗</h2>
        <h3>${escapeHtml(activeCity()?.name || "城市")}生命歸零</h3>
        <div class="result-highlight failed">Combo 已重置，請重新整備後再挑戰。</div>
        <p>回到城市頁後生命會恢復為 3 點，再次選擇景點開始挑戰。</p>
      `, "failed");
    }

    function showWrongAnswerResult(scene, title = "答題失敗") {
      showResultCard(`
        <h2>${escapeHtml(title)}</h2>
        <h3>${escapeHtml(scene?.name || "景點")}尚未完成</h3>
        <div class="result-highlight failed">沒有獲得 EXP 與金幣</div>
        <p>再閱讀一次景點故事，找出最符合線索的答案。</p>
      `, "failed");
    }

    function showSceneResult(city, scene) {
      const cityProgress = progress(city);
      const nextDone = Math.min(cityProgress.done + 1, cityProgress.total);
      showResultCard(`
        <h2>答題成功！</h2>
        <h3>${escapeHtml(scene.name)}探索完成</h3>
        <div class="result-rewards">
          <div class="result-reward">EXP +${scene.expReward || 0}</div>
          <div class="result-reward">金幣 +${scene.coinReward || 0}</div>
        </div>
        <div class="result-highlight">目前城市進度：${nextDone} / ${cityProgress.total}</div>
      `);
    }

    function showBossFailedResult(city, title = "守護者挑戰失敗") {
      showResultCard(`
        <h2>${escapeHtml(title)}</h2>
        <h3>${escapeHtml(city?.bossName || "城市守護者")} 擋下了這次挑戰</h3>
        <div class="result-highlight failed">徽章尚未解鎖</div>
        <p>完成所有景點後，重新確認城市故事與題目線索，再次挑戰守護者。</p>
      `, "failed");
    }

    function showBossResult(city, recordResult = null) {
      const badge = badgeParts(city);
      const nextCity = nextCityAfter(city);
      const itemProgress = progress(city);
      const rank = recordResult?.rank || calculateBattleRank();
      const newRecord = Boolean(recordResult?.newRecord);
      showResultCard(`
        <h2>城市關卡完成！</h2>
        <h3>城市：${escapeHtml(city?.name || "城市")}</h3>
        <div class="result-highlight">評價：${rank}</div>
        ${newRecord ? `<div class="result-highlight">NEW RECORD</div>` : ""}
        <div class="result-rewards">
          <div class="result-reward">完成景點：${itemProgress.done} / ${itemProgress.total}</div>
          <div class="result-reward">最高 Combo：${cityBattleStats.maxCombo}</div>
          <div class="result-reward">剩餘生命：${cityLives}</div>
          <div class="result-reward">答對題數：${cityBattleStats.correctAnswers}</div>
          <div class="result-reward">答錯題數：${cityBattleStats.wrongAnswers}</div>
          <div class="result-reward">時間到：${cityBattleStats.timeoutCount}</div>
          <div class="result-reward">本次 EXP：${cityBattleStats.earnedExp}</div>
          <div class="result-reward">本次金幣：${cityBattleStats.earnedCoins}</div>
        </div>
        <p>獲得徽章：</p>
        <div class="result-highlight">${escapeHtml(city.badgeIcon || "🏅")} ${escapeHtml(badge.name)}</div>
        ${badge.quote ? `<p class="result-quote">「${escapeHtml(badge.quote)}」</p>` : ""}
        ${nextCity ? `
          <div class="result-highlight">下一座城市：${escapeHtml(nextCity.name)}<br>已解鎖</div>
        ` : `
          <div class="result-highlight">六座城市探索完成！</div>
        `}
      `);
    }

    async function submitBattleResult(city, rank, battleResultToken) {
      return api(`/api/cities/${city.id}/battle-result`, {
        method: "POST",
        body: JSON.stringify({
          battleResultToken,
          rank,
          maxCombo: cityBattleStats.maxCombo,
          remainingLives: cityLives,
          correctAnswers: cityBattleStats.correctAnswers,
          wrongAnswers: cityBattleStats.wrongAnswers,
          timeoutCount: cityBattleStats.timeoutCount,
          earnedExp: cityBattleStats.earnedExp,
          earnedCoins: cityBattleStats.earnedCoins,
          difficulty: selectedDifficulty
        })
      });
    }

    function shouldShowFinalEnding() {
      return appState?.journeyCompleted === true;
    }

    function maybeShowFinalEnding() {
      if (!appState || finalEndingShown || cityStageTransitionPlaying
          || pendingCityStageTransition || !tutorialIsCompleted() || overlayIsOpen()) return;
      if (shouldShowFinalEnding()) {
        showFinalEnding();
      }
    }

    function showFinalEnding() {
      const user = appState.user || {};
      const cities = appState.cities || [];
      finalEndingShown = true;
      stopQuizTimer();
      const cityRoute = cities.map((city, index) => `
        <div class="final-city" style="--d:${index * 0.16}s">
          <span>${escapeHtml(city.badgeIcon || "✓")}</span>
          <strong>${escapeHtml(city.name)}</strong>
          <span>✓</span>
        </div>
      `).join("");

      document.getElementById("finalEndingCard").innerHTML = `
        <section class="final-section final-hero">
          <h2>🏆 恭喜完成臺灣六城文化冒險！</h2>
          <p>六座城市的文化旅程已全部完成。</p>
          <div class="final-checks">
            <div class="final-check">完成城市：${appState.completedCityCount} / ${appState.totalCityCount}</div>
            <div class="final-check">完成景點：${appState.completedLandmarkCount} / ${appState.totalLandmarkCount}</div>
            <div class="final-check">取得徽章：${appState.badgeCount} / ${appState.totalBadgeCount}</div>
          </div>
        </section>

        <section class="final-section">
          <div class="section-head">
            <h2>臺灣旅程</h2>
            <strong>${appState.completedCityCount} / ${appState.totalCityCount} 城完成</strong>
          </div>
          <div class="final-route">${cityRoute}</div>
        </section>

        <section class="final-section">
          <div class="section-head">
            <h2>玩家統計</h2>
            <strong>${escapeHtml(user.username || "旅行者")}</strong>
          </div>
          <div class="final-stats">
            <div class="final-stat"><span>最終等級</span>Lv.${user.level || 1}</div>
            <div class="final-stat"><span>累積金幣</span>${formatNumber(user.coins || 0)}</div>
            <div class="final-stat"><span>完成城市</span>${appState.completedCityCount} / ${appState.totalCityCount}</div>
            <div class="final-stat"><span>完成景點</span>${appState.completedLandmarkCount} / ${appState.totalLandmarkCount}</div>
            <div class="final-stat"><span>取得徽章</span>${appState.badgeCount} / ${appState.totalBadgeCount}</div>
          </div>
        </section>

        <section class="final-section final-title">
          <span style="font-size:44px">🏅</span>
          <strong>最終稱號：臺灣文化冒險家</strong>
          <p>「旅行的終點，不是抵達，而是更了解腳下的土地。」</p>
        </section>

        <section class="final-section">
          <div class="final-actions">
            <button class="btn full" id="finalCollectionBtn" type="button">查看完整收藏</button>
            <button class="btn red full" id="finalChallengeBtn" type="button">重新挑戰 Boss</button>
            <button class="btn ghost full" id="finalMapBtn" type="button">回到城市地圖</button>
          </div>
        </section>
      `;

      document.getElementById("finalEnding").classList.remove("hidden");
      const collectionButton = document.getElementById("finalCollectionBtn");
      collectionButton.addEventListener("click", () => runWithButtonLock(collectionButton, async () => {
        closeFinalEnding();
        await openCollection();
      }));
      const challengeButton = document.getElementById("finalChallengeBtn");
      challengeButton.addEventListener("click", () =>
        runWithButtonLock(challengeButton, replayFinalBoss)
      );
      document.getElementById("finalMapBtn").addEventListener("click", () => {
        closeFinalEnding();
        openTaiwanMapView();
      });
    }

    function closeFinalEnding() {
      document.getElementById("finalEnding").classList.add("hidden");
      document.getElementById("finalEndingCard").innerHTML = "";
      renderCityDetail(activeCityId);
    }

    async function replayFinalBoss() {
      const finalCity = [...(appState?.cities || [])]
        .sort((first, second) => Number(second.unlockOrder) - Number(first.unlockOrder))[0];
      if (!finalCity) return;
      closeFinalEnding();
      openCityStageView(finalCity);
      await startBossQuiz(finalCity.id);
    }

async function challengeBoss(answer, answerText) {
      if (answerSubmitting) return;
      answerSubmitting = true;
      stopQuizTimer();
      const questionId = activeQuizQuestion?.questionId;
      const city = activeCity();
      disableVisibleQuizOptions();
      try {
        const result = await api(`/api/cities/${city.id}/boss/challenge`, {
          method: "POST",
          body: JSON.stringify({
            answer,
            answerText,
            questionId,
            difficulty: selectedDifficulty
          })
        });
        activeBossQuizCityId = null;
        activeQuizQuestion = null;
        if (result.win) {
          registerCorrectAnswer({ expReward: result.earnedExp || 0, coinReward: result.earnedCoins || 0 });
          const rank = calculateBattleRank();
          let recordResult = null;
          try {
            recordResult = await submitBattleResult(city, rank, result.battleResultToken);
          } catch (error) {
            addLog(`最佳紀錄儲存失敗：${error.message}`);
          }
          showBossResult(city, recordResult);
          addLog(`成功擊敗 ${city.bossName}。`);
        } else {
          registerWrongAnswer();
          if (cityLives <= 0) {
            showCityFailedResult();
            addLog(`${city?.name || "城市"}生命歸零，關卡失敗。`);
            return;
          }
          showBossFailedResult(city);
          addLog(`${city.bossName} 太強了，請提升實力後再挑戰。`);
        }
        if (result.win) {
          await refreshCityMapWithAnimation(city.id);
        } else {
          await refreshState();
        }
      } catch (error) {
        addLog(error.message);
      } finally {
        answerSubmitting = false;
      }
    }

    async function restartCity(cityId) {
      const city = appState?.cities.find(item => item.id === cityId);
      try {
        await api(`/api/cities/${cityId}/restart`, { method: "POST" });
        resetLocalBattleState();
        addLog(`${city?.name || "城市"}已重新開啟挑戰，最佳紀錄會保留。`);
        await refreshState();
      } catch (error) {
        addLog(error.message);
      }
    }
