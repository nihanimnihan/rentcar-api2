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
      code:        urlParams.get("offerCode")  || "",
      name:        urlParams.get("offerName")  || "",
      description: urlParams.get("offerDesc")  || "",
      totalPrice:  parseFloat(urlParams.get("totalPrice")) || 0,
      seats:       parseInt(urlParams.get("seats"), 10)   || 0,
      bags:        parseInt(urlParams.get("bags"),  10)   || 0,
      electric:    urlParams.get("electric") === "true",
      imageUrl:    null,
    };
  }

  var transferType   = urlParams.get("transferType")  || "ONE_WAY";
  var pickupLocation = urlParams.get("pickupLocation") || "";
  var pickupDateTime = urlParams.get("pickupDateTime") || "";
  var durationHours  = urlParams.get("durationHours")  || "";
  var includedKm     = urlParams.get("includedKm")     || "";

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

    var durLine = "";
    if (transferType === "HOURLY" && durationHours) {
      durLine = '<div class="atb-summary-row"><span>Duration</span><span>' + durationHours + 'h'
              + (includedKm ? ' / ' + includedKm + ' km' : '') + '</span></div>';
    }

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

  // ── Book Now ───────────────────────────────────────────────────────────────
  var bookBtn = document.getElementById("atbBookNow");
  if (bookBtn) {
    bookBtn.addEventListener("click", function () {
      if (!validateForm()) return;

      var payload = {
        transferType:  transferType,
        offerCode:     offer.code,
        pickupLocation: pickupLocation,
        pickupDateTime: pickupDateTime,
        durationHours:  durationHours || null,
        includedKm:     includedKm    || null,
        passenger: {
          firstName:   document.getElementById("atbFirstName").value.trim(),
          lastName:    document.getElementById("atbLastName").value.trim(),
          email:       document.getElementById("atbEmail").value.trim(),
          countryCode: document.getElementById("atbCountryCode").value.trim(),
          phoneNumber: document.getElementById("atbPhone").value.trim(),
        },
        additionalRequests: {
          notesToDriver:   (document.getElementById("atbNotesToDriver") || {}).value || "",
          nameBoard:       (document.getElementById("atbNameBoard") || {}).value || "",
          infantSeatCount: parseInt((document.getElementById("atbInfantCount") || {}).value, 10) || 0,
          childSeatCount:  parseInt((document.getElementById("atbChildCount")  || {}).value, 10) || 0,
          boosterSeatCount:parseInt((document.getElementById("atbBoosterCount")|| {}).value, 10) || 0,
        },
        billingDetails: {
          street:  (document.getElementById("atbStreet")  || {}).value || "",
          country: (document.getElementById("atbCountry") || {}).value || "",
          city:    (document.getElementById("atbCity")    || {}).value || "",
          zip:     (document.getElementById("atbZip")     || {}).value || "",
          company: (document.getElementById("atbCompany") || {}).value || "",
        },
        totalPrice: offer.totalPrice,
      };

      console.log("Transfer booking payload:", payload);

      var t = (typeof window.t === "function") ? window.t : function (k) { return k; };
      alert(t("booking.successMsg"));
    });
  }
});
