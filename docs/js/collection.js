function formatDateTime(value) {
      if (!value) return "尚未取得";
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return "尚未取得";
      return date.toLocaleString("zh-TW", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
      });
    }

    async function openCollection() {
      try {
        stopQuizTimer();
        collectionState = await api("/api/collection");
        collectionTab = "landmarks";
        selectedCollectionId = null;
        document.getElementById("collectionOverlay").classList.remove("hidden");
        renderCollection();
      } catch (error) {
        addLog(error.message);
      }
    }

    function closeCollection() {
      document.getElementById("collectionOverlay").classList.add("hidden");
      selectedCollectionId = null;
      renderCityDetail(activeCityId);
    }

    function collectionItems() {
      if (!collectionState) return [];
      return collectionTab === "badges"
        ? collectionState.badges || []
        : collectionState.landmarks || [];
    }

    function collectionItemId(item) {
      return collectionTab === "badges" ? `badge-${item.cityId}` : `landmark-${item.id}`;
    }

    function ensureSelectedCollectionItem(items) {
      if (!items.length) {
        selectedCollectionId = null;
        return null;
      }
      const selected = items.find(item => collectionItemId(item) === selectedCollectionId);
      if (selected) return selected;
      const firstUnlocked = items.find(item => item.unlocked || item.badgeUnlocked) || items[0];
      selectedCollectionId = collectionItemId(firstUnlocked);
      return firstUnlocked;
    }

    function renderCollection() {
      const overlay = document.getElementById("collectionOverlay");
      if (!collectionState || overlay.classList.contains("hidden")) return;

      document.getElementById("collectionProgress").textContent =
        `景點 ${collectionState.completedLandmarks || 0} / ${collectionState.totalLandmarks || 0}　徽章 ${collectionState.unlockedBadges || 0} / ${collectionState.totalBadges || 0}`;

      document.querySelectorAll("[data-collection-tab]").forEach(button => {
        button.classList.toggle("active", button.dataset.collectionTab === collectionTab);
      });

      const items = collectionItems();
      const selected = ensureSelectedCollectionItem(items);
      document.getElementById("collectionGrid").innerHTML = items
        .map(item => collectionTab === "badges" ? renderBadgeCollectionCard(item) : renderLandmarkCollectionCard(item))
        .join("");
      renderCollectionDetail(selected);

      document.querySelectorAll("[data-collection-id]").forEach(button => {
        button.addEventListener("click", () => {
          selectedCollectionId = button.dataset.collectionId;
          renderCollection();
        });
      });
    }

    function renderLandmarkCollectionCard(item) {
      const id = collectionItemId(item);
      const unlocked = Boolean(item.unlocked);
      return `
        <button class="collection-card ${unlocked ? "" : "locked"} ${id === selectedCollectionId ? "active" : ""}" type="button" data-collection-id="${id}">
          <strong>${unlocked ? "✅" : "🔒"} ${escapeHtml(item.name)}</strong>
          <span>${escapeHtml(item.cityName)}</span>
          <span>${unlocked ? "已收錄景點故事" : "完成對應景點後開放"}</span>
        </button>
      `;
    }

    function renderBadgeCollectionCard(item) {
      const id = collectionItemId(item);
      const unlocked = Boolean(item.badgeUnlocked);
      const rank = item.bestRank ? `最佳評價：${escapeHtml(item.bestRank)}` : "尚未通關";
      return `
        <button class="collection-card ${unlocked ? "" : "locked"} ${id === selectedCollectionId ? "active" : ""}" type="button" data-collection-id="${id}">
          <strong>${unlocked ? escapeHtml(item.badgeIcon || "🏅") : "🔒"} ${escapeHtml(item.badgeName)}</strong>
          <span>${escapeHtml(item.cityName)}</span>
          <span>${unlocked ? rank : "完成城市守護者後取得"}</span>
        </button>
      `;
    }

    function renderCollectionDetail(item) {
      const detail = document.getElementById("collectionDetail");
      if (!item) {
        detail.innerHTML = "<h3>尚無收藏</h3><p>完成景點或城市後，收藏會出現在這裡。</p>";
        return;
      }

      if (collectionTab === "badges") {
        renderBadgeCollectionDetail(item);
      } else {
        renderLandmarkCollectionDetail(item);
      }
    }

    function renderLandmarkCollectionDetail(item) {
      const detail = document.getElementById("collectionDetail");
      if (!item.unlocked) {
        detail.innerHTML = `
          <h3>??? 未解鎖</h3>
          <p>完成 ${escapeHtml(item.cityName)} 的對應景點後，將開放圖片、NPC 故事與文化介紹。</p>
        `;
        return;
      }

      detail.innerHTML = `
        ${item.imageUrl ? `<img class="collection-detail-image" src="${escapeHtml(item.imageUrl)}" alt="${escapeHtml(item.realName || item.name)}">` : ""}
        <h3>${escapeHtml(item.realName || item.name)}</h3>
        <p><strong>${escapeHtml(item.cityName)}</strong></p>
        <p>${escapeHtml(item.story || "尚未收錄 NPC 故事。")}</p>
        <p>${escapeHtml(item.description || "尚未收錄歷史文化介紹。")}</p>
        <div class="collection-meta">
          <div><span>第一次完成</span><strong>${formatDateTime(item.completedAt)}</strong></div>
          <div><span>獲得 EXP</span><strong>+${formatNumber(item.earnedExp || 0)}</strong></div>
          <div><span>獲得金幣</span><strong>+${formatNumber(item.earnedCoins || 0)}</strong></div>
        </div>
      `;
    }

    function renderBadgeCollectionDetail(item) {
      const detail = document.getElementById("collectionDetail");
      if (!item.badgeUnlocked) {
        detail.innerHTML = `
          <h3>??? 未取得</h3>
          <p>完成 ${escapeHtml(item.cityName)} 的全部景點並擊敗城市守護者後，徽章會收進收藏櫃。</p>
        `;
        return;
      }

      detail.innerHTML = `
        <h3>${escapeHtml(item.badgeIcon || "🏅")} ${escapeHtml(item.realBadgeName || item.badgeName)}</h3>
        <p>${escapeHtml(item.cityName)} 城市徽章</p>
        <p>${item.badgeQuote ? `「${escapeHtml(item.badgeQuote)}」` : "完成城市探索後獲得的榮譽徽章。"}</p>
        <div class="collection-meta">
          <div><span>取得日期</span><strong>${formatDateTime(item.acquiredAt)}</strong></div>
          <div><span>最佳評價</span><strong>${escapeHtml(item.bestRank || "尚未紀錄")}</strong></div>
          <div><span>最高 Combo</span><strong>${formatNumber(item.bestCombo || 0)}</strong></div>
        </div>
      `;
    }
