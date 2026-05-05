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
    if (carsCount) carsCount.textContent = "0 cars";

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

  carsList.innerHTML = cars.map(car => `
    <div class="col-12" id="car-card-${car.id}">
      <div class="border-top-light pt-30">
        <div class="row x-gap-20 y-gap-20">

          <div class="col-md-auto">
            <div class="cardImage ratio ratio-1:1 w-250 md:w-1/1 rounded-4">
              <div class="cardImage__content">
                <img
                  class="rounded-4 col-12"
                  src="${car.imageUrl || 'img/lists/car/1/1.png'}"
                  alt="${car.brand || ''} ${car.model || ''}">
              </div>
            </div>
          </div>

          <div class="col-md">
            <div class="d-flex flex-column h-full justify-between">
              <div>
                <div class="row x-gap-5 items-center">
                  <div class="col-auto">
                    <div class="text-14 text-light-1">Available car</div>
                  </div>

                  <div class="col-auto">
                    <div class="size-3 rounded-full bg-light-1"></div>
                  </div>

                  <div class="col-auto">
                    <div class="text-14 text-light-1">${car.segment || ''}</div>
                  </div>
                </div>

                <h3 class="text-18 lh-16 fw-500 mt-5">
                  ${car.brand || ''} ${car.model || ''}
                </h3>
              </div>

              <div class="col-lg-7 mt-20">
                <div class="row y-gap-5">
                  <div class="col-sm-6">
                    <div class="d-flex items-center">
                      <i class="icon-transmission"></i>
                      <div class="text-14 ml-10">
                        ${car.segment || 'Standard'}
                      </div>
                    </div>
                  </div>

                  <div class="col-sm-6">
                    <div class="d-flex items-center">
                      <i class="icon-check"></i>
                      <div class="text-14 ml-10">Available</div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="mt-20">
                <div class="d-flex items-center">
                  <i class="icon-check text-10"></i>
                  <div class="text-14 fw-500 text-green-2 ml-10">
                    Free Cancellation
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="col-md-auto text-right md:text-left">
            <div class="text-22 lh-12 fw-600 mt-70 md:mt-20">
              €${car.dailyPrice || '-'}
            </div>

            <div class="text-14 text-light-1 mt-5">
              Per day
            </div>

            <button
              type="button"
              onclick="showCarDetail(${car.id})"
              class="button h-50 px-24 bg-dark-1 -yellow-1 text-white mt-24">
              View Detail
            </button>
          </div>

        </div>
      </div>
    </div>
  `).join("");
}

async function showCarDetail(carId) {
  removeExistingDetail();

  try {
    const response = await fetch(`/api/cars/${carId}`);

    if (!response.ok) {
      throw new Error("Car detail could not be loaded");
    }

    const car = await response.json();
    const card = document.getElementById(`car-card-${carId}`);

    if (!card) return;

    const detailHtml = buildDetailHtml(car, carId);
    card.insertAdjacentHTML("afterend", detailHtml);

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