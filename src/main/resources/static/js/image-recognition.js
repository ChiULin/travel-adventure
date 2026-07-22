function stopImageRecognitionTimer() {
      if (imageRecognitionState.timerId) {
        clearInterval(imageRecognitionState.timerId);
      }
      imageRecognitionState.timerId = null;
    }

    async function startImageRecognition(scene) {
      if (!scene || imageRecognitionState.loading || imageRecognitionState.submitting) return;
      stopQuizTimer();
      stopImageRecognitionTimer();
      imageRecognitionState = createImageRecognitionState();
      imageRecognitionState.loading = true;
      imageRecognitionState.cityId = activeCityId;
      imageRecognitionState.sceneId = scene.id;
      renderImageRecognition();
      document.getElementById("image-recognition")?.scrollIntoView({ behavior: "smooth", block: "start" });
      try {
        const challenge = await api(
          `/api/image-challenges/scenes/${scene.id}?difficulty=${encodeURIComponent(selectedDifficulty)}`
        );
        imageRecognitionState = {
          ...createImageRecognitionState(),
          ...challenge,
          sceneId: scene.id,
          cityId: Number(challenge.cityId || activeCityId),
          initialBlurLevel: Number(challenge.blurLevel || 0),
          currentBlurLevel: Number(challenge.blurLevel || 0),
          candidates: challenge.candidates || []
        };
        difficultyLocked = true;
        startImageRecognitionTimer();
      } catch (error) {
        imageRecognitionState.error = error.message;
        addLog(error.message);
      } finally {
        imageRecognitionState.loading = false;
        renderImageRecognition();
      }
    }

    function startImageRecognitionTimer() {
      stopImageRecognitionTimer();
      updateImageRecognitionClock();
      imageRecognitionState.timerId = setInterval(updateImageRecognitionClock, 250);
    }

    function updateImageRecognitionClock() {
      if (!imageRecognitionState.expiresAt || imageRecognitionState.result) {
        stopImageRecognitionTimer();
        return;
      }
      const expiresAt = new Date(imageRecognitionState.expiresAt).getTime();
      const issuedAt = new Date(imageRecognitionState.issuedAt).getTime();
      const remainingMs = Math.max(0, expiresAt - Date.now());
      const totalMs = Math.max(1, expiresAt - issuedAt);
      imageRecognitionState.remainingSeconds = Math.ceil(remainingMs / 1000);
      imageRecognitionState.currentBlurLevel = imageRecognitionState.initialBlurLevel
        * Math.min(1, remainingMs / totalMs);
      if (remainingMs <= 0) {
        imageRecognitionState.expired = true;
        stopImageRecognitionTimer();
        renderImageRecognition();
        return;
      }
      const timer = document.querySelector(".image-timer");
      if (timer) {
        timer.textContent = `剩餘 ${imageRecognitionState.remainingSeconds} 秒`;
        timer.classList.toggle("warning", imageRecognitionState.remainingSeconds <= 2);
      }
      const image = document.querySelector(".recognition-image-frame img");
      if (image) {
        image.style.filter = `blur(${imageRecognitionState.currentBlurLevel.toFixed(2)}px)`;
      }
    }

    function renderImageRecognition() {
      const container = document.getElementById("image-recognition");
      if (!container || !session?.token) return;
      if (Number(imageRecognitionState.cityId) !== Number(activeCityId)
          && !imageRecognitionState.result) {
        container.innerHTML = "";
        return;
      }
      if (imageRecognitionState.loading) {
        container.innerHTML = `
          <article class="image-recognition-card">
            <span class="exploration-kicker">圖片辨識挑戰</span>
            <h2>正在準備景點照片…</h2>
          </article>
        `;
        return;
      }
      if (imageRecognitionState.error) {
        container.innerHTML = `
          <article class="image-recognition-card">
            <span class="exploration-kicker">圖片辨識挑戰</span>
            <h2>目前無法取得圖片</h2>
            <p>${escapeHtml(imageRecognitionState.error)}</p>
            <button class="btn ghost" id="retryImageRecognitionBtn" type="button">重新取得挑戰</button>
          </article>
        `;
        document.getElementById("retryImageRecognitionBtn")?.addEventListener("click", retryImageRecognition);
        return;
      }
      if (imageRecognitionState.result) {
        renderImageRecognitionResult(container);
        return;
      }
      if (!imageRecognitionState.questionId) {
        container.innerHTML = "";
        return;
      }

      const disabled = imageRecognitionState.submitting || imageRecognitionState.expired;
      container.innerHTML = `
        <article class="image-recognition-card">
          <div class="section-head">
            <div>
              <span class="exploration-kicker">圖片辨識挑戰 · ${escapeHtml(imageRecognitionState.difficulty)}</span>
              <h2>${escapeHtml(imageRecognitionState.prompt)}</h2>
            </div>
            <span class="image-timer ${imageRecognitionState.remainingSeconds <= 2 ? "warning" : ""}">
              ${imageRecognitionState.expired ? "時間到" : `剩餘 ${imageRecognitionState.remainingSeconds} 秒`}
            </span>
          </div>
          <div class="recognition-image-frame">
            <img src="${escapeHtml(imageRecognitionState.imageUrl)}" alt="待辨識的景點照片"
                 style="filter: blur(${imageRecognitionState.currentBlurLevel.toFixed(2)}px)" />
            <span>${imageRecognitionState.displayMode === "FULL" ? "完整照片" : "照片會逐漸清晰"}</span>
          </div>
          <div class="recognition-candidates">
            ${imageRecognitionState.candidates.map(candidate => `
              <button class="btn ghost" type="button" data-image-candidate="${candidate.sceneId}"
                      ${disabled ? "disabled" : ""}>${escapeHtml(candidate.name)}</button>
            `).join("")}
          </div>
          ${imageRecognitionState.expired ? `
            <div class="exploration-result wrong"><strong>挑戰已過期</strong><span>請重新取得一張圖片再試一次。</span></div>
            <button class="btn full" id="retryImageRecognitionBtn" type="button">重新取得挑戰</button>
          ` : ""}
        </article>
      `;
      document.querySelectorAll("[data-image-candidate]").forEach(button => {
        button.addEventListener("click", () => submitImageRecognition(Number(button.dataset.imageCandidate)));
      });
      document.getElementById("retryImageRecognitionBtn")?.addEventListener("click", retryImageRecognition);
    }

    function renderImageRecognitionResult(container) {
      const result = imageRecognitionState.result;
      if (!result.correct) {
        container.innerHTML = `
          <article class="image-recognition-card">
            <span class="exploration-kicker">圖片辨識挑戰</span>
            <div class="exploration-result wrong">
              <strong>辨識錯誤</strong>
              <span>「${escapeHtml(result.sceneName)}」不是這張照片的景點，本題已失效。</span>
            </div>
            <button class="btn full" id="retryImageRecognitionBtn" type="button">重新取得挑戰</button>
          </article>
        `;
        document.getElementById("retryImageRecognitionBtn")?.addEventListener("click", retryImageRecognition);
        return;
      }
      container.innerHTML = `
        <article class="image-recognition-card image-recognition-complete">
          <span class="exploration-kicker">圖片辨識完成</span>
          <h2>辨識成功：${escapeHtml(result.sceneName)}</h2>
          <p>${escapeHtml(result.cultureExplanation)}</p>
          <div class="exploration-rewards">
            <div><span>獲得 EXP</span><strong>+${formatNumber(result.experienceGained)}</strong></div>
            <div><span>獲得金幣</span><strong>+${formatNumber(result.coinsGained)}</strong></div>
            <div><span>城市進度</span><strong>${result.cityBossUnlocked ? "守護者已解鎖" : "已更新"}</strong></div>
          </div>
        </article>
      `;
    }

    async function submitImageRecognition(sceneId) {
      if (!imageRecognitionState.questionId || imageRecognitionState.submitting
          || imageRecognitionState.expired) return;
      imageRecognitionState.submitting = true;
      renderImageRecognition();
      try {
        const result = await api(
          `/api/image-challenges/${encodeURIComponent(imageRecognitionState.questionId)}/complete`,
          {
            method: "POST",
            body: JSON.stringify({ sceneId, difficulty: imageRecognitionState.difficulty })
          }
        );
        stopImageRecognitionTimer();
        imageRecognitionState.result = result;
        if (result.correct) {
          addLog(`${result.sceneName}圖片辨識成功，完成打卡。`);
          await refreshState();
        } else {
          addLog(`${result.sceneName}圖片辨識錯誤，本題已失效。`);
        }
      } catch (error) {
        stopImageRecognitionTimer();
        imageRecognitionState.error = error.message;
        imageRecognitionState.questionId = null;
        addLog(error.message);
      } finally {
        imageRecognitionState.submitting = false;
        renderImageRecognition();
      }
    }

    function retryImageRecognition() {
      const city = appState?.cities.find(item => item.id === Number(imageRecognitionState.cityId));
      const scene = city?.scenes?.find(item => item.id === Number(imageRecognitionState.sceneId));
      if (scene) startImageRecognition(scene);
    }
