const CITY_MAP_POSITIONS = Object.freeze({
      1: { top: "12%", left: "61%" },
      2: { top: "35%", left: "43%" },
      3: { top: "61%", left: "34%" },
      4: { top: "76%", left: "31%" },
      5: { top: "48%", left: "70%" },
      6: { top: "67%", left: "10%" }
    });

const CITY_ROUTE_POSITIONS = Object.freeze({
      1: { left: "22%", top: "78%" },
      2: { left: "70%", top: "59%" },
      3: { left: "30%", top: "37%" },
      4: { left: "72%", top: "16%" }
    });

const CITY_MAP_THEMES = Object.freeze({
      1: {
        key: "taipei",
        title: "臺北城市冒險",
        subtitle: "穿越現代地標與歷史文化",
        icon: "🏙️",
        backgroundClass: "city-route-board--taipei",
        decorations: [
          { icon: "🏙️", className: "city-route-decoration--primary" },
          { icon: "🏮", className: "city-route-decoration--secondary" }
        ]
      },
      2: {
        key: "taichung",
        title: "臺中城市冒險",
        subtitle: "探索濕地、建築與繽紛聚落",
        icon: "🌅",
        backgroundClass: "city-route-board--taichung",
        decorations: [
          { icon: "🌾", className: "city-route-decoration--primary" },
          { icon: "🌅", className: "city-route-decoration--secondary" }
        ]
      },
      3: {
        key: "tainan",
        title: "臺南古城冒險",
        subtitle: "穿越府城古蹟與歷史記憶",
        icon: "🏯",
        backgroundClass: "city-route-board--tainan",
        decorations: [
          { icon: "🏯", className: "city-route-decoration--primary" },
          { icon: "🧱", className: "city-route-decoration--secondary" }
        ]
      },
      4: {
        key: "kaohsiung",
        title: "高雄港都冒險",
        subtitle: "探索港灣、藝術與城市文化",
        icon: "⚓",
        backgroundClass: "city-route-board--kaohsiung",
        decorations: [
          { icon: "⚓", className: "city-route-decoration--primary" },
          { icon: "🎨", className: "city-route-decoration--secondary" }
        ]
      },
      5: {
        key: "hualien",
        title: "花蓮山海冒險",
        subtitle: "走入峽谷、海岸與自然秘境",
        icon: "⛰️",
        backgroundClass: "city-route-board--hualien",
        decorations: [
          { icon: "⛰️", className: "city-route-decoration--primary" },
          { icon: "🌊", className: "city-route-decoration--secondary" }
        ]
      },
      6: {
        key: "penghu",
        title: "澎湖海島冒險",
        subtitle: "航向石滬、跨海大橋與花火之夜",
        icon: "🏝️",
        backgroundClass: "city-route-board--penghu",
        decorations: [
          { icon: "🏝️", className: "city-route-decoration--primary" },
          { icon: "🐚", className: "city-route-decoration--secondary" }
        ]
      }
    });

function cityRouteLandmarks(city) {
      if (Array.isArray(city?.landmarks)) return city.landmarks;
      return Array.isArray(city?.scenes) ? city.scenes : [];
    }

function shouldRenderAdventureMap(city) {
      const landmarks = cityRouteLandmarks(city);
      return landmarks.length === 3
        && landmarks.every(landmark => landmark.stageConfigured === true)
        && city?.bossStage != null
        && getCityMapTheme(city) != null;
    }

function getCityMapTheme(city) {
      return CITY_MAP_THEMES[Number(city?.unlockOrder)] ?? {
        key: "default",
        title: `${city?.name || "城市"}城市冒險`,
        subtitle: "完成景點關卡，挑戰城市守護者",
        icon: "🧭",
        backgroundClass: "city-route-board--default",
        decorations: [
          { icon: "🧭", className: "city-route-decoration--primary" },
          { icon: "✨", className: "city-route-decoration--secondary" }
        ]
      };
    }

function buildCityRouteStages(city) {
      const landmarks = cityRouteLandmarks(city);
      const stages = [...landmarks]
        .sort((first, second) => Number(first.stageOrder) - Number(second.stageOrder))
        .map(landmark => ({
          id: landmark.id,
          cityId: city.id,
          order: Number(landmark.stageOrder),
          label: landmark.stageLabel,
          name: landmark.name,
          status: landmark.stageStatus,
          actionLabel: landmark.actionLabel,
          interactionType: landmark.interactionType,
          type: "LANDMARK",
          source: landmark
        }));

      if (city?.bossStage) {
        stages.push({
          id: `boss-${city.id}`,
          cityId: city.id,
          order: Number(city.bossStage.stageOrder),
          label: city.bossStage.stageLabel,
          name: city.bossStage.bossName,
          status: city.bossStage.stageStatus,
          actionLabel: city.bossStage.actionLabel,
          interactionType: "BOSS",
          type: "BOSS",
          source: city.bossStage
        });
      }

      return stages.sort((first, second) => first.order - second.order);
    }

