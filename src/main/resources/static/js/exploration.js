async function loadExplorationMission(cityId = 3) {
      explorationLoading = true;
      explorationError = null;
      renderExplorationMission();
      try {
        explorationMission = await api(`/api/explorations/cities/${cityId}/random`);
        explorationResult = null;
      } catch (error) {
        explorationMission = null;
        explorationError = error.message;
      } finally {
        explorationLoading = false;
        renderExplorationMission();
      }
    }

    function renderExplorationMission() {
      const container = document.getElementById("exploration-mission");
      if (!container || !session?.token) return;

      if (explorationLoading) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">台南旅行委託 · 試作版</span>
            <h2>正在接收旅行委託…</h2>
          </div>
        `;
        return;
      }

      if (explorationError) {
        container.innerHTML = `
          <div class="exploration-card">
            <span class="exploration-kicker">台南旅行委託 · 試作版</span>
            <h2>目前無法取得探索任務</h2>
            <p>${escapeHtml(explorationError)}</p>
            <button class="btn ghost" id="retryExplorationBtn" type="button">重新取得任務</button>
          </div>
        `;
        document.getElementById("retryExplorationBtn").addEventListener("click", () => loadExplorationMission());
        return;
      }

      if (!explorationMission) {
        container.innerHTML = "";
        return;
      }

      const solved = Boolean(explorationResult?.correct);
      const resultMarkup = explorationResult ? `
        <div class="exploration-result ${solved ? "correct" : "wrong"}" role="status">
          <strong>${solved ? "推理成功！" : "推理尚未成功"}</strong>
          <span>${solved
            ? `你找到了「${escapeHtml(explorationResult.sceneName)}」。下一階段將接續文化小挑戰與打卡。`
            : `「${escapeHtml(explorationResult.sceneName)}」不符合所有線索，再推敲一次。`}</span>
        </div>
      ` : "";

      container.innerHTML = `
        <article class="exploration-card">
          <span class="exploration-kicker">台南旅行委託 · 試作版</span>
          <div class="section-head">
            <div>
              <h2>${escapeHtml(explorationMission.title)}</h2>
              <p>${escapeHtml(explorationMission.description)}</p>
            </div>
            <span class="status-pill ${solved ? "completed" : "active"}">${solved ? "已破解" : "調查中"}</span>
          </div>
          <div class="exploration-clues">
            ${explorationMission.clues.map((clue, index) => `
              <div class="exploration-clue">
                <span>線索 ${index + 1}</span>
                <strong>${escapeHtml(clue)}</strong>
              </div>
            `).join("")}
          </div>
          <div class="exploration-guess">
            <h3>你認為是哪裡？</h3>
            <div class="exploration-candidates">
              ${explorationMission.candidates.map(candidate => `
                <button class="btn ghost" type="button" data-exploration-scene-id="${candidate.sceneId}"
                        ${solved || explorationSubmitting ? "disabled" : ""}>
                  ${escapeHtml(candidate.name)}
                </button>
              `).join("")}
            </div>
          </div>
          ${resultMarkup}
        </article>
      `;

      document.querySelectorAll("[data-exploration-scene-id]").forEach(button => {
        button.addEventListener("click", () => submitExplorationGuess(Number(button.dataset.explorationSceneId)));
      });
    }

    async function submitExplorationGuess(sceneId) {
      if (!explorationMission || explorationSubmitting) return;
      explorationSubmitting = true;
      renderExplorationMission();
      try {
        explorationResult = await api(`/api/explorations/${encodeURIComponent(explorationMission.missionId)}/guess`, {
          method: "POST",
          body: JSON.stringify({ sceneId })
        });
        addLog(explorationResult.correct
          ? `旅行委託推理成功：${explorationResult.sceneName}。`
          : `旅行委託推理失敗：${explorationResult.sceneName}。`);
      } catch (error) {
        explorationError = error.message;
        addLog(error.message);
      } finally {
        explorationSubmitting = false;
        renderExplorationMission();
      }
    }
