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

function showAuthMessage(mode, message = "", type = "error") {
      const messageElement = document.getElementById(`${mode}-message`);
      messageElement.textContent = message;
      messageElement.classList.remove("login-message--error", "login-message--success");
      if (message) {
        messageElement.classList.add(`login-message--${type}`);
      }
    }

function showLoginMessage(message = "", type = "error") {
      showAuthMessage("login", message, type);
    }

function showRegisterMessage(message = "", type = "error") {
      showAuthMessage("register", message, type);
    }

function clearAuthMessage(mode) {
      showAuthMessage(mode, "");
    }

function clearLoginMessage() {
      clearAuthMessage("login");
    }

function clearAuthMessages() {
      clearAuthMessage("login");
      clearAuthMessage("register");
    }

function setAuthFieldError(input, message = "") {
      const errorElement = document.getElementById(`${input.id}-error`);
      input.classList.toggle("input--invalid", Boolean(message));
      input.setAttribute("aria-invalid", String(Boolean(message)));
      errorElement.textContent = message;
    }

function clearAuthFieldError(input) {
      setAuthFieldError(input);
    }

function clearAuthFormFieldErrors(mode) {
      document.querySelectorAll(`#${mode}-form [aria-invalid]`).forEach(input => {
        clearAuthFieldError(input);
      });
    }

function clearAuthFieldErrors() {
      clearAuthFormFieldErrors("login");
      clearAuthFormFieldErrors("register");
    }

function validateCredentialFields(mode, username, password) {
      const usernameInput = document.getElementById(`${mode}-username`);
      const passwordInput = document.getElementById(`${mode}-password`);

      if (!username) {
        setAuthFieldError(usernameInput, "請輸入旅人帳號");
      } else if (username.length < 3) {
        setAuthFieldError(usernameInput, "旅人帳號至少需要 3 個字元");
      } else if (username.length > 20) {
        setAuthFieldError(usernameInput, "旅人帳號最多只能有 20 個字元");
      }

      if (!password) {
        setAuthFieldError(passwordInput, "請輸入密碼");
      } else if (password.length < 8) {
        setAuthFieldError(passwordInput, "密碼至少需要 8 個字元");
      } else if (password.length > 72) {
        setAuthFieldError(passwordInput, "密碼最多只能有 72 個字元");
      }
    }

function focusFirstInvalidAuthField(mode) {
      const firstInvalidInput = document.querySelector(`#${mode}-form .input--invalid`);
      if (!firstInvalidInput) return true;
      firstInvalidInput.focus();
      return false;
    }

function validateLoginFields(username, password) {
      clearAuthFormFieldErrors("login");
      clearAuthMessage("login");
      validateCredentialFields("login", username, password);
      return focusFirstInvalidAuthField("login");
    }

function validateRegisterFields(username, password, confirmPassword) {
      clearAuthFormFieldErrors("register");
      clearAuthMessage("register");
      validateCredentialFields("register", username, password);

      const confirmPasswordInput = document.getElementById("register-confirm-password");
      if (!confirmPassword) {
        setAuthFieldError(confirmPasswordInput, "請再次輸入密碼");
      } else if (password !== confirmPassword) {
        setAuthFieldError(confirmPasswordInput, "兩次輸入的密碼不一致");
      }

      return focusFirstInvalidAuthField("register");
    }

function togglePasswordVisibility(inputId, buttonId, label = "密碼") {
      const input = document.getElementById(inputId);
      const button = document.getElementById(buttonId);
      const willShow = input.type === "password";

      input.type = willShow ? "text" : "password";
      button.textContent = willShow ? "隱藏" : "顯示";
      button.setAttribute("aria-label", `${willShow ? "隱藏" : "顯示"}${label}`);
      button.setAttribute("aria-pressed", String(willShow));
    }

function resetAuthPasswordVisibility() {
      [
        ["login-password", "toggle-login-password", "密碼"],
        ["register-password", "toggle-register-password", "密碼"],
        ["register-confirm-password", "toggle-register-confirm-password", "確認密碼"]
      ].forEach(([inputId, buttonId, label]) => {
        const input = document.getElementById(inputId);
        const button = document.getElementById(buttonId);
        input.type = "password";
        button.textContent = "顯示";
        button.setAttribute("aria-label", `顯示${label}`);
        button.setAttribute("aria-pressed", "false");
      });
    }

function clearAuthPasswords() {
      [
        "login-password",
        "register-password",
        "register-confirm-password"
      ].forEach(inputId => {
        document.getElementById(inputId).value = "";
      });
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
      if (typeof switchAuthMode === "function") {
        switchAuthMode("login", { focus: false });
      }
      clearAuthPasswords();
      resetAuthPasswordVisibility();
      clearAuthFieldErrors();
      document.querySelector(".login-card")?.classList.remove("login-card--success");
      showLoginMessage(message, "error");
      document.getElementById("login").classList.remove("hidden");
      document.getElementById("login-username").focus();
    }

async function authenticate(path, username, password, successMessage) {
  try {
    const auth = await api(path, {
      method: "POST",
      body: JSON.stringify({ username, password })
    });

    saveSession(auth);
    await showLoginSuccessTransition(successMessage);

    await refreshState();

    console.log("登入後 Journey：", appState);

    renderTaiwanAdventureMap(appState);

    document.getElementById("login").classList.add("hidden");
    showTutorialIfNeeded();

    return auth;
  } catch (error) {
    console.error("登入流程錯誤：", error);

    if (session?.token) {
      clearAuthState();
      showLoginPage(error.message);
    }

    throw error;
  }
}

        async function register(username, password) {
          return authenticate(
            "/api/auth/register",
              username,
              password,
            "註冊成功，正在建立你的旅程……"
          );
      }

    async function register(username, password) {
      return api("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({ username, password })
      });
    }
