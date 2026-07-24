function stopPuzzleTimer() {
      if (puzzleState.timerId) {
        clearInterval(puzzleState.timerId);
      }
      puzzleState.timerId = null;
    }

function openIssuedPuzzleChallenge(landmark, challenge) {
      if (!landmark || !challenge) return;
      stopQuizTimer();
      stopImageRecognitionTimer();
      stopPuzzleTimer();
      puzzleState = {
        ...createPuzzleState(),
        ...challenge,
        cityId: Number(activeCityId),
        landmarkId: Number(landmark.id),
        tileOrder: [...(challenge.initialTileOrder || [])],
        candidates: [...(challenge.candidates || [])],
        remainingSeconds: Number(challenge.seconds || 0)
      };
      difficultyLocked = true;
      renderPlayerSummary();
      startPuzzleTimer();
      renderPuzzleChallenge();
      document.getElementById("puzzle-challenge")?.scrollIntoView({
        behavior: prefersReducedCityMotion() ? "auto" : "smooth",
        block: "start"
      });
    }

function startPuzzleTimer() {
      stopPuzzleTimer();
      updatePuzzleClock();
      if (!puzzleState.expired && !puzzleState.result) {
        puzzleState.timerId = setInterval(updatePuzzleClock, 250);
      }
    }

function updatePuzzleClock() {
      if (!puzzleState.expiresAt || puzzleState.result) {
        stopPuzzleTimer();
        return;
      }
      const remainingMs = new Date(puzzleState.expiresAt).getTime() - Date.now();
      puzzleState.remainingSeconds = Math.max(0, Math.ceil(remainingMs / 1000));
      if (remainingMs <= 0) {
        puzzleState.expired = true;
        stopPuzzleTimer();
        renderPuzzleChallenge();
        return;
      }
      const timer = document.querySelector(".puzzle-timer");
      if (timer) {
        timer.textContent = `剩餘 ${puzzleState.remainingSeconds} 秒`;
        timer.classList.toggle("warning", puzzleState.remainingSeconds <= 10);
      }
    }

function isPuzzleSolved(tileOrder) {
      return tileOrder.every((tileNumber, index) => tileNumber === index);
    }

function puzzleTileStyle(tileNumber, gridSize, imageUrl) {
      const row = Math.floor(tileNumber / gridSize);
      const column = tileNumber % gridSize;
      const x = gridSize === 1 ? 0 : column / (gridSize - 1) * 100;
      const y = gridSize === 1 ? 0 : row / (gridSize - 1) * 100;
      const safeUrl = String(imageUrl || "").replace(/['"\\()]/g, "");
      return `background-image:url('${safeUrl}');background-size:${gridSize * 100}% ${gridSize * 100}%;background-position:${x}% ${y}%;`;
    }

function renderPuzzleChallenge() {
      const container = document.getElementById("puzzle-challenge");
      if (!container || !session?.token) return;
      if (Number(puzzleState.cityId) !== Number(activeCityId) && !puzzleState.result) {
        container.innerHTML = "";
        return;
      }
      if (!puzzleState.challengeId && !puzzleState.error) {
        container.innerHTML = "";
        return;
      }
      if (puzzleState.error) {
        container.innerHTML = `
          <article class="puzzle-card">
            <span class="exploration-kicker">景點拼圖</span>
            <h2>目前無法繼續拼圖</h2>
            <p>${escapeHtml(puzzleState.error)}</p>
          </article>
        `;
        return;
      }
      if (puzzleState.result) {
        renderPuzzleResult(container);
        return;
      }

      const controlsDisabled = puzzleState.expired || puzzleState.solved || puzzleState.submitting;
      container.innerHTML = `
        <article class="puzzle-card">
          <div class="section-head">
            <div>
              <span class="exploration-kicker">景點拼圖 · ${escapeHtml(puzzleState.difficulty)}</span>
              <h2>${puzzleState.solved ? "圖片已還原，這是哪一個景點？" : "交換兩塊拼圖片，還原旅行照片"}</h2>
            </div>
            <span class="puzzle-timer ${puzzleState.remainingSeconds <= 10 ? "warning" : ""}">
              ${puzzleState.expired ? "時間到" : `剩餘 ${puzzleState.remainingSeconds} 秒`}
            </span>
          </div>
          <div class="puzzle-board"
               style="--puzzle-size:${Number(puzzleState.gridSize) || 3}"
               aria-label="景點拼圖">
            ${puzzleState.tileOrder.map((tileNumber, position) => `
              <button type="button"
                      class="puzzle-tile ${puzzleState.selectedPosition === position ? "puzzle-tile--selected" : ""}"
                      data-puzzle-position="${position}"
                      style="${puzzleTileStyle(tileNumber, puzzleState.gridSize, puzzleState.imageUrl)}"
                      aria-label="拼圖片 ${tileNumber + 1}"
                      ${controlsDisabled ? "disabled" : ""}></button>
            `).join("")}
          </div>
          ${puzzleState.solved ? `
            <div class="puzzle-solved" role="status">✓ 圖片已還原，請選擇正確景點</div>
            <div class="puzzle-candidates">
              ${puzzleState.candidates.map(candidate => `
                <button type="button" class="btn ghost"
                        data-puzzle-candidate="${candidate.landmarkId}"
                        ${puzzleState.submitting ? "disabled" : ""}>
                  ${escapeHtml(candidate.name)}
                </button>
              `).join("")}
            </div>
          ` : ""}
          ${puzzleState.expired ? `
            <div class="exploration-result wrong">
              <strong>拼圖時間到</strong>
              <span>本次挑戰已失效，重新開始未知挑戰後再試一次。</span>
            </div>
            <button class="btn full" id="retryPuzzleBtn" type="button">重新開始未知挑戰</button>
          ` : ""}
        </article>
      `;

      container.querySelectorAll("[data-puzzle-position]").forEach(button => {
        button.addEventListener("click", () =>
          selectPuzzleTile(Number(button.dataset.puzzlePosition))
        );
      });
      container.querySelectorAll("[data-puzzle-candidate]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          submitPuzzleAnswer(Number(button.dataset.puzzleCandidate))
        ));
      });
      document.getElementById("retryPuzzleBtn")?.addEventListener("click", event =>
        runWithButtonLock(event.currentTarget, retryPuzzleChallenge)
      );
    }

