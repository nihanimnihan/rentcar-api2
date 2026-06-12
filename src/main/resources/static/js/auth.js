(function () {
  const state = {
    email: ""
  };

  function showStep(name) {
    document.querySelectorAll(".rc-auth-step").forEach(step => {
      step.classList.toggle("is-active", step.dataset.step === name);
    });
  }

  function getEmail() {
    return document.getElementById("email").value.trim();
  }

  function goVerify() {
    const email = getEmail();
    const invalid = !isValidEmail(email);

    markError("email", invalid);

    if (invalid) return;

    state.email = email;
    document.getElementById("verifyEmailText").textContent = email;
    showStep("verify");
    focusFirstCodeInput();
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
  const popupUrl = '/oauth2/authorize?returnTo=' + encodeURIComponent(returnTo) + '&provider=google&popup=1';

  // Try opening a popup
  const popup = openCenteredPopup(popupUrl, 600, 700);
  if (!popup) {
    // Popup blocked — fallback to full-page redirect
    window.location.href = popupUrl;
    return;
  }

  // Listen for postMessage from popup callback
  const onMessage = async (e) => {
    console.info('oauth popup message event', e.origin, e.data);
    // Only accept messages from same origin
    if (e.origin !== window.location.origin) return;
    const data = e.data || {};
    if (data.type !== 'oauth') return;

    window.removeEventListener('message', onMessage);

    // Refresh auth state and navigate accordingly
    try {
      const res = await fetch('/api/auth/me');
      if (res.ok) {
        const info = await res.json();
        const returnToFromPopup = data.returnTo || returnTo || '/index.html';
        if (data.profileComplete) {
          window.location.href = returnToFromPopup;
          return;
        } else {
          window.location.href = '/signup.html?step=profile&returnTo=' + encodeURIComponent(returnToFromPopup);
          return;
        }
      }
    } catch (err) {
      // On error, fallback to navigating to returnTo
      window.location.href = returnTo || '/index.html';
    }
  };

  window.addEventListener('message', onMessage);
  console.info('oauth popup listener attached');
}


    // Save profile to backend (if session-authenticated) or fallback to local storage
    async function saveProfile() {
      const email = document.getElementById("profileEmail").value.trim();
      const firstName = document.getElementById("firstName").value.trim();
      const lastName = document.getElementById("lastName").value.trim();
      const country = document.getElementById("country").value;

      const emailInvalid = !isValidEmail(email);
      const firstNameInvalid = !firstName;
      const lastNameInvalid = !lastName;
      const countryInvalid = !country;

      markError("profileEmail", emailInvalid);
      markError("firstName", firstNameInvalid);
      markError("lastName", lastNameInvalid);
      markError("country", countryInvalid);

      if (emailInvalid || firstNameInvalid || lastNameInvalid || countryInvalid) {
        return;
      }

      const payload = { firstName, lastName, country };

      try {
        const res = await fetch('/api/auth/profile', {
          method: 'POST',
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
        alert('Unable to save profile: ' + (errText || res.status));
        return;
      } catch (e) {
        console.error('Profile save request failed', e);
        alert('Unable to save profile due to a network error');
        return;
      }
    }

  function verifyCode() {
    const code = Array.from(document.querySelectorAll(".rc-code-row input"))
      .map(input => input.value)
      .join("");

    if (code.length !== 6) {
      alert("Please enter the 6-digit code.");
      return;
    }

    const profileEmail = document.getElementById("profileEmail");
    if (profileEmail) profileEmail.value = state.email;

    showStep("profile");
  }


  function focusFirstCodeInput() {
    const first = document.querySelector(".rc-code-row input");
    if (first) setTimeout(() => first.focus(), 100);
  }

  function bindCodeInputs() {
    const inputs = Array.from(document.querySelectorAll(".rc-code-row input"));

    inputs.forEach((input, index) => {
      input.addEventListener("input", () => {
        input.value = input.value.replace(/\D/g, "").slice(0, 1);
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

document.addEventListener("DOMContentLoaded", async () => {
  const params = new URLSearchParams(window.location.search);
  const step = params.get("step");

  if (step === "profile") {
    showStep("profile");
  }

  try {
    const res = await fetch("/api/auth/me");
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

        const countryEl = document.getElementById("country");
        if (countryEl && data.country) countryEl.value = data.country;
      }
    }
  } catch (e) {}

  bindEvents();
});
})();