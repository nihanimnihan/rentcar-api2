/**
 * TRANSFER BOOKING — API CONTRACT VERIFICATION CHECKLIST
 *
 * No JS test framework is configured in this project.
 * When one is added, cover the points below with automated tests.
 * Until then, verify manually after any change to this file or to
 * POST /api/transfer/bookings.
 *
 * ── Request payload ────────────────────────────────────────────────────────
 * [1] Payload shape must match CreateTransferBookingRequest:
 *     {
 *       customerName    : "First Last"           // firstName + " " + lastName
 *       customerEmail   : "email@example.com"
 *       customerPhone   : "+34600000077"         // countryCode + phone
 *       pickupDateTime  : "YYYY-MM-DDTHH:mm"    // from URL param (no seconds)
 *       durationHours   : 2                      // Integer from URL param
 *       categoryId      : 1                      // Long from offer.categoryId
 *       passengers      : 2                      // Integer from URL param
 *       notes           : "..."                  // atbNotesToDriver.value (nullable)
 *     }
 *     Verify: Network tab → POST /api/transfer/bookings → request body.
 *
 * [2] categoryId must come from offer.categoryId (sessionStorage "selectedTransferOffer")
 *     or from URL param "categoryId" as a fallback.
 *     Verify: log offer.categoryId before fetch; must be a numeric ID, not undefined.
 *
 * [3] pickupDateTime format must be YYYY-MM-DDTHH:mm (no seconds, no Z).
 *     Backend rejects other formats with 400.
 *
 * ── Response fields (TransferBookingResponse) ─────────────────────────────
 * [4] Success panel must render:
 *     id               — booking reference
 *     status           — expected "PENDING"
 *     categoryName     — vehicle category name
 *     assignedCarBrand + assignedCarModel — vehicle name
 *     pickupDateTime   — confirmation datetime
 *     durationHours    — confirmed duration
 *     totalPrice       — final price
 *     Verify: submit valid booking, inspect Network response, confirm card renders.
 *
 * ── Error paths ────────────────────────────────────────────────────────────
 * [5] HTTP 400 → atbErrorBanner shows response.message (validation detail).
 * [6] HTTP 409 → atbErrorBanner shows "no car available" friendly message.
 * [7] Network failure → atbErrorBanner shows generic fallback (no JS crash).
 *     Verify each: open DevTools → Network → Block request URL pattern.
 *
 * ── Existing flows ─────────────────────────────────────────────────────────
 * [8] Normal rental search (/api/cars/search) and review.html booking flow
 *     must be completely unaffected by any change here.
 */