function findCurrentStage(stages) {
      const available = stages.find(stage => stage.status === "AVAILABLE");
      if (available) return available;

      return [...stages]
        .filter(stage => stage.status === "COMPLETED")
        .sort((first, second) => second.order - first.order)[0] ?? null;
    }

function getRouteStageStatusText(status) {
      if (status === "COMPLETED") return "已完成";
      if (status === "AVAILABLE") return "可以挑戰";
      return "尚未解鎖";
    }

function getRouteInteractionText(stage) {
      if (stage.type === "BOSS") return "城市守護者挑戰";
      return {
        EXPLORATION: "探索推理",
        IMAGE_RECOGNITION: "圖片辨識",
        QUIZ: "文化問答"
      }[stage.interactionType] || "景點挑戰";
    }

function getRouteStageIcon(stage) {
      if (stage.status === "LOCKED") return "🔒";
      if (stage.status === "COMPLETED") return "✓";
      if (stage.type === "BOSS") return "👹";
      return {
        EXPLORATION: "🔍",
        IMAGE_RECOGNITION: "🖼️",
        QUIZ: "❓"
      }[stage.interactionType] || "📍";
    }

function renderCityRouteDecorations(theme) {
      return (theme.decorations || [])
        .map(decoration => `
          <span class="city-route-decoration ${escapeHtml(decoration.className)}" aria-hidden="true">
            ${escapeHtml(decoration.icon)}
          </span>
        `)
        .join("");
    }

function renderCityRouteNode(stage, currentStage) {
      const position = CITY_ROUTE_POSITIONS[stage.order];
      if (!position) return "";
      const status = String(stage.status || "LOCKED").toLowerCase();
      const isCurrent = currentStage?.id === stage.id;

      return `
        <button
          type="button"
          class="city-route-node city-route-node--${status} ${stage.type === "BOSS" ? "city-route-node--boss" : ""} ${isCurrent ? "city-route-node--current" : ""}"
          data-city-route-stage="${escapeHtml(stage.id)}"
          style="left:${position.left}; top:${position.top};"
          aria-label="${escapeHtml(stage.label || `第 ${stage.order} 關`)}，${escapeHtml(stage.name)}，${getRouteStageStatusText(stage.status)}"
          aria-disabled="${stage.status === "LOCKED"}"
        >
          <span class="city-route-node__icon" aria-hidden="true">${getRouteStageIcon(stage)}</span>
          <span class="city-route-node__order">${escapeHtml(stage.label || `第 ${stage.order} 關`)}</span>
          <strong>${escapeHtml(stage.name)}</strong>
          <small>${getRouteStageStatusText(stage.status)}</small>
        </button>
      `;
    }

