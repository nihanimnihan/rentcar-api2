document.addEventListener("DOMContentLoaded", () => {
    loadPopularCars();
});

async function loadPopularCars() {
    const container = document.getElementById("popularCarsContainer");

    if (!container) {
        return;
    }

    // Build search params once — read current homepage state if available,
    // fall back to safe defaults so cars.html never opens with empty parameters.
    const searchParamsStr = buildSearchParamsStr();

    try {
        const response = await fetch("/api/cars/popular");

        if (!response.ok) {
            throw new Error("Popular cars could not be loaded");
        }

        const cars = await response.json();

        container.innerHTML = cars
            .slice(0, 4)
            .map(car => renderPopularCarCard(car, searchParamsStr))
            .join("");

    } catch (error) {
        console.error(error);
        container.innerHTML = "";
    }
}

/**
 * Reads the current homepage search state from the DOM inputs.
 * Falls back to BCN Airport T1 + tomorrow/day-after-tomorrow at 10:00
 * so popular-car links always open cars.html with valid parameters.
 */
function buildSearchParamsStr() {
    const DEFAULT_LOCATION = "BCN Airport T1";

    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(today.getDate() + 1);
    const dayAfter = new Date(today);
    dayAfter.setDate(today.getDate() + 2);

    function isoDate(d) {
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    }

    const pickupLocation = document.getElementById("pickupLocation")?.value?.trim() || DEFAULT_LOCATION;
    const dropoffLocation = document.getElementById("dropoffLocation")?.value?.trim() || DEFAULT_LOCATION;

    // pickupDateText carries a data-date attribute set by main-search.js
    const pickupDate = document.getElementById("pickupDateText")?.getAttribute("data-date") || isoDate(tomorrow);
    const dropoffDate = document.getElementById("dropoffDateText")?.getAttribute("data-date") || isoDate(dayAfter);

    const pickupHour = document.getElementById("pickupHour")?.value || "10:00";
    const dropoffHour = document.getElementById("dropoffHour")?.value || "10:00";

    return new URLSearchParams({
        pickupLocation,
        dropoffLocation,
        pickupDateTime: `${pickupDate}T${pickupHour}`,
        dropoffDateTime: `${dropoffDate}T${dropoffHour}`,
    }).toString();
}

function renderPopularCarCard(car, searchParamsStr) {
    const imgSrc      = safeSrc(car.imageUrl, "img/lists/car/1/1.png");
    const location    = escapeHtml(car.location || car.pickupLocation || "Barcelona");
    const category    = escapeHtml(car.category || "Car");
    const name        = `${escapeHtml(car.brand || "")} ${escapeHtml(car.model || "")}`.trim() || "Car";
    const seats       = Number.isFinite(Number(car.seats))       ? Number(car.seats)       : "-";
    const bags        = Number.isFinite(Number(car.bags))        ? Number(car.bags)        : "-";
    const transmission = escapeHtml(car.transmission || "-");
    const dailyPrice  = Number.isFinite(Number(car.dailyPrice))  ? Number(car.dailyPrice).toFixed(2)
                      : Number.isFinite(Number(car.pricePerDay)) ? Number(car.pricePerDay).toFixed(2)
                      : "-";

    return `
        <div class="col-xl-3 col-lg-4 col-md-6">
            <a href="cars.html?${searchParamsStr}" class="carCard -type-1 d-block rounded-4">
                <div class="carCard__image">
                    <div class="cardImage ratio border-light ratio-3:2">
                        <div class="cardImage__content">
                            <img class="rounded-4 col-12" src="${imgSrc}" alt="${name}">
                        </div>
                    </div>
                </div>

                <div class="carCard__content mt-10">
                    <div class="d-flex items-center lh-14 mb-5">
                        <div class="text-14 text-light-1">${location}</div>
                        <div class="size-3 bg-light-1 rounded-full ml-10 mr-10"></div>
                        <div class="text-14 text-light-1 uppercase">${category}</div>
                    </div>

                    <h4 class="text-dark-1 text-18 lh-16 fw-500">
                        ${name} <span class="text-15 text-light-1 fw-400">or similar</span>
                    </h4>

                    <div class="row x-gap-20 y-gap-10 items-center pt-5">
                        <div class="col-auto">
                            <div class="d-flex items-center text-14 text-dark-1">
                                <i class="icon-user-2 mr-10"></i>
                                <div class="lh-14">${seats}</div>
                            </div>
                        </div>

                        <div class="col-auto">
                            <div class="d-flex items-center text-14 text-dark-1">
                                <i class="icon-luggage mr-10"></i>
                                <div class="lh-14">${bags}</div>
                            </div>
                        </div>

                        <div class="col-auto">
                            <div class="d-flex items-center text-14 text-dark-1">
                                <i class="icon-transmission mr-10"></i>
                                <div class="lh-14">${transmission}</div>
                            </div>
                        </div>
                    </div>

                    <div class="mt-15">
                        <div class="text-light-1">
                            From <span class="fw-500 text-dark-1">€${dailyPrice}</span> / day
                        </div>
                    </div>
                </div>
            </a>
        </div>
    `;
}