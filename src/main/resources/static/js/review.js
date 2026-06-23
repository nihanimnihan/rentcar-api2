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
  initReviewBackToAddonsLink();

  loadStripePublishableKey().then(key => {
    if (key) {
      initStripeElements();
      showStripeCardContainer(true);
    }
  });
});

function initReviewBackToAddonsLink() {
  const addonsLink = document.getElementById("reviewBackToAddonsLink");
  const searchLink = document.getElementById("reviewChangeSearchLink");

  const params = new URLSearchParams(window.location.search);
  if (!params.get("carId")) {
    if (addonsLink) addonsLink.style.display = "none";
    if (searchLink) searchLink.style.display = "none";
    return;
  }

  const query = params.toString();
  if (addonsLink) addonsLink.href = `addons.html?${query}`;
  if (searchLink) searchLink.href = `cars.html?${query}`;
}

// ── State ────────────────────────────────────────────────────────────────────

let reviewCar = null;
let reviewAllAddons = [];
let reviewAddonIds = [];
let reviewMileageOption = "INCLUDED";
let reviewInsurancePackage = null;

// Checkout session signature stored for reuse detection
let reviewCheckoutSignature = null;

// Stripe Elements state
let stripe = null;
let stripeElements = null;
let stripeCard = null;
let stripePublishableKey = null;
let stripeInitialized = false;

/**
 * Deterministic, URL-safe signature representing the current checkout parameters.
 * Not a secret — used only to detect whether stored pending booking belongs to
 * the same search (car/dates/addons/mileage).
 */
function buildCheckoutSignature({carId, pickupDateTime, dropoffDateTime, pickupLocation, dropoffLocation, addonIds, mileageOption, insurancePackageId}) {
  const addons = (Array.isArray(addonIds) ? [...addonIds] : []).filter(Boolean).map(Number).sort((a,b)=>a-b);
  // Use a simple canonical string. Avoid JSON.stringify ordering gotchas by explicit order.
  return `${carId}|${pickupDateTime || ''}|${dropoffDateTime || ''}|${pickupLocation || ''}|${dropoffLocation || ''}|${mileageOption || ''}|${insurancePackageId || ''}|${addons.join(',')}`;
}

async function loadStripePublishableKey() {
  try {
    const res = await fetch('/api/config/stripe-publishable-key');
    if (!res.ok) return null;
    const data = await res.json();
    stripePublishableKey = data?.publishableKey || null;
    return stripePublishableKey;
  } catch (e) {
    console.warn('Could not load stripe publishable key:', e);
    return null;
  }
}

function initStripeElements() {
  if (stripeInitialized) return;
  if (!stripePublishableKey) return;
  try {
    stripe = Stripe(stripePublishableKey);
    stripeElements = stripe.elements();
    stripeCard = stripeElements.create('card', {
      hidePostalCode: true,
      disableLink: true
    });
    stripeCard.on('change', event => {
      setStripeCardError(event.error?.message || "");
    });
    stripeInitialized = true;
  } catch (e) {
    console.error('Stripe initialization failed', e);
    stripeInitialized = false;
  }
}

function showStripeCardContainer(show) {
  const container = document.getElementById('stripe-card-container');
  if (!container) return;
  container.style.display = show ? 'block' : 'none';
  if (show && stripeInitialized && stripeCard) {
    // mount when visible
    try { stripeCard.mount('#stripe-card-element'); } catch (e) { /* already mounted */ }
  } else if (!show && stripeCard) {
    try { stripeCard.unmount(); } catch (e) { /* ignore */ }
  }
}

function setStripeCardError(message) {
  const errorDiv = document.getElementById('stripe-card-errors');
  const element = document.getElementById('stripe-card-element');
  if (!errorDiv) return;
  const text = message || "";
  errorDiv.textContent = text;
  errorDiv.style.display = text ? 'block' : 'none';
  if (element) {
    element.classList.toggle('is-invalid', Boolean(text));
    element.setAttribute('aria-invalid', text ? 'true' : 'false');
  }
}

function focusStripeCardField() {
  const container = document.getElementById('stripe-card-container');
  if (container) {
    container.scrollIntoView({ behavior: "smooth", block: "center" });
  }
  if (stripeCard && typeof stripeCard.focus === "function") {
    setTimeout(() => stripeCard.focus(), 120);
  }
}

