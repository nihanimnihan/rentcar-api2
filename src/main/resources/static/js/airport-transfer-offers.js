document.addEventListener("DOMContentLoaded", function () {
  var params = new URLSearchParams(window.location.search);
  var transferType = params.get("transferType");
  var durationHours = params.get("durationHours");
  var includedKm = params.get("includedKm");
  var pickupDateTime = params.get("pickupDateTime");
  var pickupLocation = params.get("pickupLocation");

  // ── Populate summary bar ──────────────────────────────────────────────────
  var durationEl = document.getElementById("selectedTransferDuration");
  if (durationEl && transferType === "HOURLY" && durationHours && includedKm) {
    var h = parseInt(durationHours, 10);
    var km = parseInt(includedKm, 10);
    durationEl.textContent = (h === 1 ? "1 hour" : h + " hours") + " (" + km + " km included)";
  }

  var grid = document.getElementById("transferOffersGrid");
  var loadingEl = document.getElementById("transferOffersLoading");
  var countEl = document.getElementById("transferOffersCountNum");

  // ── Render helpers ────────────────────────────────────────────────────────
  function formatPrice(total) {
    if (total == null) return "—";
    var parts = parseFloat(total).toFixed(2).split(".");
    return "\u20AC " + parts[0] + "<small>." + parts[1] + "</small>";
  }

  function buildCard(offer) {
    var article = document.createElement("article");
    article.className = "transfer-offer-card";

    var icons = '<span>\uD83D\uDC65 ' + offer.seats + '</span>' +
                '<span>\uD83D\uDCBC ' + offer.bags + '</span>' +
                (offer.electric ? '<span>\u26A1 Electric</span>' : '');

    var imgHtml = offer.imageUrl
      ? '<img src="' + offer.imageUrl + '" alt="' + offer.name + '">'
      : '';

    article.innerHTML =
      '<h3>' + offer.name + '</h3>' +
      '<p>' + (offer.description || '') + '</p>' +
      '<div class="transfer-offer-icons">' + icons + '</div>' +
      imgHtml +
      '<div class="transfer-offer-bottom">' +
        '<div class="transfer-price">' + formatPrice(offer.totalPrice) + '</div>' +
        '<button type="button" data-i18n="transfer.next">Next</button>' +
      '</div>';

    return article;
  }

  function renderOffers(offers) {
    if (loadingEl) loadingEl.remove();

    if (!offers || offers.length === 0) {
      if (countEl) countEl.textContent = "0";
      var empty = document.createElement("p");
      empty.style.padding = "2rem";
      empty.textContent = "No transfer options available for the selected time.";
      grid.appendChild(empty);
      return;
    }

    if (countEl) countEl.textContent = offers.length;
    offers.forEach(function (offer) {
      grid.appendChild(buildCard(offer));
    });

    // Re-run i18n on newly inserted nodes
    if (typeof applyTranslations === "function") {
      applyTranslations(document.documentElement);
    }
  }

  // ── Mock fallback ─────────────────────────────────────────────────────────
  function renderMockFallback() {
    renderOffers([
      { name: "Ride", description: "Reliable ride at the best price.", seats: 3, bags: 2, electric: false, imageUrl: "img/cars/bmw_x1_sdrive.png", totalPrice: 95 },
      { name: "Green", description: "Ride in hybrid or electric vehicles.", seats: 3, bags: 2, electric: true, imageUrl: "img/cars/audi_q2.png", totalPrice: 120 },
      { name: "Ride XL", description: "Ride in spacious vans for group travels.", seats: 6, bags: 4, electric: false, imageUrl: "img/cars/mercedes_vito.png", totalPrice: 140 }
    ]);
  }

  // ── Fetch from API ────────────────────────────────────────────────────────
  if (transferType === "HOURLY" && pickupDateTime && durationHours) {
    var query = new URLSearchParams({
      pickupDateTime: pickupDateTime,
      durationHours: durationHours
    });
    if (pickupLocation) query.set("pickupLocation", pickupLocation);

    fetch("/api/transfer/offers?" + query.toString())
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(function (data) {
        renderOffers(data);
      })
      .catch(function (err) {
        console.warn("Could not load transfer offers from API, using fallback.", err);
        renderMockFallback();
      });
  } else {
    // No valid params — show mock
    renderMockFallback();
  }
});
