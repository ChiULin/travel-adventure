function tutorialIsCompleted() {
      return localStorage.getItem(tutorialStorageKey()) === "true";
    }

    function tutorialStorageKey() {
      const userId = appState?.user?.id || session?.userId;
      return userId ? `${TUTORIAL_KEY}:${userId}` : TUTORIAL_KEY;
    }

    function showTutorialIfNeeded() {
      if (!tutorialIsCompleted()) {
        stopQuizTimer();
        document.getElementById("tutorial").classList.remove("hidden");
      }
    }

    function completeTutorial() {
      localStorage.setItem(tutorialStorageKey(), "true");
      document.getElementById("tutorial").classList.add("hidden");
      addLog("新手教學完成，開始你的臺灣探索旅程。");
      setTimeout(maybeShowFinalEnding, 0);
    }

function showLoginMessage(message = "", type = "error") {
      const messageElement = document.getElementById("login-message");
      messageElement.textContent = message;
      messageElement.classList.remove("login-message--error", "login-message--success");
      if (message) {
        messageElement.classList.add(`login-message--${type}`);
      }
    }

function clearLoginMessage() {
      showLoginMessage("");
    }

function setLoginFieldError(input, message = "") {
      const errorElement = document.getElementById(`${input.id}-error`);
      input.classList.toggle("input--invalid", Boolean(message));
      input.setAttribute("aria-invalid", String(Boolean(message)));
      errorElement.textContent = message;
    }

function clearLoginFieldError(input) {
      setLoginFieldError(input);
    }

function clearLoginFieldErrors() {
      ["login-username", "login-password"].forEach(inputId => {
        clearLoginFieldError(document.getElementById(inputId));
      });
    }

function validateLoginFields(username, password) {
      const usernameInput = document.getElementById("login-username");
      const passwordInput = document.getElementById("login-password");
      clearLoginFieldErrors();
      clearLoginMessage();

      if (!username) {
        setLoginFieldError(usernameInput, "請輸入旅人帳號");
      } else if (username.length < 3) {
        setLoginFieldError(usernameInput, "旅人帳號至少需要 3 個字元");
      } else if (username.length > 20) {
        setLoginFieldError(usernameInput, "旅人帳號最多只能有 20 個字元");
      }

      if (!password) {
        setLoginFieldError(passwordInput, "請輸入密碼");
      } else if (password.length < 8) {
        setLoginFieldError(passwordInput, "密碼至少需要 8 個字元");
      } else if (password.length > 72) {
        setLoginFieldError(passwordInput, "密碼最多只能有 72 個字元");
      }

      const firstInvalidInput = loginForm.querySelector(".input--invalid");
      if (firstInvalidInput) {
        firstInvalidInput.focus();
        return false;
      }
      return true;
    }

function togglePasswordVisibility() {
      const input = document.getElementById("login-password");
      const button = document.getElementById("toggle-login-password");
      const willShow = input.type === "password";

      input.type = willShow ? "text" : "password";
      button.textContent = willShow ? "隱藏" : "顯示";
      button.setAttribute("aria-label", willShow ? "隱藏密碼" : "顯示密碼");
      button.setAttribute("aria-pressed", String(willShow));
    }

async function showLoginSuccessTransition(message) {
      showLoginMessage(message, "success");
      document.querySelector(".login-card")?.classList.add("login-card--success");

      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (!reduceMotion) {
        await new Promise(resolve => setTimeout(resolve, 650));
      }
    }

function clearAuthState() {
      saveSession(null);
      selectedDifficulty = "NORMAL";
      resetLocalBattleState();
      stopImageRecognitionTimer();
      stopPuzzleTimer();
      appState = null;
      missionsState = null;
      achievementsState = null;
      collectionState = null;
      selectedCollectionId = null;
      explorationState = createExplorationState();
      imageRecognitionState = createImageRecognitionState();
      puzzleState = createPuzzleState();
      activeCityId = null;
      journeyView = "map";
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
      document.getElementById("puzzle-challenge").innerHTML = "";
      closeResultCard();
    }

function showLoginPage(message = "") {
      const passwordInput = document.getElementById("login-password");
      const passwordToggle = document.getElementById("toggle-login-password");
      passwordInput.value = "";
      passwordInput.type = "password";
      passwordToggle.textContent = "顯示";
      passwordToggle.setAttribute("aria-label", "顯示密碼");
      passwordToggle.setAttribute("aria-pressed", "false");
      clearLoginFieldErrors();
      document.querySelector(".login-card")?.classList.remove("login-card--success");
      showLoginMessage(message, "error");
      document.getElementById("login").classList.remove("hidden");
    }

async function authenticate(path, username, password, successMessage) {
      try {
        const auth = await api(path, {
          method: "POST",
          body: JSON.stringify({ username, password })
        });
        saveSession(auth);
        await showLoginSuccessTransition(successMessage);
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
      const auth = await authenticate(
        "/api/auth/login",
        username,
        password,
        "登入成功，正在載入你的旅程……"
      );
      addLog(`${auth.username} 已登入。`);
    }

    async function register(username, password) {
      const auth = await authenticate(
        "/api/auth/register",
        username,
        password,
        "旅人帳號建立成功，現在可以開始旅程。"
      );
      addLog(`${auth.username} 的帳號已建立。`);
    }
