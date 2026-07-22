function escapeHtml(value) {
      return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
    }

function formatNumber(value) {
      return Number(value || 0).toLocaleString("zh-TW");
    }

    function progress(city) {
      const total = city?.total || 0;
      const done = city?.done || 0;
      return { done, total, percent: total ? Math.round(done / total * 100) : 0 };
    }

    function cityStatus(city) {
      const itemProgress = progress(city);
      if (!city.unlocked) return { key: "locked", text: "尚未解鎖", note: "完成上一座城市後解鎖" };
      if (city.defeated) return { key: "completed", text: "已完成", note: "城市徽章已取得" };
      if (city.bossUnlocked || (itemProgress.total > 0 && itemProgress.done >= itemProgress.total)) {
        return { key: "challenge", text: "可挑戰", note: `可以挑戰 ${city.bossName}` };
      }
      return { key: "active", text: "探索中", note: `還有 ${itemProgress.total - itemProgress.done} 個景點` };
    }

    function activeCity() {
      return appState?.cities.find(city => city.id === activeCityId) || appState?.cities[0];
    }

    const interactionHandlers = {
      EXPLORATION: async scene => {
        if ((!explorationState.mission || Number(explorationState.mission.cityId) !== Number(activeCityId))
            && !explorationState.loading) {
          await loadExplorationMission(activeCityId);
        }
        document.getElementById("exploration-mission")?.scrollIntoView({ behavior: "smooth", block: "start" });
      },
      IMAGE_RECOGNITION: startImageRecognition,
      QUIZ: scene => startSceneQuiz(scene.id)
    };

    function startSceneInteraction(scene) {
      const handler = interactionHandlers[scene?.interactionType];
      if (!handler) {
        addLog("目前無法開啟這個景點玩法。");
        return;
      }
      handler(scene);
    }

    function addLog(text) {
      const time = new Date().toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" });
      logs.unshift({ time, text });
      logs = logs.slice(0, 4);
      renderCityDetail(activeCityId);
    }

    function renderOptionButtons(options, dataAttribute, targetId) {
      if (!options) return "";
      return Object.entries(options)
        .filter(([, value]) => value)
        .map(([key, value]) => `
          <button class="quiz-option" type="button" ${dataAttribute}="${targetId}" data-answer="${key}" data-answer-text="${escapeHtml(value)}">
            ${key}. ${escapeHtml(value)}
          </button>
        `).join("");
    }

    function badgeParts(city) {
      const raw = city?.badgeName || `${city?.name || "城市"}探索者`;
      const [name, quote] = raw.split(/[：:]/).map(part => part.trim());
      return { name, quote: quote || "" };
    }

    function nextCityAfter(city) {
      if (!appState?.cities || !city) return null;
      const index = appState.cities.findIndex(item => item.id === city.id);
      return index >= 0 ? appState.cities[index + 1] : null;
    }

    function closeResultCard() {
      document.getElementById("resultModal").classList.add("hidden");
      document.getElementById("resultCard").innerHTML = "";
      if (cityFailedPending) {
        answerCombo = 0;
        cityLives = difficultyConfig().lives;
        cityFailedPending = false;
        difficultyLocked = false;
        resetCityBattleStats({ keepFailedAttempt: true });
        renderPlayerSummary();
      }
      if (appState) {
        renderCityDetail(activeCityId);
        setTimeout(maybeShowFinalEnding, 0);
      }
    }

    function showResultCard(html, type = "success") {
      stopQuizTimer();
      const resultCard = document.getElementById("resultCard");
      resultCard.className = `result-card ${type === "failed" ? "failed" : ""}`.trim();
      document.getElementById("resultCard").innerHTML = `
        ${html}
        <button class="btn full" id="resultCloseBtn" type="button">繼續旅程</button>
      `;
      document.getElementById("resultModal").classList.remove("hidden");
      document.getElementById("resultCloseBtn").addEventListener("click", closeResultCard);
    }

    function overlayIsOpen() {
      return !document.getElementById("resultModal").classList.contains("hidden")
        || !document.getElementById("tutorial").classList.contains("hidden")
        || !document.getElementById("finalEnding").classList.contains("hidden")
        || !document.getElementById("collectionOverlay").classList.contains("hidden");
    }

