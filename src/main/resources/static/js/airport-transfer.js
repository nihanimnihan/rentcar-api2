document.addEventListener("DOMContentLoaded", function () {
  const tabs = document.querySelectorAll("[data-transfer-mode]");
  const destinationField = document.getElementById("transferDestinationField");
  const durationField = document.getElementById("transferDurationField");
  const durationSelect = document.getElementById("transferDurationSelect");
  const kmHelp = document.getElementById("transferKmHelp");
  let currentMode = "oneway";
  let durationsData = [];

  // ── Date/time state (always ISO internally) ────────────────────────────────
  const dateInput = document.getElementById("transferPickupDateInput");
  const timeInput = document.getElementById("transferPickupTimeInput");

  function padZ(n) { return String(n).padStart(2, "0"); }

  function formatDisplayDate(isoDate) {
    var d = new Date(isoDate + "T00:00:00");
    var months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
    return d.getDate() + " " + months[d.getMonth()];
  }

  // Initialise date input to today
  (function () {
    var today = new Date();
    var iso = today.getFullYear() + "-" + padZ(today.getMonth() + 1) + "-" + padZ(today.getDate());
    if (dateInput) {
      dateInput.dataset.isoDate = iso;
      dateInput.value = formatDisplayDate(iso);
    }
  }());

  // ── Fallback durations used if the API is unavailable ──────────────────────
  function buildFallbackDurations() {
    return Array.from({ length: 12 }, (_, i) => ({
      id: i + 1,
      hours: i + 1,
      includedKm: (i + 1) * 30,
      label: (i + 1 === 1 ? "1 hour" : (i + 1) + " hours") + " (" + ((i + 1) * 30) + " km included)"
    }));
  }

  function renderDurationOptions(durations) {
    durationSelect.innerHTML = "";
    durations.forEach(function (d) {
      const opt = document.createElement("option");
      opt.value = d.id;
      opt.dataset.hours = d.hours;
      opt.dataset.includedKm = d.includedKm;
      opt.textContent = "Up to " + (d.hours === 1 ? "1 hour" : d.hours + " hours");
      durationSelect.appendChild(opt);
    });
    updateKmHelp();
  }

  function updateKmHelp() {
    const selected = durationSelect.options[durationSelect.selectedIndex];
    if (selected && kmHelp) {
      kmHelp.textContent = selected.dataset.includedKm + " km included";
    }
  }

  durationSelect.addEventListener("change", updateKmHelp);

  // ── Load durations from API ─────────────────────────────────────────────────
  fetch("/api/transfer/durations")
    .then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.json();
    })
    .then(function (data) {
      durationsData = data;
      renderDurationOptions(durationsData);
    })
    .catch(function (err) {
      console.warn("Could not load transfer durations from API, using fallback.", err);
      durationsData = buildFallbackDurations();
      renderDurationOptions(durationsData);
    });

  // ── Tab switching ───────────────────────────────────────────────────────────
  tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      tabs.forEach(t => t.classList.remove("is-active"));
      tab.classList.add("is-active");
      currentMode = tab.getAttribute("data-transfer-mode");

      if (currentMode === "hourly") {
        destinationField.classList.add("d-none");
        durationField.classList.remove("d-none");
      } else {
        durationField.classList.add("d-none");
        destinationField.classList.remove("d-none");
      }
    });
  });

  // ── Show offers button ──────────────────────────────────────────────────────
  const showOffersButton = document.getElementById("transferShowOffersButton");
  if (showOffersButton) {
    showOffersButton.addEventListener("click", function () {
      const params = new URLSearchParams();

      const pickupLocationInput = document.querySelector(".transfer-field input[type='text']:not(#transferPickupDateInput):not(#transferPickupTimeInput)");
      const allTextInputs = document.querySelectorAll(".transfer-field input[type='text']");
      const pickupLocInput = allTextInputs[0];
      const destinationInput = allTextInputs[1];

      // Build ISO datetime: use data-isoDate attribute from date input + time input value
      var isoDate = dateInput && dateInput.dataset.isoDate ? dateInput.dataset.isoDate : "";
      var timeVal  = timeInput ? timeInput.value.trim() : "10:00";

      if (!isoDate) {
        var today = new Date();
        isoDate = today.getFullYear() + "-" + padZ(today.getMonth() + 1) + "-" + padZ(today.getDate());
      }
      // Normalise time to HH:mm (strip any extra text)
      var timeMatch = timeVal.match(/^(\d{1,2}):(\d{2})/);
      var cleanTime = timeMatch ? padZ(timeMatch[1]) + ":" + timeMatch[2] : "10:00";

      if (currentMode === "hourly") {
        params.set("transferType", "HOURLY");
        const selected = durationSelect.options[durationSelect.selectedIndex];
        if (selected) {
          params.set("durationHours", selected.dataset.hours);
          params.set("includedKm", selected.dataset.includedKm);
        }
      } else {
        params.set("transferType", "ONE_WAY");
        if (destinationInput && destinationInput.value) {
          params.set("destination", destinationInput.value);
        }
      }

      if (pickupLocInput && pickupLocInput.value) {
        params.set("pickupLocation", pickupLocInput.value);
      }

      params.set("pickupDateTime", isoDate + "T" + cleanTime);

      window.location.href = "airport-transfer-offers.html?" + params.toString();
    });
  }
});
