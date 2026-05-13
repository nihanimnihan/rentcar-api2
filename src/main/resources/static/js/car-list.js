document.addEventListener("DOMContentLoaded", function () {
  fillCarsPageSearchFromUrl();
  fillHeaderBookingSummary();
  loadCars();
});

// Module-level maps so selectMileageOption can access car data without DOM hacks
const carCache = {};       // carId -> CarDetailResponse
const mileageOptions = {}; // carId -> "INCLUDED" | "UNLIMITED"

async function loadCars() {
  const params = new URLSearchParams(window.location.search);

  const pickupStr  = params.get("pickupDateTime");
  const dropoffStr = params.get("dropoffDateTime");
  const errorType  = validateSearchDates(pickupStr, dropoffStr);
  if (errorType) {
    document.getElementById("carsList").innerHTML = renderInvalidSearchError(errorType);
    return;
  }

  const apiUrl = "/api/cars/search?" + params.toString();

  try {
    const response = await fetch(apiUrl);

    if (!response.ok) {
      throw new Error("API returned " + response.status);
    }

    const cars = await response.json();
    renderCars(cars);
  } catch (error) {
    console.error("API error:", error);
    document.getElementById("carsList").innerHTML = renderInvalidSearchError("API_ERROR");
  }
}

window.loadCars = loadCars;

/**
 * Returns an error type string if dates are missing, invalid, or in the past.
 * Returns null when dates are acceptable for a car search.
 */
function validateSearchDates(pickupStr, dropoffStr) {
  if (!pickupStr || !dropoffStr) return null; // no dates = filter-less search, that's fine

  const pickup  = new Date(pickupStr);
  const dropoff = new Date(dropoffStr);

  if (isNaN(pickup.getTime()) || isNaN(dropoff.getTime())) return "INVALID_DATE";
  if (pickup < new Date()) return "PAST_DATE";
  if (dropoff <= pickup)  return "INVALID_RANGE";

  return null;
}

/** Render a SIXT-style error panel inside the car list area. */
function renderInvalidSearchError(type) {
  const messages = {
    PAST_DATE:    "Your pickup date is in the past. Please start a new search with a valid future date.",
    INVALID_RANGE:"Return date must be after the pickup date. Please adjust your dates.",
    INVALID_DATE: "The date in the URL is not valid. Please start a new search.",
    API_ERROR:    "Cars could not be loaded at this time. Please try again."
  };
  const message = messages[type] || "Please start a new search.";

  return `
    <div class="col-12">
      <div class="rentcar-search-error">
        <div class="rentcar-search-error-inner">
          <div class="rentcar-search-error-icon">
            <i class="icon-calendar text-20"></i>
          </div>
          <div class="rentcar-search-error-body">
            <div class="rentcar-search-error-title">Sorry</div>
            <div class="rentcar-search-error-message">${message}</div>
          </div>
          <a href="index.html" class="rentcar-search-error-action">New search</a>
        </div>
      </div>
    </div>
  `;
}

