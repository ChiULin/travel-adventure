const CITY_MAP_POSITIONS = Object.freeze({
      1: { top: "12%", left: "61%" },
      2: { top: "35%", left: "43%" },
      3: { top: "61%", left: "34%" },
      4: { top: "76%", left: "31%" },
      5: { top: "48%", left: "70%" },
      6: { top: "67%", left: "10%" }
    });

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
