document.addEventListener("DOMContentLoaded", function () {
  loadPartial("footer-placeholder", "partials/footer.html").then(function () {
    initSupportRequestModal();
  });
});

document.addEventListener("DOMContentLoaded", function () {
  loadPartial("language-modal-placeholder", "partials/language-modal.html");
});

function loadPartial(elementId, filePath) {
  var element = document.getElementById(elementId);

  if (!element) return Promise.resolve();

  return fetch(filePath)
    .then(function (response) { return response.text(); })
    .then(function (html) {
      element.innerHTML = html;
      if (typeof applyTranslations === "function") {
        applyTranslations(element);
      }
    })
    .catch(function (error) { console.error("Partial could not be loaded:", error); });
}

function initSupportRequestModal() {
  var modal = document.getElementById("supportRequestModal");
  if (!modal || modal.dataset.supportReady === "true") return;

  var openButton = document.getElementById("supportRequestOpen");
  var form = document.getElementById("supportRequestForm");
  var success = document.getElementById("supportRequestSuccess");
  var topic = document.getElementById("supportRequestTopic");
  var bookingReference = document.getElementById("supportRequestBookingReference");
  var email = document.getElementById("supportRequestEmail");
  var message = document.getElementById("supportRequestMessage");
  var counter = document.getElementById("supportRequestMessageCount");
  var status = document.getElementById("supportRequestStatus");
  var submitButton = document.getElementById("supportRequestSubmit");

  if (!openButton || !form || !success || !topic || !bookingReference || !email || !message || !counter || !status || !submitButton) {
    return;
  }

  modal.dataset.supportReady = "true";

  function tr(key, fallback) {
    return typeof t === "function" ? t(key) : fallback;
  }

  function updateCounter() {
    counter.textContent = String(message.value.length) + " / 500";
  }

  function clearErrors() {
    modal.querySelectorAll("[data-support-error-for]").forEach(function (el) {
      el.textContent = "";
    });
    status.hidden = true;
    status.textContent = "";
  }

  function setFieldError(name, text) {
    var error = modal.querySelector('[data-support-error-for="' + name + '"]');
    if (error) {
      error.textContent = text;
    }
  }

  function showError(text) {
    status.textContent = text;
    status.hidden = false;
  }

  function resetModal() {
    form.hidden = false;
    success.hidden = true;
    form.reset();
    clearErrors();
    updateCounter();
    submitButton.disabled = false;
    submitButton.textContent = tr("support.submit", "Submit request");
    if (typeof applyTranslations === "function") {
      applyTranslations(modal);
    }
  }

  function openModal() {
    resetModal();
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    setTimeout(function () {
      topic.focus();
    }, 0);
  }

  function closeModal() {
    modal.hidden = true;
    document.body.style.overflow = "";
  }

  function validate() {
    clearErrors();

    var isValid = true;
    var bookingReferenceValue = bookingReference.value.trim();
    var emailValue = email.value.trim();
    var messageValue = message.value.trim();

    if (!topic.value) {
      setFieldError("topic", tr("support.error.topicRequired", "Please choose a topic."));
      isValid = false;
    }

    if (bookingReferenceValue.length > 50) {
      setFieldError("bookingReference", tr("support.error.bookingReferenceMax", "Booking reference must be 50 characters or fewer."));
      isValid = false;
    }

    if (!emailValue) {
      setFieldError("email", tr("support.error.emailRequired", "Please enter your email address."));
      isValid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailValue)) {
      setFieldError("email", tr("support.error.emailInvalid", "Please enter a valid email address."));
      isValid = false;
    }

    if (!messageValue) {
      setFieldError("message", tr("support.error.messageRequired", "Please enter a message."));
      isValid = false;
    } else if (messageValue.length > 500) {
      setFieldError("message", tr("support.error.messageMax", "Message must be 500 characters or fewer."));
      isValid = false;
    }

    if (!isValid) {
      showError(tr("support.error.validation", "Please check the highlighted fields."));
      return null;
    }

    return {
      topic: topic.value,
      bookingReference: bookingReferenceValue || null,
      email: emailValue,
      message: messageValue
    };
  }

  openButton.addEventListener("click", openModal);

  modal.querySelectorAll("[data-support-close]").forEach(function (el) {
    el.addEventListener("click", closeModal);
  });

  message.addEventListener("input", updateCounter);

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    var payload = validate();
    if (!payload) return;

    submitButton.disabled = true;
    submitButton.textContent = tr("support.sending", "Sending...");

    try {
      var response = await fetch("/api/support-requests", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error("Support request failed with status " + response.status);
      }

      form.hidden = true;
      success.hidden = false;
      if (typeof applyTranslations === "function") {
        applyTranslations(success);
      }
    } catch (error) {
      console.error("Support request could not be submitted:", error);
      submitButton.disabled = false;
      submitButton.textContent = tr("support.submit", "Submit request");
      showError(tr("support.error.submitFailed", "We could not send your request. Please try again."));
    }
  });

  document.addEventListener("keydown", function (event) {
    if (!modal.hidden && event.key === "Escape") {
      closeModal();
    }
  });

  document.addEventListener("languageChanged", function () {
    updateCounter();
    if (!submitButton.disabled) {
      submitButton.textContent = tr("support.submit", "Submit request");
    }
  });

  updateCounter();
}

