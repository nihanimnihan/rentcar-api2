(function () {
  const state = {
    email: "",
    profileToken: ""
  };

  function showStep(name) {
    document.querySelectorAll(".rc-auth-step").forEach(step => {
      step.classList.toggle("is-active", step.dataset.step === name);
    });
  }

  function getEmail() {
    return document.getElementById("email").value.trim();
  }

  async function goVerify() {
    const email = getEmail();
    const invalid = !isValidEmail(email);

    markError("email", invalid);

    if (invalid) return;

    state.email = email;
    try {
      await requestCode();
      document.getElementById("verifyEmailText").textContent = email;
      clearCodeInputs();
      clearFormMessage("loginMessage");
      clearFormMessage("verifyMessage");
      showStep("verify");
      focusFirstCodeInput();
    } catch (e) {
      showFormMessage("loginMessage", message("auth.codeRequestFailed", "Unable to send the code. Please try again."), "error");
    }
  }

function getReturnTo() {
  const params = new URLSearchParams(window.location.search);
  const rt = params.get("returnTo");

  if (rt && rt.startsWith("/") && !rt.startsWith("//")) {
    return rt;
  }

  return "/index.html";
}

function openCenteredPopup(url, width = 600, height = 700) {
  const left = Math.floor((window.screen.width - width) / 2);
  const top = Math.floor((window.screen.height - height) / 2);
  const opts = `toolbar=0,location=0,status=0,menubar=0,scrollbars=1,resizable=1,width=${width},height=${height},top=${top},left=${left}`;
  try {
    return window.open(url, 'rc_google_oauth', opts);
  } catch (e) { return null; }
}

function goProfileFromGoogle() {
  const returnTo = getReturnTo();
  const popupUrl =
    '/oauth2/authorize?returnTo=' +
    encodeURIComponent(returnTo) +
    '&provider=google&popup=1';

  const popup = openCenteredPopup(popupUrl, 600, 700);

  if (!popup) {
    window.location.href = popupUrl;
    return;
  }

  const onMessage = async (e) => {
    if (e.origin !== window.location.origin) return;

    const data = e.data || {};
    if (data.type !== 'oauth') return;

    window.removeEventListener('message', onMessage);

    const returnToFromPopup = data.returnTo || returnTo || '/index.html';

    if (data.profileComplete) {
      window.location.href = returnToFromPopup;
    } else {
      window.location.href =
        '/signup.html?step=profile&returnTo=' +
        encodeURIComponent(returnToFromPopup);
    }
  };

  window.addEventListener('message', onMessage);
}


    // Save profile to backend (if session-authenticated) or fallback to local storage
    async function saveProfile() {
      const email = document.getElementById("profileEmail").value.trim();
      const firstName = document.getElementById("firstName").value.trim();
      const lastName = document.getElementById("lastName").value.trim();
      const phoneCountryCode = document.getElementById("phoneCountryCode").value;
      const phoneNumber = document.getElementById("phoneNumber").value.trim();

      const emailInvalid = !isValidEmail(email);
      const firstNameInvalid = !firstName;
      const lastNameInvalid = !lastName;
      const phoneCountryCodeInvalid = !phoneCountryCode;
      const phoneNumberInvalid = !isValidPhoneNumber(phoneNumber);

      markError("profileEmail", emailInvalid);
      markError("firstName", firstNameInvalid);
      markError("lastName", lastNameInvalid);
      markError("phoneCountryCode", phoneCountryCodeInvalid);
      markError("phoneNumber", phoneNumberInvalid);

      if (emailInvalid || firstNameInvalid || lastNameInvalid || phoneCountryCodeInvalid || phoneNumberInvalid) {
        return;
      }

      const payload = { firstName, lastName, phoneCountryCode, phoneNumber };
      const endpoint = state.profileToken ? '/api/auth/email/complete-profile' : '/api/auth/profile';
      if (state.profileToken) payload.profileToken = state.profileToken;

      try {
        const res = await fetch(endpoint, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        if (res.ok) {
          // Use client-side validated returnTo
          const returnTo = getReturnTo();
          window.location.href = returnTo;
          return;
        }

        // Non-ok: show error and stop
        let errText = '';
        try {
          const errJson = await res.json();
          errText = errJson.error || JSON.stringify(errJson);
        } catch (ee) {
          try { errText = await res.text(); } catch (_) { errText = '' }
        }
        console.error('Profile save failed', res.status, errText);
        showFormMessage("profileMessage", message("auth.profileSaveFailed", "Unable to save profile. Please check the fields and try again."), "error");
        return;
      } catch (e) {
        console.error('Profile save request failed', e);
        showFormMessage("profileMessage", message("auth.networkError", "Unable to continue due to a network error."), "error");
        return;
      }
    }

  async function requestCode() {
    const res = await fetch('/api/auth/email/request-code', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: state.email })
    });
    if (!res.ok) throw new Error('request-code failed');
  }

  async function verifyCode() {
    const code = Array.from(document.querySelectorAll(".rc-code-row input"))
      .map(input => input.value)
      .join("");

    if (code.length !== 6) {
      showFormMessage("verifyMessage", message("auth.requiredCode", "Please enter the 6-digit code."), "error");
      return;
    }

    try {
      clearFormMessage("verifyMessage");
      const res = await fetch('/api/auth/email/verify-code', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: state.email, code })
      });
      if (!res.ok) {
        showFormMessage("verifyMessage", message("auth.invalidCode", "The code is invalid or has expired. Please request a new code."), "error");
        return;
      }
      const data = await res.json();
      if (data.status === 'LOGGED_IN') {
        window.location.href = getReturnTo();
        return;
      }
      if (data.status === 'PROFILE_REQUIRED' && data.profileToken) {
        state.profileToken = data.profileToken;
        const profileEmail = document.getElementById("profileEmail");
        if (profileEmail) profileEmail.value = state.email;
        clearFormMessage("profileMessage");
        showStep("profile");
        return;
      }
      showFormMessage("verifyMessage", message("auth.unexpectedResponse", "Unable to continue. Please try again."), "error");
    } catch (e) {
      showFormMessage("verifyMessage", message("auth.networkError", "Unable to continue due to a network error."), "error");
    }
  }

  async function resendCode() {
    if (!state.email) state.email = getEmail();
    if (!isValidEmail(state.email)) return showStep("login");
    try {
      await requestCode();
      clearCodeInputs();
      clearFormMessage("verifyMessage");
      focusFirstCodeInput();
      showFormMessage("verifyMessage", message("auth.codeResent", "A new code has been sent."), "success");
    } catch (e) {
      showFormMessage("verifyMessage", message("auth.codeRequestFailed", "Unable to send the code. Please try again."), "error");
    }
  }


  function focusFirstCodeInput() {
    const first = document.querySelector(".rc-code-row input");
    if (first) setTimeout(() => first.focus(), 100);
  }

  function clearCodeInputs() {
    document.querySelectorAll(".rc-code-row input").forEach(input => input.value = "");
  }

  function showFormMessage(id, text, type) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.classList.remove("is-error", "is-success");
    el.classList.add("is-visible", type === "success" ? "is-success" : "is-error");
  }

  function clearFormMessage(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = "";
    el.classList.remove("is-visible", "is-error", "is-success");
  }

  function bindCodeInputs() {
    const inputs = Array.from(document.querySelectorAll(".rc-code-row input"));

    inputs.forEach((input, index) => {
      input.addEventListener("input", () => {
        input.value = input.value.replace(/\D/g, "").slice(0, 1);
        clearFormMessage("verifyMessage");
        if (input.value && inputs[index + 1]) {
          inputs[index + 1].focus();
        }
      });

      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !input.value && inputs[index - 1]) {
          inputs[index - 1].focus();
        }
      });
    });
  }

