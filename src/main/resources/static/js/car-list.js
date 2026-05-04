document.addEventListener("DOMContentLoaded", function () {
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

  // Empty result
  if (!cars || cars.length === 0) {

    carsCount.textContent = "0 cars";

    carsList.innerHTML = `
      <div class="col-12">
        <div class="text-15 text-light-1">
          No cars found.
        </div>
      </div>
    `;

    return;
  }

  // Count
  carsCount.textContent = `${cars.length} cars`;

  // Render list
  carsList.innerHTML = cars.map(car => `
    <div class="col-12">

      <div class="border-top-light pt-30">

        <div class="row x-gap-20 y-gap-20">

          <!-- Image -->
          <div class="col-md-auto">
            <div class="cardImage ratio ratio-1:1 w-250 md:w-1/1 rounded-4">

              <div class="cardImage__content">
                <img
                  class="rounded-4 col-12"
                  src="${car.imageUrl || 'img/lists/car/1/1.png'}"
                  alt="${car.brand} ${car.model}">
              </div>
            </div>
          </div>

          <!-- Info -->
          <div class="col-md">
            <div class="d-flex flex-column h-full justify-between">
              <div>
                <div class="row x-gap-5 items-center">

                  <div class="col-auto">
                    <div class="text-14 text-light-1">
                      Available car
                    </div>
                  </div>

                  <div class="col-auto">
                    <div class="size-3 rounded-full bg-light-1"></div>
                  </div>

                  <div class="col-auto">
                    <div class="text-14 text-light-1">
                      ${car.segment || ''}
                    </div>
                  </div>
                </div>

                <h3 class="text-18 lh-16 fw-500 mt-5">
                  ${car.brand} ${car.model}
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
                      <div class="text-14 ml-10">
                        Available
                      </div>
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

          <!-- Price -->
          <div class="col-md-auto text-right md:text-left">

            <div class="text-22 lh-12 fw-600 mt-70 md:mt-20">
              €${car.dailyPrice}
            </div>

            <div class="text-14 text-light-1 mt-5">
              Per day
            </div>
            <a
              href="${buildCarDetailUrl(car.id)}"
              class="button h-50 px-24 bg-dark-1 -yellow-1 text-white mt-24">
              View Detail
            </a>
          </div>
        </div>
      </div>
    </div>
  `).join("");
}

function buildCarDetailUrl(carId) {
  const currentParams = new URLSearchParams(window.location.search);
  const detailParams = new URLSearchParams();

  detailParams.set("id", carId);

  copyParam(currentParams, detailParams, "pickupLocation");
  copyParam(currentParams, detailParams, "dropoffLocation");
  copyParam(currentParams, detailParams, "pickupDateTime");
  copyParam(currentParams, detailParams, "dropoffDateTime");

  return "car.html?" + detailParams.toString();
}

function copyParam(source, target, name) {
  const value = source.get(name);

  if (value) {
    target.set(name, value);
  }
}