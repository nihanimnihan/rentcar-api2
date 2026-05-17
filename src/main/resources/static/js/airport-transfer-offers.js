document.addEventListener("DOMContentLoaded", function () {

  // ── URL params ────────────────────────────────────────────────────────────
  var urlParams     = new URLSearchParams(window.location.search);
  var transferType  = urlParams.get("transferType");
  var durationHours = urlParams.get("durationHours");
  var includedKm    = urlParams.get("includedKm");
  var pickupLocation = urlParams.get("pickupLocation");

  // ── State ─────────────────────────────────────────────────────────────────
  var allTransferOffers = [];
  var currentSort       = "PRICE_ASC";
  var currentPassengers = 1;
  var selectedPickupDate = "";   // YYYY-MM-DD
  var selectedPickupTime = "";   // HH:mm
  var tempDate = "";             // staging while dt panel is open
  var tempTime = "";

  // Duration state
  var selectedDurationHours = parseInt(durationHours, 10) || 1;
  var selectedIncludedKm    = parseInt(includedKm, 10)    || selectedDurationHours * 30;
  var draftDurationHours    = selectedDurationHours;
  var draftIncludedKm       = selectedIncludedKm;
  var availableDurations    = [];

  // Initialise date/time from URL param
  (function () {
    var raw = urlParams.get("pickupDateTime");
    if (raw) {
      raw = raw.trim();
      // Prefer strict ISO format YYYY-MM-DDTHH:mm
      var isoMatch = raw.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/);
      if (isoMatch) {
        selectedPickupDate = isoMatch[1];
        selectedPickupTime = isoMatch[2];
      }
      // Fallback: YYYY-MM-DD HH:mm (space separator)
      if (!selectedPickupDate) {
        var spaceMatch = raw.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})/);
        if (spaceMatch) {
          selectedPickupDate = spaceMatch[1];
          selectedPickupTime = spaceMatch[2];
        }
      }
    }
    if (!selectedPickupDate) {
      selectedPickupDate = new Date().toISOString().slice(0, 10);
    }
    if (!selectedPickupTime) selectedPickupTime = "10:00";
  }());

  // ── DOM refs ──────────────────────────────────────────────────────────────
  var grid        = document.getElementById("transferOffersGrid");
  var countEl     = document.getElementById("transferOffersCountNum");
  var durationEl  = document.getElementById("selectedTransferDuration");
  var metaEl      = document.getElementById("durationMetaEl");
  var sortBtn     = document.getElementById("sortToggleBtn");
  var sortArrow   = document.getElementById("sortArrow");
  var sortLabel   = document.getElementById("sortLabel");
  var passBtn     = document.getElementById("passengersBtn");
  var passLabel   = document.getElementById("passengersLabel");
  var passDrop    = document.getElementById("passengersDropdown");
  var dtTrigger   = document.getElementById("pickupDateTimeTrigger");
  var dtDisplay   = document.getElementById("pickupDateTimeDisplay");
  var dtPanel     = document.getElementById("dtEditPanel");
  var dtDateGrid  = document.getElementById("dtDateGrid");
  var dtTimeList  = document.getElementById("dtTimeList");
  var dtCloseBtn  = document.getElementById("dtEditClose");
  var dtCancelBtn = document.getElementById("dtCancelBtn");
  var dtApplyBtn  = document.getElementById("dtApplyBtn");
  var durTrigger  = document.getElementById("durationTrigger");
  var durPanel    = document.getElementById("durEditPanel");
  var durList     = document.getElementById("durOptionsList");
  var durCloseBtn = document.getElementById("durEditClose");
  var durCancelBtn= document.getElementById("durCancelBtn");
  var durApplyBtn = document.getElementById("durApplyBtn");

  // ── Helpers ───────────────────────────────────────────────────────────────
  function t(key) {
    if (typeof window.i18nTranslations !== "undefined") {
      var lang = document.documentElement.lang || "en";
      var dict = window.i18nTranslations[lang] || window.i18nTranslations["en"] || {};
      return dict[key] || key;
    }
    return key;
  }

  function padZ(n) { return n < 10 ? "0" + n : "" + n; }

  function formatDateDisplay(iso) {
    var p = iso.split("-");
    return p.length === 3 ? p[2] + "/" + p[1] + "/" + p[0] : iso;
  }

  function updateDateTimeDisplay(date, time) {
    if (!dtDisplay) return;
    dtDisplay.innerHTML = "<span>" + formatDateDisplay(date) + "</span><span>" + time + "</span>";
  }

  function formatPrice(total) {
    if (total == null) return "\u2014";
    var p = parseFloat(total).toFixed(2).split(".");
    return "\u20AC " + p[0] + "<small>." + p[1] + "</small>";
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function getFallbackTransferImage(code) {
    var images = {
      RIDE:           "img/cars/bmw_x1_sdrive.png",
      GREEN:          "img/cars/audi_q2.png",
      FIRST:          "img/cars/mb_clase_v_auto.png",
      BUSINESS:       "img/cars/mb_clase_v_auto.png",
      RIDE_XL:        "img/cars/mercedes_vito.png",
      BUSINESS_GREEN: "img/cars/audi_q2.png",
      BUSINESS_XL:    "img/cars/mercedes_vito.png"
    };
    return images[code] || "img/cars/bmw_x1_sdrive.png";
  }

  // ── Card builder ──────────────────────────────────────────────────────────
  function buildCard(offer) {
    var article = document.createElement("article");
    article.className = "transfer-offer-card";
    var icons = "<span>\uD83D\uDC65 " + offer.seats + "</span>" +
                "<span>\uD83D\uDCBC " + offer.bags + "</span>" +
                (offer.electric ? "<span>\u26A1 Electric</span>" : "");
    var imageUrl = offer.imageUrl || getFallbackTransferImage(offer.code);
    article.innerHTML =
      "<h3>" + escapeHtml(offer.name) + "</h3>" +
      "<p>" + escapeHtml(offer.description || "") + "</p>" +
      '<div class="transfer-offer-icons">' + icons + "</div>" +
      '<div class="transfer-offer-image">' +
        '<img src="' + escapeHtml(imageUrl) + '" alt="' + escapeHtml(offer.name) + '">' +
      "</div>" +
      '<div class="transfer-offer-bottom">' +
        '<div class="transfer-price">' + formatPrice(offer.totalPrice) + "</div>" +
        '<button type="button" data-i18n="transfer.next">Next</button>' +
      "</div>";
    return article;
  }

  // ── Core render (filter + sort → paint) ───────────────────────────────────
  function applyAndRender() {
    var filtered = allTransferOffers.filter(function (o) {
      return o.seats >= currentPassengers;
    });
    filtered = filtered.slice().sort(function (a, b) {
      var pa = parseFloat(a.totalPrice) || 0;
      var pb = parseFloat(b.totalPrice) || 0;
      return currentSort === "PRICE_ASC" ? pa - pb : pb - pa;
    });

    grid.innerHTML = "";
    if (countEl) countEl.textContent = filtered.length;

    if (filtered.length === 0) {
      var empty = document.createElement("p");
      empty.style.cssText = "padding:2rem;color:#555;";
      empty.textContent = t("transfer.noOffersForPassengers");
      grid.appendChild(empty);
      return;
    }
    filtered.forEach(function (offer) { grid.appendChild(buildCard(offer)); });
    if (typeof applyTranslations === "function") applyTranslations(document.documentElement);
  }

  // ── Offer loading ──────────────────────────────────────────────────────────
  function showGridLoading() {
    grid.innerHTML = '<div style="text-align:center;padding:2rem;">' + t("common.loading") + '</div>';
  }

  function loadMockFallback() {
    allTransferOffers = [
      { code: "RIDE",    name: "Ride",    description: "Reliable ride at the best price.",         seats: 3, bags: 2, electric: false, imageUrl: null, totalPrice: 95  },
      { code: "GREEN",   name: "Green",   description: "Ride in hybrid or electric vehicles.",     seats: 3, bags: 2, electric: true,  imageUrl: null, totalPrice: 120 },
      { code: "RIDE_XL", name: "Ride XL", description: "Ride in spacious vans for group travels.", seats: 6, bags: 4, electric: false, imageUrl: null, totalPrice: 140 }
    ];
    applyAndRender();
  }

  function loadOffers(date, time) {
    showGridLoading();
    var pickupDt = date + "T" + time;
    if (transferType === "HOURLY" && pickupDt && selectedDurationHours) {
      var q = new URLSearchParams({ pickupDateTime: pickupDt, durationHours: selectedDurationHours });
      if (pickupLocation) q.set("pickupLocation", pickupLocation);
      fetch("/api/transfer/offers?" + q.toString())
        .then(function (res) {
          if (!res.ok) throw new Error("HTTP " + res.status);
          return res.json();
        })
        .then(function (data) {
          allTransferOffers = data || [];
          applyAndRender();
        })
        .catch(function (err) {
          console.warn("Transfer offers API unavailable, using fallback.", err);
          loadMockFallback();
        });
    } else {
      loadMockFallback();
    }
  }

  // ── Sort toggle ───────────────────────────────────────────────────────────
  if (sortBtn) {
    sortBtn.addEventListener("click", function () {
      currentSort = currentSort === "PRICE_ASC" ? "PRICE_DESC" : "PRICE_ASC";
      if (sortArrow) sortArrow.textContent = currentSort === "PRICE_ASC" ? "↑" : "↓";
      if (sortLabel) {
        sortLabel.removeAttribute("data-i18n");
        sortLabel.textContent = currentSort === "PRICE_ASC" ? t("transfer.priceLowHigh") : t("transfer.priceHighLow");
      }
      applyAndRender();
    });
  }

  // ── Passenger dropdown ────────────────────────────────────────────────────
  if (passBtn && passDrop) {
    passBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      passDrop.style.display = passDrop.style.display !== "none" ? "none" : "block";
    });
    passDrop.querySelectorAll("li").forEach(function (li) {
      li.addEventListener("click", function () {
        currentPassengers = parseInt(li.getAttribute("data-value"), 10);
        passDrop.querySelectorAll("li").forEach(function (el) { el.classList.remove("is-active"); });
        li.classList.add("is-active");
        if (passLabel) {
          passLabel.removeAttribute("data-i18n");
          passLabel.textContent = currentPassengers + "+ Passengers";
        }
        passDrop.style.display = "none";
        applyAndRender();
      });
    });
    document.addEventListener("click", function () { passDrop.style.display = "none"; });
  }

  // ── Date / time edit panel ────────────────────────────────────────────────
  var DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  var MON_NAMES = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

  function buildDatePills() {
    if (!dtDateGrid) return;
    dtDateGrid.innerHTML = "";
    var base = new Date();
    base.setHours(0, 0, 0, 0);
    for (var i = 0; i < 14; i++) {
      var d = new Date(base.getTime() + i * 86400000);
      var iso = d.getFullYear() + "-" + padZ(d.getMonth() + 1) + "-" + padZ(d.getDate());
      var pill = document.createElement("div");
      pill.className = "dt-date-pill" + (iso === tempDate ? " is-active" : "");
      pill.dataset.date = iso;
      pill.innerHTML =
        '<span class="dt-day">' + DAY_NAMES[d.getDay()] + "</span>" +
        '<span class="dt-num">' + d.getDate() + "</span>" +
        '<span class="dt-mon">' + MON_NAMES[d.getMonth()] + "</span>";
      pill.addEventListener("click", function () {
        tempDate = this.dataset.date;
        dtDateGrid.querySelectorAll(".dt-date-pill").forEach(function (el) { el.classList.remove("is-active"); });
        this.classList.add("is-active");
      });
      dtDateGrid.appendChild(pill);
    }
  }

  function buildTimeSlots() {
    if (!dtTimeList || dtTimeList.children.length > 0) return;
    for (var h = 0; h < 24; h++) {
      for (var m = 0; m < 60; m += 15) {
        var slot = padZ(h) + ":" + padZ(m);
        var opt = document.createElement("div");
        opt.className = "dt-time-option";
        opt.textContent = slot;
        opt.dataset.time = slot;
        opt.addEventListener("click", function () {
          tempTime = this.dataset.time;
          dtTimeList.querySelectorAll(".dt-time-option").forEach(function (el) { el.classList.remove("is-active"); });
          this.classList.add("is-active");
        });
        dtTimeList.appendChild(opt);
      }
    }
  }

  function markActiveTimeSlot() {
    if (!dtTimeList) return;
    dtTimeList.querySelectorAll(".dt-time-option").forEach(function (opt) {
      opt.classList.toggle("is-active", opt.dataset.time === tempTime);
    });
  }

  function scrollToActiveTime() {
    if (!dtTimeList) return;
    var active = dtTimeList.querySelector(".dt-time-option.is-active");
    if (active) active.scrollIntoView({ block: "center" });
  }

  function openPanel() {
    if (!dtPanel) return;
    tempDate = selectedPickupDate;
    tempTime = selectedPickupTime;
    buildDatePills();
    buildTimeSlots();
    markActiveTimeSlot();
    dtPanel.style.display = "block";
    setTimeout(scrollToActiveTime, 50);
  }

  function closePanel() {
    if (dtPanel) dtPanel.style.display = "none";
  }

  if (dtCloseBtn) dtCloseBtn.addEventListener("click", closePanel);
  if (dtCancelBtn) dtCancelBtn.addEventListener("click", closePanel);

  if (dtApplyBtn) {
    dtApplyBtn.addEventListener("click", function () {
      selectedPickupDate = tempDate;
      selectedPickupTime = tempTime;
      updateDateTimeDisplay(selectedPickupDate, selectedPickupTime);
      // Update URL without page reload
      var np = new URLSearchParams(window.location.search);
      np.set("pickupDateTime", selectedPickupDate + "T" + selectedPickupTime);
      window.history.replaceState(null, "", "?" + np.toString());
      closePanel();
      loadOffers(selectedPickupDate, selectedPickupTime);
    });
  }

  // ── Duration edit panel ───────────────────────────────────────────────────
  function durationLabel(h) { return h === 1 ? "1 hour" : h + " hours"; }

  function updateDurationSummary() {
    if (durationEl) durationEl.textContent = durationLabel(selectedDurationHours) + " (" + selectedIncludedKm + " km included)";
    if (metaEl)     metaEl.textContent = "est. " + selectedDurationHours + " hrs";
  }

  function buildDurationOptions() {
    if (!durList) return;
    durList.innerHTML = "";
    availableDurations.forEach(function (d) {
      var opt = document.createElement("div");
      opt.className = "dur-option" + (d.hours === draftDurationHours ? " is-active" : "");
      opt.dataset.hours = d.hours;
      opt.dataset.km    = d.includedKm;
      opt.innerHTML =
        '<div class="dur-opt-name">' + durationLabel(d.hours) + "</div>" +
        '<div class="dur-opt-km">'   + d.includedKm + " km included</div>";
      opt.addEventListener("click", function () {
        draftDurationHours = d.hours;
        draftIncludedKm    = d.includedKm;
        durList.querySelectorAll(".dur-option").forEach(function (el) { el.classList.remove("is-active"); });
        opt.classList.add("is-active");
      });
      durList.appendChild(opt);
    });
  }

  function buildFallbackDurations() {
    return Array.from({ length: 12 }, function (_, i) {
      return { hours: i + 1, includedKm: (i + 1) * 30 };
    });
  }

  function openDurPanel() {
    if (!durPanel) return;
    closeDurPanel();   // reset only; also close dt panel
    closePanel();
    draftDurationHours = selectedDurationHours;
    draftIncludedKm    = selectedIncludedKm;

    if (availableDurations.length === 0) {
      fetch("/api/transfer/durations")
        .then(function (res) { if (!res.ok) throw new Error("HTTP " + res.status); return res.json(); })
        .then(function (data) {
          availableDurations = (data || []).map(function (d) {
            return { hours: d.hours, includedKm: d.includedKm };
          });
          if (!availableDurations.length) throw new Error("empty");
        })
        .catch(function () { availableDurations = buildFallbackDurations(); })
        .then(function () {
          buildDurationOptions();
          durPanel.style.display = "block";
        });
    } else {
      buildDurationOptions();
      durPanel.style.display = "block";
    }
  }

  function closeDurPanel() {
    if (durPanel) durPanel.style.display = "none";
  }

  if (durTrigger)  durTrigger.addEventListener("click", openDurPanel);
  if (durCloseBtn) durCloseBtn.addEventListener("click", closeDurPanel);
  if (durCancelBtn) durCancelBtn.addEventListener("click", closeDurPanel);

  if (durApplyBtn) {
    durApplyBtn.addEventListener("click", function () {
      selectedDurationHours = draftDurationHours;
      selectedIncludedKm    = draftIncludedKm;
      updateDurationSummary();
      var np = new URLSearchParams(window.location.search);
      np.set("durationHours", selectedDurationHours);
      np.set("includedKm",    selectedIncludedKm);
      window.history.replaceState(null, "", "?" + np.toString());
      closeDurPanel();
      loadOffers(selectedPickupDate, selectedPickupTime);
    });
  }

  // Make dt panel opening close the dur panel too
  if (dtTrigger) dtTrigger.addEventListener("click", function () { closeDurPanel(); openPanel(); });

  // ── Initialise display and load ───────────────────────────────────────────
  updateDurationSummary();
  updateDateTimeDisplay(selectedPickupDate, selectedPickupTime);
  loadOffers(selectedPickupDate, selectedPickupTime);
});