function renderCars(cars) {
  const carsList = document.getElementById("carsList");
  const carsCount = document.getElementById("carsCount");

  if (!cars || cars.length === 0) {
    if (carsCount) {
      carsCount.textContent = "0 cars";
    }
    const filterCarsCount = document.getElementById("filterCarsCount");

    if (filterCarsCount) {
      filterCarsCount.textContent = 0;
    }

    carsList.innerHTML = `
      <div class="col-12">
        <div class="text-15 text-light-1">
          No cars found.
        </div>
      </div>
    `;
    return;
  }

  if (carsCount) carsCount.textContent = `${cars.length} cars`;
  const filterCarsCount = document.getElementById("filterCarsCount");
  if (filterCarsCount) filterCarsCount.textContent = cars.length;

  carsList.innerHTML = cars.map(car => {
    const displayClass = escapeHtml(car.displayClass || car.segment || "Standard");
    const vehicleType  = escapeHtml(car.vehicleType  || "");
    const transmission = escapeHtml(car.transmission || "");
    const fuelType     = escapeHtml(car.fuelType     || "");

    const seats       = Number.isFinite(Number(car.seats))       ? Number(car.seats)       : "-";
    const doors       = Number.isFinite(Number(car.doors))       ? Number(car.doors)       : 4;
    const bags        = Number.isFinite(Number(car.bags))        ? Number(car.bags)        : "-";
    const minDriverAge = Number.isFinite(Number(car.minDriverAge)) ? Number(car.minDriverAge) : 21;
    const hasAc = car.airConditioning !== false;

    const dailyPrice = Number.isFinite(Number(car.dailyPrice)) ? Number(car.dailyPrice).toFixed(2) : "-";
    const totalPrice = Number.isFinite(Number(car.totalPrice)) ? Number(car.totalPrice).toFixed(2) : dailyPrice;

    const brandModel  = `${escapeHtml(car.brand || "")} ${escapeHtml(car.model || "")}`.trim();
    const imgSrc      = safeSrc(car.imageUrl, "img/lists/car/1/1.png");

    return `
      <div class="col-lg-6 col-12" id="car-card-${car.id}">
        <div class="border-top-light pt-30 h-full">
          <div class="rounded-4 bg-white px-20 py-20 h-full">

            <div>
              <h3 class="text-22 fw-600">
                ${brandModel}
              </h3>
              <div class="rentcar-spec-list mt-10">
                <span class="rentcar-spec-pill">${displayClass}</span>
                <span class="rentcar-spec-pill">${vehicleType}</span>
                <span class="rentcar-spec-pill">${transmission}</span>
                <span class="rentcar-spec-pill">${fuelType}</span>
              </div>
            </div>

            <div class="cardImage ratio ratio-3:2 rounded-4 mt-20">
              <div class="cardImage__content">
                <img
                  class="rounded-4 col-12"
                  src="${imgSrc}"
                  alt="${brandModel}">
              </div>
            </div>

            <div class="d-flex x-gap-15 y-gap-10 flex-wrap mt-20 text-14">
                <div class="d-flex items-center">
                  <i class="icon-user text-16 mr-5"></i>
                  ${seats} seats
                </div>

                <div class="d-flex items-center">
                  <i class="icon-car text-16 mr-5"></i>
                  ${doors} doors
                </div>

                <div class="d-flex items-center">
                  <i class="icon-luggage text-16 mr-5"></i>
                  ${bags} bags
                </div>

                <div class="d-flex items-center">
                  ${hasAc ? "AC" : "No AC"}
                </div>

                <div class="d-flex items-center">
                  <i class="icon-customer text-16 mr-5"></i>
                  Min age ${minDriverAge}
                </div>
            </div>

            <div class="d-flex x-gap-20 y-gap-10 flex-wrap mt-20">
              <div class="d-flex items-center">
                <i class="icon-check text-10 text-green-2 mr-10"></i>
                <div class="text-14 fw-500 text-green-2">
                  Free cancellation
                </div>
              </div>

              <div class="d-flex items-center">
                <i class="icon-check text-10 text-green-2 mr-10"></i>
                <div class="text-14 fw-500 text-green-2">
                  Unlimited kilometers available
                </div>
              </div>
            </div>

            <div class="d-flex items-end justify-between mt-25">
               <div class="rentcar-price-row">
                  <div class="text-28 fw-700 text-red-1 lh-1">
                    €${dailyPrice}
                    <span class="text-14 fw-600">/ day</span>
                  </div>
                  <div class="rentcar-total-price">
                    €${totalPrice} total
                  </div>
                </div>

              <button
                type="button"
                onclick="showCarDetail(${car.id})"
                class="button h-50 px-24 bg-dark-1 -yellow-1 text-white">
                View Detail
              </button>
            </div>

          </div>
        </div>
      </div>
    `;
  }).join("");
}

async function showCarDetail(carId) {
  const card = document.getElementById(`car-card-${carId}`);

  if (!card) return;

  if (card.classList.contains("car-card-selected")) {
    card.classList.remove("car-card-selected");
    removeExistingDetail();
    return;
  }

  removeExistingDetail();

  try {
    const params = window.location.search;
    const response = await fetch(`/api/cars/${carId}${params}`);

    if (!response.ok) {
      throw new Error("Car detail could not be loaded");
    }

    const car = await response.json();
    carCache[carId] = car; // keep for live mileage total updates

    document.querySelectorAll(".car-card-selected")
      .forEach(el => el.classList.remove("car-card-selected"));

    card.classList.add("car-card-selected");

    const detailHtml = buildDetailHtml(car, carId);
    const insertAfterCard = findRowEndCard(card);

    insertAfterCard.insertAdjacentHTML("afterend", detailHtml);

    document.getElementById(`car-detail-${carId}`)?.scrollIntoView({
      behavior: "smooth",
      block: "nearest"
    });
  } catch (error) {
    console.error("Car detail could not be loaded:", error);
  }
}

window.showCarDetail = showCarDetail;

function removeExistingDetail() {
  document.querySelectorAll(".inline-car-detail").forEach(el => el.remove());
  // Clean up any price modals appended to body when a detail panel was open
  document.querySelectorAll('[id^="price-modal-"]').forEach(el => el.remove());
}

