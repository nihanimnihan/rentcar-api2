// booking-summary.js — shared booking summary card renderer
//
// Both addons.html and review.html use the same summary card markup and the
// same element IDs so this single module can drive both pages.
//
// Expected element IDs in the host page:
//   summaryCarImage, summaryCarName, summaryCarSubtitle
//   summaryPickupLocation, summaryPickupDate
//   summaryDropoffLocation, summaryDropoffDate
//   summaryAddedFeatures, summaryTotal

// ── Total calculation ────────────────────────────────────────────────────────

/**
 * Returns total breakdown for the booking.
 *
 * @param {object} car             CarDetailResponse
 * @param {string} mileageOption   "INCLUDED" | "UNLIMITED" | "EXTRA"
 * @param {Iterable<number>} selectedAddonIds
 * @param {Array<object>} availableAddons  AddonResponse[]
 * @returns {{ total, rentalDays, base, unlimitedKmCharge, addonsTotal }}
 */
function calcBookingTotal(car, mileageOption, selectedAddonIds, availableAddons) {
  const rentalDays = car?.priceBreakdown?.rentalDays || 1;
  const base = Number(car?.totalPrice || 0);

  const unlimitedKmCharge = mileageOption === "UNLIMITED"
    ? Number(car?.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays
    : 0;

  const addonsTotal = Array.from(selectedAddonIds).reduce((sum, id) => {
    const addon = availableAddons.find(a => a.id === id);
    if (!addon) return sum;
    const addonPrice = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    return sum + addonPrice;
  }, 0);

  return {
    total: base + unlimitedKmCharge + addonsTotal,
    rentalDays,
    base,
    unlimitedKmCharge,
    addonsTotal
  };
}

// ── Summary card renderer ────────────────────────────────────────────────────

/**
 * Populates the booking summary card DOM.
 *
 * @param {object} opts
 * @param {object}          opts.car              CarDetailResponse
 * @param {URLSearchParams} opts.params            Current page URL search params
 * @param {string}          opts.mileageOption     "INCLUDED" | "UNLIMITED" | "EXTRA"
 * @param {Iterable<number>} opts.selectedAddonIds  Set or array of selected addon IDs
 * @param {Array<object>}   opts.availableAddons   AddonResponse[]
 */
function renderBookingSummary({ car, params, mileageOption, selectedAddonIds, availableAddons }) {
  if (!car) return;

  const carName = `${car.brand || ""} ${car.model || ""}`.trim();

  _setText("summaryCarName", carName || "Car");
  _setText("summaryCarSubtitle",
    `${car.segment || ""} ${car.vehicleType || ""}`.trim() || "or similar");

  const imgEl = document.getElementById("summaryCarImage");
  if (imgEl) {
    imgEl.src = car.imageUrl || "img/lists/car/1/1.png";
    imgEl.alt = carName || "car";
    imgEl.onerror = () => { imgEl.src = "img/lists/car/1/1.png"; };
  }

  _setText("summaryPickupLocation",  params.get("pickupLocation")  || "-");
  _setText("summaryDropoffLocation", params.get("dropoffLocation") || "-");
  _setText("summaryPickupDate",  _formatDT(params.get("pickupDateTime")));
  _setText("summaryDropoffDate", _formatDT(params.get("dropoffDateTime")));

  const { total, rentalDays, unlimitedKmCharge } =
    calcBookingTotal(car, mileageOption, selectedAddonIds, availableAddons);

  _renderAddedFeatures(mileageOption, unlimitedKmCharge, selectedAddonIds, availableAddons, rentalDays);

  _setText("summaryTotal", _formatMoney(total));
}

// ── Private helpers ──────────────────────────────────────────────────────────

function _renderAddedFeatures(mileageOption, unlimitedKmCharge, selectedAddonIds, availableAddons, rentalDays) {
  const container = document.getElementById("summaryAddedFeatures");
  if (!container) return;

  const lines = [];

  if (mileageOption === "UNLIMITED") {
    lines.push(`
      <div class="d-flex justify-between mb-8">
        <span>✓ ${t('car.unlimitedKm')}</span>
        <strong>${_formatMoney(unlimitedKmCharge)}</strong>
      </div>
    `);
  }

  Array.from(selectedAddonIds).forEach(addonId => {
    const addon = availableAddons.find(a => a.id === addonId);
    if (!addon) return;
    const price = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    lines.push(`
      <div class="d-flex justify-between mb-8">
        <span>✓ ${_esc(localAddonName(addon))}</span>
        <strong>${_formatMoney(price)}</strong>
      </div>
    `);
  });

  container.innerHTML = lines.length > 0
    ? lines.join("")
    : t('summary.noAddons');
}

function _setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function _formatMoney(amount) {
  return `€${Number(amount).toFixed(2)}`;
}

function _formatDT(isoString) {
  if (!isoString) return "-";
  try {
    const d = new Date(isoString);
    return d.toLocaleString(getLanguage() === 'es' ? 'es-ES' : 'en-GB', {
      weekday: "short", year: "numeric", month: "short",
      day: "numeric", hour: "2-digit", minute: "2-digit"
    });
  } catch {
    return isoString;
  }
}

function _esc(str) {
  if (typeof escapeHtml === "function") return escapeHtml(str);
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
