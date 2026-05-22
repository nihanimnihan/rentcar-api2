// review.js — booking review / checkout page logic

const COUNTRIES = [
  { value: "ES", en: "Spain",          es: "España" },
  { value: "US", en: "United States",  es: "Estados Unidos" },
  { value: "GB", en: "United Kingdom", es: "Reino Unido" },
  { value: "DE", en: "Germany",        es: "Alemania" },
  { value: "FR", en: "France",         es: "Francia" },
  { value: "IT", en: "Italy",          es: "Italia" },
  { value: "NL", en: "Netherlands",    es: "Países Bajos" },
  { value: "BE", en: "Belgium",        es: "Bélgica" },
  { value: "CH", en: "Switzerland",    es: "Suiza" },
  { value: "AT", en: "Austria",        es: "Austria" },
  { value: "PT", en: "Portugal",       es: "Portugal" },
  { value: "AU", en: "Australia",      es: "Australia" },
  { value: "CA", en: "Canada",         es: "Canadá" },
];

function populateCountrySelect() {
  const sel = document.getElementById("rfInvCountry");
  if (!sel) return;
  const lang = getLanguage();
  const current = sel.value;
  sel.innerHTML =
    `<option value="">${t('review.selectCountry')}</option>` +
    COUNTRIES.map(c =>
      `<option value="${c.value}">${lang === 'es' ? c.es : c.en}</option>`
    ).join("");
  sel.value = current; // restore selection
}

document.addEventListener("DOMContentLoaded", () => {
  populateCountrySelect();
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
  ["rfPriceDetailsBtn", "reviewSummaryPriceDetailsBtn"].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.addEventListener("click", buildAndOpenPriceModal);
  });
}

