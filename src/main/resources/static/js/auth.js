function tutorialIsCompleted() {
      return localStorage.getItem(TUTORIAL_KEY) === "true";
    }

    function showTutorialIfNeeded() {
      if (!tutorialIsCompleted()) {
        stopQuizTimer();
        document.getElementById("tutorial").classList.remove("hidden");
      }
    }

    function completeTutorial() {
      localStorage.setItem(TUTORIAL_KEY, "true");
      document.getElementById("tutorial").classList.add("hidden");
      addLog("新手教學完成，開始你的台灣探索旅程。");
      setTimeout(maybeShowFinalEnding, 0);
    }

function validateAuthInput(username, password) {
      if (!username) return "請輸入玩家名稱";
      if (username.length < 3 || username.length > 20) return "玩家名稱需為 3 到 20 個字元";
      if (!password) return "請輸入密碼";
      if (password.length < 8 || password.length > 72) return "密碼需為 8 到 72 個字元";
      return "";
    }

function clearAuthState() {
      saveSession(null);
      selectedDifficulty = "NORMAL";
      resetLocalBattleState();
      stopImageRecognitionTimer();
      appState = null;
      missionsState = null;
      achievementsState = null;
      collectionState = null;
      selectedCollectionId = null;
      explorationState = createExplorationState();
      imageRecognitionState = createImageRecognitionState();
      activeCityId = null;
      answerSubmitting = false;
      finalEndingShown = false;
      logs = [];

      document.getElementById("tutorial")?.classList.add("hidden");
      document.getElementById("finalEnding")?.classList.add("hidden");
      document.getElementById("collectionOverlay")?.classList.add("hidden");
      document.getElementById("finalEndingCard").innerHTML = "";
      document.getElementById("collectionGrid").innerHTML = "";
      document.getElementById("collectionDetail").innerHTML = "";
      document.getElementById("exploration-mission").innerHTML = "";
      document.getElementById("image-recognition").innerHTML = "";
      closeResultCard();
    }

function showLoginPage(message = "") {
      document.getElementById("passwordInput").value = "";
      document.getElementById("loginError").textContent = message;
      document.getElementById("login").classList.remove("hidden");
    }

async function authenticate(path, username, password) {
      try {
        const auth = await api(path, {
          method: "POST",
          body: JSON.stringify({ username, password })
        });
        saveSession(auth);
        document.getElementById("login").classList.add("hidden");
        await refreshState();
        showTutorialIfNeeded();
        return auth;
      } catch (error) {
        if (session?.token) {
          clearAuthState();
          showLoginPage(error.message);
        }
        throw error;
      }
    }

    async function login(username, password) {
      const auth = await authenticate("/api/auth/login", username, password);
      addLog(`${auth.username} 已登入。`);
    }

    async function register(username, password) {
      const auth = await authenticate("/api/auth/register", username, password);
      addLog(`${auth.username} 的帳號已建立。`);
    }
