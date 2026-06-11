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
      const rt = params.get('returnTo');
      if (rt) return rt;
      return window.location.pathname + window.location.search;
    }

    function goProfileFromGoogle() {
      const returnTo = getReturnTo();
      // Redirect to backend which stores returnTo in session then forwards to Google's OAuth
      window.location.href = '/oauth2/authorize?returnTo=' + encodeURIComponent(returnTo) + '&provider=google';
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
          const params = new URLSearchParams(window.location.search);
          const returnTo = params.get('returnTo') || '/index.html';
          window.location.href = decodeURIComponent(returnTo);
          return;
        }
      } catch (e) {
        console.warn('Profile save failed, falling back to local storage', e);
      }

      // Fallback for demo: save locally
      const user = {
        email,
        firstName,
        lastName,
        country,
        customerNumber: "RC-CUST-" + Math.floor(100000 + Math.random() * 900000)
      };

      localStorage.setItem("rentcarUser", JSON.stringify(user));
      const params = new URLSearchParams(window.location.search);
      const returnTo = params.get('returnTo') || '/index.html';
      window.location.href = decodeURIComponent(returnTo);
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
    document.getElementById("continueEmailBtn").addEventListener("click", goVerify);
    document.getElementById("googleBtn").addEventListener("click", goProfileFromGoogle);
    document.getElementById("verifyCodeBtn").addEventListener("click", verifyCode);
    document.getElementById("saveProfileBtn").addEventListener("click", saveProfile);

    document.querySelectorAll("[data-back]").forEach(btn => {
      btn.addEventListener("click", () => showStep(btn.dataset.back));
    });

    bindCodeInputs();
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
    // If arriving at profile step, prefill fields from backend if available
    const params = new URLSearchParams(window.location.search);
    const step = params.get('step');
    if (step === 'profile') {
      try {
        const res = await fetch('/api/auth/me');
        if (res.ok) {
          const data = await res.json();
          if (data && data.email) {
            const emailEl = document.getElementById('profileEmail');
            if (emailEl) {
              emailEl.value = data.email;
              emailEl.readOnly = true; // ensure readonly
            }
            if (data.firstName) {
              const fn = document.getElementById('firstName'); if (fn && !fn.value) fn.value = data.firstName;
            }
            if (data.lastName) {
              const ln = document.getElementById('lastName'); if (ln && !ln.value) ln.value = data.lastName;
            }
            if (data.country) {
              const c = document.getElementById('country'); if (c && !c.value) c.value = data.country;
            }
          }
        }
      } catch (e) { /* ignore */ }
    }

    bindEvents();
  });
})();