function buildAndOpenPriceModal() {
  if (!reviewCar?.priceBreakdown) return;
  const modalId = "reviewPriceModal";
  const existing = document.getElementById(modalId);
  if (existing) existing.remove();

  const rentalDays = reviewCar.priceBreakdown.rentalDays || 1;
  const addonLines = [];

  if (reviewMileageOption === "UNLIMITED") {
    const charge = Number(reviewCar.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays;
    addonLines.push({ name: t('car.unlimitedKm'), totalPrice: charge.toFixed(2) });
  }

  reviewAddonIds.forEach(addonId => {
    const addon = reviewAllAddons.find(a => a.id === addonId);
    if (!addon) return;
    const totalPrice = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    addonLines.push({ name: localAddonName(addon), totalPrice: totalPrice.toFixed(2) });
  });

  document.body.insertAdjacentHTML("beforeend", buildPriceDetailsModalHtml(modalId, reviewCar.priceBreakdown, addonLines));
  openPriceDetailsModal(modalId);
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

  if (confirmBtn) { confirmBtn.disabled = true; confirmBtn.textContent = t('review.processing'); }
  if (errorDiv)   errorDiv.style.display = "none";

  // Track whether payment completed so the finally block knows not to re-enable the button.
  let paymentSucceeded = false;
  try {
    const res = await fetch("/api/bookings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    if (res.ok) {
      const booking = await res.json();
      if (confirmBtn) confirmBtn.textContent = t('review.processing');

      // Step 2: Create payment intent — amount/currency come from the booking,
      // never from the frontend.
      let intent;
      try {
        intent = await createPaymentIntent(booking.id);
      } catch (err) {
        console.error("Payment intent creation failed:", err);
        showBookingFormError(err.message || t('review.bookingCreatedPaymentFailed'));
        return; // paymentSucceeded stays false → finally re-enables button
      }

      // TODO (Stripe): when real Stripe is enabled, replace the process call below
      // with a stripe.confirmCardPayment() call using the clientSecret from the intent:
      //
      //   const { error } = await stripe.confirmCardPayment(intent.clientSecret, {
      //     payment_method: {
      //       card: cardElement,           // Stripe Elements card element
      //       billing_details: { name: `${firstName} ${lastName}`, email },
      //     },
      //   });
      //   if (error) { showBookingFormError(error.message); return; }
      //   // On success, stripe.js will have confirmed the PaymentIntent server-side.
      //   // Then call processBookingPayment() to advance the booking to CONFIRMED.

      if (confirmBtn) confirmBtn.textContent = t('review.confirmingPayment');
      paymentSucceeded = await processBookingPayment(booking.id, firstName);
    } else if (res.status === 409) {
      const msg = await res.text().catch(() => t('error.carNoLongerAvailable'));
      showBookingFormError(msg);
    } else if (res.status === 400) {
      const data = await res.json().catch(() => null);
      const msg = data?.message || t('error.checkDetails');
      showBookingFormError(msg);
    } else {
      showBookingFormError(t('error.somethingWrong'));
    }
  } catch (err) {
    console.error("Booking submission failed:", err);
    showBookingFormError(t('error.networkError'));
  } finally {
    // Re-enable only on failure — success replaces the form with the confirmation screen.
    if (!paymentSucceeded && confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.textContent = t('review.payAndBook');
    }
  }
}

// ── Payment processing ────────────────────────────────────────────────────────

/**
 * Calls POST /api/bookings/{bookingId}/payments/intent.
 *
 * Returns the intent payload: { bookingId, bookingReference, amount, currencyCode,
 * providerName, clientSecret, paymentReference }.
 *
 * Amount and currency are always derived server-side from the booking — the
 * frontend never sends them.  clientSecret is null in dev/fake mode and is
 * the real Stripe secret in production.
 *
 * Throws with a user-visible message on non-OK responses.
 */
async function createPaymentIntent(bookingId) {
  const res = await fetch(`/api/bookings/${bookingId}/payments/intent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  });
  if (!res.ok) {
    let msg;
    try { msg = (await res.json()).message; } catch (_) {}
    throw new Error(msg || t('review.bookingCreatedPaymentFailed'));
  }
  return res.json();
}

/**
 * Returns the payment method ID to send to the backend.
 *
 * MVP: always returns the mock valid token.  The radio buttons on the review
 * page are wired to `initPaymentOptions()` and update the UI selection, but
 * there is no real Stripe integration yet.  When Stripe Elements are added,
 * replace the return value here with the generated PaymentMethod ID.
 */
function getSelectedPaymentMethodId() {
  // Future: read from Stripe Elements / selected radio and map to real PM id.
  return "pm_test_valid";
}

/**
 * Calls POST /api/bookings/{bookingId}/payments/process.
 * The booking already exists and a payment intent has been created;
 * this call transitions it from PENDING → CONFIRMED (success) or FAILED (decline).
 *
 * Returns true if the booking reached CONFIRMED, false on any failure.
 * The error panel is shown before returning false.
 */
async function processBookingPayment(bookingId, firstName) {
  try {
    const res = await fetch(`/api/bookings/${bookingId}/payments/process`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ paymentMethodId: getSelectedPaymentMethodId() })
    });

    // Try to parse body regardless of status — backend may include a message.
    let data = null;
    try { data = await res.json(); } catch (_) { /* non-JSON body, ignore */ }

    if (res.ok && data?.status === "CONFIRMED") {
      showBookingSuccess(data, firstName);
      return true;
    }

    // Payment failed: prefer backend message, fall back to generic copy.
    const fallback = t('review.bookingCreatedPaymentFailed');
    showBookingFormError(data?.message || fallback);
    return false;
  } catch (err) {
    console.error("Payment processing failed:", err);
    showBookingFormError(t('review.bookingCreatedPaymentFailed'));
    return false;
  }
}

// ── Success screen ────────────────────────────────────────────────────────────

function showBookingSuccess(booking, firstName) {
  const formColumn = document.getElementById("reviewFormColumn");
  if (!formColumn) return;

  formColumn.innerHTML = `
    <div id="rfSuccessPanel" class="rentcar-review-card" style="text-align:center;padding:60px 40px">
      <div style="font-size:56px;margin-bottom:20px">🎉</div>
      <h2 class="text-28 fw-700 mb-15">${t('review.bookingConfirmed')}</h2>
      <p class="text-18 text-light-1 mb-10">
        ${t('review.thankYou', { name: esc(firstName) })}
      </p>
      <p class="text-15 text-light-1 mb-10">
        ${t('review.bookingRef')} <strong>${esc(String(booking.bookingReference || booking.id || "—"))}</strong>
      </p>
      <p class="mb-30">
        <span class="rc-badge rc-badge--success">${t('review.statusConfirmed')}</span>
      </p>
      <a href="index.html" class="button h-60 px-50 bg-yellow-1 text-dark-1 rounded-8 fw-700">
        ${t('review.backToHome')}
      </a>
    </div>
  `;

  window.scrollTo({ top: 0, behavior: "smooth" });
}

// ── Error helpers ─────────────────────────────────────────────────────────────

function showBookingFormError(message) {
  const div = document.getElementById("rfBookingError");
  if (!div) return;
  div.innerHTML = `
    <div class="rc-alert rc-alert--error">
      <div class="rc-alert__icon"><i class="icon-close"></i></div>
      <div class="rc-alert__content">
        <div class="rc-alert__message">${esc(message)}</div>
      </div>
    </div>`;
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
  populateCountrySelect();
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