function buildDetailHtml(car, carId) {
  const carName      = `${escapeHtml(car.brand || "")} ${escapeHtml(car.model || "")}`.trim();
  const displayClass = escapeHtml(car.displayClass || car.segment || "Standard");
  const transmission = escapeHtml(car.transmission || "");
  const seats        = Number.isFinite(Number(car.seats))        ? Number(car.seats)        : "-";
  const doors        = Number.isFinite(Number(car.doors))        ? Number(car.doors)        : 4;
  const bags         = Number.isFinite(Number(car.bags))         ? Number(car.bags)         : "-";
  const minDriverAge = Number.isFinite(Number(car.minDriverAge)) ? Number(car.minDriverAge) : 21;
  const dailyPrice   = Number.isFinite(Number(car.dailyPrice))   ? Number(car.dailyPrice).toFixed(2) : "-";
  const totalPrice   = Number.isFinite(Number(car.totalPrice))   ? Number(car.totalPrice).toFixed(2) : dailyPrice;
  const imgSrc       = safeSrc(car.imageUrl, "img/lists/car/1/1.png");

  return `
    <div class="col-12 inline-car-detail mt-20" id="car-detail-${carId}">
      <div class="rentcar-detail-card">

        <button
          type="button"
          class="rentcar-detail-close"
          onclick="removeExistingDetail(); document.querySelectorAll('.car-card-selected').forEach(el => el.classList.remove('car-card-selected'));">
          <i class="icon-close"></i>
        </button>

        <div class="rentcar-detail-grid">

          <div class="rentcar-detail-left">
            <div class="text-center">
              <h2 class="rentcar-detail-title">${carName}</h2>
              <div class="rentcar-detail-subtitle">
                ${displayClass} · ${transmission}
              </div>
            </div>

            <div class="rentcar-detail-image-wrap">
              <img
                src="${imgSrc}"
                alt="${carName}">
            </div>

            <div class="rentcar-detail-specs">
              <span><i class="icon-user"></i> ${seats} Seats</span>
              <span><i class="icon-luggage"></i> ${bags} Bags</span>
              <span><i class="icon-car"></i> ${doors} Doors</span>
              <span><i class="icon-transmission"></i> ${transmission}</span>
              <span><i class="icon-customer"></i> Min age ${minDriverAge}</span>
            </div>
          </div>

          <div class="rentcar-detail-right">
            <div class="rentcar-mileage-title">Mileage</div>

            <label class="rentcar-choice-card is-selected">
              <input
                type="radio"
                name="mileage-${carId}"
                value="limited"
                checked
                onchange="selectMileageOption(this, ${carId})">

              <span class="rentcar-radio"></span>

              <div class="rentcar-choice-content">
                <div class="rentcar-choice-title">${car.priceBreakdown?.includedKm != null ? Number(car.priceBreakdown.includedKm).toLocaleString("en") + " km" : "—"}</div>
                <div class="rentcar-choice-sub">+€0.25 / for every additional km</div>
              </div>

              <div class="rentcar-choice-price">Included</div>
            </label>

            <label class="rentcar-choice-card">
              <input
                type="radio"
                name="mileage-${carId}"
                value="unlimited"
                onchange="selectMileageOption(this, ${carId})">

              <span class="rentcar-radio"></span>

              <div class="rentcar-choice-content">
                <div class="rentcar-choice-title">Unlimited kilometers</div>
                <div class="rentcar-choice-sub">All kilometers are included in the price</div>
              </div>

              <div class="rentcar-choice-price">${car.priceBreakdown?.unlimitedKmDailyPrice != null ? "+ €" + Number(car.priceBreakdown.unlimitedKmDailyPrice).toFixed(2) + " / day" : "—"}</div>
            </label>

            <div class="rentcar-detail-action-row">
              <div>
                <div class="rentcar-detail-price">
                  €${dailyPrice}<span>/ day</span>
                  <strong id="detail-total-${carId}">€${totalPrice} total</strong>
                </div>

                <button type="button" class="rentcar-price-details" onclick="openCarPriceModal(${carId})">
                  Price details
                </button>
              </div>

              <button type="button" class="rentcar-next-button" onclick="goToAddons(${carId})">
                Next
              </button>
            </div>
          </div>

        </div>
      </div>
  `;
}

