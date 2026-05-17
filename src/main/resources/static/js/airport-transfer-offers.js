document.addEventListener("DOMContentLoaded", function () {
  var params = new URLSearchParams(window.location.search);
  var transferType  = params.get("transferType");
  var durationHours = params.get("durationHours");
  var includedKm    = params.get("includedKm");
  var pickupDateTime  = params.get("pickupDateTime");
  var pickupLocation  = params.get("pickupLocation");

  // ── State ────────────────────────────────────────────────────────────────
  var allTransferOffers  = [];
  var currentSort        = "PRICE_ASC";
  var currentPassengers  = 1;

  // ── DOM refs ──────────────────────────────────────────────────────────────
  var grid       = document.getElementById("transferOffersGrid");
  var loadingEl  = document.getElementById("transferOffersLoading");
  var countEl    = document.getElementById("transferOffersCountNum");
  var durationEl = document.getElementById("selectedTransferDuration");
  var sortBtn    = document.getElementById("sortToggleBtn");
  var sortArrow  = document.getElementById("sortArrow");
  var sortLabel  = document.getElementById("sortLabel");
  var passBtn    = document.getElementById("passengersBtn");
  var passLabel  = document.getElementById("passengersLabel");
  var passDrop   = document.getElementById("passengersDropdown");

  // ── Populate summary bar ──────────────────────────────────────────────────
  if (durationEl && transferType === "HOURLY" && durationHours && includedKm) {
    var h  = parseInt(durationHours, 10);
    var km = parseInt(includedKm, 10);
    durationEl.textContent = (h === 1 ? "1 hour" : h + " hours") + " (" + km + " km included)";
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  function t(key) {
    if (typeof window.i18nTranslations !== "undefined") {
      var lang = document.documentElement.lang || "en";
      var dict = window.i18nTranslations[lang] || window.i18nTranslations["en"] || {};
      return dict[key] || key;
    }
    return key;
  }

  function formatPrice(total) {
    if (total == null) return "—";
    var parts = parseFloat(total).toFixed(2).split(".");
    return "\u20AC " + parts[0] + "<small>." + parts[1] + "</small>";
  }

  function buildCard(offer) {
    var article = document.createElement("article");
    article.className = "transfer-offer-card";
    var icons = "<span>\uD83D\uDC65 " + offer.seats + "</span>" +
                "<span>\uD83D\uDCBC " + offer.bags + "</span>" +
                (offer.electric ? "<span>\u26A1 Electric</span>" : "");
    var imgHtml = offer.imageUrl
      ? '<img src="' + offer.imageUrl + '" alt="' + offer.name + '">'
      : "";
    article.innerHTML =
      "<h3>" + offer.name + "</h3>" +
      "<p>" + (offer.description || "") + "</p>" +
      '<div class="transfer-offer-icons">' + icons + "</div>" +
      imgHtml +
      '<div class="transfer-offer-bottom">' +
        '<div class="transfer-price">' + formatPrice(offer.totalPrice) + "</div>" +
        '<button type="button" data-i18n="transfer.next">Next</button>' +
      "</div>";
    return article;
  }

  // ── Core render (applies filter + sort then paints grid) ──────────────────
  function applyAndRender() {
    // Filter
    var filtered = allTransferOffers.filter(function (o) {
      return o.seats >= currentPassengers;
    });

    // Sort
    filtered = filtered.slice().sort(function (a, b) {
      var pa = parseFloat(a.totalPrice) || 0;
      var pb = parseFloat(b.totalPrice) || 0;
      return currentSort === "PRICE_ASC" ? pa - pb : pb - pa;
    });

    // Clear grid (keep no-op if still loading)
    grid.innerHTML = "";

    if (countEl) countEl.textContent = filtered.length;

    if (filtered.length === 0) {
      var empty = document.createElement("p");
      empty.style.cssText = "padding:2rem;color:#555;";
      empty.textContent = t("transfer.noOffersForPassengers");
      grid.appendChild(empty);
      return;
    }

    filtered.forEach(function (offer) {
      grid.appendChild(buildCard(offer));
    });

    if (typeof applyTranslations === "function") {
      applyTranslations(document.documentElement);
    }
  }

  // ── Initial render after data load ───────────────────────────────────────
  function onOffersLoaded(offers) {
    if (loadingEl) loadingEl.remove();
    allTransferOffers = offers || [];
    applyAndRender();
  }

  // ── Sort toggle ───────────────────────────────────────────────────────────
  if (sortBtn) {
    sortBtn.addEventListener("click", function () {
      currentSort = currentSort === "PRICE_ASC" ? "PRICE_DESC" : "PRICE_ASC";
      if (sortArrow) sortArrow.textContent = currentSort === "PRICE_ASC" ? "↑" : "↓";
      if (sortLabel) {
        sortLabel.removeAttribute("data-i18n");
        sortLabel.textContent = currentSort === "PRICE_ASC"
          ? t("transfer.priceLowHigh")
          : t("transfer.priceHighLow");
      }
      applyAndRender();
    });
  }

  // ── Passenger dropdown ────────────────────────────────────────────────────
  if (passBtn && passDrop) {
    passBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      var isOpen = passDrop.style.display !== "none";
      passDrop.style.display = isOpen ? "none" : "block";
    });

    passDrop.querySelectorAll("li").forEach(function (li) {
      li.addEventListener("click", function () {
        currentPassengers = parseInt(li.getAttribute("data-value"), 10);

        // Update active highlight
        passDrop.querySelectorAll("li").forEach(function (el) {
          el.classList.remove("is-active");
        });
        li.classList.add("is-active");

        // Update button label
        if (passLabel) {
          passLabel.removeAttribute("data-i18n");
          passLabel.textContent = currentPassengers + "+ Passengers";
        }

        passDrop.style.display = "none";
        applyAndRender();
      });
    });

    // Close dropdown on outside click
    document.addEventListener("click", function () {
      passDrop.style.display = "none";
    });
  }

  // ── Mock fallback ─────────────────────────────────────────────────────────
  function loadMockFallback() {
    onOffersLoaded([
      { name: "Ride",    description: "Reliable ride at the best price.",      seats: 3, bags: 2, electric: false, imageUrl: "img/cars/bmw_x1_sdrive.png", totalPrice: 95  },
      { name: "Green",   description: "Ride in hybrid or electric vehicles.",  seats: 3, bags: 2, electric: true,  imageUrl: "img/cars/audi_q2.png",        totalPrice: 120 },
      { name: "Ride XL", description: "Ride in spacious vans for group travels.", seats: 6, bags: 4, electric: false, imageUrl: "img/cars/mercedes_vito.png", totalPrice: 140 }
    ]);
  }

  // ── Fetch from API ────────────────────────────────────────────────────────
  if (transferType === "HOURLY" && pickupDateTime && durationHours) {
    var query = new URLSearchParams({ pickupDateTime: pickupDateTime, durationHours: durationHours });
    if (pickupLocation) query.set("pickupLocation", pickupLocation);

    fetch("/api/transfer/offers?" + query.toString())
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(function (data) { onOffersLoaded(data); })
      .catch(function (err) {
        console.warn("Could not load transfer offers from API, using fallback.", err);
        loadMockFallback();
      });
  } else {
    loadMockFallback();
  }
});