function showStripeCardValidation(message) {
  setStripeCardError(message || t('review.cardDetailsIncomplete'));
  focusStripeCardField();
}

function isStripeValidationError(error) {
  if (!error) return false;
  const validationCodes = [
    "incomplete_number",
    "incomplete_expiry",
    "incomplete_cvc",
    "incomplete_zip",
    "invalid_number",
    "invalid_expiry_month",
    "invalid_expiry_year",
    "invalid_cvc",
    "incorrect_number"
  ];
  return error.type === "validation_error" || validationCodes.includes(error.code);
}

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
  const insurancePackageId = params.get("insurancePackageId");
  if (!insurancePackageId) {
    showReviewError(t('protection.required'), "insurance.html" + window.location.search);
    return;
  }

  // Fetch car details + available addons
  try {
    const lang = typeof getLanguage === "function" ? getLanguage() : "en";
    const [carRes, addonsRes, insuranceRes] = await Promise.all([
      fetch(`/api/cars/${encodeURIComponent(carId)}${window.location.search}`),
      fetch("/api/addons/active"),
      fetch(`/api/insurance-packages/active?lang=${encodeURIComponent(lang)}`)
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
    if (!insuranceRes.ok) {
      showReviewError(t('protection.loadError'), "insurance.html" + window.location.search);
      return;
    }
    const insurancePackages = await insuranceRes.json();
    reviewInsurancePackage = insurancePackages.find(pkg => Number(pkg.id) === Number(insurancePackageId));
    if (!reviewInsurancePackage) {
      showReviewError(t('protection.unavailable'), "insurance.html" + window.location.search);
      return;
    }

    // Render shared summary card
    renderBookingSummary({
      car: reviewCar,
      params,
      mileageOption: reviewMileageOption,
      selectedAddonIds: reviewAddonIds,
      availableAddons: reviewAllAddons,
      selectedInsurance: reviewInsurancePackage,
      pageType: 'review'
    });

    // Render page-specific total
    const { total } = calcBookingTotal(reviewCar, reviewMileageOption, reviewAddonIds, reviewAllAddons, reviewInsurancePackage);
    const fmt = (v) => `€${Number(v).toFixed(2)}`;
    const rfTotal = document.getElementById("rfTotalAmount");
    if (rfTotal) rfTotal.textContent = fmt(total);

    // Min age
    const ageSpan = document.getElementById("rfMinAge");
    if (ageSpan) ageSpan.textContent = reviewCar.minDriverAge || 21;

    // Prefill driver info from authenticated user (if available)
    try {
      const authRes = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (authRes.ok) {
        const auth = await authRes.json();
        if (auth && auth.email) {
          const emailInput = document.getElementById('rfEmail');
          const fn = document.getElementById('rfFirstName');
          const ln = document.getElementById('rfLastName');
          if (emailInput && !emailInput.value) emailInput.value = auth.email;
          if (fn && !fn.value && auth.firstName) fn.value = auth.firstName;
          if (ln && !ln.value && auth.lastName) ln.value = auth.lastName;
        }
      }
    } catch (e) { /* ignore */ }

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

  // Price details buttons — footer card + summary card (direct binding if present)
  ["rfPriceDetailsBtn", "reviewSummaryPriceDetailsBtn"].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.addEventListener("click", buildAndOpenPriceModal);
  });

  // Robust delegated handler: catches clicks on dynamically rendered summary button
  document.addEventListener("click", function (event) {
    if (event.target.closest("#reviewSummaryPriceDetailsBtn, [data-summary-price-details]")) {
      event.preventDefault();
      buildAndOpenPriceModal();
    }
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

  if (reviewInsurancePackage) {
    const charge = Number(reviewInsurancePackage.pricePerDay || 0) * rentalDays;
    addonLines.push({ name: reviewInsurancePackage.name || t('summary.protection'), totalPrice: charge.toFixed(2) });
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

function clearCheckoutSession() {
  sessionStorage.removeItem('rentcarPendingBookingId');
  sessionStorage.removeItem('rentcarCheckoutSessionToken');
  sessionStorage.removeItem('rentcarCheckoutSignature');
}

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

  // Compute a deterministic signature of the current checkout parameters so we can
  // safely reuse an existing pending booking created for the same selection.
  const currentCheckoutSignature = buildCheckoutSignature({
    carId,
    pickupDateTime,
    dropoffDateTime,
    pickupLocation,
    dropoffLocation,
    addonIds,
    mileageOption: reviewMileageOption,
    insurancePackageId: Number(params.get("insurancePackageId"))
  });


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
    insurancePackageId: Number(params.get("insurancePackageId")),
    addonIds: addonIds.length > 0 ? addonIds : null,
    mileageOption,
    language: currentReviewLanguage()
  };

  const confirmBtn = document.getElementById("rfConfirmBtn");
  const errorDiv   = document.getElementById("rfBookingError");

  if (confirmBtn) { confirmBtn.disabled = true; confirmBtn.textContent = t('review.processing'); }
  if (errorDiv)   errorDiv.style.display = "none";

  // Track whether payment completed so the finally block knows not to re-enable the button.
  let paymentSucceeded = false;
  try {
    // Check for existing pending booking owned by this session
    const storedId = sessionStorage.getItem('rentcarPendingBookingId');
    const storedToken = sessionStorage.getItem('rentcarCheckoutSessionToken');
    const storedSignature = sessionStorage.getItem('rentcarCheckoutSignature');
    let booking;

    // Require id + token + signature match for safe reuse; otherwise clear partial state
    if (storedId && storedToken && storedSignature && storedSignature === currentCheckoutSignature) {
      booking = { id: Number(storedId), status: "PENDING" };
    } else {
      clearCheckoutSession();
    }

    // If no reusable booking found, create one
    if (!booking) {
      const res = await fetch("/api/bookings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });

      if (res.ok) {
        booking = await res.json();

        const tokenHeader = res.headers.get("X-Checkout-Session-Token");
        if (tokenHeader) {
          sessionStorage.setItem("rentcarPendingBookingId", String(booking.id));
          sessionStorage.setItem("rentcarCheckoutSessionToken", tokenHeader);
          sessionStorage.setItem("rentcarCheckoutSignature", currentCheckoutSignature);
        }

        if (confirmBtn) confirmBtn.textContent = t("review.processing");

      } else if (res.status === 409) {
        const data = await res.json().catch(() => null);
        showReviewFlowModal({
          variant: "error",
          icon: "!",
          title: "Car no longer available",
          message: data?.message || "This car has just been reserved for the selected dates. Please choose another vehicle to continue.",
          buttonText: "Choose another car",
          onClose: goBackToCarsWithSameSearch
        });
        return;

      } else if (res.status === 400) {
        const data = await res.json().catch(() => null);
        showBookingFormError(data?.message || t("error.checkDetails"));
        return;

      } else {
        showBookingFormError(t("error.somethingWrong"));
        return;
      }
    }
    // Step 2: Create payment intent — amount/currency come from the booking,
    // never from the frontend.
    let intent;
    try {
      intent = await createPaymentIntent(booking.id);
    } catch (err) {
      console.error("Payment intent creation failed:", err);
      // If token rejected, clear stored token so user can create a fresh booking
      if (err.message && err.message.toLowerCase().includes('checkout')) {
        sessionStorage.removeItem('rentcarPendingBookingId');
        sessionStorage.removeItem('rentcarCheckoutSessionToken');
        sessionStorage.removeItem('rentcarCheckoutSignature');
      }
      showReviewFlowModal({
        title: t('review.paymentStartFailedTitle'),
        message: t('review.paymentStartFailedMessage'),
        buttonText: t('review.tryAgain')
      });
      return; // paymentSucceeded stays false → finally re-enables button
    }

    const provider = intent?.providerName || intent?.provider;
    if (provider !== "STRIPE" || !intent?.clientSecret) {
      showReviewFlowModal({
        title: t('review.paymentStartFailedTitle'),
        message: t('review.paymentUnavailableMessage'),
        buttonText: t('review.tryAgain')
      });
      return;
    }

    // Stripe flow: confirm the PaymentIntent client-side with Stripe Elements.
    if (!stripePublishableKey) {
      await loadStripePublishableKey();
    }
    if (!stripePublishableKey) {
      showBookingFormError('Payment provider not available. Please try again later.');
      return;
    }

    if (!stripeInitialized) initStripeElements();

    if (!stripe || !stripeCard) {
      showBookingFormError('Payment initialization failed. Please try again.');
      return;
    }

    setStripeCardError("");
    if (confirmBtn) confirmBtn.textContent = t('review.confirmingPayment');

    try {
      const result = await stripe.confirmCardPayment(intent.clientSecret, {
        payment_method: {
          card: stripeCard,
          billing_details: { name: `${firstName} ${lastName}`, email }
        }
      });

      if (result.error) {
        console.error('Stripe confirm error', result.error);
        if (isStripeValidationError(result.error)) {
          showStripeCardValidation(result.error.message || t('review.cardDetailsIncomplete'));
          return;
        }
        showReviewFlowModal({
          title: t('review.paymentCouldNotBeCompletedTitle'),
          message: result.error.message || t('review.paymentCouldNotBeCompletedMessage'),
          buttonText: t('review.tryAgain'),
          variant: "error"
        });
        return;
      }

      const paymentIntent = result.paymentIntent;
      if (paymentIntent && paymentIntent.status === 'succeeded') {
        // Backend verifies intent status before confirming booking
        paymentSucceeded = await processBookingPayment(booking.id, firstName);
      } else if (paymentIntent && (paymentIntent.status === 'processing' || paymentIntent.status === 'requires_action' || paymentIntent.status === 'requires_confirmation' || paymentIntent.status === 'requires_capture')) {
        showReviewFlowModal({
          title: t('review.paymentProcessingTitle'),
          message: t('review.paymentProcessingMessage'),
          buttonText: t('common.ok')
        });
        return;
      } else {
        showReviewFlowModal({
          title: t('review.paymentNotCompletedTitle'),
          message: t('review.paymentNotCompletedMessage'),
          buttonText: t('review.tryAgain')
        });
        return;
      }
    } catch (err) {
      console.error('Stripe confirmation failed', err);
      showReviewFlowModal({
        title: t('review.paymentNetworkErrorTitle'),
        message: t('review.paymentNetworkErrorMessage'),
        buttonText: t('review.tryAgain'),
        variant: "error"
      });
      return;
    } finally {
      // Hide Stripe card to avoid leaving sensitive UI mounted after flow
      // but keep it mounted if you want subsequent retries without reload.
      // showStripeCardContainer(false);
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
 * frontend never sends them. clientSecret must be a Stripe secret; the server
 * rejects fake/dev providers for public checkout.
 *
 * Throws with a user-visible message on non-OK responses.
 */
async function createPaymentIntent(bookingId) {
  const token = sessionStorage.getItem('rentcarCheckoutSessionToken');
  const body = token ? { checkoutSessionToken: token } : {};
  const res = await fetch(`/api/bookings/${bookingId}/payments/intent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
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
 * The backend verifies the Stripe PaymentIntent status by id, so this legacy
 * value is ignored for public checkout.
 */
function getSelectedPaymentMethodId() {
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
    const token = sessionStorage.getItem('rentcarCheckoutSessionToken');
    const res = await fetch(`/api/bookings/${bookingId}/payments/process`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        paymentMethodId: getSelectedPaymentMethodId(),
        checkoutSessionToken: token || null
      })
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
    showReviewFlowModal({

      title: t('review.paymentCouldNotBeCompletedTitle'),
      message: data?.message || fallback,
      buttonText: t('review.tryAgain'),
      variant: "error"
    });
    return false;
  } catch (err) {
    console.error("Payment processing failed:", err);
    showReviewFlowModal({

      title: t('review.paymentCouldNotBeCompletedTitle'),
      message: t('review.bookingCreatedPaymentFailed'),
      buttonText: t('review.tryAgain'),
      variant: "error"
    });
    return false;
  }
}

// ── Success screen ────────────────────────────────────────────────────────────
function formatReviewDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return esc(String(value));
  return esc(date.toLocaleString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }));
}

function formatReviewMoney(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return "—";
  return esc(new Intl.NumberFormat(typeof getLanguage === "function" ? getLanguage() : undefined, {
    style: "currency",
    currency: "EUR"
  }).format(amount));
}

function bookingLocationValue(booking, key, paramName) {
  return esc(booking?.[key] || new URLSearchParams(window.location.search).get(paramName) || "—");
}

function bookingCarName(booking) {
  const car = booking?.car || reviewCar;
  const value = car
    ? `${car.brand || ""} ${car.model || ""}`.trim()
    : "";
  return esc(value || t('review.vehicle'));
}

function bookingCarCategory(booking) {
  const car = booking?.car || reviewCar || {};
  const parts = [car.segment, car.vehicleType]
    .filter(Boolean)
    .map(value => typeof tEnum === "function" ? tEnum(value === car.segment ? "segment" : "vehicleType", value) : value);
  return esc(parts.join(" ") || t('summary.orSimilar'));
}

function reviewRentalDays(booking) {
  return Number(booking?.rentalDays || reviewCar?.priceBreakdown?.rentalDays || 1);
}

function reviewDurationLabel(booking) {
  const days = reviewRentalDays(booking);
  const key = days === 1 ? 'price.rentalDay' : 'price.rentalDays';
  return `${days} ${t(key)}`;
}

function selectedAddonRows(booking) {
  const rentalDays = reviewRentalDays(booking);
  const rows = [];

  if (reviewMileageOption === "UNLIMITED") {
    const charge = Number(reviewCar?.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays;
    rows.push({ name: t('car.unlimitedKm'), totalPrice: charge });
  }

  if (Array.isArray(booking?.addons) && booking.addons.length > 0) {
    booking.addons.forEach(addon => {
      rows.push({
        name: addon.name || addon.code || t('review.addedFeatures'),
        totalPrice: Number(addon.totalPrice || addon.price || 0)
      });
    });
  } else {
    reviewAddonIds.forEach(addonId => {
      const addon = reviewAllAddons.find(item => item.id === addonId);
      if (!addon) return;
      const price = addon.pricingType === "DAILY"
        ? Number(addon.price) * rentalDays
        : Number(addon.price);
      rows.push({ name: localAddonName(addon), totalPrice: price });
    });
  }

  return rows;
}

function selectedInsuranceRow(booking) {
  const rentalDays = reviewRentalDays(booking);
  const name = booking?.insuranceNameSnapshot || reviewInsurancePackage?.name;
  const daily = Number(booking?.insuranceDailyPriceSnapshot ?? reviewInsurancePackage?.pricePerDay ?? 0);
  const total = Number(booking?.insuranceTotalSnapshot ?? (daily * rentalDays));
  const deposit = booking?.depositAmountSnapshot ?? reviewInsurancePackage?.depositAmount;
  if (!name) return null;
  return {
    name,
    totalPrice: total,
    depositAmount: Number(deposit || 0)
  };
}

function includedFeatureRows(booking) {
  const car = booking?.car || reviewCar || {};
  const included = car.includedFeatures || car.included || [];
  return Array.isArray(included) ? included.filter(Boolean) : [];
}

function formatBookingCreatedDate(booking) {
  return formatReviewDateTime(booking?.createdAt || booking?.createdDate || booking?.createdDateTime || new Date().toISOString());
}

function paymentStatusLabel(booking) {
  const status = booking?.paymentStatus || "PAID";
  return status === "PAID" ? t('review.paidVerified') : esc(String(status));
}

function priceDetailsRows(booking, addonRows) {
  const price = reviewCar?.priceBreakdown;
  if (!price) return "";
  const rows = [];
  if (Number(price.rentalCharge) > 0) {
    rows.push([t('price.rentalCharges'), formatReviewMoney(price.rentalCharge)]);
  }
  if (Number(price.oneWayFee) > 0) {
    rows.push([t('price.oneWayFee'), formatReviewMoney(price.oneWayFee)]);
  }
  if (Number(price.premiumLocationFee) > 0) {
    rows.push([t('price.premiumLocationFee'), formatReviewMoney(price.premiumLocationFee)]);
  }
  const insurance = selectedInsuranceRow(booking);
  if (insurance && Number(insurance.totalPrice) > 0) {
    rows.push([esc(insurance.name), formatReviewMoney(insurance.totalPrice)]);
  }
  addonRows.forEach(addon => {
    if (Number(addon.totalPrice) > 0) {
      rows.push([esc(addon.name), formatReviewMoney(addon.totalPrice)]);
    }
  });
  return rows.map(([label, value]) => `
    <div class="rc-booking-complete__price-row">
      <span>${label}</span>
      <strong>${value}</strong>
    </div>
  `).join("");
}

function showBookingSuccess(booking, firstName) {
  clearCheckoutSession();

  const formColumn = document.getElementById("reviewFormColumn");
  if (!formColumn) return;

  const params = new URLSearchParams(window.location.search);
  const bookingRefRaw = String(booking.bookingReference || "");
  const bookingRef = esc(bookingRefRaw || "—");
  const pickupDateTime = booking?.pickupDateTime || params.get("pickupDateTime");
  const dropoffDateTime = booking?.dropoffDateTime || params.get("dropoffDateTime");
  const total = booking?.totalPrice ?? reviewCar?.priceBreakdown?.totalPrice;
  const pickupLocation = bookingLocationValue(booking, "pickupLocation", "pickupLocation");
  const dropoffLocation = bookingLocationValue(booking, "dropoffLocation", "dropoffLocation");
  const addonRows = selectedAddonRows(booking);
  const insuranceRow = selectedInsuranceRow(booking);
  const includedRows = includedFeatureRows(booking);
  const priceRows = priceDetailsRows(booking, addonRows);
  const existingSummary = document.getElementById("bookingSummaryCard")?.closest(".col-lg-4");

  if (existingSummary) existingSummary.style.display = "none";
  formColumn.className = "col-12";

  formColumn.innerHTML = `
    <section id="rfSuccessPanel" class="rc-booking-complete-layout" aria-labelledby="bookingCompleteTitle">
      <div class="rc-booking-complete-card rc-booking-complete-card--main">
        <div class="rc-booking-complete__hero">
          <div class="rc-booking-complete__check" aria-hidden="true"><i class="icon-check"></i></div>
          <h2 id="bookingCompleteTitle">${t('booking.completed.title')}</h2>
          <p>${t('booking.completed.subtitle')}</p>
        </div>

        <div class="rc-booking-complete__info-row">
          <div class="rc-booking-complete__info-card rc-booking-complete__info-card--reference">
            <span>${t('review.bookingReferenceLabel')}</span>
            <strong>${bookingRef}</strong>
          </div>
          <div class="rc-booking-complete__info-card">
            <span>${t('booking.completed.bookingDate')}</span>
            <strong>${formatBookingCreatedDate(booking)}</strong>
          </div>
          <div class="rc-booking-complete__info-card">
            <span>${t('booking.completed.paymentStatus')}</span>
            <strong>${paymentStatusLabel(booking)}</strong>
          </div>
        </div>

        <div class="rc-booking-complete__trip-card">
          <div class="rc-booking-complete__vehicle">
            <strong>${bookingCarName(booking)}</strong>
            <span>${bookingCarCategory(booking)}</span>
          </div>
          <div class="rc-booking-complete__route">
            <div>
              <span>${t('review.pickup')}</span>
              <strong>${pickupLocation}</strong>
              <small>${formatReviewDateTime(pickupDateTime)}</small>
            </div>
            <div class="rc-booking-complete__route-arrow" aria-hidden="true">→</div>
            <div>
              <span>${t('review.return')}</span>
              <strong>${dropoffLocation}</strong>
              <small>${formatReviewDateTime(dropoffDateTime)}</small>
            </div>
          </div>
        </div>

        <div class="rc-booking-complete__next">
          <div>
            <h3>${t('booking.completed.whatsNext')}</h3>
            <p>${t('booking.completed.nextText')}</p>
          </div>
          <a href="manage-booking.html?ref=${encodeURIComponent(bookingRefRaw)}" class="rc-booking-complete__button rc-booking-complete__button--primary">
            ${t('review.manageBooking')}
            <i class="icon-arrow-right" aria-hidden="true"></i>
          </a>
        </div>

        <div class="rc-booking-complete__main-total">
          <span>${t('summary.total')}</span>
          <strong>${formatReviewMoney(total)}</strong>
        </div>

        <p class="rc-booking-complete__secured">
          <i class="icon-lock" aria-hidden="true"></i>
          ${t('booking.completed.securedNote')}
        </p>
      </div>

      <aside class="rc-booking-complete-card rc-booking-complete-card--summary" aria-label="${t('booking.completed.summaryTitle')}">
        <h3>${t('booking.completed.summaryTitle')}</h3>

        <div class="rc-booking-complete__summary-vehicle">
          <strong>${bookingCarName(booking)}</strong>
          <span>${bookingCarCategory(booking)}</span>
        </div>

        <div class="rc-booking-complete__summary-list">
          <div>
            <span>${t('review.pickup')}</span>
            <strong>${pickupLocation}</strong>
            <small>${formatReviewDateTime(pickupDateTime)}</small>
          </div>
          <div>
            <span>${t('review.return')}</span>
            <strong>${dropoffLocation}</strong>
            <small>${formatReviewDateTime(dropoffDateTime)}</small>
          </div>
          <div>
            <span>${t('transfer.duration')}</span>
            <strong>${reviewDurationLabel(booking)}</strong>
          </div>
        </div>

        ${includedRows.length > 0 ? `
          <div class="rc-booking-complete__summary-section">
            <h4>${t('summary.includedFeatures')}</h4>
            <ul>${includedRows.map(item => `<li>${esc(item)}</li>`).join("")}</ul>
          </div>
        ` : ""}

        <div class="rc-booking-complete__summary-section">
          <h4>${t('summary.protection')}</h4>
          ${insuranceRow
            ? `<ul><li><span>${esc(insuranceRow.name)}</span><strong>${formatReviewMoney(insuranceRow.totalPrice)}</strong></li></ul>
               <p>${t('protection.deposit')}: ${formatReviewMoney(insuranceRow.depositAmount)}</p>`
            : `<p>${t('summary.noProtection')}</p>`}
        </div>

        <div class="rc-booking-complete__summary-section">
          <h4>${t('summary.addedFeatures')}</h4>
          ${addonRows.length > 0
            ? `<ul>${addonRows.map(addon => `<li><span>${esc(addon.name)}</span><strong>${formatReviewMoney(addon.totalPrice)}</strong></li>`).join("")}</ul>`
            : `<p>${t('summary.noAddons')}</p>`}
        </div>

        <div class="rc-booking-complete__summary-total">
          <span>${t('summary.total')}</span>
          <strong>${formatReviewMoney(total)}</strong>
        </div>

        ${priceRows ? `
          <details class="rc-booking-complete__price-details">
            <summary>${t('review.priceDetails')}</summary>
            ${priceRows}
          </details>
        ` : ""}
      </aside>
    </section>
  `;

  window.scrollTo({ top: 0, behavior: "smooth" });
}

// ── Error helpers ─────────────────────────────────────────────────────────────
function showReviewFlowModal({
  icon,
  title,
  message,
  buttonText,
  onClose,
  variant = "warning"
}) {
  const modal = document.getElementById("reviewFlowModal");
  modal.classList.remove("is-warning", "is-error", "is-info");
  modal.classList.add(`is-${variant}`);
  if (!modal) {
    showBookingFormError(message);
    return;
  }

  document.getElementById("reviewFlowModalIcon").textContent = icon || "⚠️";
  document.getElementById("reviewFlowModalTitle").textContent = title || "";
  document.getElementById("reviewFlowModalMessage").textContent = message || "";

  const btn = document.getElementById("reviewFlowModalBtn");
  btn.textContent = buttonText || "OK";
  btn.onclick = () => {
    modal.style.display = "none";
    if (typeof onClose === "function") onClose();
  };

  modal.style.display = "flex";
}

function goBackToCarsWithSameSearch() {
  window.location.href = "cars.html" + window.location.search;
}

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

function currentReviewLanguage() {
  if (typeof getLanguage === "function") {
    return getLanguage();
  }
  return document.documentElement.lang || "en";
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
      availableAddons: reviewAllAddons,
      selectedInsurance: reviewInsurancePackage,
      pageType: 'review'
    });
  }
});
