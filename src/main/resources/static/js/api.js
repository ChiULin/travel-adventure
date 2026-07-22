async function api(path, options = {}) {
      const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
      if (session?.token) headers.Authorization = `Bearer ${session.token}`;
      const response = await fetch(path, { ...options, headers });
      const text = await response.text();
      let body = null;
      try {
        body = text ? JSON.parse(text) : null;
      } catch {
        throw new Error(`伺服器回傳格式不正確（${response.status}）`);
      }

      const validEnvelope = body
        && typeof body.success === "boolean"
        && typeof body.message === "string"
        && Object.prototype.hasOwnProperty.call(body, "data");
      if (!validEnvelope) {
        throw new Error(`伺服器回傳格式不正確（${response.status}）`);
      }
      if (!response.ok || !body.success) {
        throw new Error(body.message || `操作失敗（${response.status}）`);
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
