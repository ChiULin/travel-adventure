async function loadFoodEvent(cityId, force = false) {
      const numericCityId = Number(cityId);
      const city = appState?.cities?.find(item => Number(item.id) === numericCityId);
      if (!session?.token || !city?.unlocked) return;
      if (!force && foodEventState.cityId === numericCityId
          && (foodEventState.loading || foodEventState.event || foodEventState.error)) {
        return;
      }

      foodEventState = {
        ...createFoodEventState(),
        cityId: numericCityId,
        loading: true
      };
      renderActiveFoodEvent();
      try {
        const event = await api(`/api/cities/${numericCityId}/food`);
        if (foodEventState.cityId !== numericCityId) return;
        foodEventState.event = event;
      } catch (error) {
        if (foodEventState.cityId !== numericCityId) return;
        foodEventState.error = error.message;
      } finally {
        if (foodEventState.cityId === numericCityId) {
          foodEventState.loading = false;
          renderActiveFoodEvent();
        }
      }
    }

    async function submitFoodClaim(questionId, answer) {
      if (foodEventState.submitting || !foodEventState.cityId) return;
      foodEventState.submitting = true;
      foodEventState.error = null;
      renderActiveFoodEvent();
      try {
        const result = await api(`/api/cities/${foodEventState.cityId}/food/claim`, {
          method: "POST",
          body: JSON.stringify({ questionId, answer })
        });
        foodEventState.result = result;
        if (result.correct) {
          foodEventState.event = {
            ...foodEventState.event,
            claimed: true,
            challenge: null
          };
          addLog(`料理解鎖成功：${result.name}`);
        } else {
          foodEventState.event = {
            ...foodEventState.event,
            challenge: null
          };
        }
      } catch (error) {
        foodEventState.error = error.message;
      } finally {
        foodEventState.submitting = false;
        renderActiveFoodEvent();
      }
    }

    function renderFoodEventCard(city) {
      if (Number(foodEventState.cityId) !== Number(city?.id)) return "";
      if (foodEventState.loading && !foodEventState.event) {
        return `
          <section class="food-event-card">
            <strong>🍲 正在查看城市特色美食…</strong>
          </section>
        `;
      }
      if (foodEventState.error && !foodEventState.event) return "";

      const event = foodEventState.event;
      if (!event) return "";
      if (!event.available) {
        const progress = Math.min(event.completedCheckins, event.requiredCheckins);
        return `
          <section class="food-event-card locked">
            <div class="section-head">
              <div>
                <span class="status-pill locked">台南特色美食</span>
                <h2>🥣 牛肉湯</h2>
              </div>
              <strong>${progress}／${event.requiredCheckins}</strong>
            </div>
            <p>完成台南 ${event.requiredCheckins} 個景點後可解鎖。</p>
            <div class="progress" aria-label="特色美食解鎖進度"><span style="--w:${Math.round(progress / event.requiredCheckins * 100)}%"></span></div>
          </section>
        `;
      }

      if (event.claimed || foodEventState.result?.correct) {
        const result = foodEventState.result;
        return `
          <section class="food-event-card claimed">
            <span class="status-pill completed">料理解鎖成功</span>
            <h2>🥣 ${escapeHtml(result?.name || event.name)}</h2>
            <p>${escapeHtml(event.shortDescription)}</p>
            <strong>${escapeHtml(result?.effectDescription || event.effect?.description)}</strong>
            ${result?.explanation ? `<p class="food-explanation">${escapeHtml(result.explanation)}</p>` : ""}
          </section>
        `;
      }

      if (foodEventState.result && !foodEventState.result.correct && !event.challenge) {
        return `
          <section class="food-event-card">
            <span class="status-pill challenge">再試一次</span>
            <h2>答案不正確</h2>
            <p>這一題已失效，可以重新取得新的牛肉湯文化題。</p>
            <button class="btn full" type="button" data-retry-food-event>重新挑戰</button>
          </section>
        `;
      }

      const challenge = event.challenge;
      if (!challenge) return "";
      return `
        <section class="food-event-card">
          <div class="section-head">
            <div>
              <span class="status-pill challenge">台南特色美食事件</span>
              <h2>🥣 ${escapeHtml(event.name)}</h2>
            </div>
            <strong>${escapeHtml(event.effect?.description)}</strong>
          </div>
          <p>${escapeHtml(event.shortDescription)}</p>
          <div class="food-challenge">
            <strong>${escapeHtml(challenge.question)}</strong>
            <div class="food-options">
              ${challenge.options.map(option => `
                <button class="quiz-option" type="button" data-food-question-id="${escapeHtml(challenge.questionId)}"
                        data-food-answer="${escapeHtml(option)}" ${foodEventState.submitting ? "disabled" : ""}>
                  ${escapeHtml(option)}
                </button>
              `).join("")}
            </div>
            <small>題目將於 ${new Date(challenge.expiresAt).toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" })} 到期</small>
          </div>
          ${foodEventState.error ? `<p class="error">${escapeHtml(foodEventState.error)}</p>` : ""}
        </section>
      `;
    }

    function bindFoodEventActions() {
      document.querySelectorAll("[data-food-question-id]").forEach(button => {
        button.addEventListener("click", () => submitFoodClaim(
          button.dataset.foodQuestionId,
          button.dataset.foodAnswer
        ));
      });
      document.querySelector("[data-retry-food-event]")?.addEventListener("click", () => {
        foodEventState.result = null;
        loadFoodEvent(foodEventState.cityId, true);
      });
    }

    function renderActiveFoodEvent() {
      if (!appState || Number(activeCityId) !== Number(foodEventState.cityId)) return;
      renderCityDetail(activeCityId);
    }