function renderCityAdventureMap(city) {
      const container = document.getElementById("city-detail");
      if (!container || !city) return;

      const theme = getCityMapTheme(city);
      const stages = buildCityRouteStages(city);
      const currentStage = findCurrentStage(stages);
      const status = cityStatus(city);
      const completedCount = stages.filter(stage => stage.status === "COMPLETED").length;
      const currentPosition = currentStage ? CITY_ROUTE_POSITIONS[currentStage.order] : null;
      const activeLandmark = stages.find(stage =>
        stage.type === "LANDMARK" && Number(stage.id) === Number(activeSceneQuizId)
      );
      const activeChallenge = activeLandmark
        ? `
          <section class="city-route-active-challenge" aria-label="目前景點挑戰">
            <span class="city-route-kicker">${escapeHtml(activeLandmark.label)} · ${escapeHtml(activeLandmark.name)}</span>
            ${renderActiveSceneQuiz(activeLandmark.source)}
          </section>
        `
        : Number(activeBossQuizCityId) === Number(city.id)
          ? `
            <section class="city-route-active-challenge city-route-active-challenge--boss" aria-label="目前守護者挑戰">
              <span class="city-route-kicker">${escapeHtml(city.bossStage?.stageLabel || "第 4 關")} · ${escapeHtml(city.bossName)}</span>
              ${renderActiveBossQuiz(city)}
            </section>
          `
          : "";
      const routeTitle = stages.map(stage => stage.name).join(" → ");
      const logsMarkup = (logs.length ? logs : [{ time: "--:--", text: `歡迎來到${city.name}旅行冒險。` }])
        .map(log => `<div class="log"><strong>${escapeHtml(log.time)}</strong> ${escapeHtml(log.text)}</div>`)
        .join("");

      container.innerHTML = `
        <div class="detail-card city-adventure-detail" data-city-theme="${escapeHtml(theme.key)}">
          <div class="detail-hero city-adventure-hero city-adventure-hero--${escapeHtml(theme.key)}">
            <span class="city-route-kicker">${escapeHtml(theme.icon)} ${escapeHtml(theme.title)}</span>
            <h2>${escapeHtml(city.badgeIcon || theme.icon)} ${escapeHtml(city.name)}旅行路線</h2>
            <p>${escapeHtml(theme.subtitle)}</p>
          </div>
          <div class="section-head">
            <div>
              <h2>城市內旅行路線</h2>
              <p class="city-copy">點擊節點查看景點情報，再決定是否開始挑戰。</p>
            </div>
            <span class="status-pill ${status.key}">${completedCount} / ${stages.length} 關完成</span>
          </div>
          <div class="difficulty-picker" aria-label="挑戰難度">
            ${Object.entries(DIFFICULTIES).map(([key, mode]) => `
              <button type="button" class="difficulty-option ${selectedDifficulty === key ? "active" : ""}"
                      data-route-difficulty="${key}" ${difficultyLocked ? "disabled" : ""}>
                <strong>${mode.label}</strong>
                <span>${mode.seconds} 秒 · ${mode.lives} 生命 · ${Math.round(mode.multiplier * 100)}% 獎勵</span>
              </button>
            `).join("")}
          </div>
          <section class="city-adventure-map city-route-board ${escapeHtml(theme.backgroundClass)}"
                   data-city-theme="${escapeHtml(theme.key)}"
                   aria-label="${escapeHtml(city.name)}四關旅行路線">
            <div class="city-adventure-map__header">
              <div>
                <span class="city-route-kicker">${escapeHtml(theme.title)}</span>
                <h3>${escapeHtml(routeTitle)}</h3>
              </div>
              <strong>${currentStage
                ? `🎒 玩家目前在${escapeHtml(currentStage.label || `第 ${currentStage.order} 關`)}`
                : "旅程尚未開始"}</strong>
            </div>
            <div class="city-adventure-map__canvas">
              ${renderCityRouteDecorations(theme)}
              <svg class="city-route-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <path d="M22 78 C39 72, 55 65, 70 59 C56 52, 42 43, 30 37 C46 29, 61 22, 72 16"></path>
              </svg>
              ${stages.map(stage => renderCityRouteNode(stage, currentStage)).join("")}
              ${currentStage && currentPosition ? `
                <div class="city-route-player" style="left:${currentPosition.left}; top:${currentPosition.top};"
                     aria-label="玩家目前在${escapeHtml(currentStage.label)}">
                  <span aria-hidden="true">🎒</span>
                </div>
              ` : ""}
            </div>
            <div class="city-route-legend" aria-label="關卡狀態圖例">
              <span>🔍／🖼️／❓ 可挑戰</span>
              <span>✓ 已完成</span>
              <span>🔒 尚未解鎖</span>
              <span>👹 城市守護者</span>
            </div>
            <div id="city-route-stage-modal" class="city-route-modal" hidden>
              <button type="button" class="city-route-modal__backdrop" data-city-route-close aria-label="關閉景點情報"></button>
              <section class="city-route-info" role="dialog" aria-modal="true" aria-labelledby="cityRouteInfoTitle">
                <button type="button" class="city-route-info__close" data-city-route-close aria-label="關閉">×</button>
                <div id="city-route-stage-info"></div>
              </section>
            </div>
          </section>
          ${activeChallenge}
          <div class="log-list">${logsMarkup}</div>
        </div>
      `;

      bindCityAdventureMapEvents(city, stages);
      startActiveQuizTimer();
    }