document.addEventListener("DOMContentLoaded", function () {

  var urlParams = new URLSearchParams(window.location.search);

  // ── Resolve offer data (sessionStorage first, fallback to URL params) ──────
  var offer = null;
  try {
    var raw = sessionStorage.getItem("selectedTransferOffer");
    if (raw) offer = JSON.parse(raw);
  } catch (_) {}

  if (!offer) {
    offer = {
      code:        urlParams.get("offerCode")                     || "",
      name:        urlParams.get("offerName")                     || "",
      description: urlParams.get("offerDesc")                     || "",
      totalPrice:  parseFloat(urlParams.get("totalPrice"))        || 0,
      seats:       parseInt(urlParams.get("seats"),   10)         || 0,
      bags:        parseInt(urlParams.get("bags"),    10)         || 0,
      electric:    urlParams.get("electric") === "true",
      imageUrl:    null,
      categoryId:  parseInt(urlParams.get("categoryId"), 10)      || null,
    };
  }

  // Normalise categoryId to a number (API returns it as a number; sessionStorage preserves it)
  if (offer.categoryId) {
    offer.categoryId = parseInt(offer.categoryId, 10) || null;
  }

  var transferType   = urlParams.get("transferType")  || "ONE_WAY";
  var pickupLocation = urlParams.get("pickupLocation") || "";
  var pickupDateTime = urlParams.get("pickupDateTime") || "";
  var durationHours  = urlParams.get("durationHours")  || "1";
  var includedKm     = urlParams.get("includedKm")     || "";
  var passengers     = parseInt(urlParams.get("passengers"), 10) || 1;

  // ── Fallback vehicle image ─────────────────────────────────────────────────
  var IMAGES = {
    RIDE:           "img/cars/bmw_x1_sdrive.png",
    GREEN:          "img/cars/bmw_x1_sdrive.png",
    FIRST:          "img/cars/mercedes_cla.png",
    BUSINESS:       "img/cars/mercedes_cla.png",
    RIDE_XL:        "img/cars/mercedes_vito.png",
    BUSINESS_GREEN: "img/cars/audi_q2.png",
    BUSINESS_XL:    "img/cars/mercedes_vito.png",
  };
  var vehicleImg = offer.imageUrl || IMAGES[offer.code] || "img/cars/bmw_x1_sdrive.png";

  // ── Render summary sidebar ─────────────────────────────────────────────────
  var summaryEl = document.getElementById("atbSummary");
  if (summaryEl) {
    var dtLabel = pickupDateTime
      ? (function () {
          var m = pickupDateTime.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/);
          if (!m) return pickupDateTime;
          var d  = new Date(m[1] + "T00:00:00");
          var locale = (typeof getLanguage === "function" && getLanguage() === "es") ? "es-ES" : "en-GB";
          return d.toLocaleDateString(locale, { weekday: "short", day: "numeric", month: "short" })
               + " · " + m[2];
        }())
      : "";

    var durLine = durationHours
      ? '<div class="atb-summary-row"><span>Duration</span><span>' + durationHours + 'h'
          + (includedKm ? ' / ' + includedKm + ' km' : '') + '</span></div>'
      : '';

    var electricBadge = offer.electric
      ? '<span class="atb-badge atb-badge--green">⚡ Electric</span>' : "";

    var priceFormatted = typeof offer.totalPrice === "number"
      ? "€ " + offer.totalPrice.toFixed(2)
      : ("€ " + offer.totalPrice);

    summaryEl.innerHTML =
      '<img class="atb-summary-img" src="' + vehicleImg + '" alt="' + offer.name + '">' +
      '<div class="atb-summary-name">' + offer.name + electricBadge + '</div>' +
      '<div class="atb-summary-desc">' + (offer.description || "") + '</div>' +
      '<div class="atb-summary-icons">' +
        '<span>👥 ' + offer.seats + '</span>' +
        '<span>💼 ' + offer.bags + '</span>' +
      '</div>' +
      '<hr class="atb-divider">' +
      '<div class="atb-summary-row"><span>Pickup</span><span>' + pickupLocation + '</span></div>' +
      (dtLabel ? '<div class="atb-summary-row"><span>Date / Time</span><span>' + dtLabel + '</span></div>' : '') +
      durLine +
      '<hr class="atb-divider">' +
      '<div class="atb-summary-total"><span data-i18n="booking.totalPrice">Total price</span>' +
        '<strong class="text-26 fw-800">' + priceFormatted + '</strong></div>';
  }

  // ── Accordion helper ───────────────────────────────────────────────────────
  document.querySelectorAll(".atb-accordion-toggle").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var body = btn.nextElementSibling;
      var isOpen = btn.classList.contains("is-open");
      btn.classList.toggle("is-open", !isOpen);
      body.style.display = isOpen ? "none" : "block";
    });
  });

  // ── Seat counter helpers ───────────────────────────────────────────────────
  document.querySelectorAll(".atb-counter").forEach(function (wrap) {
    var display = wrap.querySelector(".atb-counter-val");
    var hidden  = wrap.querySelector("input[type=hidden]");
    var val = 0;
    wrap.querySelector(".atb-counter-dec").addEventListener("click", function () {
      if (val > 0) { val--; display.textContent = val; hidden.value = val; }
    });
    wrap.querySelector(".atb-counter-inc").addEventListener("click", function () {
      val++; display.textContent = val; hidden.value = val;
    });
  });

  // ── Inline validation ──────────────────────────────────────────────────────
  function setError(fieldId, msg) {
    var field = document.getElementById(fieldId);
    if (!field) return;
    var wrapper = field.closest(".atb-field");
    if (!wrapper) { wrapper = field.parentElement; }
    wrapper.classList.toggle("has-error", !!msg);
    var errEl = wrapper.querySelector(".atb-field-error-msg");
    if (errEl) errEl.textContent = msg || "";
  }

  function validateForm() {
    var ok = true;
    var t = (typeof window.t === "function") ? window.t : function (k) { return k; };

    var required = ["atbFirstName", "atbLastName", "atbEmail", "atbPhone"];
    required.forEach(function (id) {
      var el = document.getElementById(id);
      if (!el || !el.value.trim()) {
        setError(id, t("booking.required"));
        ok = false;
      } else {
        setError(id, "");
      }
    });

    var emailEl = document.getElementById("atbEmail");
    if (emailEl && emailEl.value.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailEl.value.trim())) {
      setError("atbEmail", t("booking.invalidEmail"));
      ok = false;
    }

    return ok;
  }

  // ── UI state helpers ──────────────────────────────────────────────────────
  var bookBtn      = document.getElementById("atbBookNow");
  var errorBanner  = document.getElementById("atbErrorBanner");
  var formSection  = document.getElementById("atbFormSection");
  var successPanel = document.getElementById("atbSuccessPanel");

  function showError(msg) {
    if (!errorBanner) return;
    errorBanner.textContent = msg;
    errorBanner.style.display = "block";
    errorBanner.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function hideError() {
    if (errorBanner) errorBanner.style.display = "none";
  }

  function setLoading(loading) {
    if (!bookBtn) return;
    bookBtn.disabled = loading;
    var span = bookBtn.querySelector("span");
    if (span) {
      span.removeAttribute("data-i18n");
      span.textContent = loading ? "Booking…" : "Book Now";
    }
  }

  function formatConfirmDt(isoStr) {
    if (!isoStr) return "";
    var m = isoStr.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/);
    if (!m) return isoStr;
    var d = new Date(m[1] + "T00:00:00");
    var locale = (typeof getLanguage === "function" && getLanguage() === "es") ? "es-ES" : "en-GB";
    return d.toLocaleDateString(locale, { weekday: "short", day: "numeric", month: "long", year: "numeric" })
         + " · " + m[2];
  }

  function showSuccess(booking) {
    if (formSection)  formSection.style.display = "none";
    if (!successPanel) return;
    var vehicle = [booking.assignedCarBrand, booking.assignedCarModel].filter(Boolean).join(" ");
    successPanel.innerHTML =
      '<div class="atb-card" style="text-align:center;padding:32px 24px;">' +
        '<div style="font-size:48px;margin-bottom:12px;">✅</div>' +
        '<h2 style="font-size:22px;font-weight:700;margin-bottom:8px;">Booking Confirmed!</h2>' +
        '<p style="color:#555;margin-bottom:20px;">Booking reference: <strong>#' + booking.id + '</strong></p>' +
        '<div style="text-align:left;border-top:1px solid #eee;padding-top:16px;">' +
          '<div class="atb-summary-row"><span>Status</span><span style="font-weight:600;color:#2d7a4f;">' + (booking.status || "PENDING") + '</span></div>' +
          '<div class="atb-summary-row"><span>Category</span><span>' + (booking.categoryName || "") + '</span></div>' +
          (vehicle ? '<div class="atb-summary-row"><span>Vehicle</span><span>' + vehicle + '</span></div>' : '') +
          '<div class="atb-summary-row"><span>Pickup</span><span>' + formatConfirmDt(booking.pickupDateTime) + '</span></div>' +
          '<div class="atb-summary-row"><span>Duration</span><span>' + (booking.durationHours || "") + 'h</span></div>' +
          '<div class="atb-summary-row" style="font-size:16px;font-weight:700;margin-top:8px;"><span>Total</span><span>€\u00A0' + parseFloat(booking.totalPrice || 0).toFixed(2) + '</span></div>' +
        '</div>' +
        '<p style="font-size:12px;color:#888;margin-top:16px;">💳 Payment will be processed separately. A confirmation email has been sent.</p>' +
      '</div>';
    successPanel.style.display = "block";
    successPanel.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  // ── Book Now ───────────────────────────────────────────────────────────────
  if (bookBtn) {
    bookBtn.addEventListener("click", function () {
      hideError();
      if (!validateForm()) return;

      var firstName   = document.getElementById("atbFirstName").value.trim();
      var lastName    = document.getElementById("atbLastName").value.trim();
      var email       = document.getElementById("atbEmail").value.trim();
      var countryCode = document.getElementById("atbCountryCode").value.trim();
      var phone       = document.getElementById("atbPhone").value.trim();
      var notes       = (document.getElementById("atbNotesToDriver") || {}).value || "";

      var payload = {
        customerName:   firstName + " " + lastName,
        customerEmail:  email,
        customerPhone:  countryCode + phone,
        pickupDateTime: pickupDateTime,
        durationHours:  parseInt(durationHours, 10) || 1,
        categoryId:     offer.categoryId,
        passengerCount: passengers,
        notes:          notes.trim() || null,
      };

      setLoading(true);
      fetch("/api/transfer/bookings", {
        method:  "POST",
        headers: { "Content-Type": "application/json" },
        body:    JSON.stringify(payload),
      })
        .then(function (res) {
          return res.json().then(function (body) {
            return { ok: res.ok, status: res.status, body: body };
          });
        })
        .then(function (result) {
          setLoading(false);
          if (result.ok) {
            showSuccess(result.body);
          } else if (result.status === 409) {
            showError("No chauffeur car is available for this category and time slot. Please try a different time or duration.");
          } else {
            showError((result.body && result.body.message) || "Booking could not be completed. Please check your details and try again.");
          }
        })
        .catch(function (err) {
          setLoading(false);
          console.warn("Transfer booking API unavailable.", err);
          showError("Unable to reach the booking service. Please check your connection and try again.");
        });
    });
  }
});