async function refreshState() {
      if (!session?.token) return;
      const [journey, missions, achievements] = await Promise.all([
        api("/api/journey/me"),
        api("/api/journey/missions").catch(() => null),
        api("/api/journey/achievements").catch(() => null)
      ]);
      appState = journey;
      missionsState = missions;
      achievementsState = achievements;
      if (!activeCityId) {
        const firstUnlocked = appState.cities.find(city => city.unlocked) || appState.cities[0];
        activeCityId = firstUnlocked.id;
      }
      renderAll();
      loadFoodEvent(activeCityId, true);
      const explorationCity = appState.cities.find(city => city.id === activeCityId);
      const supportsExploration = explorationCity?.scenes?.some(scene =>
        scene.interactionType === "EXPLORATION" && !scene.checked
      );
      if (supportsExploration && explorationCity.unlocked && !explorationState.mission && !explorationState.loading
          && !explorationState.error && !explorationState.completion) {
        loadExplorationMission(explorationCity.id);
      }
      setTimeout(maybeShowFinalEnding, 0);
    }

    function renderPlayerSummary() {
      const user = appState.user;
      const currentExp = user.currentLevelExp ?? 0;
      const nextExp = user.nextLevelExp ?? 100;
      const badgeCount = appState.cities.filter(city => city.badgeUnlocked).length;
      const unlocked = appState.cities.filter(city => city.unlocked).length;
      const avatar = (user.username || "T").trim().slice(0, 1).toUpperCase();

      document.getElementById("player-summary").innerHTML = `
        <div class="summary-card">
          <div class="summary-layout">
            <div class="avatar">${escapeHtml(avatar)}</div>
            <div>
              <div class="player-name">玩家：${escapeHtml(user.username)}</div>
              <div class="player-title">等級：Lv.${user.level || 1} ${escapeHtml(user.title || "新手旅行者")}</div>
            </div>
          </div>
          <div class="player-level">
            <div class="level-header">
              <span id="player-level">Lv.1</span>
              <span id="player-exp-text">0 / 100 EXP</span>
            </div>
            <div class="exp-bar">
              <div id="exp-bar-fill"></div>
            </div>
          </div>
          <div class="metric-grid">
            <div class="metric"><strong>${currentExp} / ${nextExp}</strong><span>經驗值</span></div>
            <div class="metric"><strong>${formatNumber(user.coins)}</strong><span>金幣</span></div>
            <div class="metric"><strong>${badgeCount}</strong><span>已取得徽章</span></div>
            <div class="metric"><strong>${unlocked} / ${appState.cities.length}</strong><span>城市總進度</span></div>
          </div>
          <div class="battle-status">
            <div class="battle-chip combo" id="combo-display">🔥 Combo ×0</div>
            <div class="battle-chip lives" id="lives-display">❤️❤️❤️</div>
          </div>
        </div>
      `;
      renderPlayerLevel(user);
      renderBattleStatus();
    }

    function renderPlayerLevel(data) {
      document.getElementById("player-level").textContent = `Lv.${data.level || 1}`;
      document.getElementById("player-exp-text").textContent =
        `${data.currentLevelExp ?? 0} / ${data.nextLevelExp ?? 100} EXP`;
      document.getElementById("exp-bar-fill").style.width =
        `${data.levelProgressPercent ?? 0}%`;
    }

    function renderJourneyProgress() {
      const totalScenes = appState.cities.reduce((sum, city) => sum + (city.total || 0), 0);
      const doneScenes = appState.cities.reduce((sum, city) => sum + (city.done || 0), 0);
      const completedCities = appState.cities.filter(city => city.defeated).length;
      const percent = totalScenes ? Math.round(doneScenes / totalScenes * 100) : 0;

      document.getElementById("journey-progress").innerHTML = `
        <div class="progress-card">
          <div class="section-head">
            <h2>旅程總覽</h2>
            <strong>${completedCities} / ${appState.cities.length} 城市完成</strong>
          </div>
          <div class="journey-line">
            <span>景點完成率：${doneScenes} / ${totalScenes}</span>
            <div class="progress" aria-label="旅程進度"><span style="--w:${percent}%"></span></div>
          </div>
        </div>
      `;
    }

