let availableAddons = [];
let selectedAddons = new Set(); // Set<Number> — stores numeric addon IDs
let selectedCar = null;
// "INCLUDED" | "UNLIMITED" — read from URL so a browser refresh preserves the choice
const mileageOption = new URLSearchParams(window.location.search).get("mileageOption") || "INCLUDED";

document.addEventListener("DOMContentLoaded", () => {
  loadAddonPage();
  document.getElementById("continueButton")?.addEventListener("click", goToBooking);
});

async function loadAddonPage() {
  const params = new URLSearchParams(window.location.search);
  const carId = params.get("carId");

  if (!carId) {
    showPageError("No car selected. Please go back and choose a vehicle.");
    return;
  }

  try {
    const [carRes, addonsRes] = await Promise.all([
      fetch(`/api/cars/${carId}${window.location.search}`),
      fetch("/api/addons/active")
    ]);

    if (!carRes.ok) {
      const status = carRes.status;
      if (status === 404) {
        showPageError("This car is no longer available. Please go back and choose another vehicle.");
      } else {
        showPageError("Failed to load car details. Please try again.");
      }
      console.error("Car detail fetch failed:", status);
      return;
    }

    if (!addonsRes.ok) {
      console.error("Add-ons fetch failed:", addonsRes.status);
      // Non-fatal: continue without add-ons rather than blocking the whole page
      availableAddons = [];
    } else {
      availableAddons = await addonsRes.json();
    }

    selectedCar = await carRes.json();
  } catch (err) {
    console.error("Failed to load page data:", err);
    showPageError("Failed to load page. Please check your connection and try again.");
    return;
  }

  renderAddonCards(availableAddons);
  renderSummary();
  renderAddonsPriceModal();
}

function showPageError(message) {
  const container = document.getElementById("addonsList");
  if (container) {
    container.innerHTML = `
      <div style="padding:40px 0;text-align:center">
        <p class="text-16 text-danger mb-20">${escapeHtml(message)}</p>
        <a href="cars.html${window.location.search}" class="button h-50 px-30 bg-dark-1 text-white rounded-8 fw-600">
          Back to search
        </a>
      </div>
    `;
  }
  // Disable continue button — there is nothing to book
  const continueBtn = document.getElementById("continueButton");
  if (continueBtn) {
    continueBtn.disabled = true;
    continueBtn.style.opacity = "0.4";
  }
}

function renderAddonCards(addons) {
  const container = document.getElementById("addonsList");
  if (!container) return;

  if (addons.length === 0) {
    container.innerHTML = "<p class='text-15 text-light-1'>No add-ons available.</p>";
    return;
  }

  const recommended = addons.filter(a => a.recommended);
  const more = addons.filter(a => !a.recommended);

  const parts = [];

  if (recommended.length > 0) {
    parts.push(`<h2 class="rentcar-addon-section-title">Recommended add-ons for your trip</h2>`);
    parts.push(...recommended.map(addon => renderAddonCard(addon, true)));
  }

  if (more.length > 0) {
    parts.push(`<h2 class="rentcar-addon-section-title rentcar-addon-section-title--more">More add-ons for you</h2>`);
    parts.push(...more.map(addon => renderAddonCard(addon, false)));
  }

  container.innerHTML = parts.join("");
}

function renderAddonCard(addon, isRecommended) {
  const priceLabel = addon.pricingType === "DAILY"
    ? `€${Number(addon.price).toFixed(2)} / day`
    : `€${Number(addon.price).toFixed(2)} / one-time`;

  if (isRecommended && addon.imageUrl) {
    return `
      <div class="rentcar-addon-card" data-addon-id="${addon.id}">
        <div class="rentcar-addon-image">
          <img src="${safeSrc(addon.imageUrl, "")}" alt="${escapeHtml(addon.name)}">
        </div>
        <div class="rentcar-addon-content">
          <div class="d-flex justify-between items-start">
            <div>
              <h3 class="text-20 fw-700">${escapeHtml(addon.name)}</h3>
              <div class="text-15 text-light-1">${escapeHtml(addon.description || "")}</div>
            </div>
            <i class="icon-info text-18"></i>
          </div>
          <div class="d-flex justify-between items-center mt-20">
            <div class="text-16 fw-700">${priceLabel}</div>
            <button type="button" class="rentcar-addon-button" onclick="toggleAddon(${addon.id})">
              Add <span>+</span>
            </button>
          </div>
        </div>
      </div>
    `;
  }

  const iconHtml = addon.imageUrl
    ? `<img src="${safeSrc(addon.imageUrl, "")}" alt="${escapeHtml(addon.name)}"
           style="width:100%;height:100%;object-fit:cover;border-radius:8px;">`
    : `<i class="icon-user text-28"></i>`;

  return `
    <div class="rentcar-addon-card" data-addon-id="${addon.id}">
      <div class="rentcar-addon-icon">
        ${iconHtml}
      </div>
      <div class="rentcar-addon-content">
        <div class="d-flex justify-between items-start">
          <div>
            <h3 class="text-20 fw-700">${escapeHtml(addon.name)}</h3>
            <div class="text-15 text-light-1">${escapeHtml(addon.description || "")}</div>
          </div>
          <i class="icon-info text-18"></i>
        </div>
        <div class="d-flex justify-between items-center mt-20">
          <div class="text-16 fw-700">${priceLabel}</div>
          <button type="button" class="rentcar-addon-button" onclick="toggleAddon(${addon.id})">
            Add <span>+</span>
          </button>
        </div>
      </div>
    </div>
  `;
}

