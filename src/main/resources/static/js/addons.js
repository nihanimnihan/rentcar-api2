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
    console.error("carId is missing from URL");
    return;
  }

  try {
    const [carRes, addonsRes] = await Promise.all([
      fetch(`/api/cars/${carId}${window.location.search}`),
      fetch("/api/addons/active")
    ]);

    selectedCar = await carRes.json();
    availableAddons = await addonsRes.json();
  } catch (err) {
    console.error("Failed to load page data:", err);
    return;
  }

  renderAddonCards(availableAddons);
  renderSummary();
  renderAddonsPriceModal();
}

function renderAddonCards(addons) {
  const container = document.getElementById("addonsList");
  if (!container) return;

  if (addons.length === 0) {
    container.innerHTML = "<p class='text-15 text-light-1'>No add-ons available.</p>";
    return;
  }

  container.innerHTML = addons.map(addon => {
    const priceLabel = addon.pricingType === "DAILY"
      ? `€${Number(addon.price).toFixed(2)} / day`
      : `€${Number(addon.price).toFixed(2)} / one-time`;

    const iconHtml = addon.imageUrl
      ? `<img src="${addon.imageUrl}" alt="${escapeHtml(addon.name)}"
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
  }).join("");
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
  const carName = `${selectedCar.brand || ""} ${selectedCar.model || ""}`.trim();
  const baseTotal = Number(selectedCar.totalPrice || 0);
  const rentalDays = selectedCar.priceBreakdown?.rentalDays || 1;
  const addonsTotal = calculateAddonsTotal(rentalDays);
  const unlimitedKmCharge = mileageOption === "UNLIMITED"
    ? Number(selectedCar.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays
    : 0;
  const finalTotal = baseTotal + addonsTotal + unlimitedKmCharge;

  setText("summaryCarName", carName || "Car");
  setText("summaryCarSubtitle", `${selectedCar.segment || ""} ${selectedCar.vehicleType || ""}`.trim() || "or similar");
  setImage("summaryCarImage", selectedCar.imageUrl || "img/cars/1.png", carName);

  setText("summaryPickupLocation", params.get("pickupLocation") || "-");
  setText("summaryDropoffLocation", params.get("dropoffLocation") || "-");
  setText("summaryPickupDate", formatDateTime(params.get("pickupDateTime")));
  setText("summaryDropoffDate", formatDateTime(params.get("dropoffDateTime")));

  renderAddedFeatures(rentalDays);

  setText("addonsHeaderTotal", formatMoney(finalTotal));
  setText("summaryTotal", formatMoney(finalTotal));

  renderAddonsPriceModal();
}

function calculateAddonsTotal(rentalDays) {
  return Array.from(selectedAddons).reduce((sum, addonId) => {
    const addon = availableAddons.find(a => a.id === addonId);
    if (!addon) return sum;
    return sum + (addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price));
  }, 0);
}

function renderAddedFeatures(rentalDays) {
  const container = document.getElementById("summaryAddedFeatures");
  if (!container) return;

  const lines = [];

  if (mileageOption === "UNLIMITED") {
    const charge = Number(selectedCar.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays;
    lines.push(`
      <div class="d-flex justify-between mb-8">
        <span>✓ Unlimited kilometers</span>
        <strong>${formatMoney(charge)}</strong>
      </div>
    `);
  }

  Array.from(selectedAddons).forEach(addonId => {
    const addon = availableAddons.find(a => a.id === addonId);
    if (!addon) return;
    const price = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    lines.push(`
      <div class="d-flex justify-between mb-8">
        <span>✓ ${escapeHtml(addon.name)}</span>
        <strong>${formatMoney(price)}</strong>
      </div>
    `);
  });

  container.innerHTML = lines.length > 0
    ? lines.join("")
    : "No add-ons selected yet.";
}

// ── Booking form modal ──────────────────────────────────────────────────────

function goToBooking() {
  const existing = document.getElementById("bookingFormModal");
  if (existing) existing.remove();

  document.body.insertAdjacentHTML("beforeend", buildBookingFormHtml());
}

function buildBookingFormHtml() {
  return `
    <div class="rentcar-price-modal is-active" id="bookingFormModal">
      <div class="rentcar-price-modal-backdrop" onclick="closeBookingModal()"></div>
      <div class="rentcar-price-modal-card" style="max-width:480px">
        <button class="rentcar-price-modal-close" onclick="closeBookingModal()">
          <i class="icon-close"></i>
        </button>
        <h2 class="rentcar-price-modal-title">COMPLETE BOOKING</h2>
        <div id="bookingFormError" class="text-red-1 mb-15" style="display:none"></div>
        <div class="mb-20">
          <label class="text-14 fw-600">Full Name</label>
          <input id="bfName" type="text"
            style="display:block;width:100%;border:1px solid #ddd;border-radius:8px;padding:10px 15px;margin-top:6px;font-size:15px"
            placeholder="John Doe">
        </div>
        <div class="mb-20">
          <label class="text-14 fw-600">Email</label>
          <input id="bfEmail" type="email"
            style="display:block;width:100%;border:1px solid #ddd;border-radius:8px;padding:10px 15px;margin-top:6px;font-size:15px"
            placeholder="john@example.com">
        </div>
        <div class="mb-20">
          <label class="text-14 fw-600">Phone</label>
          <input id="bfPhone" type="tel"
            style="display:block;width:100%;border:1px solid #ddd;border-radius:8px;padding:10px 15px;margin-top:6px;font-size:15px"
            placeholder="+1234567890">
        </div>
        <button id="bookingSubmitBtn" onclick="submitBooking()"
          class="button h-60 px-50 bg-yellow-1 text-dark-1 rounded-8 fw-700"
          style="width:100%">
          Confirm Booking
        </button>
      </div>
    </div>
  `;
}

function closeBookingModal() {
  document.getElementById("bookingFormModal")?.remove();
}

async function submitBooking() {
  const params = new URLSearchParams(window.location.search);

  const customerName = document.getElementById("bfName")?.value?.trim();
  const customerEmail = document.getElementById("bfEmail")?.value?.trim();
  const customerPhone = document.getElementById("bfPhone")?.value?.trim();

  if (!customerName || !customerEmail || !customerPhone) {
    showBookingError("Please fill in all required fields.");
    return;
  }

  const submitBtn = document.getElementById("bookingSubmitBtn");
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = "Booking...";
  }

  const body = {
    carId: Number(params.get("carId")),
    customerName,
    customerEmail,
    customerPhone,
    pickupDateTime: params.get("pickupDateTime"),
    dropoffDateTime: params.get("dropoffDateTime"),
    pickupLocation: params.get("pickupLocation"),
    dropoffLocation: params.get("dropoffLocation"),
    addonIds: Array.from(selectedAddons),
    mileageOption
  };

  try {
    const response = await fetch("/api/bookings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      showBookingError(err.message || "Booking failed. Please try again.");
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = "Confirm Booking";
      }
      return;
    }

    const booking = await response.json();
    showBookingSuccess(booking);
  } catch (err) {
    console.error("Booking error:", err);
    showBookingError("An unexpected error occurred. Please try again.");
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = "Confirm Booking";
    }
  }
}

function showBookingError(message) {
  const el = document.getElementById("bookingFormError");
  if (el) {
    el.textContent = message;
    el.style.display = "block";
  }
}

function showBookingSuccess(booking) {
  const card = document.querySelector("#bookingFormModal .rentcar-price-modal-card");
  if (!card) return;

  card.innerHTML = `
    <div style="text-align:center;padding:30px 0">
      <div style="font-size:56px;margin-bottom:12px">🎉</div>
      <h3 class="text-22 fw-700 mb-10">Booking confirmed!</h3>
      <p class="text-16 mb-5">Booking ID: <strong>#${booking.id}</strong></p>
      <p class="text-15 text-light-1 mb-25">
        Confirmation sent to <strong>${escapeHtml(booking.customer?.email || "")}</strong>
      </p>
      <a href="index.html"
        class="button h-50 px-40 bg-yellow-1 text-dark-1 rounded-8 fw-700">
        Back to Home
      </a>
    </div>
  `;
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
    el.src = src;
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

function escapeHtml(str) {
  const div = document.createElement("div");
  div.appendChild(document.createTextNode(str || ""));
  return div.innerHTML;
}

window.toggleAddon = toggleAddon;
window.closeBookingModal = closeBookingModal;
window.submitBooking = submitBooking;