function renderCityCards() {
      document.getElementById("city-list").innerHTML = `
        <div class="section-head">
          <h2>城市旅程</h2>
          <strong>點選城市查看景點</strong>
        </div>
        <div class="city-grid">
          ${appState.cities.map(city => {
            const itemProgress = progress(city);
            const status = cityStatus(city);
            const active = city.id === activeCityId;
            const icon = city.unlocked ? city.badgeIcon || "🎒" : "🔒";
            const bestRank = city.bestRank || "";
            const rankClass = bestRank ? `rank-${bestRank.toLowerCase()}` : "";
            const progressMarkup = city.unlocked ? `
              <p class="city-copy">城市進度：${itemProgress.done} / ${itemProgress.total}</p>
              <div class="progress" aria-label="${escapeHtml(city.name)} 進度"><span style="--w:${itemProgress.percent}%"></span></div>
              <div class="city-record">
                <span class="rank-badge ${rankClass}">最佳評價：${bestRank || "尚未通關"}</span>
                <span>最高 Combo：${city.bestCombo || 0}</span>
              </div>
              <div class="city-badge-line">🏅 ${escapeHtml(city.badgeName || city.name + "徽章")}</div>
            ` : `
              <p class="city-copy">${status.note}</p>
            `;

            return `
              <button class="city-card ${status.key} ${active ? "active" : ""}" type="button" data-city-id="${city.id}">
                <div class="city-title"><span class="city-icon">${icon}</span>${escapeHtml(city.name)}</div>
                ${progressMarkup}
                <span class="status-pill ${status.key}">狀態：${status.text}</span>
              </button>
            `;
          }).join("")}
        </div>
      `;

      document.querySelectorAll("[data-city-id]").forEach(button => {
        button.addEventListener("click", () => {
          activeCityId = Number(button.dataset.cityId);
          resetLocalBattleState();
          renderCityCards();
          renderPlayerSummary();
          renderCityDetail(activeCityId);
          loadFoodEvent(activeCityId, true);
        });
      });
    }

    function renderCityDetail(cityId) {
      if (!appState) return;
      const city = appState.cities.find(item => item.id === cityId) || activeCity();
      const itemProgress = progress(city);
      const status = cityStatus(city);
      const logsMarkup = (logs.length ? logs : [{ time: "--:--", text: "歡迎來到台灣旅行冒險。" }])
        .map(log => `<div class="log"><strong>${escapeHtml(log.time)}</strong> ${escapeHtml(log.text)}</div>`)
        .join("");

      if (!city.unlocked) {
        document.getElementById("city-detail").innerHTML = `
          <div class="detail-card">
            <div class="detail-hero">
              <h2>🔒 ${escapeHtml(city.name)}</h2>
              <p>${escapeHtml(status.note)}</p>
            </div>
            <div class="status-pill locked">狀態：尚未解鎖</div>
            <div class="log-list">${logsMarkup}</div>
          </div>
        `;
        return;
      }

      document.getElementById("city-detail").innerHTML = `
        <div class="detail-card">
          <div class="detail-hero">
            <h2>${escapeHtml(city.badgeIcon || "🎒")} ${escapeHtml(city.name)}</h2>
            <p class="story-text">${escapeHtml(city.story || city.intro || "")}</p>
          </div>
          <div class="section-head">
            <h2>景點列表</h2>
            <span class="status-pill ${status.key}">${status.text}</span>
          </div>
          <p class="city-copy">城市進度：${itemProgress.done} / ${itemProgress.total}，完成率 ${itemProgress.percent}%</p>
          <div class="progress" style="margin:10px 0 14px"><span style="--w:${itemProgress.percent}%"></span></div>
          <div class="difficulty-picker" aria-label="挑戰難度">
            ${Object.entries(DIFFICULTIES).map(([key, mode]) => `
              <button type="button" class="difficulty-option ${selectedDifficulty === key ? "active" : ""}"
                      data-difficulty="${key}" ${difficultyLocked ? "disabled" : ""}>
                <strong>${mode.label}</strong>
                <span>${mode.seconds} 秒 · ${mode.lives} 生命 · ${Math.round(mode.multiplier * 100)}% 獎勵</span>
              </button>
            `).join("")}
          </div>
          <div class="scene-list">
            ${city.scenes.map(scene => {
              const sceneActive = activeSceneQuizId === scene.id;
              return `
              <article class="scene-card ${scene.checked ? "done" : ""} ${sceneActive ? "active" : ""}">
                <div class="scene-image" ${scene.imageUrl ? `style="background-image: linear-gradient(180deg, rgba(255,255,255,.08), rgba(11,35,71,.18)), url('${escapeHtml(scene.imageUrl)}')"` : ""}></div>
                <div>
                  <h3>${scene.checked ? "✓ " : ""}${escapeHtml(scene.name)}</h3>
                  <p class="story-text">${escapeHtml(scene.story || scene.desc)}</p>
                  <div class="reward-row"><span>EXP +${scaledReward(scene.expReward)}</span><span>金幣 +${scaledReward(scene.coinReward)}</span></div>
                  ${scene.checked ? `
                    <button class="btn full" type="button" data-view-scene-story="${scene.id}">
                      ${escapeHtml(scene.actionLabel || "查看景點故事")}
                    </button>
                  ` : sceneActive ? `
                    <div class="quiz-box">
                      <strong>${escapeHtml(activeQuizQuestion?.question || scene.quizQuestion || "回答景點問題後完成打卡")}</strong>
                      <div class="quiz-timer" data-quiz-timer>剩餘 ${difficultyConfig().seconds} 秒</div>
                      <div class="quiz-options">
                        ${renderOptionButtons(activeQuizQuestion?.options || scene.quizOptions, "data-scene-id", scene.id)}
                      </div>
                    </div>
                  ` : `
                    <button class="btn full" type="button" data-start-scene-interaction="${scene.id}" ${cityLives <= 0 ? "disabled" : ""}>
                      ${escapeHtml(scene.actionLabel || "開始答題")}
                    </button>
                  `}
                </div>
              </article>
            `;
            }).join("")}
          </div>
          ${renderFoodEventCard(city)}
          ${renderBossChallenge(city, itemProgress)}
          <div class="log-list">${logsMarkup}</div>
        </div>
      `;

      document.querySelectorAll("[data-start-scene-interaction]").forEach(button => {
        button.addEventListener("click", () => {
          const scene = city.scenes.find(item => item.id === Number(button.dataset.startSceneInteraction));
          startSceneInteraction(scene);
        });
      });

      document.querySelectorAll("[data-view-scene-story]").forEach(button => {
        button.addEventListener("click", async () => {
          await openCollection();
          selectedCollectionId = `landmark-${button.dataset.viewSceneStory}`;
          renderCollection();
        });
      });

      document.querySelectorAll("[data-difficulty]").forEach(button => {
        button.addEventListener("click", () => {
          selectedDifficulty = button.dataset.difficulty;
          resetLocalBattleState();
          renderPlayerSummary();
          renderCityDetail(activeCityId);
        });
      });

      document.querySelectorAll("[data-scene-id]").forEach(button => {
        button.addEventListener("click", () => checkin(Number(button.dataset.sceneId), button.dataset.answer, button.dataset.answerText));
      });

      document.querySelectorAll("[data-start-boss-city-id]:not([data-start-boss-food-key]):not([data-start-boss-no-food])").forEach(button => {
        button.addEventListener("click", () => prepareBossChallenge(Number(button.dataset.startBossCityId)));
      });

      document.querySelectorAll("[data-start-boss-food-key]").forEach(button => {
        button.addEventListener("click", () => startBossQuiz(
          Number(button.dataset.startBossCityId),
          button.dataset.startBossFoodKey || null
        ));
      });

      document.querySelectorAll("[data-start-boss-no-food]").forEach(button => {
        button.addEventListener("click", () => startBossQuiz(
          Number(button.dataset.startBossCityId),
          null
        ));
      });

      document.querySelectorAll("[data-boss-city-id]").forEach(button => {
        button.addEventListener("click", () => challengeBoss(button.dataset.answer, button.dataset.answerText));
      });

      document.querySelectorAll("[data-restart-city-id]").forEach(button => {
        button.addEventListener("click", () => restartCity(Number(button.dataset.restartCityId)));
      });

      bindFoodEventActions();
      startActiveQuizTimer();
    }

    function renderBossChallenge(city, itemProgress) {
      if (city.defeated) {
        return `
          <div class="detail-actions">
            <button class="btn red full" type="button" disabled>首領已擊敗，徽章已取得</button>
            <button class="btn full" type="button" data-restart-city-id="${city.id}">重新挑戰城市</button>
          </div>
        `;
      }
      if (itemProgress.done < itemProgress.total) {
        return `
          <div class="detail-actions">
            <button class="btn red full" type="button" disabled>還差 ${itemProgress.total - itemProgress.done} 個景點</button>
          </div>
        `;
      }
      if (bossPreparationCityId === city.id && activeBossQuizCityId !== city.id) {
        return renderBossPreparation(city);
      }
      if (activeBossQuizCityId !== city.id) {
        return `
          <div class="detail-actions">
            <button class="btn red full" type="button" data-start-boss-city-id="${city.id}" ${cityLives <= 0 ? "disabled" : ""}>開始挑戰守護者</button>
          </div>
        `;
      }
      return `
        <div class="detail-actions quiz-box">
          ${activeBossBattle?.activeFood ? `
            <div class="boss-active-food">
              <strong>已使用：${escapeHtml(activeBossBattle.activeFood.name)}</strong>
              <span>本題時間：${activeBossBattle.questionSeconds} 秒</span>
            </div>
          ` : `
            <div class="boss-active-food neutral">
              <strong>本場未使用補給</strong>
              <span>本題時間：${activeBossBattle?.questionSeconds || activeQuizQuestion?.seconds || difficultyConfig().seconds} 秒</span>
            </div>
          `}
          <strong>${escapeHtml(city.bossName)}：${escapeHtml(activeQuizQuestion?.question || city.bossQuestion || "回答首領問題")}</strong>
          <div class="quiz-timer" data-quiz-timer>剩餘 ${activeQuizQuestion?.seconds || difficultyConfig().seconds} 秒</div>
          <div class="quiz-options">
            ${renderOptionButtons(activeQuizQuestion?.options || city.bossOptions, "data-boss-city-id", city.id)}
          </div>
        </div>
      `;
    }

    function renderBossPreparation(city) {
      const event = Number(foodEventState.cityId) === Number(city.id) ? foodEventState.event : null;
      const beefSoup = event?.foodKey === "TAINAN_BEEF_SOUP" ? event : null;
      const unlocked = Boolean(beefSoup?.claimed);
      return `
        <div class="detail-actions boss-preparation">
          <div>
            <span class="status-pill challenge">守護者挑戰準備</span>
            <h3>${escapeHtml(city.bossName)}</h3>
            <p>選擇本場旅途補給，開始後無法切換。</p>
          </div>
          ${beefSoup ? `
            <div class="boss-supply-option ${unlocked ? "unlocked" : "locked"}">
              <div>
                <strong>🥣 ${escapeHtml(beefSoup.name)}</strong>
                <p>${unlocked ? escapeHtml(beefSoup.effect?.description) : "完成台南美食文化事件後解鎖"}</p>
              </div>
              <button class="btn" type="button" data-start-boss-city-id="${city.id}"
                      data-start-boss-food-key="TAINAN_BEEF_SOUP" ${unlocked ? "" : "disabled"}>
                ${unlocked ? "使用牛肉湯" : "尚未解鎖"}
              </button>
            </div>
          ` : ""}
          <button class="btn ghost full" type="button" data-start-boss-city-id="${city.id}" data-start-boss-no-food>
            不使用補給
          </button>
        </div>
      `;
    }

    function renderAll() {
      if (!appState) return;
      renderPlayerSummary();
      renderJourneyProgress();
      renderExplorationMission();
      renderImageRecognition();
      renderDailyMissions();
      renderAchievements();
      renderCityCards();
      renderCityDetail(activeCityId);
    }
