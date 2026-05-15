// review.js — booking review / checkout page logic

document.addEventListener("DOMContentLoaded", () => {
  loadReviewPage();
  initPaymentOptions();
  initFlightToggle();
  initConfirmButtons();
});

// ── State ────────────────────────────────────────────────────────────────────

let reviewCar = null;
let reviewAllAddons = [];
let reviewAddonIds = [];
let reviewMileageOption = "INCLUDED";

// ── Bootstrap ────────────────────────────────────────────────────────────────

async function loadReviewPage() {
  const params = new URLSearchParams(window.location.search);

  const carId = params.get("carId");
  if (!carId) {
    showReviewError(t('error.missingBooking'), "cars.html");
    return;
  }

  const pickupDateTime  = params.get("pickupDateTime");
  const dropoffDateTime = params.get("dropoffDateTime");
  reviewMileageOption   = params.get("mileageOption") || "INCLUDED";
  reviewAddonIds        = params.getAll("addonIds").map(Number).filter(Boolean);

  // Fetch car details + available addons
  try {
    const [carRes, addonsRes] = await Promise.all([
      fetch(`/api/cars/${encodeURIComponent(carId)}${window.location.search}`),
      fetch("/api/addons/active")
    ]);

    if (!carRes.ok) {
      if (carRes.status === 404) {
        showReviewError(t('error.carNotAvailable'), "cars.html");
      } else {
        showReviewError(t('error.couldNotLoad'), "cars.html");
      }
      return;
    }

    reviewCar = await carRes.json();

    if (addonsRes.ok) {
      reviewAllAddons = await addonsRes.json();
    }

    // Render shared summary card
    renderBookingSummary({
      car: reviewCar,
      params,
      mileageOption: reviewMileageOption,
      selectedAddonIds: reviewAddonIds,
      availableAddons: reviewAllAddons
    });

    // Render page-specific total
    const { total } = calcBookingTotal(reviewCar, reviewMileageOption, reviewAddonIds, reviewAllAddons);
    const fmt = (v) => `€${Number(v).toFixed(2)}`;
    const rfTotal = document.getElementById("rfTotalAmount");
    if (rfTotal) rfTotal.textContent = fmt(total);

    // Min age
    const ageSpan = document.getElementById("rfMinAge");
    if (ageSpan) ageSpan.textContent = reviewCar.minDriverAge || 21;

  } catch (err) {
    console.error("Failed to load review page data:", err);
    showReviewError(t('error.networkError'), "cars.html");
  }
}

// ── Payment option selection ─────────────────────────────────────────────────

function initPaymentOptions() {
  document.querySelectorAll(".rentcar-payment-option").forEach(label => {
    const radio = label.querySelector("input[type='radio']");
    if (!radio) return;
    radio.addEventListener("change", () => {
      document.querySelectorAll(".rentcar-payment-option").forEach(l => l.classList.remove("is-selected"));
      if (radio.checked) label.classList.add("is-selected");
    });
    // Also handle click on label
    label.addEventListener("click", () => {
      document.querySelectorAll(".rentcar-payment-option").forEach(l => l.classList.remove("is-selected"));
      label.classList.add("is-selected");
    });
  });
}

// ── Flight toggle ────────────────────────────────────────────────────────────

function initFlightToggle() {
  const noFlightChk  = document.getElementById("rfNoFlight");
  const flightField  = document.getElementById("field-flightNumber");
  const flightInput  = document.getElementById("rfFlightNumber");

  if (!noFlightChk || !flightField) return;

  noFlightChk.addEventListener("change", () => {
    if (noFlightChk.checked) {
      flightField.style.opacity  = "0.4";
      flightField.style.pointerEvents = "none";
      if (flightInput) flightInput.value = "";
    } else {
      flightField.style.opacity  = "";
      flightField.style.pointerEvents = "";
    }
  });
}

// ── Confirm button ───────────────────────────────────────────────────────────

function initConfirmButtons() {
  const footerBtn = document.getElementById("rfConfirmBtn");
  if (footerBtn) footerBtn.addEventListener("click", () => submitBooking());

  // Price details buttons — footer card + summary card
  const openPriceModal = () => {
    if (!reviewCar) return;
    openPriceDetailsModal(reviewCar, reviewMileageOption);
  };

  ["rfPriceDetailsBtn", "reviewSummaryPriceDetailsBtn"].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.addEventListener("click", openPriceModal);
  });
}

// ── Form validation ──────────────────────────────────────────────────────────

function validateForm() {
  let valid = true;

  // Email
  const email = document.getElementById("rfEmail")?.value?.trim() || "";
  setFieldError("field-email", !email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email));
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) valid = false;

  // First name
  const firstName = document.getElementById("rfFirstName")?.value?.trim() || "";
  setFieldError("field-firstName", !firstName);
  if (!firstName) valid = false;

  // Last name
  const lastName = document.getElementById("rfLastName")?.value?.trim() || "";
  setFieldError("field-lastName", !lastName);
  if (!lastName) valid = false;

  // Phone
  const phone = document.getElementById("rfPhone")?.value?.trim() || "";
  setFieldError("field-phone", !phone);
  if (!phone) valid = false;

  // Age confirmation
  const ageConfirmed = document.getElementById("rfAgeConfirm")?.checked;
  const ageErr = document.getElementById("ageConfirmError");
  if (!ageConfirmed) {
    if (ageErr) ageErr.style.display = "block";
    valid = false;
  } else {
    if (ageErr) ageErr.style.display = "none";
  }

  // Terms
  const termsAccepted = document.getElementById("rfTerms")?.checked;
  const termsErr = document.getElementById("termsError");
  if (!termsAccepted) {
    if (termsErr) termsErr.style.display = "block";
    valid = false;
  } else {
    if (termsErr) termsErr.style.display = "none";
  }

  return valid;
}

