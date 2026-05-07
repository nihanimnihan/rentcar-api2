document.addEventListener("DOMContentLoaded", function () {
  fillCarsPageSearchFromUrl();
  fillHeaderBookingSummary();
  loadCars();
});

async function loadCars() {
  const params = new URLSearchParams(window.location.search);
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

    document.getElementById("carsList").innerHTML = `
      <div class="col-12">
        <div class="text-15 text-red-1">
          Cars could not be loaded.
        </div>
      </div>
    `;
  }
}

window.loadCars = loadCars;

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
    const displayClass = car.displayClass || car.segment || "Standard";
    const vehicleType = car.vehicleType || "";
    const transmission = car.transmission || "";
    const fuelType = car.fuelType || "";

    const seats = car.seats || "-";
    const doors = car.doors || 4;
    const bags = car.bags || "-";
    const minDriverAge = car.minDriverAge || 21;
    const hasAc = car.airConditioning !== false;

    const dailyPrice = car.dailyPrice || "-";
    const totalPrice = car.totalPrice || dailyPrice;

    return `
      <div class="col-lg-6 col-12" id="car-card-${car.id}">
        <div class="border-top-light pt-30 h-full">
          <div class="rounded-4 bg-white px-20 py-20 h-full">

            <div>
              <h3 class="text-22 fw-600">
                ${car.brand || ""} ${car.model || ""}
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
                  src="${car.imageUrl || "img/lists/car/1/1.png"}"
                  alt="${car.brand || ""} ${car.model || ""}">
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
    const response = await fetch(`/api/cars/${carId}`);

    if (!response.ok) {
      throw new Error("Car detail could not be loaded");
    }

    const car = await response.json();

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
}

function buildDetailHtml(car, carId) {
  return `
    <div class="col-12 inline-car-detail mt-20" id="car-detail-${carId}">
      <div class="border-light rounded-4 shadow-4 bg-white px-30 py-30">
        <div class="row y-gap-30">

          <div class="col-lg-7">
            <h2 class="text-24 fw-600">
              ${car.brand || ''} ${car.model || ''}
            </h2>

            <div
              class="rounded-4 bg-light-2 mt-20 d-flex items-center justify-center"
              style="height:360px;">

              <img
                src="${car.imageUrl || 'img/lists/car/1/1.png'}"
                alt="${car.brand || ''} ${car.model || ''}"
                style="max-width:100%; max-height:100%; object-fit:contain;">
            </div>
          </div>

          <div class="col-lg-5">
            <div class="text-20 fw-500">Booking option</div>

            <div class="border-light rounded-4 px-20 py-20 mt-20">
              <div class="fw-500">Best price</div>
              <div class="text-15 text-light-1 mt-5">Free cancellation</div>

              <div class="text-24 fw-600 mt-20">
                €${car.dailyPrice || '-'}
              </div>
            </div>

            <div class="row pt-25 y-gap-10">
              <div class="col-6">Seats: ${car.seats || "-"}</div>
              <div class="col-6">Bags: ${car.bags || "-"}</div>
              <div class="col-6">Transmission: ${car.transmission || "-"}</div>
              <div class="col-6">Segment: ${car.segment || "-"}</div>
            </div>
          </div>

        </div>
      </div>
    </div>
  `;
}

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