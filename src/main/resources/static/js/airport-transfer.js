document.addEventListener("DOMContentLoaded", function () {
  const tabs = document.querySelectorAll("[data-transfer-mode]");
  const destinationField = document.getElementById("transferDestinationField");
  const durationField = document.getElementById("transferDurationField");

  tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      tabs.forEach(t => t.classList.remove("is-active"));
      tab.classList.add("is-active");

      const mode = tab.getAttribute("data-transfer-mode");

      if (mode === "hourly") {
        destinationField.classList.add("d-none");
        durationField.classList.remove("d-none");
      } else {
        durationField.classList.add("d-none");
        destinationField.classList.remove("d-none");
      }
    });
  });
});
const showOffersButton = document.getElementById("transferShowOffersButton");

if (showOffersButton) {
  showOffersButton.addEventListener("click", function () {
    window.location.href = "airport-transfer-offers.html";
  });
}
const transferDurations = [
  { hours: 1, includedKm: 30 },
  { hours: 2, includedKm: 60 },
  { hours: 3, includedKm: 90 },
  { hours: 4, includedKm: 120 },
  { hours: 5, includedKm: 150 },
  { hours: 6, includedKm: 180 },
  { hours: 7, includedKm: 210 },
  { hours: 8, includedKm: 240 },
  { hours: 9, includedKm: 270 },
  { hours: 10, includedKm: 300 },
  { hours: 11, includedKm: 330 },
  { hours: 12, includedKm: 360 }
];