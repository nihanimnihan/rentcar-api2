document.addEventListener("DOMContentLoaded", () => {
    loadPopularCars();
});

async function loadPopularCars() {
    const container = document.getElementById("popularCarsContainer");

    if (!container) {
        return;
    }

    try {
        const response = await fetch("/api/cars/popular");

        if (!response.ok) {
            throw new Error("Popular cars could not be loaded");
        }

        const cars = await response.json();

        container.innerHTML = cars
            .slice(0, 4)
            .map(renderPopularCarCard)
            .join("");

    } catch (error) {
        console.error(error);
        container.innerHTML = "";
    }
}

function renderPopularCarCard(car) {
    const imageUrl = car.imageUrl || "img/cars/1.png";
    const location = car.location || car.pickupLocation || "Barcelona";
    const category = car.category || "Car";
    const name = `${car.brand || ""} ${car.model || ""}`.trim() || "Car";
    const seats = car.seats || "-";
    const bags = car.bags || "-";
    const transmission = car.transmission || "-";
    const dailyPrice = car.dailyPrice || car.pricePerDay || "-";

    return `
        <div class="col-xl-3 col-lg-4 col-md-6">
            <a href="cars.html" class="carCard -type-1 d-block rounded-4">
                <div class="carCard__image">
                    <div class="cardImage ratio border-light ratio-3:2">
                        <div class="cardImage__content">
                            <img class="rounded-4 col-12" src="${imageUrl}" alt="${name}">
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