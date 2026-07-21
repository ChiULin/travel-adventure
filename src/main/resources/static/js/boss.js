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

    async function submitBattleResult(city, rank) {
      return api(`/api/cities/${city.id}/battle-result`, {
        method: "POST",
        body: JSON.stringify({
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

    function finalEndingStats() {
      const user = appState.user || {};
      const cities = appState.cities || [];
      const completedCities = cities.filter(city => city.defeated).length;
      const totalScenes = cities.reduce((sum, city) => sum + Number(city.total || 0), 0);
      const doneScenes = cities.reduce((sum, city) => sum + Number(city.done || 0), 0);
      const badgeCount = cities.filter(city => city.badgeUnlocked).length;
      const bestCombo = cities.reduce((max, city) => Math.max(max, Number(city.bestCombo || 0)), 0);
      const sRankCount = cities.filter(city => city.bestRank === "S").length;
      return {
        user,
        cities,
        completedCities,
        totalCities: cities.length,
        totalScenes,
        doneScenes,
        badgeCount,
        bestCombo,
        sRankCount
      };
    }

    function shouldShowFinalEnding() {
      const stats = finalEndingStats();
      return stats.totalCities > 0 && stats.completedCities >= stats.totalCities;
    }

    function maybeShowFinalEnding() {
      if (!appState || finalEndingShown || !tutorialIsCompleted() || overlayIsOpen()) return;
      if (shouldShowFinalEnding()) {
        showFinalEnding();
      }
    }

    function showFinalEnding() {
      const stats = finalEndingStats();
      finalEndingShown = true;
      stopQuizTimer();
      const cityRoute = stats.cities.map((city, index) => `
        <div class="final-city" style="--d:${index * 0.16}s">
          <span>${escapeHtml(city.badgeIcon || "✓")}</span>
          <strong>${escapeHtml(city.name)}</strong>
          <span>✓</span>
        </div>
      `).join("");

      document.getElementById("finalEndingCard").innerHTML = `
        <section class="final-section final-hero">
          <h2>🏆 台灣旅行冒險<br>Congratulations！</h2>
          <p>你已完成台灣六座城市探索之旅。</p>
          <div class="final-checks">
            <div class="final-check">✓ 六座城市</div>
            <div class="final-check">✓ ${stats.totalScenes} 個景點</div>
            <div class="final-check">✓ 六位城市守護者</div>
            <div class="final-check">✓ ${stats.badgeCount} / 6 枚城市徽章</div>
          </div>
        </section>

        <section class="final-section">
          <div class="section-head">
            <h2>台灣旅程</h2>
            <strong>六城完成</strong>
          </div>
          <div class="final-route">${cityRoute}</div>
        </section>

        <section class="final-section">
          <div class="section-head">
            <h2>玩家統計</h2>
            <strong>${escapeHtml(stats.user.username || "旅行者")}</strong>
          </div>
          <div class="final-stats">
            <div class="final-stat"><span>等級</span>Lv.${stats.user.level || 1}</div>
            <div class="final-stat"><span>總 EXP</span>${formatNumber(stats.user.experience || stats.user.exp || 0)}</div>
            <div class="final-stat"><span>總金幣</span>${formatNumber(stats.user.coins || 0)}</div>
            <div class="final-stat"><span>最高 Combo</span>${stats.bestCombo}</div>
            <div class="final-stat"><span>S 評價</span>${stats.sRankCount} 座</div>
            <div class="final-stat"><span>徽章</span>${stats.badgeCount} / 6</div>
          </div>
        </section>

        <section class="final-section final-title">
          <span style="font-size:44px">🏅</span>
          <strong>最終稱號：台灣文化冒險家</strong>
          <p>「旅行的終點，不是抵達，而是更了解腳下的土地。」</p>
        </section>

        <section class="final-section">
          <div class="final-actions">
            <button class="btn full" id="finalChallengeBtn" type="button">重新挑戰</button>
            <button class="btn ghost full" id="finalHomeBtn" type="button">返回首頁</button>
            <button class="btn red full" id="finalRestartBtn" type="button">重新開始旅程</button>
          </div>
        </section>
      `;

      document.getElementById("finalEnding").classList.remove("hidden");
      document.getElementById("finalChallengeBtn").addEventListener("click", closeFinalEnding);
      document.getElementById("finalHomeBtn").addEventListener("click", () => {
        closeFinalEnding();
        window.scrollTo({ top: 0, behavior: "smooth" });
      });
      document.getElementById("finalRestartBtn").addEventListener("click", restartCompletedJourney);
    }

    function closeFinalEnding() {
      document.getElementById("finalEnding").classList.add("hidden");
      document.getElementById("finalEndingCard").innerHTML = "";
      renderCityDetail(activeCityId);
    }

    async function restartCompletedJourney() {
      const completedCities = (appState?.cities || []).filter(city => city.defeated);
      try {
        for (const city of completedCities) {
          await api(`/api/cities/${city.id}/restart`, { method: "POST" });
        }
        resetLocalBattleState();
        activeCityId = appState?.cities?.[0]?.id || activeCityId;
        finalEndingShown = false;
        closeFinalEnding();
        addLog("台灣旅行冒險已重新開始，歷史最佳紀錄會保留。");
        await refreshState();
      } catch (error) {
        addLog(error.message);
      }
    }

async function challengeBoss(answer, answerText) {
      stopQuizTimer();
      activeBossQuizCityId = null;
      const questionId = activeQuizQuestion?.questionId;
      activeQuizQuestion = null;
      const city = activeCity();
      try {
        const result = await api(`/api/cities/${city.id}/boss/challenge`, {
          method: "POST",
          body: JSON.stringify({ answer, answerText, questionId, difficulty: selectedDifficulty })
        });
        if (result.win) {
          registerCorrectAnswer({ expReward: result.earnedExp || 0, coinReward: result.earnedCoins || 0 });
          const rank = calculateBattleRank();
          let recordResult = null;
          try {
            recordResult = await submitBattleResult(city, rank);
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
        await refreshState();
      } catch (error) {
        addLog(error.message);
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
