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

    function goProfileFromGoogle() {
      state.email = "";
      showStep("profile");

      const profileEmail = document.getElementById("profileEmail");
      if (profileEmail) {
        profileEmail.value = "";
        profileEmail.placeholder = t("auth.emailPlaceholder");
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

  function saveProfile() {
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

    const user = {
      email,
      firstName,
      lastName,
      country,
      customerNumber: "RC-CUST-" + Math.floor(100000 + Math.random() * 900000)
    };

    localStorage.setItem("rentcarUser", JSON.stringify(user));
    window.location.href = "index.html";
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

  document.addEventListener("DOMContentLoaded", bindEvents);
})();