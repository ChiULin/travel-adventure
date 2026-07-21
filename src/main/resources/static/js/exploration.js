async function loadExplorationMission(cityId = 3) {
      explorationState = createExplorationState();
      explorationState.loading = true;
      renderExplorationMission();
      try {
        explorationState.mission = await api(`/api/explorations/cities/${cityId}/random`);
      } catch (error) {
        explorationState.error = error.message;
      } finally {
        explorationState.loading = false;
        renderExplorationMission();
      }
    }

    function renderExplorationMission() {
      const container = document.getElementById("exploration-mission");
      if (!container || !session?.token) return;
      const tainanUnlocked = appState?.cities?.some(city => city.id === 3 && city.unlocked);
      if (!tainanUnlocked && !explorationState.completion) {
        container.innerHTML = "";
        return;
      }

      if (explorationState.loading) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">台南旅行委託 · 試作版</span>
            <h2>正在接收旅行委託…</h2>
          </div>
        `;
        return;
      }

      if (explorationState.error) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">台南旅行委託 · 試作版</span>
            <h2>目前無法取得探索任務</h2>
            <p>${escapeHtml(explorationState.error)}</p>
            <button class="btn ghost" id="retryExplorationBtn" type="button">重新取得任務</button>
          </div>
        `;
        document.getElementById("retryExplorationBtn").addEventListener("click", () => loadExplorationMission());
        return;
      }

      if (explorationState.completion) {
        renderExplorationCompletion(container);
        return;
      }

      const mission = explorationState.mission;
      if (!mission) {
        container.innerHTML = "";
        return;
      }

      const feedback = explorationState.feedback ? `
        <div class="exploration-result ${explorationState.feedback.type}" role="status">
          <strong>${escapeHtml(explorationState.feedback.title)}</strong>
          <span>${escapeHtml(explorationState.feedback.message)}</span>
        </div>
      ` : "";

      container.innerHTML = `
        <article class="exploration-card">
          <span class="exploration-kicker">台南旅行委託 · 試作版</span>
          <div class="section-head">
            <div>
              <h2>${escapeHtml(mission.title)}</h2>
              <p>${escapeHtml(mission.description)}</p>
            </div>
            <span class="status-pill ${explorationState.guessCorrect ? "challenge" : "active"}">
              ${explorationState.guessCorrect ? "文化確認" : "調查中"}
            </span>
          </div>
          ${explorationState.guessCorrect ? renderCultureChallengeStage() : renderGuessStage(mission)}
          ${feedback}
        </article>
      `;

      document.querySelectorAll("[data-exploration-scene-id]").forEach(button => {
        button.addEventListener("click", () => submitExplorationGuess(Number(button.dataset.explorationSceneId)));
      });
      document.querySelectorAll("[data-culture-answer]").forEach(button => {
        button.addEventListener("click", () => submitCultureChallenge(button.dataset.cultureAnswer));
      });
      document.getElementById("startCultureChallengeBtn")?.addEventListener("click", startCultureChallenge);
      document.getElementById("retryCultureChallengeBtn")?.addEventListener("click", retryCultureChallenge);
    }

    function renderGuessStage(mission) {
      return `
        <div class="exploration-clues">
          ${mission.clues.map((clue, index) => `
            <div class="exploration-clue">
              <span>線索 ${index + 1}</span>
              <strong>${escapeHtml(clue)}</strong>
            </div>
          `).join("")}
        </div>
        <div class="exploration-guess">
          <h3>你認為是哪裡？</h3>
          <div class="exploration-candidates">
            ${mission.candidates.map(candidate => `
              <button class="btn ghost" type="button" data-exploration-scene-id="${candidate.sceneId}"
                      ${explorationState.submitting ? "disabled" : ""}>
                ${escapeHtml(candidate.name)}
              </button>
            `).join("")}
          </div>
        </div>
      `;
    }

    function renderCultureChallengeStage() {
      const sceneName = explorationState.guessedScene?.sceneName || "安平古堡";
      const challenge = explorationState.cultureChallenge;
      if (!challenge) {
        return `
          <div class="exploration-stage">
            <h3>文化挑戰尚未完成</h3>
            <p>這次題目已失效，重新取得題目後才能正式打卡。</p>
            <button class="btn full" id="retryCultureChallengeBtn" type="button"
                    ${explorationState.submitting ? "disabled" : ""}>重新取得文化挑戰</button>
          </div>
        `;
      }
      if (!explorationState.challengeStarted) {
        return `
          <div class="exploration-stage correct">
            <h3>推理成功！</h3>
            <p>你成功找到了「${escapeHtml(sceneName)}」。完成文化小挑戰後，才會正式打卡並獲得獎勵。</p>
            <button class="btn full" id="startCultureChallengeBtn" type="button">開始文化挑戰</button>
          </div>
        `;
      }
      return `
        <div class="exploration-stage culture">
          <span class="exploration-kicker">文化小挑戰</span>
          <h3>${escapeHtml(challenge.question)}</h3>
          <div class="exploration-candidates culture-options">
            ${challenge.options.map((option, index) => `
              <button class="btn ghost" type="button" data-culture-answer="${escapeHtml(option)}"
                      ${explorationState.submitting ? "disabled" : ""}>
                ${String.fromCharCode(65 + index)}. ${escapeHtml(option)}
              </button>
            `).join("")}
          </div>
          <p class="exploration-expiry">本題為一次性挑戰，請在 ${formatDateTime(challenge.expiresAt)} 前完成。</p>
        </div>
      `;
    }

    function renderExplorationCompletion(container) {
      const completion = explorationState.completion;
      container.innerHTML = `
        <article class="exploration-card exploration-complete">
          <span class="exploration-kicker">旅行委託完成</span>
          <h2>探索完成！</h2>
          <p>你通過文化挑戰，已正式打卡「${escapeHtml(completion.sceneName)}」。</p>
          <div class="exploration-rewards">
            <div><span>獲得 EXP</span><strong>+${formatNumber(completion.experienceGained)}</strong></div>
            <div><span>獲得金幣</span><strong>+${formatNumber(completion.coinsGained)}</strong></div>
            <div><span>城市進度</span><strong>${completion.cityBossUnlocked ? "守護者已解鎖" : "已更新"}</strong></div>
          </div>
          ${completion.levelUp ? `<div class="exploration-result correct"><strong>LEVEL UP！</strong><span>玩家等級已提升。</span></div>` : ""}
          <div class="exploration-candidates">
            <button class="btn full" id="viewExplorationStoryBtn" type="button">查看景點故事</button>
            <button class="btn ghost" id="returnTainanMapBtn" type="button">返回台南地圖</button>
          </div>
        </article>
      `;
      document.getElementById("viewExplorationStoryBtn").addEventListener("click", () => focusTainan("city-detail"));
      document.getElementById("returnTainanMapBtn").addEventListener("click", () => focusTainan("city-list"));
    }

    function startCultureChallenge() {
      explorationState.challengeStarted = true;
      explorationState.feedback = null;
      renderExplorationMission();
    }

    async function submitExplorationGuess(sceneId) {
      if (!explorationState.mission || explorationState.submitting) return;
      explorationState.submitting = true;
      explorationState.feedback = null;
      renderExplorationMission();
      try {
        const result = await api(`/api/explorations/${encodeURIComponent(explorationState.mission.missionId)}/guess`, {
          method: "POST",
          body: JSON.stringify({ sceneId })
        });
        explorationState.guessCorrect = result.correct;
        if (result.correct) {
          explorationState.guessedScene = result;
          explorationState.cultureChallenge = result.challenge;
          explorationState.challengeStarted = false;
          addLog(`旅行委託推理成功：${result.sceneName}。`);
        } else {
          explorationState.feedback = {
            type: "wrong",
            title: "推理尚未成功",
            message: `「${result.sceneName}」不符合所有線索，再推敲一次。`
          };
          addLog(`旅行委託推理失敗：${result.sceneName}。`);
        }
      } catch (error) {
        explorationState.feedback = { type: "wrong", title: "無法提交推理", message: error.message };
        addLog(error.message);
      } finally {
        explorationState.submitting = false;
        renderExplorationMission();
      }
    }

    async function submitCultureChallenge(answer) {
      const mission = explorationState.mission;
      const challenge = explorationState.cultureChallenge;
      if (!mission || !challenge || explorationState.submitting) return;
      explorationState.submitting = true;
      explorationState.feedback = null;
      renderExplorationMission();
      try {
        const result = await api(`/api/explorations/${encodeURIComponent(mission.missionId)}/complete`, {
          method: "POST",
          body: JSON.stringify({
            questionId: challenge.questionId,
            answer,
            difficulty: selectedDifficulty
          })
        });
        if (!result.completed) {
          explorationState.cultureChallenge = null;
          explorationState.challengeStarted = false;
          explorationState.feedback = {
            type: "wrong",
            title: "文化挑戰未通過",
            message: "答案不正確，重新取得題目後再試一次。"
          };
          addLog("安平古堡文化挑戰答案錯誤。");
          return;
        }
        explorationState.completion = result;
        addLog(`${result.sceneName}探索完成，獲得 EXP ${result.experienceGained}、金幣 ${result.coinsGained}。`);
        await refreshState();
      } catch (error) {
        explorationState.cultureChallenge = null;
        explorationState.challengeStarted = false;
        explorationState.feedback = { type: "wrong", title: "文化挑戰失效", message: error.message };
        addLog(error.message);
      } finally {
        explorationState.submitting = false;
        renderExplorationMission();
      }
    }

    async function retryCultureChallenge() {
      const sceneId = explorationState.guessedScene?.sceneId;
      if (!sceneId) return;
      await submitExplorationGuess(sceneId);
    }

    function focusTainan(targetId) {
      const tainan = appState?.cities.find(city => city.id === 3);
      if (tainan) {
        activeCityId = tainan.id;
        renderCityCards();
        renderCityDetail(activeCityId);
      }
      document.getElementById(targetId)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
