function journeyTotals() {
      const doneScenes = appState.cities.reduce((sum, city) => sum + (city.done || 0), 0);
      const completedCities = appState.cities.filter(city => city.defeated).length;
      const badgeCount = appState.cities.filter(city => city.badgeUnlocked).length;
      const firstCity = appState.cities.reduce((first, city) => {
        if (!first) return city;
        return Number(city.unlockOrder) < Number(first.unlockOrder) ? city : first;
      }, null);
      return { doneScenes, completedCities, badgeCount, firstCityCompleted: Boolean(firstCity?.defeated) };
    }

    function missionRow(label, current, target) {
      const capped = Math.min(current, target);
      const done = capped >= target;
      return `
        <div class="mission-item ${done ? "done" : ""}">
          <div>${done ? "✅" : "□"} ${label}<br><span>${capped} / ${target}</span></div>
          <strong>${Math.round((capped / target) * 100)}%</strong>
        </div>
      `;
    }

    function missionRowFromApi(mission) {
      return missionRow(mission.label, Number(mission.current || 0), Number(mission.target || 1));
    }

    function renderDailyMissions() {
      const totals = journeyTotals();
      const fallbackMissions = [
        { label: "完成景點", current: totals.doneScenes, target: 2 },
        { label: "答對題目", current: totals.doneScenes, target: 3 },
        { label: "Boss 挑戰", current: totals.completedCities, target: 1 }
      ];
      const missionRows = (missionsState?.missions || fallbackMissions)
              .map(missionRowFromApi)
              .join("");
      const allDone = missionsState?.completed ??
              fallbackMissions.every(mission => mission.current >= mission.target);
      const rewardExp = missionsState?.rewardExp ?? 100;
      const rewardCoins = missionsState?.rewardCoins ?? 50;

      document.getElementById("daily-missions").innerHTML = `
        <div class="progress-card">
          <div class="section-head">
            <h2>今日任務</h2>
            <strong>${allDone ? "可領取獎勵" : "冒險目標"}</strong>
          </div>
          <div class="mission-list">
            ${missionRows}
          </div>
          <div class="mission-reward">完成獎勵：EXP +${rewardExp}　金幣 +${rewardCoins}</div>
        </div>
      `;
    }

    function achievementItem(unlocked, title, description) {
      return `
        <div class="achievement-item ${unlocked ? "" : "locked"}">
          <div>${unlocked ? "✅" : "🔒"} ${title}</div>
          <span>${description}</span>
        </div>
      `;
    }

    function renderAchievements() {
      const totals = journeyTotals();
      const fallbackAchievements = [
        { title: "初次探索", description: "完成第一次景點打卡", unlocked: totals.doneScenes >= 1 },
        { title: "台北征服者", description: "完成第一座城市", unlocked: totals.firstCityCompleted },
        { title: "三題連勝", description: "累積答對 3 題", unlocked: totals.doneScenes >= 3 },
        { title: "徽章收藏家", description: "收集 3 枚城市徽章", unlocked: totals.badgeCount >= 3 },
        { title: "台灣冒險王", description: "完成全部 6 座城市", unlocked: totals.completedCities >= 6 }
      ];
      const achievements = achievementsState?.achievements || fallbackAchievements;
      const unlockedCount = achievementsState?.unlockedCount ?? achievements.filter(item => item.unlocked).length;
      const totalAchievements = achievementsState?.total ?? achievements.length;

      document.getElementById("achievements").innerHTML = `
        <div class="progress-card">
          <div class="section-head">
            <h2>成就</h2>
            <strong>${unlockedCount} / ${totalAchievements} 已解鎖</strong>
          </div>
          <div class="achievement-grid">
            ${achievements.map(item => achievementItem(item.unlocked, item.title, item.description)).join("")}
          </div>
        </div>
      `;
    }