function toggleAddon(addonId) {
  const id = Number(addonId);

  if (selectedAddons.has(id)) {
    selectedAddons.delete(id);
  } else {
    selectedAddons.add(id);
  }

  document
    .querySelector(`[data-addon-id="${id}"]`)
    ?.classList.toggle("is-selected", selectedAddons.has(id));

  const button = document.querySelector(`[data-addon-id="${id}"] .rentcar-addon-button`);
  if (button) {
    button.innerHTML = selectedAddons.has(id)
      ? `Added <span>✓</span>`
      : `Add <span>+</span>`;
  }

  renderSummary();
}

function renderSummary() {
  if (!selectedCar) return;

  const params = new URLSearchParams(window.location.search);

  renderBookingSummary({
    car: selectedCar,
    params,
    mileageOption,
    selectedAddonIds: selectedAddons,
    availableAddons
  });

  const { total } = calcBookingTotal(selectedCar, mileageOption, selectedAddons, availableAddons);
  setText("addonsHeaderTotal", formatMoney(total));

  renderAddonsPriceModal();
}

// ── Navigate to review page ─────────────────────────────────────────────────

function goToBooking() {
  const params = new URLSearchParams(window.location.search);

  // Remove stale addonIds from previous navigation, then append current selection
  params.delete("addonIds");
  Array.from(selectedAddons).forEach(id => params.append("addonIds", id));

  window.location.href = `review.html?${params.toString()}`;
}

// ── Price details modal ─────────────────────────────────────────────────────

document.addEventListener("click", function (event) {
  if (event.target.closest("#addonsPriceDetailsButton")) {
    event.preventDefault();
    renderAddonsPriceModal();
    openPriceDetailsModal("addonsPriceModal");
  }
});

function renderAddonsPriceModal() {
  const existing = document.getElementById("addonsPriceModal");
  if (existing) existing.remove();

  if (!selectedCar?.priceBreakdown) return;

  const rentalDays = selectedCar.priceBreakdown.rentalDays || 1;

  // Build addon lines — unlimited km treated as a line item so it shows in price modal
  const addonLines = [];

  if (mileageOption === "UNLIMITED") {
    const charge = Number(selectedCar.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays;
    addonLines.push({ name: "Unlimited kilometers", totalPrice: charge.toFixed(2) });
  }

  Array.from(selectedAddons).map(addonId => {
    const addon = availableAddons.find(a => a.id === addonId);
    if (!addon) return null;
    const totalPrice = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    return { name: addon.name, totalPrice: totalPrice.toFixed(2) };
  }).filter(Boolean).forEach(line => addonLines.push(line));

  document.body.insertAdjacentHTML(
    "beforeend",
    buildPriceDetailsModalHtml("addonsPriceModal", selectedCar.priceBreakdown, addonLines)
  );
}

// ── Utilities ───────────────────────────────────────────────────────────────

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function setImage(id, src, alt) {
  const el = document.getElementById(id);
  if (el) {
    el.src = safeSrc(src, "img/lists/car/1/1.png");
    el.alt = alt || "car image";
  }
}

function formatMoney(value) {
  return `€${Number(value).toFixed(2)}`;
}

function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

// escapeHtml and safeSrc are provided by escape-html.js (loaded before this file)

window.toggleAddon = toggleAddon;
window.closeBookingModal = closeBookingModal;
window.submitBooking = submitBooking;