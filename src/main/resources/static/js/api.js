class ApiError extends Error {
      constructor(message, status) {
        super(message);
        this.name = "ApiError";
        this.status = status;
      }
    }

const API_ERROR_MESSAGES = {
      400: "題目已失效，請重新開始挑戰",
      401: "登入已過期，請重新登入",
      403: "沒有操作權限",
      409: "請先完成上一個景點關卡",
      429: "操作過於頻繁，請稍後再試"
    };

const API_ERROR_TRANSLATIONS = {
      "too many pending questions": "操作過於頻繁，請稍後再試",
      "question challenge does not match": "題目已失效，請重新開始挑戰",
      "question time expired": "題目已失效，請重新開始挑戰",
      "boss battle has not been started": "Boss 挑戰已失效，請重新開始挑戰",
      "boss difficulty does not match active battle": "Boss 挑戰已失效，請重新開始挑戰",
      "battle result does not match a completed battle": "Boss 結算已失效，請重新開始挑戰",
      "battle result has already been recorded": "Boss 結算已完成，請重新整理旅程",
      "city not unlocked": "城市尚未解鎖",
      "complete all city scenes first": "請先完成所有景點關卡",
      "scene already checked in": "此景點已完成打卡",
      "invalid difficulty": "挑戰難度無效，請重新開始挑戰"
    };

function resolveApiErrorMessage(status, body, path, authenticatedRequest) {
      const serverMessage = typeof body?.message === "string" ? body.message.trim() : "";

      if (status >= 500) {
        return "系統暫時發生錯誤，請稍後重試";
      }
      if (status === 401 && authenticatedRequest) {
        return API_ERROR_MESSAGES[401];
      }
      if (status === 409 && path.startsWith("/api/auth/")) {
        return serverMessage || "帳號資料發生衝突，請重新確認";
      }
      if (API_ERROR_TRANSLATIONS[serverMessage]) {
        return API_ERROR_TRANSLATIONS[serverMessage];
      }
      if (serverMessage && /[\u3400-\u9fff]/u.test(serverMessage)) {
        return serverMessage;
      }
      return API_ERROR_MESSAGES[status] || serverMessage || `操作失敗（${status}）`;
    }

async function api(path, options = {}) {
      const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
      const requestToken = session?.token || null;
      if (requestToken) headers.Authorization = `Bearer ${requestToken}`;

      let response;
      try {
        response = await fetch(path, { ...options, headers });
      } catch {
        throw new ApiError("無法連線至伺服器，請檢查網路後重試", 0);
      }

      const text = await response.text();
      let body = null;
      try {
        body = text ? JSON.parse(text) : null;
      } catch {
        if (!response.ok) {
          const authenticatedRequest = Boolean(requestToken && session?.token === requestToken);
          const message = resolveApiErrorMessage(response.status, null, path, authenticatedRequest);
          if (response.status === 401 && authenticatedRequest) {
            clearAuthState();
            showLoginPage(message);
          }
          throw new ApiError(message, response.status);
        }
        throw new ApiError(`伺服器回傳格式不正確（${response.status}）`, response.status);
      }

      const validEnvelope = body
        && typeof body.success === "boolean"
        && typeof body.message === "string"
        && Object.prototype.hasOwnProperty.call(body, "data");
      if (!response.ok || (validEnvelope && !body.success)) {
        const authenticatedRequest = Boolean(requestToken && session?.token === requestToken);
        const message = resolveApiErrorMessage(response.status, body, path, authenticatedRequest);
        if (response.status === 401 && authenticatedRequest) {
          clearAuthState();
          showLoginPage(message);
        }
        throw new ApiError(message, response.status);
      }
      if (!validEnvelope) {
        throw new ApiError(`伺服器回傳格式不正確（${response.status}）`, response.status);
      }
      return body.data;
    }

const buttonLocks = new WeakSet();

async function runWithButtonLock(button, action) {
      if (!button || button.disabled || buttonLocks.has(button)) {
        return;
      }

      const originalContent = button.innerHTML;
      buttonLocks.add(button);
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
      button.textContent = "處理中…";

      try {
        return await action();
      } finally {
        buttonLocks.delete(button);
        button.removeAttribute("aria-busy");
        if (button.isConnected) {
          button.disabled = false;
          button.innerHTML = originalContent;
        }
      }
    }