function openCityRouteStageInfo(city, stage, stages) {
      const modal = document.getElementById("city-route-stage-modal");
      const content = document.getElementById("city-route-stage-info");
      if (!modal || !content || !stage) return;

      const locked = stage.status === "LOCKED";
      const completed = stage.status === "COMPLETED";
      const previous = stages
        .filter(item => item.order < stage.order)
        .sort((first, second) => second.order - first.order)[0];
      const description = stage.type === "BOSS"
        ? `完成城市最終文化挑戰，取得${city.name}徽章。`
        : completed
          ? stage.source.story || stage.source.desc || "這個景點已收錄至收藏圖鑑。"
          : stage.source.desc || "完成挑戰後解鎖景點故事。";

      content.innerHTML = locked
        ? `
          <span class="city-route-info__status city-route-info__status--locked">🔒 鎖定</span>
          <h3 id="cityRouteInfoTitle">${escapeHtml(stage.name)}尚未解鎖</h3>
          <p>請先完成${previous ? `「${escapeHtml(previous.name)}」` : "上一個關卡"}。</p>
          <div class="city-route-info__hint">鎖定節點僅提供解鎖提示，不會發出挑戰請求。</div>
        `
        : `
          <span class="city-route-info__status city-route-info__status--${completed ? "completed" : "available"}">
            ${completed ? "✓ 已完成" : "📍 可挑戰"}
          </span>
          <h3 id="cityRouteInfoTitle">${escapeHtml(stage.name)}</h3>
          <strong class="city-route-info__label">${escapeHtml(stage.label || `第 ${stage.order} 關`)}</strong>
          <p>${escapeHtml(description)}</p>
          <div class="city-route-info__meta">
            <span><small>玩法</small>${escapeHtml(getRouteInteractionText(stage))}</span>
            <span><small>狀態</small>${getRouteStageStatusText(stage.status)}</span>
          </div>
          <div class="city-route-info__actions">
            ${completed && stage.type === "LANDMARK" ? `
              <button class="btn ghost" type="button" data-route-stage-story="${stage.id}">查看故事</button>
            ` : ""}
            <button class="btn ${stage.type === "BOSS" ? "red" : ""}" type="button" data-route-stage-start>
              ${completed ? "再次挑戰" : "開始挑戰"}
            </button>
          </div>
        `;

      modal.hidden = false;

      content.querySelector("[data-route-stage-story]")?.addEventListener("click", event => {
        runWithButtonLock(event.currentTarget, async () => {
          closeCityRouteStageInfo();
          await viewSceneStory(stage.id);
        });
      });
      content.querySelector("[data-route-stage-start]")?.addEventListener("click", event => {
        runWithButtonLock(event.currentTarget, async () => {
          closeCityRouteStageInfo();
          if (stage.type === "BOSS") {
            await openBossDifficultySelection(stage.cityId);
            return;
          }
          await dispatchLandmarkInteraction(stage.source);
        });
      });
    }

function closeCityRouteStageInfo() {
      const modal = document.getElementById("city-route-stage-modal");
      if (modal) modal.hidden = true;
    }

function bindCityAdventureMapEvents(city, stages) {
      document.querySelectorAll("[data-city-route-stage]").forEach(button => {
        button.addEventListener("click", () => {
          const stage = stages.find(item => String(item.id) === button.dataset.cityRouteStage);
          openCityRouteStageInfo(city, stage, stages);
        });
      });

      document.querySelectorAll("[data-city-route-close]").forEach(button => {
        button.addEventListener("click", closeCityRouteStageInfo);
      });

      document.querySelectorAll("[data-route-difficulty]").forEach(button => {
        button.addEventListener("click", () => {
          selectedDifficulty = button.dataset.routeDifficulty;
          resetLocalBattleState();
          renderPlayerSummary();
          renderCityAdventureMap(city);
        });
      });

      document.querySelectorAll("[data-scene-id]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          checkin(Number(button.dataset.sceneId), button.dataset.answer, button.dataset.answerText)
        ));
      });

      document.querySelectorAll("[data-boss-city-id]").forEach(button => {
        button.addEventListener("click", () => runWithButtonLock(button, () =>
          challengeBoss(button.dataset.answer, button.dataset.answerText)
        ));
      });
    }

function dispatchLandmarkInteraction(scene) {
      return startSceneInteraction(scene);
    }

function openBossDifficultySelection(cityId) {
      return startBossQuiz(Number(cityId));
    }

async function refreshCurrentCityAdventureMap(cityId) {
      await refreshState();
      const city = appState?.cities?.find(item => Number(item.id) === Number(cityId));
      if (!city) {
        openTaiwanMapView();
        return;
      }
      activeCityId = Number(city.id);
      journeyView = "city";
      if (shouldRenderAdventureMap(city)) {
        renderCityAdventureMap(city);
      } else {
        renderCityDetail(city.id);
      }
    }