function bindEvents() {
  try {
    document.getElementById("continueEmailBtn")?.addEventListener("click", goVerify);
    document.getElementById("googleBtn")?.addEventListener("click", goProfileFromGoogle);
    document.getElementById("verifyCodeBtn")?.addEventListener("click", verifyCode);
    document.getElementById("resendCodeBtn")?.addEventListener("click", resendCode);
    document.getElementById("saveProfileBtn")?.addEventListener("click", saveProfile);

    document.querySelectorAll("[data-back]").forEach(btn => {
      btn.addEventListener("click", () => showStep(btn.dataset.back));
    });

    bindCodeInputs();
  } catch (e) {
    // Ensure binding doesn't fail completely; log for diagnostics
    console.error('bindEvents error', e);
  }
}

  function markError(id, hasError) {
    const input = document.getElementById(id);
    const error = document.getElementById(id + "Error");

    if (input) input.classList.toggle("rc-auth-field-error", hasError);
    if (error) error.style.display = hasError ? "block" : "none";
  }

  function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  function isValidPhoneNumber(phone) {
    return /^[0-9\s]{4,24}$/.test(phone || "") && phone.replace(/\s+/g, "").length >= 4;
  }

  function message(key, fallback) {
    return typeof window.t === "function" ? window.t(key) : fallback;
  }

document.addEventListener("DOMContentLoaded", async () => {
  const params = new URLSearchParams(window.location.search);
  const step = params.get("step");

  if (step === "profile") {
    showStep("profile");
  }

  try {
    const res = await fetch("/api/auth/me", { credentials: 'same-origin' });
    if (res.ok) {
      const data = await res.json();
      if (data && data.email) {
        const emailEl = document.getElementById("profileEmail");
        if (emailEl) {
          emailEl.value = data.email;
          emailEl.readOnly = true;
        }

        const firstNameEl = document.getElementById("firstName");
        if (firstNameEl && data.firstName) firstNameEl.value = data.firstName;

        const lastNameEl = document.getElementById("lastName");
        if (lastNameEl && data.lastName) lastNameEl.value = data.lastName;

        const phoneCountryCodeEl = document.getElementById("phoneCountryCode");
        if (phoneCountryCodeEl && data.phoneCountryCode) phoneCountryCodeEl.value = data.phoneCountryCode;

        const phoneNumberEl = document.getElementById("phoneNumber");
        if (phoneNumberEl && data.phoneNumber) phoneNumberEl.value = data.phoneNumber;
      }
    }
  } catch (e) {}

  bindEvents();
});
})();