function setFieldError(fieldId, hasError) {
  const field = document.getElementById(fieldId);
  if (!field) return;
  if (hasError) {
    field.classList.add("has-error");
  } else {
    field.classList.remove("has-error");
  }
}

// ── Booking submission ────────────────────────────────────────────────────────

async function submitBooking() {
  if (!validateForm()) {
    // Scroll to first error
    const firstError = document.querySelector(".has-error, #ageConfirmError[style*='block'], #termsError[style*='block']");
    if (firstError) firstError.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }

  const params = new URLSearchParams(window.location.search);

  const carId          = Number(params.get("carId"));
  const pickupDateTime = params.get("pickupDateTime");
  const dropoffDateTime = params.get("dropoffDateTime");
  const pickupLocation  = params.get("pickupLocation");
  const dropoffLocation = params.get("dropoffLocation");
  const mileageOption   = params.get("mileageOption") || "INCLUDED";
  const addonIds        = params.getAll("addonIds").map(Number).filter(Boolean);

  const firstName = document.getElementById("rfFirstName").value.trim();
  const lastName  = document.getElementById("rfLastName").value.trim();
  const email     = document.getElementById("rfEmail").value.trim();
  const countryCode = document.getElementById("rfCountryCode").value;
  const phone     = `${countryCode}${document.getElementById("rfPhone").value.trim()}`;

  const body = {
    carId,
    customerName:  `${firstName} ${lastName}`,
    customerEmail: email,
    customerPhone: phone,
    pickupDateTime,
    dropoffDateTime,
    pickupLocation,
    dropoffLocation,
    addonIds: addonIds.length > 0 ? addonIds : null,
    mileageOption
  };

  const confirmBtn = document.getElementById("rfConfirmBtn");
  const errorDiv   = document.getElementById("rfBookingError");

  if (confirmBtn) { confirmBtn.disabled = true; confirmBtn.textContent = "Processing…"; }  if (errorDiv)   errorDiv.style.display = "none";

  try {
    const res = await fetch("/api/bookings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    if (res.ok) {
      const booking = await res.json();
      showBookingSuccess(booking, firstName);
    } else if (res.status === 409) {
      const msg = await res.text().catch(() => "This car is no longer available for the selected dates.");
      showBookingFormError(msg);
    } else if (res.status === 400) {
      const data = await res.json().catch(() => null);
      const msg = data?.message || "Please check your details and try again.";
      showBookingFormError(msg);
    } else {
      showBookingFormError("Something went wrong. Please try again.");
    }
  } catch (err) {
    console.error("Booking submission failed:", err);
    showBookingFormError("A network error occurred. Please check your connection.");
  } finally {
    if (confirmBtn) { confirmBtn.disabled = false; confirmBtn.textContent = t('review.payAndBook'); }
  }
}

// ── Success screen ────────────────────────────────────────────────────────────

function showBookingSuccess(booking, firstName) {
  const formColumn = document.getElementById("reviewFormColumn");
  if (!formColumn) return;

  formColumn.innerHTML = `
    <div class="rentcar-review-card" style="text-align:center;padding:60px 40px">
      <div style="font-size:56px;margin-bottom:20px">🎉</div>
      <h2 class="text-28 fw-700 mb-15">Booking Confirmed!</h2>
      <p class="text-18 text-light-1 mb-10">
        Thank you, <strong>${esc(firstName)}</strong>! Your car is reserved.
      </p>
      <p class="text-15 text-light-1 mb-30">
        Booking reference: <strong>#${esc(String(booking.id || "—"))}</strong>
      </p>
      <a href="index.html" class="button h-60 px-50 bg-yellow-1 text-dark-1 rounded-8 fw-700">
        Back to Home
      </a>
    </div>
  `;

  window.scrollTo({ top: 0, behavior: "smooth" });
}

// ── Error helpers ─────────────────────────────────────────────────────────────

function showBookingFormError(message) {
  const div = document.getElementById("rfBookingError");
  if (!div) return;
  div.textContent = message;
  div.style.display = "block";
  div.scrollIntoView({ behavior: "smooth", block: "center" });
}

function showReviewError(message, backHref) {
  const errorDiv = document.getElementById("reviewPageError");
  const msgEl    = document.getElementById("reviewPageErrorMsg");
  const backEl   = document.getElementById("reviewBackLink");

  if (msgEl)   msgEl.textContent = message;
  if (backEl && backHref) backEl.href = backHref;
  if (errorDiv) errorDiv.style.display = "block";

  // Hide the form cards
  document.querySelectorAll(".rentcar-review-card").forEach(el => el.style.display = "none");
}

// ── Utility ───────────────────────────────────────────────────────────────────

function esc(str) {
  if (typeof escapeHtml === "function") return escapeHtml(str);
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

document.addEventListener('languageChanged', function () {
  applyTranslations(document);
  if (reviewCar) {
    renderBookingSummary({
      car: reviewCar,
      params: new URLSearchParams(window.location.search),
      mileageOption: reviewMileageOption,
      selectedAddonIds: reviewAddonIds,
      availableAddons: reviewAllAddons
    });
  }
});