function getCityMapStatus(city) {
      if (!city?.unlocked) return "LOCKED";
      if (city.bossStage?.stageStatus === "COMPLETED" || city.defeated) return "COMPLETED";
      return "AVAILABLE";
    }

function getCityStatusIcon(status) {
      if (status === "LOCKED") return "🔒";
      if (status === "COMPLETED") return "🏅";
      return "📍";
    }

function getCityStatusText(status) {
      if (status === "LOCKED") return "尚未解鎖";
      if (status === "COMPLETED") return "城市已完成";
      return "可以進入";
    }

function getCityProgressText(city) {
      if (!city?.unlocked) return "尚未解鎖";

      const completedScenes = Array.isArray(city.scenes)
        ? city.scenes.filter(scene => scene.stageStatus === "COMPLETED" || scene.checked).length
        : Number(city.done || 0);
      const bossCompleted = city.bossStage?.stageStatus === "COMPLETED" || city.defeated ? 1 : 0;
      return `${Math.min(completedScenes + bossCompleted, 4)} / 4`;
    }

function renderCityMapNode(city) {
      const position = CITY_MAP_POSITIONS[Number(city.unlockOrder)];
      if (!position) return "";

      const status = getCityMapStatus(city);
      const active = Number(city.id) === Number(activeCityId);
      return `
        <button
          type="button"
          class="city-map-node city-map-node--${status.toLowerCase()} ${active ? "city-map-node--active" : ""}"
          data-city-map-id="${Number(city.id)}"
          style="top:${position.top}; left:${position.left};"
          aria-label="${escapeHtml(city.name)}，${getCityStatusText(status)}，進度 ${getCityProgressText(city)}"
          aria-disabled="${status === "LOCKED"}"
        >
          <span class="city-map-node__icon" aria-hidden="true">${getCityStatusIcon(status)}</span>
          <span class="city-map-node__name">${escapeHtml(city.name)}</span>
          <span class="city-map-node__progress">${getCityProgressText(city)}</span>
        </button>
      `;
    }

function renderTaiwanMap(cities) {
      const container = document.getElementById("taiwan-city-nodes");
      if (!container || !Array.isArray(cities)) return;

      const message = document.getElementById("taiwan-map-message");
      if (message) message.textContent = "";

      const orderedCities = [...cities].sort(
        (first, second) => Number(first.unlockOrder) - Number(second.unlockOrder)
      );
      container.innerHTML = orderedCities.map(renderCityMapNode).join("");

      const completedCount = orderedCities.filter(
        city => getCityMapStatus(city) === "COMPLETED"
      ).length;
      const summary = document.getElementById("taiwan-map-summary");
      if (summary) summary.textContent = `${completedCount} / ${orderedCities.length} 城市完成`;

      bindCityMapEvents(orderedCities);
    }

function bindCityMapEvents(cities) {
      document.querySelectorAll("[data-city-map-id]").forEach(button => {
        button.addEventListener("click", () => {
          const cityId = Number(button.dataset.cityMapId);
          const city = cities.find(item => Number(item.id) === cityId);
          if (!city) return;

          if (!city.unlocked) {
            const message = document.getElementById("taiwan-map-message");
            if (message) {
              message.textContent = `${city.name}尚未解鎖，請先擊敗上一座城市的守護者。`;
            }
            return;
          }

          openCityStageView(city);
        });
      });
    }

function syncJourneyView() {
      const mapView = document.getElementById("taiwan-map-view");
      const cityView = document.getElementById("city-stage-view");
      if (!mapView || !cityView) return;

      const cityIsOpen = journeyView === "city";
      mapView.toggleAttribute("hidden", cityIsOpen);
      cityView.toggleAttribute("hidden", !cityIsOpen);
    }

function openCityStageView(city) {
      if (!city?.unlocked) return;

      activeCityId = Number(city.id);
      journeyView = "city";
      resetLocalBattleState();
      renderPlayerSummary();
      renderCityDetail(activeCityId);
      renderTaiwanMap(appState?.cities);
      syncJourneyView();
      document.getElementById("city-stage-view")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }

function openTaiwanMapView() {
      journeyView = "map";
      renderTaiwanMap(appState?.cities);
      syncJourneyView();
      document.getElementById("taiwan-map-view")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
