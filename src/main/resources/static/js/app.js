const authCard = document.querySelector(".login-card");
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const loginModeTab = document.getElementById("login-mode-tab");
const registerModeTab = document.getElementById("register-mode-tab");
const loginSubmitButton = document.getElementById("login-submit");
const registerSubmitButton = document.getElementById("register-submit");

const AUTH_MODE_CONTENT = {
      login: {
        title: "開始你的台灣冒險",
        description: "從台北出發，探索六座城市，解鎖文化地標與守護者。"
      },
      register: {
        title: "建立你的旅人身分",
        description: "建立旅人帳號，準備收藏文化記憶並展開六城冒險。"
      }
    };

function setAuthSubmitting(activeForm, activeButton, submitting, busyText) {
      const authButtons = [
        ...document.querySelectorAll(".login-form button, .auth-mode-tab")
      ];
      [loginForm, registerForm].forEach(form => {
        form.toggleAttribute("aria-busy", submitting && form === activeForm);
      });
      authButtons.forEach(button => {
        button.disabled = submitting;
      });

      if (submitting) {
        activeButton.dataset.idleText = activeButton.textContent.trim();
        activeButton.textContent = busyText;
        activeButton.setAttribute("aria-busy", "true");
      } else {
        activeButton.textContent = activeButton.dataset.idleText || activeButton.textContent;
        delete activeButton.dataset.idleText;
        activeButton.removeAttribute("aria-busy");
      }
    }

function setLoginSubmitting(submitting) {
      setAuthSubmitting(
        loginForm,
        loginSubmitButton,
        submitting,
        "正在進入旅程……"
      );
    }

function setRegisterSubmitting(submitting) {
      setAuthSubmitting(
        registerForm,
        registerSubmitButton,
        submitting,
        "正在建立旅人帳號……"
      );
    }

async function runAuthRequest(mode, setSubmitting, action) {
      if (authCard.dataset.submitting === "true") return;
      authCard.dataset.submitting = "true";
      setSubmitting(true);
      clearAuthMessage(mode);
      try {
        return await action();
      } finally {
        delete authCard.dataset.submitting;
        setSubmitting(false);
      }
    }

function switchAuthMode(mode, { focus = true } = {}) {
      if (!AUTH_MODE_CONTENT[mode] || authCard.dataset.submitting === "true") {
        return false;
      }

      const loginUsername = document.getElementById("login-username");
      const registerUsername = document.getElementById("register-username");
      const username = mode === "login"
        ? registerUsername.value.trim() || loginUsername.value.trim()
        : loginUsername.value.trim() || registerUsername.value.trim();

      loginUsername.value = username;
      registerUsername.value = username;
      clearAuthMessages();
      clearAuthFieldErrors();
      clearAuthPasswords();
      resetAuthPasswordVisibility();
      document.querySelector(".login-card")?.classList.remove("login-card--success");

      const loginMode = mode === "login";
      loginForm.hidden = !loginMode;
      registerForm.hidden = loginMode;
      loginModeTab.classList.toggle("auth-mode-tab--active", loginMode);
      registerModeTab.classList.toggle("auth-mode-tab--active", !loginMode);
      loginModeTab.setAttribute("aria-selected", String(loginMode));
      registerModeTab.setAttribute("aria-selected", String(!loginMode));
      loginModeTab.tabIndex = loginMode ? 0 : -1;
      registerModeTab.tabIndex = loginMode ? -1 : 0;

      document.getElementById("auth-card-title").textContent = AUTH_MODE_CONTENT[mode].title;
      document.getElementById("auth-card-description").textContent = AUTH_MODE_CONTENT[mode].description;

      if (focus) {
        document.getElementById(`${mode}-username`).focus();
      }
      return true;
    }

loginForm.addEventListener("submit", async event => {
      event.preventDefault();
      if (authCard.dataset.submitting === "true") return;
      const username = document.getElementById("login-username").value.trim();
      const password = document.getElementById("login-password").value;
      if (!validateLoginFields(username, password)) return;

      try {
        await runAuthRequest("login", setLoginSubmitting, () => login(username, password));
      } catch (error) {
        const message = error?.status === 401
          ? "帳號或密碼錯誤，請重新輸入。"
          : error?.message || "登入失敗，請確認帳號與密碼。";
        showLoginMessage(message, "error");
      }
    });

    registerForm.addEventListener("submit", async event => {
      event.preventDefault();
      if (authCard.dataset.submitting === "true") return;
      const username = document.getElementById("register-username").value.trim();
      const password = document.getElementById("register-password").value;
      const confirmPassword = document.getElementById("register-confirm-password").value;
      if (!validateRegisterFields(username, password, confirmPassword)) return;

      try {
        const auth = await runAuthRequest(
          "register",
          setRegisterSubmitting,
          () => register(username, password)
        );
        document.getElementById("login-username").value = auth?.username || username;
        document.getElementById("register-username").value = auth?.username || username;
        switchAuthMode("login", { focus: false });
        showLoginMessage("旅人帳號建立成功，現在可以開始旅程。", "success");
        document.getElementById("login-password").focus();
      } catch (error) {
        showRegisterMessage(error?.message || "帳號建立失敗，請稍後再試。", "error");
      }
    });

    loginModeTab.addEventListener("click", () => switchAuthMode("login"));
    registerModeTab.addEventListener("click", () => switchAuthMode("register"));

    [loginModeTab, registerModeTab].forEach(tab => {
      tab.addEventListener("keydown", event => {
        if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
        event.preventDefault();
        const mode = event.key === "ArrowLeft" || event.key === "Home"
          ? "login"
          : "register";
        switchAuthMode(mode);
      });
    });

    [
      ["toggle-login-password", "login-password", "密碼"],
      ["toggle-register-password", "register-password", "密碼"],
      ["toggle-register-confirm-password", "register-confirm-password", "確認密碼"]
    ].forEach(([buttonId, inputId, label]) => {
      document.getElementById(buttonId).addEventListener("click", () => {
        togglePasswordVisibility(inputId, buttonId, label);
      });
    });

    [
      ["login-username", "login"],
      ["login-password", "login"],
      ["register-username", "register"],
      ["register-password", "register"],
      ["register-confirm-password", "register"]
    ].forEach(([inputId, mode]) => {
      document.getElementById(inputId).addEventListener("input", event => {
        clearAuthFieldError(event.currentTarget);
        clearAuthMessage(mode);
      });
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
      clearAuthState();
      showLoginPage();
    });

    document.getElementById("startAdventureBtn").addEventListener("click", completeTutorial);
    document.getElementById("back-to-taiwan-map").addEventListener("click", openTaiwanMapView);

    if (session?.token) {
      document.getElementById("login").classList.add("hidden");
      refreshState().then(showTutorialIfNeeded).catch(error => {
        if (session?.token) {
          clearAuthState();
          showLoginPage(error.message);
        }
      });
    }

    document.documentElement.dataset.appReady = "true";