function selectPuzzleTile(position) {
      if (puzzleState.expired || puzzleState.solved || puzzleState.submitting) return;
      if (!Number.isInteger(position) || position < 0 || position >= puzzleState.tileOrder.length) return;
      if (puzzleState.selectedPosition == null) {
        puzzleState.selectedPosition = position;
        renderPuzzleChallenge();
        return;
      }
      if (puzzleState.selectedPosition === position) {
        puzzleState.selectedPosition = null;
        renderPuzzleChallenge();
        return;
      }

      const nextOrder = [...puzzleState.tileOrder];
      [nextOrder[puzzleState.selectedPosition], nextOrder[position]] =
        [nextOrder[position], nextOrder[puzzleState.selectedPosition]];
      puzzleState.tileOrder = nextOrder;
      puzzleState.selectedPosition = null;
      puzzleState.solved = isPuzzleSolved(nextOrder);
      if (puzzleState.solved) {
        addLog("景點拼圖已還原，請辨認照片中的景點。");
      }
      renderPuzzleChallenge();
    }

async function submitPuzzleAnswer(selectedLandmarkId) {
      if (!puzzleState.challengeId || !puzzleState.solved
          || puzzleState.expired || puzzleState.submitting) return;
      puzzleState.submitting = true;
      renderPuzzleChallenge();
      try {
        const result = await api(
          `/api/puzzle-challenges/${encodeURIComponent(puzzleState.challengeId)}/complete`,
          {
            method: "POST",
            body: JSON.stringify({ selectedLandmarkId })
          }
        );
        stopPuzzleTimer();
        puzzleState.result = result;
        if (result.correct) {
          addLog(`${result.sceneName}拼圖辨識成功，完成打卡。`);
          await refreshCityMapWithAnimation(puzzleState.cityId || activeCityId);
        } else {
          addLog(`${result.sceneName}不是正確景點，本次拼圖挑戰結束。`);
        }
      } catch (error) {
        stopPuzzleTimer();
        puzzleState.error = error.message;
        addLog(error.message);
      } finally {
        puzzleState.submitting = false;
        renderPuzzleChallenge();
      }
    }

function renderPuzzleResult(container) {
      const result = puzzleState.result;
      if (!result.correct) {
        container.innerHTML = `
          <article class="puzzle-card">
            <span class="exploration-kicker">景點拼圖</span>
            <div class="exploration-result wrong">
              <strong>景點辨識錯誤</strong>
              <span>「${escapeHtml(result.sceneName)}」不是這張拼圖的景點，本次挑戰已結束。</span>
            </div>
            <button class="btn full" id="retryPuzzleBtn" type="button">重新開始未知挑戰</button>
          </article>
        `;
        document.getElementById("retryPuzzleBtn")?.addEventListener("click", event =>
          runWithButtonLock(event.currentTarget, retryPuzzleChallenge)
        );
        return;
      }
      container.innerHTML = `
        <article class="puzzle-card puzzle-card--complete">
          <span class="exploration-kicker">景點拼圖完成</span>
          <h2>辨識成功：${escapeHtml(result.sceneName)}</h2>
          <div class="exploration-rewards">
            <div><span>獲得 EXP</span><strong>+${formatNumber(result.experienceGained)}</strong></div>
            <div><span>獲得金幣</span><strong>+${formatNumber(result.coinsGained)}</strong></div>
            <div><span>城市進度</span><strong>${result.cityBossUnlocked ? "守護者已解鎖" : "已更新"}</strong></div>
          </div>
        </article>
      `;
    }

function retryPuzzleChallenge() {
      const city = appState?.cities.find(item => item.id === Number(puzzleState.cityId));
      const landmark = city?.scenes?.find(item => item.id === Number(puzzleState.landmarkId));
      if (landmark) {
        return startMysteryChallenge(landmark, selectedDifficulty);
      }
    }
