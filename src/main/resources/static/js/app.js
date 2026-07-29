const loginForm = document.getElementById("login-form");
const loginSubmitButton = document.getElementById("login-submit");
const registerButton = document.getElementById("show-register-button");
const passwordToggleButton = document.getElementById("toggle-login-password");

function setAuthSubmitting(activeButton, submitting, busyText) {
      const authButtons = [...loginForm.querySelectorAll("button")];
      loginForm.toggleAttribute("aria-busy", submitting);
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
      setAuthSubmitting(loginSubmitButton, submitting, "正在進入旅程……");
    }

function setRegisterSubmitting(submitting) {
      setAuthSubmitting(registerButton, submitting, "正在建立旅人帳號……");
    }

async function runAuthRequest(setSubmitting, action) {
      if (loginForm.dataset.submitting === "true") return;
      loginForm.dataset.submitting = "true";
      setSubmitting(true);
      clearLoginMessage();
      try {
        return await action();
      } finally {
        delete loginForm.dataset.submitting;
        setSubmitting(false);
      }
    }

loginForm.addEventListener("submit", async event => {
      event.preventDefault();
      const username = document.getElementById("login-username").value.trim();
      const password = document.getElementById("login-password").value;
      if (!validateLoginFields(username, password)) return;

      try {
        await runAuthRequest(setLoginSubmitting, () => login(username, password));
      } catch (error) {
        const message = error?.status === 401
          ? "帳號或密碼錯誤，請重新輸入。"
          : error?.message || "登入失敗，請確認帳號與密碼。";
        showLoginMessage(message, "error");
      }
    });

    registerButton.addEventListener("click", async () => {
      const username = document.getElementById("login-username").value.trim();
      const password = document.getElementById("login-password").value;
      if (!validateLoginFields(username, password)) return;

      try {
        await runAuthRequest(setRegisterSubmitting, () => register(username, password));
      } catch (error) {
        showLoginMessage(error?.message || "帳號建立失敗，請稍後再試。", "error");
      }
    });

    passwordToggleButton.addEventListener("click", togglePasswordVisibility);

    ["login-username", "login-password"].forEach(inputId => {
      document.getElementById(inputId).addEventListener("input", event => {
        clearLoginFieldError(event.currentTarget);
        clearLoginMessage();
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
