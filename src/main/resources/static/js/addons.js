const ADDONS = {
  additionalDriver: {
    name: "Additional driver",
    price: 19,
    type: "daily"
  },
  gps: {
    name: "GPS navigation",
    price: 12,
    type: "daily"
  },
  refueling: {
    name: "Refueling service",
    price: 25,
    type: "oneTime"
  },
  childSeat: {
    name: "Child seat",
    price: 15,
    type: "daily"
  }
};

let selectedAddons = new Set();
let selectedCar = null;

document.addEventListener("DOMContentLoaded", () => {
  loadAddonPage();
});

async function loadAddonPage() {
  const params = new URLSearchParams(window.location.search);
  const carId = params.get("carId");

  if (!carId) {
    console.error("carId is missing");
    return;
  }

  const response = await fetch(`/api/cars/${carId}${window.location.search}`);
  selectedCar = await response.json();

  renderSummary();
  renderAddonsPriceModal();
  function renderSummary() {
    if (!selectedCar) {
      return;
    }

    const params = new URLSearchParams(window.location.search);

    const carName = `${selectedCar.brand || ""} ${selectedCar.model || ""}`.trim();
    const baseTotal = Number(selectedCar.totalPrice || 0);
    const rentalDays = selectedCar.priceBreakdown?.rentalDays || 1;
    const addonsTotal = calculateAddonsTotal(rentalDays);
    const finalTotal = baseTotal + addonsTotal;

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
}

function toggleAddon(addonId) {
  if (selectedAddons.has(addonId)) {
    selectedAddons.delete(addonId);
  } else {
    selectedAddons.add(addonId);
  }

  document
    .querySelector(`[data-addon-id="${addonId}"]`)
    ?.classList.toggle("is-selected", selectedAddons.has(addonId));

  const button = document.querySelector(`[data-addon-id="${addonId}"] .rentcar-addon-button`);
  if (button) {
    button.innerHTML = selectedAddons.has(addonId)
      ? `Added <span>✓</span>`
      : `${addonId === "childSeat" ? "Choose seat" : "Add"} <span>+</span>`;
  }

  renderSummary();
}

function renderSummary() {
  if (!selectedCar) {
    return;
  }

  const params = new URLSearchParams(window.location.search);

  const carName = `${selectedCar.brand || ""} ${selectedCar.model || ""}`.trim();
  const baseTotal = Number(selectedCar.totalPrice || 0);
  const rentalDays = selectedCar.priceBreakdown?.rentalDays || 1;
  const addonsTotal = calculateAddonsTotal(rentalDays);
  const finalTotal = baseTotal + addonsTotal;

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
}

function calculateAddonsTotal(rentalDays) {
  return Array.from(selectedAddons)
    .map(addonId => ADDONS[addonId])
    .reduce((sum, addon) => {
      if (addon.type === "daily") {
        return sum + addon.price * rentalDays;
      }

      return sum + addon.price;
    }, 0);
}

function renderAddedFeatures(rentalDays) {
  const container = document.getElementById("summaryAddedFeatures");

  if (!container) {
    return;
  }

  if (selectedAddons.size === 0) {
    container.innerHTML = "No add-ons selected yet.";
    return;
  }

  container.innerHTML = Array.from(selectedAddons)
    .map(addonId => {
      const addon = ADDONS[addonId];
      const price = addon.type === "daily"
        ? addon.price * rentalDays
        : addon.price;

      return `
        <div class="d-flex justify-between mb-8">
          <span>✓ ${addon.name}</span>
          <strong>${formatMoney(price)}</strong>
        </div>
      `;
    })
    .join("");
}

function setText(id, value) {
  const element = document.getElementById(id);

  if (element) {
    element.textContent = value;
  }
}

function setImage(id, src, alt) {
  const element = document.getElementById(id);

  if (element) {
    element.src = src;
    element.alt = alt || "car image";
  }
}

function formatMoney(value) {
  return `€${Number(value).toFixed(2)}`;
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

window.toggleAddon = toggleAddon;

document.addEventListener("click", function (event) {
  if (event.target.closest("#addonsPriceDetailsButton")) {
    event.preventDefault();

    renderAddonsPriceModal();
    openPriceDetailsModal("addonsPriceModal");
  }
});

function openAddonsPriceDetails() {
  renderAddonsPriceDetails();
  document.getElementById("addonsPriceModal")?.classList.add("is-active");
}

function closeAddonsPriceDetails() {
  document.getElementById("addonsPriceModal")?.classList.remove("is-active");
}

window.closeAddonsPriceDetails = closeAddonsPriceDetails;

function renderAddonsPriceDetails() {
  const container = document.getElementById("addonsPriceDetailsContent");

  if (!container || !selectedCar) {
    return;
  }

  const price = selectedCar.priceBreakdown;
  const rentalDays = price?.rentalDays || 1;
  const baseTotal = Number(selectedCar.totalPrice || 0);

  const addonRows = Array.from(selectedAddons)
    .map(addonId => {
      const addon = ADDONS[addonId];
      const total = addon.type === "daily"
        ? addon.price * rentalDays
        : addon.price;

      return `
        <div class="rentcar-price-row-line">
          <span>${addon.name}</span>
          <strong>${formatMoney(total)}</strong>
        </div>
      `;
    })
    .join("");

  const addonsTotal = calculateAddonsTotal(rentalDays);
  const finalTotal = baseTotal + addonsTotal;

  container.innerHTML = `
    <div class="rentcar-price-section">
      <div class="rentcar-price-section-title">Car rental</div>

      <div class="rentcar-price-row-line">
        <span>${rentalDays} rental day${rentalDays > 1 ? "s" : ""}</span>
        <strong>${formatMoney(baseTotal)}</strong>
      </div>
    </div>

    ${addonRows ? `
      <div class="rentcar-price-divider"></div>

      <div class="rentcar-price-section">
        <div class="rentcar-price-section-title">Add-ons</div>
        ${addonRows}
      </div>
    ` : ""}

    <div class="rentcar-price-divider"></div>

    <div class="rentcar-price-total">
      <span>Total</span>
      <strong>${formatMoney(finalTotal)}</strong>
    </div>
  `;
}

window.openAddonsPriceDetails = openAddonsPriceDetails;
window.closeAddonsPriceDetails = closeAddonsPriceDetails;

function renderAddonsPriceModal() {
  const existing = document.getElementById("addonsPriceModal");
  if (existing) {
    existing.remove();
  }

  if (!selectedCar?.priceBreakdown) {
    return;
  }

  const rentalDays = selectedCar.priceBreakdown.rentalDays || 1;

  const addonLines = Array.from(selectedAddons).map(addonId => {
    const addon = ADDONS[addonId];
    const totalPrice = addon.type === "daily"
      ? addon.price * rentalDays
      : addon.price;

    return {
      name: addon.name,
      totalPrice: totalPrice.toFixed(2)
    };
  });

  document.body.insertAdjacentHTML(
    "beforeend",
    buildPriceDetailsModalHtml(
      "addonsPriceModal",
      selectedCar.priceBreakdown,
      addonLines
    )
  );
}