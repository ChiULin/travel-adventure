const loginForm = document.getElementById("login-form");

async function runAuthRequest(button, action) {
      if (loginForm.dataset.submitting === "true") return;
      loginForm.dataset.submitting = "true";
      const authButtons = [...loginForm.querySelectorAll("button")];
      authButtons.filter(item => item !== button).forEach(item => {
        item.disabled = true;
      });
      try {
        await runWithButtonLock(button, action);
      } finally {
        delete loginForm.dataset.submitting;
        authButtons.filter(item => item !== button && item.isConnected).forEach(item => {
          item.disabled = false;
        });
      }
    }

loginForm.addEventListener("submit", async event => {
      event.preventDefault();
      const button = event.submitter || loginForm.querySelector('button[type="submit"]');
      const username = document.getElementById("login-username").value.trim() || "旅行者";
      const password = document.getElementById("login-password").value;
      document.getElementById("login-error").textContent = "";
      try {
        await runAuthRequest(button, () => login(username, password));
      } catch (error) {
        document.getElementById("login-error").textContent = error.message;
      }
    });

    document.getElementById("show-register-button").addEventListener("click", async event => {
      const username = document.getElementById("login-username").value.trim();
      const password = document.getElementById("login-password").value;
      const errorElement = document.getElementById("login-error");
      const validationMessage = validateAuthInput(username, password);
      errorElement.textContent = validationMessage;
      if (validationMessage) return;
      try {
        await runAuthRequest(event.currentTarget, () => register(username, password));
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