function selectMileageOption(input, carId) {
  const isUnlimited = input.value === "unlimited";
  mileageOptions[carId] = isUnlimited ? "UNLIMITED" : "INCLUDED";

  // Toggle selected card styling
  const detail = input.closest(".inline-car-detail");
  detail
    .querySelectorAll(".rentcar-choice-card")
    .forEach(card => card.classList.remove("is-selected"));
  input.closest(".rentcar-choice-card").classList.add("is-selected");

  const car = carCache[carId];
  if (!car) return;

  const baseTotal = Number(car.totalPrice || 0);
  const rentalDays = car.priceBreakdown?.rentalDays || 1;
  const unlimitedKmDailyPrice = Number(car.priceBreakdown?.unlimitedKmDailyPrice || 0);
  const unlimitedCharge = isUnlimited ? unlimitedKmDailyPrice * rentalDays : 0;

  // Update live total in the detail panel
  const totalEl = document.getElementById(`detail-total-${carId}`);
  if (totalEl) totalEl.textContent = `€${(baseTotal + unlimitedCharge).toFixed(2)} total`;
}

window.selectMileageOption = selectMileageOption;

/**
 * Build the price details modal fresh and append it to document.body so it
 * inherits no card-container styles — identical rendering to the addons.html modal.
 * Always reflects the currently selected mileage option.
 */
function openCarPriceModal(carId) {
  const car = carCache[carId];
  if (!car?.priceBreakdown) return;

  // Remove stale modal for this car (e.g. from a previous open)
  document.getElementById(`price-modal-${carId}`)?.remove();

  const rentalDays = car.priceBreakdown.rentalDays || 1;
  const addonLines = [];
  if (mileageOptions[carId] === "UNLIMITED") {
    const charge = Number(car.priceBreakdown.unlimitedKmDailyPrice || 0) * rentalDays;
    addonLines.push({ name: "Unlimited kilometers", totalPrice: charge.toFixed(2) });
  }

  document.body.insertAdjacentHTML(
    "beforeend",
    buildPriceDetailsModalHtml(`price-modal-${carId}`, car.priceBreakdown, addonLines)
  );
  openPriceDetailsModal(`price-modal-${carId}`);
}

window.openCarPriceModal = openCarPriceModal;

function fillCarsPageSearchFromUrl() {
  const params = new URLSearchParams(window.location.search);

  setInputValue("pickupLocation", params.get("pickupLocation"));
  setInputValue("dropoffLocation", params.get("dropoffLocation"));

  setDateTimeValue("pickupDateText", "pickupHour", params.get("pickupDateTime"));
  setDateTimeValue("dropoffDateText", "dropoffHour", params.get("dropoffDateTime"));
}

function fillHeaderBookingSummary() {
  const params = new URLSearchParams(window.location.search);

  const pickupLocation = params.get("pickupLocation") || "-";
  const dropoffLocation = params.get("dropoffLocation") || "-";
  const pickupDateTime = params.get("pickupDateTime");
  const dropoffDateTime = params.get("dropoffDateTime");

  setTextValue("headerRouteText", `${pickupLocation} → ${dropoffLocation}`);

  setTextValue(
    "headerDateText",
    `${formatDateTimeForHeader(pickupDateTime)} - ${formatDateTimeForHeader(dropoffDateTime)}`
  );
}

function setDateTimeValue(dateTextId, hourSelectId, value) {
  if (!value) return;

  const [datePart, timePart] = value.split("T");

  const dateText = document.getElementById(dateTextId);
  if (dateText) {
    dateText.textContent = formatDateForDisplay(datePart);
    dateText.setAttribute("data-date", datePart);
  }

  const hourSelect = document.getElementById(hourSelectId);
  if (hourSelect && timePart) {
    hourSelect.value = timePart.substring(0, 5);
  }
}

function setInputValue(id, value) {
  const element = document.getElementById(id);

  if (element && value) {
    element.value = value;
  }
}

function setTextValue(id, value) {
  const element = document.getElementById(id);

  if (element && value) {
    element.textContent = value;
  }
}

function formatDateForDisplay(value) {
  if (!value) return "Select date";

  const date = new Date(value + "T00:00:00");

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short"
  });
}

function formatDateTimeForHeader(value) {
  if (!value) return "-";

  const [datePart, timePart] = value.split("T");
  return `${formatDateForDisplay(datePart)} ${timePart ? timePart.substring(0, 5) : ""}`;
}

function findRowEndCard(card) {
  const nextCard = card.nextElementSibling;

  if (
    nextCard &&
    nextCard.id?.startsWith("car-card-") &&
    card.offsetTop === nextCard.offsetTop
  ) {
    return nextCard;
  }

  return card;
}

function goToAddons(carId) {
  const params = new URLSearchParams(window.location.search);
  params.set("carId", carId);
  params.set("mileageOption", mileageOptions[carId] || "INCLUDED");

  window.location.href = `/addons.html?${params.toString()}`;
}

window.goToAddons = goToAddons;