document.addEventListener("DOMContentLoaded", async function () {
  var filtersContainer = document.getElementById("car-filters-placeholder");
  if (!filtersContainer) return;

  var response = await fetch("/partials/car-filters.html?v=20260619-filter-fix");
  var html = await response.text();

  filtersContainer.innerHTML = html;

  if (typeof applyTranslations === "function") {
    applyTranslations(filtersContainer);
  }

  if (typeof initCarFilters === "function") {
    initCarFilters();
    markSelectedFilters();
  }

  // Trigger car search after partial load so #carsList is guaranteed to exist.
  if (typeof loadCars === "function") {
    loadCars();
  }
});

// Preloader removed to prevent white flash on theme pages. Previously injected partial and removed after 800ms.

document.addEventListener("DOMContentLoaded", function () {
    var minimalFooter = document.getElementById("minimal-footer-placeholder");
    if (!minimalFooter) return;
    fetch("partials/footer-minimal.html")
        .then(function (r) { return r.text(); })
        .then(function (html) {
            minimalFooter.innerHTML = html;
            if (typeof applyTranslations === "function") {
                applyTranslations(minimalFooter);
            }
        });
});
const minimalHeader = document.getElementById("minimal-header-placeholder");

if (minimalHeader) {
    fetch("partials/header-minimal.html")
        .then(response => response.text())
        .then(html => {
            minimalHeader.innerHTML = html;
            if (typeof applyTranslations === "function") {
                applyTranslations(minimalHeader);
            }
            if (typeof updateCurrentLanguageLabels === "function") {
                updateCurrentLanguageLabels();
            }
        });
}

// ── Language dropdown ─────────────────────────────────────────────────────
(function () {
    function getLangMenu() {
        return document.querySelector('.langMenu');
    }

    function getLangContent() {
        var dd = getLangMenu();
        return dd ? dd.querySelector('.langMenu__content') : null;
    }

    function positionUnder(btn) {
        var content = getLangContent();
        if (!content) return;
        var rect = btn.getBoundingClientRect();
        content.style.top   = (rect.bottom + 8) + 'px';
        content.style.right = (window.innerWidth - rect.right) + 'px';
        content.style.left  = 'auto';
    }

    function openDropdown(btn) {
        var dd = getLangMenu();
        if (!dd) return;
        positionUnder(btn);
        dd.classList.remove('is-hidden');
    }

    function closeDropdown() {
        var dd = getLangMenu();
        if (dd) dd.classList.add('is-hidden');
    }

    document.addEventListener('click', function (e) {
        var btn = e.target.closest('.js-lang-toggle');
        if (btn) {
            var dd = getLangMenu();
            if (dd && !dd.classList.contains('is-hidden')) {
                closeDropdown();
            } else {
                openDropdown(btn);
            }
            return;
        }

        // Click-outside: close if the click lands outside the dropdown content
        var content = getLangContent();
        var dd = getLangMenu();
        if (dd && !dd.classList.contains('is-hidden')) {
            if (!content || !content.contains(e.target)) {
                closeDropdown();
            }
        }
    });
}());
