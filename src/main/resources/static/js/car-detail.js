document.addEventListener("DOMContentLoaded", function () {
  fillCarDetailSearchFields();
  loadCarDetail();
});

async function loadCarDetail() {
  const params = new URLSearchParams(window.location.search);
  const carId = params.get("id");

  if (!carId) {
    console.error("Car id is missing in URL");
    return;
  }

  try {
    const response = await fetch(`/api/cars/${carId}`);

    if (!response.ok) {
      throw new Error("API returned " + response.status);
    }

    const car = await response.json();
    renderCarDetail(car);

  } catch (error) {
    console.error("Car detail could not be loaded:", error);
    setTextValue("carTitle", "Car could not be loaded");
  }
}

function renderCarDetail(car) {
  const carName = `${car.brand || ""} ${car.model || ""}`.trim();

  setTextValue("carTitle", carName || "Car detail");
  setTextValue("carTotalPrice", `€${car.dailyPrice || "-"}`);

  setTextValue("carSeats", car.seats || "-");
  setTextValue("carLuggage", car.luggage || "-");
  setTextValue("carTransmission", car.transmission || "-");

  setTextValue("carBrand", car.brand || "-");
  setTextValue("carModel", car.model || "-");
  setTextValue("carYear", car.year || "-");
  setTextValue("carSegment", car.segment || "-");
  setTextValue("carStatus", car.available === false ? "Not available" : "Available");

  setImageValue("carMainImage", car.imageUrl || "img/lists/car/1/1.png", carName);
}

function fillCarDetailSearchFields() {
  const params = new URLSearchParams(window.location.search);

  setInputValue("detailPickupLocation", params.get("pickupLocation"));
  setInputValue("detailDropoffLocation", params.get("dropoffLocation"));

  setTextValue("detailPickupDateText", formatDisplayDate(params.get("pickupDateTime")));
  setTextValue("detailDropoffDateText", formatDisplayDate(params.get("dropoffDateTime")));
}

function setInputValue(id, value) {
  const element = document.getElementById(id);

  if (element && value) {
    element.value = value;
  }
}

function setTextValue(id, value) {
  const element = document.getElementById(id);

  if (element && value !== undefined && value !== null) {
    element.textContent = value;
  }
}

function setImageValue(id, src, alt) {
  const element = document.getElementById(id);

  if (element) {
    element.src = src;
    element.alt = alt || "car image";
  }
}

function formatDisplayDate(value) {
  if (!value) return null;

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short"
  });
}