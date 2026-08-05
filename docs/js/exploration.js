async function loadExplorationMission(cityId = activeCityId || 1) {
      explorationState = createExplorationState();
      explorationState.cityId = Number(cityId);
      explorationState.loading = true;
      renderExplorationMission();
      try {
        explorationState.mission = await api(`/api/explorations/cities/${cityId}/random`);
        explorationState.cityId = Number(explorationState.mission.cityId || cityId);
        applyExplorationProgress(explorationState.mission);
      } catch (error) {
        explorationState.error = error.message;
      } finally {
        explorationState.loading = false;
        renderExplorationMission();
      }
    }

    function openIssuedExplorationMission(mission) {
      if (!mission) return;
      explorationState = createExplorationState();
      explorationState.mission = mission;
      explorationState.cityId = Number(mission.cityId || activeCityId);
      applyExplorationProgress(mission);
      difficultyLocked = true;
      renderPlayerSummary();
      renderExplorationMission();
      document.getElementById("exploration-mission")?.scrollIntoView({
        behavior: prefersReducedCityMotion() ? "auto" : "smooth",
        block: "start"
      });
    }

    function applyExplorationProgress(data) {
      if (!data) return;
      if (Number.isFinite(Number(data.remainingActions))) {
        explorationState.remainingActions = Number(data.remainingActions);
      }
      if (Array.isArray(data.discoveredClues)) {
        explorationState.discoveredClues = data.discoveredClues;
      }
      if (Number.isFinite(Number(data.wrongGuesses))) {
        explorationState.wrongGuesses = Number(data.wrongGuesses);
      }
    }

    function renderExplorationMission() {
      const container = document.getElementById("exploration-mission");
      if (!container || !session?.token) return;
      const explorationCityId = Number(explorationState.mission?.cityId || explorationState.cityId);
      const explorationCity = appState?.cities?.find(city => city.id === explorationCityId);
      const cityName = explorationCity?.name || "城市";
      if ((!explorationCity?.unlocked || explorationCityId !== Number(activeCityId))
          && !explorationState.completion) {
        container.innerHTML = "";
        return;
      }

      if (explorationState.loading) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">${escapeHtml(cityName)}旅行委託</span>
            <h2>正在接收旅行委託…</h2>
          </div>
        `;
        return;
      }

      if (explorationState.error) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">${escapeHtml(cityName)}旅行委託</span>
            <h2>目前無法取得探索任務</h2>
            <p>${escapeHtml(explorationState.error)}</p>
            <button class="btn ghost" id="retryExplorationBtn" type="button">重新取得任務</button>
          </div>
        `;
        const retryButton = document.getElementById("retryExplorationBtn");
        retryButton.addEventListener("click", () =>
          runWithButtonLock(retryButton, () => loadExplorationMission(explorationCityId))
        );
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
          <span class="exploration-kicker">${escapeHtml(cityName)}旅行委託</span>
          <div class="section-head">
            <div>
              <h2>${escapeHtml(mission.title)}</h2>
              <p>${escapeHtml(mission.description)}</p>
            </div>
            <span class="status-pill ${explorationState.guessCorrect ? "challenge" : "active"}">
              ${explorationState.guessCorrect ? "文化確認" : "探索中"}
            </span>
          </div>
          ${explorationState.guessCorrect ? renderCultureChallengeStage() : renderInvestigationStage(mission)}
          ${feedback}
        </article>
      `;

      bindExplorationEvents();
    }

    function renderInvestigationStage(mission) {
      const actionDots = Array.from({ length: 4 }, (_, index) =>
        `<span class="action-dot ${index < explorationState.remainingActions ? "available" : "used"}"></span>`
      ).join("");
      return `
        <div class="exploration-action-bar">
          <strong>剩餘探索行動：${explorationState.remainingActions}</strong>
          <div class="action-dots" aria-label="剩餘 ${explorationState.remainingActions} 次探索行動">${actionDots}</div>
          <span>錯誤推理：${explorationState.wrongGuesses}</span>
        </div>

        <section class="exploration-panel" id="investigationActions">
          <div class="section-head">
            <h3>選擇調查方式</h3>
            <span>每項新調查消耗 1 次行動</span>
          </div>
          <div class="investigation-actions">
            ${mission.availableInvestigations.map(investigation => {
              const discovered = discoveredClue(investigation.type);
              return `
                <button class="investigation-button ${discovered ? "done" : ""}" type="button"
                        data-investigation-action="${investigation.type}"
                        ${discovered || explorationState.submitting ? "disabled" : ""}>
                  <strong>${discovered ? "✓ 已完成" : escapeHtml(investigation.name)}</strong>
                  <span>${investigationHint(investigation.type)}</span>
                </button>
              `;
            }).join("")}
          </div>
        </section>

        <section class="exploration-panel traveler-notes">
          <div class="section-head">
            <h3>旅人筆記</h3>
            <span>${explorationState.discoveredClues.length} / 3 條線索</span>
          </div>
          <div class="note-list">
            ${mission.availableInvestigations.map(investigation => {
              const clue = discoveredClue(investigation.type);
              return `
                <div class="note-item ${clue ? "discovered" : "pending"}">
                  <span>${clue ? "✓" : "□"}</span>
                  <strong>${escapeHtml(clue?.text || undiscoveredLabel(investigation.type))}</strong>
                </div>
              `;
            }).join("")}
          </div>
        </section>

        <div class="exploration-decision">
          <button class="btn ghost" id="continueInvestigatingBtn" type="button">繼續調查</button>
          <button class="btn full" id="startReasoningBtn" type="button">開始推理</button>
        </div>

        ${explorationState.reasoningStarted ? `
          <section class="exploration-panel exploration-guess">
            <h3>根據目前筆記，你認為是哪裡？</h3>
            <div class="exploration-candidates">
              ${mission.candidates.map(candidate => `
                <button class="btn ghost" type="button" data-exploration-scene-id="${candidate.sceneId}"
                        ${explorationState.submitting ? "disabled" : ""}>
                  ${escapeHtml(candidate.name)}
                </button>
              `).join("")}
            </div>
          </section>
        ` : ""}
      `;
    }

    function renderCultureChallengeStage() {
      const sceneName = explorationState.guessedScene?.sceneName || "目標景點";
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
          <div class="exploration-grade grade-${escapeHtml(completion.explorationGrade || "c").toLowerCase()}">
            <span>探索評價</span>
            <strong>${escapeHtml(completion.explorationGrade || "C")}</strong>
            <small>使用 ${completion.cluesUsed} 條線索 · 錯誤推理 ${completion.wrongGuesses} 次</small>
          </div>
          <div class="exploration-rewards">
            <div><span>獲得 EXP</span><strong>+${formatNumber(completion.experienceGained)}</strong></div>
            <div><span>獲得金幣</span><strong>+${formatNumber(completion.coinsGained)}</strong></div>
            <div><span>城市進度</span><strong>${completion.cityBossUnlocked ? "守護者已解鎖" : "已更新"}</strong></div>
          </div>
          ${completion.levelUp ? `<div class="exploration-result correct"><strong>LEVEL UP！</strong><span>玩家等級已提升。</span></div>` : ""}
          <div class="exploration-candidates">
            <button class="btn full" id="viewExplorationStoryBtn" type="button">查看景點故事</button>
            <button class="btn ghost" id="returnExplorationMapBtn" type="button">返回城市地圖</button>
          </div>
        </article>
      `;
      document.getElementById("viewExplorationStoryBtn").addEventListener("click", () => focusExplorationCity("city-detail"));
      document.getElementById("returnExplorationMapBtn").addEventListener("click", openTaiwanMapView);
    }

    function bindExplorationEvents() {
      document.querySelectorAll("[data-investigation-action]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          submitInvestigation(button.dataset.investigationAction)
        ));
      });
      document.querySelectorAll("[data-exploration-scene-id]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          submitExplorationGuess(Number(button.dataset.explorationSceneId))
        ));
      });
      document.querySelectorAll("[data-culture-answer]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          submitCultureChallenge(button.dataset.cultureAnswer)
        ));
      });
      document.getElementById("continueInvestigatingBtn")?.addEventListener("click", () => {
        explorationState.reasoningStarted = false;
        explorationState.feedback = null;
        renderExplorationMission();
        document.getElementById("investigationActions")?.scrollIntoView({ behavior: "smooth", block: "center" });
      });
      document.getElementById("startReasoningBtn")?.addEventListener("click", () => {
        explorationState.reasoningStarted = true;
        explorationState.feedback = null;
        renderExplorationMission();
      });
      document.getElementById("startCultureChallengeBtn")?.addEventListener("click", () => {
        explorationState.challengeStarted = true;
        explorationState.feedback = null;
        renderExplorationMission();
      });
      const retryButton = document.getElementById("retryCultureChallengeBtn");
      retryButton?.addEventListener("click", () =>
        runWithButtonLock(retryButton, retryCultureChallenge)
      );
    }

    function discoveredClue(type) {
      return explorationState.discoveredClues.find(clue => clue.type === type) || null;
    }

    function investigationHint(type) {
      return ({ LOCAL: "確認所在區域", HISTORY: "追查歷史背景", VISUAL: "辨認建築外觀" })[type] || "取得旅行線索";
    }

    function undiscoveredLabel(type) {
      return ({ LOCAL: "地點線索尚未取得", HISTORY: "歷史背景尚未確認", VISUAL: "建築外觀尚未確認" })[type] || "線索尚未取得";
    }

    async function submitInvestigation(action) {
      const mission = explorationState.mission;
      if (!mission || explorationState.submitting) return;
      explorationState.submitting = true;
      explorationState.feedback = null;
      renderExplorationMission();
      try {
        const result = await api(`/api/explorations/${encodeURIComponent(mission.missionId)}/investigate`, {
          method: "POST",
          body: JSON.stringify({ action })
        });
        applyExplorationProgress(result);
        explorationState.feedback = {
          type: "correct",
          title: result.alreadyDiscovered ? "這項調查已完成" : "發現新線索",
          message: result.alreadyDiscovered ? "重複調查不會消耗探索行動。" : result.clue
        };
      } catch (error) {
        explorationState.feedback = { type: "wrong", title: "調查失敗", message: error.message };
        addLog(error.message);
      } finally {
        explorationState.submitting = false;
        renderExplorationMission();
      }
    }

    async function submitExplorationGuess(sceneId) {
      const mission = explorationState.mission;
      if (!mission || explorationState.submitting) return;
      explorationState.submitting = true;
      explorationState.feedback = null;
      renderExplorationMission();
      try {
        const result = await api(`/api/explorations/${encodeURIComponent(mission.missionId)}/guess`, {
          method: "POST",
          body: JSON.stringify({ sceneId })
        });
        applyExplorationProgress(result);
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
            message: `「${result.sceneName}」與目前線索不完全吻合，剩餘 ${result.remainingActions} 次行動。`
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
          body: JSON.stringify({ questionId: challenge.questionId, answer, difficulty: selectedDifficulty })
        });
        if (!result.completed) {
          explorationState.cultureChallenge = null;
          explorationState.challengeStarted = false;
          explorationState.feedback = {
            type: "wrong",
            title: "文化挑戰未通過",
            message: "答案不正確，重新取得題目後再試一次。"
          };
          addLog(`${explorationState.guessedScene?.sceneName || "景點"}文化挑戰答案錯誤。`);
          return;
        }
        explorationState.completion = result;
        addLog(`${result.sceneName}探索完成，獲得 ${result.explorationGrade} 評價。`);
        await refreshCityMapWithAnimation(mission.cityId || explorationState.cityId || activeCityId);
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

    function focusExplorationCity(targetId) {
      const cityId = Number(explorationState.mission?.cityId || explorationState.cityId);
      const city = appState?.cities.find(item => item.id === cityId);
      if (city) {
        openCityStageView(city);
      }
      document.getElementById(targetId)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
