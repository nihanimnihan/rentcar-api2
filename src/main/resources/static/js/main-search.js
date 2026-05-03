document.addEventListener("DOMContentLoaded", function () {
  const DEFAULT_LOCATION = "BCN Airport T1";

  const pickupInput = document.getElementById("pickupLocation");
  const dropoffInput = document.getElementById("dropoffLocation");

  const pickupDateInput = document.getElementById("pickupDate");
  const dropoffDateInput = document.getElementById("dropoffDate");

  const pickupHourInput = document.getElementById("pickupHour");
  const dropoffHourInput = document.getElementById("dropoffHour");

  if (pickupInput && !pickupInput.value.trim()) {
    pickupInput.value = DEFAULT_LOCATION;
  }

  if (dropoffInput && !dropoffInput.value.trim()) {
    dropoffInput.value = DEFAULT_LOCATION;
  }

  function formatIso(date) {
    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const year = date.getFullYear();

    return `${year}-${month}-${day}`;
  }

  const today = new Date();

  const tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);

  if (pickupDateInput && !pickupDateInput.value) {
    pickupDateInput.value = formatIso(today);
  }

  if (dropoffDateInput && !dropoffDateInput.value) {
    dropoffDateInput.value = formatIso(tomorrow);
  }

  const searchButton = document.getElementById("searchCarsButton");

  if (!searchButton) {
    return;
  }

  searchButton.addEventListener("click", function () {
    const pickupLocation = pickupInput?.value.trim() || DEFAULT_LOCATION;
    const dropoffLocation = dropoffInput?.value.trim() || DEFAULT_LOCATION;

    const pickupDate = pickupDateInput?.value || formatIso(today);
    const dropoffDate = dropoffDateInput?.value || formatIso(tomorrow);

    const pickupHour = pickupHourInput?.value || "10:00";
    const dropoffHour = dropoffHourInput?.value || "10:00";

    const params = new URLSearchParams({
      pickupLocation,
      dropoffLocation,
      pickupDateTime: `${pickupDate}T${pickupHour}`,
      dropoffDateTime: `${dropoffDate}T${dropoffHour}`
    });

    window.location.href = "/cars.html?" + params.toString();
  });
});