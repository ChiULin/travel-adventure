document.getElementById("loginForm").addEventListener("submit", async event => {
      event.preventDefault();
      const username = document.getElementById("nameInput").value.trim() || "旅行者";
      const password = document.getElementById("passwordInput").value;
      document.getElementById("loginError").textContent = "";
      try {
        await login(username, password);
      } catch (error) {
        document.getElementById("loginError").textContent = error.message;
      }
    });

    document.getElementById("registerBtn").addEventListener("click", async () => {
      const username = document.getElementById("nameInput").value.trim();
      const password = document.getElementById("passwordInput").value;
      const errorElement = document.getElementById("loginError");
      const validationMessage = validateAuthInput(username, password);
      errorElement.textContent = validationMessage;
      if (validationMessage) return;
      try {
        await register(username, password);
      } catch (error) {
        errorElement.textContent = error.message;
      }
    });

    document.getElementById("openCollectionBtn").addEventListener("click", openCollection);
    document.getElementById("closeCollectionBtn").addEventListener("click", closeCollection);
    document.querySelectorAll("[data-collection-tab]").forEach(button => {
      button.addEventListener("click", () => {
        collectionTab = button.dataset.collectionTab;
        selectedCollectionId = null;
        renderCollection();
      });
    });

    document.getElementById("logoutBtn").addEventListener("click", () => {
      saveSession(null);
      appState = null;
      missionsState = null;
      achievementsState = null;
      collectionState = null;
      selectedCollectionId = null;
      explorationState = createExplorationState();
      stopImageRecognitionTimer();
      imageRecognitionState = createImageRecognitionState();
      foodEventState = createFoodEventState();
      activeCityId = null;
      bossPreparationCityId = null;
      activeBossBattle = null;
      finalEndingShown = false;
      logs = [];
      document.getElementById("tutorial").classList.add("hidden");
      document.getElementById("finalEnding").classList.add("hidden");
      document.getElementById("finalEndingCard").innerHTML = "";
      document.getElementById("collectionOverlay").classList.add("hidden");
      document.getElementById("collectionGrid").innerHTML = "";
      document.getElementById("collectionDetail").innerHTML = "";
      document.getElementById("exploration-mission").innerHTML = "";
      document.getElementById("image-recognition").innerHTML = "";
      closeResultCard();
      document.getElementById("login").classList.remove("hidden");
    });

    document.getElementById("startAdventureBtn").addEventListener("click", completeTutorial);

    if (session?.token) {
      document.getElementById("login").classList.add("hidden");
      refreshState().then(showTutorialIfNeeded).catch(() => {
        saveSession(null);
        document.getElementById("tutorial").classList.add("hidden");
        document.getElementById("login").classList.remove("hidden");
      });
    }

    document.documentElement.dataset.appReady = "true";
