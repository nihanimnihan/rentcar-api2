document.addEventListener("DOMContentLoaded", function () {
  const tabs = document.querySelectorAll("[data-transfer-mode]");
  const destinationField = document.getElementById("transferDestinationField");
  const durationField = document.getElementById("transferDurationField");
  const durationSelect = document.getElementById("transferDurationSelect");
  const kmHelp = document.getElementById("transferKmHelp");
  let currentMode = "oneway";
  let durationsData = [];

  // ── State ──────────────────────────────────────────────────────────────────
  var selectedTransferDate = "";   // YYYY-MM-DD
  var selectedTransferTime = "10:00";

  function padZ(n) { return String(n).padStart(2, "0"); }

  function todayIso() {
    var d = new Date();
    return d.getFullYear() + "-" + padZ(d.getMonth() + 1) + "-" + padZ(d.getDate());
  }

  function formatDisplayDate(isoDate) {
    var d = new Date(isoDate + "T00:00:00");
    var months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
    return d.getDate() + " " + months[d.getMonth()];
  }

  selectedTransferDate = todayIso();

  // ── DOM refs ───────────────────────────────────────────────────────────────
  var dateField     = document.getElementById("transferPickupDateField");
  var dateTrigger   = document.getElementById("transferPickupDateTrigger");
  var dateValueEl   = document.getElementById("transferPickupDateValue");
  var calPopup      = document.getElementById("transferCalendarPopup");
  var calHost       = document.getElementById("transferCalendarHost");
  var timeField     = document.getElementById("transferPickupTimeField");
  var timeTrigger   = document.getElementById("transferPickupTimeTrigger");
  var timeValueEl   = document.getElementById("transferPickupTimeValue");
  var timePopup     = document.getElementById("transferTimePopup");
  var timeList      = document.getElementById("transferTimeList");

  var calInstance = null;
  var timeSlotsBuilt = false;

  function updateDateDisplay() {
    if (dateValueEl) dateValueEl.textContent = formatDisplayDate(selectedTransferDate);
  }

  function updateTimeDisplay() {
    if (timeValueEl) timeValueEl.textContent = selectedTransferTime;
  }

  updateDateDisplay();
  updateTimeDisplay();

  // ── Calendar popup ─────────────────────────────────────────────────────────
  function openCalendar() {
    if (!calPopup) return;
    closeTimePopup();
    if (window.RentCarCalendar && calHost) {
      if (!calInstance) {
        calInstance = RentCarCalendar.mount(calHost, {
          initialDate: selectedTransferDate,
          onDateSelected: function (iso) {
            selectedTransferDate = iso;
            updateDateDisplay();
            closeCalendar();
          }
        });
      } else {
        calInstance.setDate(selectedTransferDate);
      }
    }
    calPopup.style.display = "block";
  }

  function closeCalendar() {
    if (calPopup) calPopup.style.display = "none";
  }

  if (dateTrigger) {
    dateTrigger.addEventListener("click", function (e) {
      e.stopPropagation();
      calPopup && calPopup.style.display !== "none" ? closeCalendar() : openCalendar();
    });
  }

  // ── Time popup ─────────────────────────────────────────────────────────────
  function buildTimeSlots() {
    if (timeSlotsBuilt || !timeList) return;
    timeSlotsBuilt = true;
    for (var h = 0; h < 24; h++) {
      for (var m = 0; m < 60; m += 15) {
        var slot = padZ(h) + ":" + padZ(m);
        var opt = document.createElement("div");
        opt.className = "tdt-time-option";
        opt.textContent = slot;
        opt.dataset.time = slot;
        (function (s, el) {
          el.addEventListener("click", function (e) {
            e.stopPropagation();
            selectedTransferTime = s;
            updateTimeDisplay();
            timeList.querySelectorAll(".tdt-time-option").forEach(function (o) {
              o.classList.toggle("is-active", o.dataset.time === selectedTransferTime);
            });
            closeTimePopup();
          });
        })(slot, opt);
        timeList.appendChild(opt);
      }
    }
  }

  function markActiveTime() {
    if (!timeList) return;
    timeList.querySelectorAll(".tdt-time-option").forEach(function (opt) {
      opt.classList.toggle("is-active", opt.dataset.time === selectedTransferTime);
    });
  }

  function scrollToActiveTime() {
    if (!timeList) return;
    var active = timeList.querySelector(".tdt-time-option.is-active");
    if (active) active.scrollIntoView({ block: "center" });
  }

  function openTimePopup() {
    if (!timePopup) return;
    closeCalendar();
    buildTimeSlots();
    markActiveTime();
    timePopup.style.display = "block";
    setTimeout(scrollToActiveTime, 30);
  }

  function closeTimePopup() {
    if (timePopup) timePopup.style.display = "none";
  }

  if (timeTrigger) {
    timeTrigger.addEventListener("click", function (e) {
      e.stopPropagation();
      timePopup && timePopup.style.display !== "none" ? closeTimePopup() : openTimePopup();
    });
  }

  // Click outside closes both popups
  document.addEventListener("click", function (e) {
    if (calPopup && calPopup.style.display !== "none") {
      if (!dateField || !dateField.contains(e.target)) closeCalendar();
    }
    if (timePopup && timePopup.style.display !== "none") {
      if (!timeField || !timeField.contains(e.target)) closeTimePopup();
    }
  });

  // ── Fallback durations used if the API is unavailable ─────────────────────
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

  // ── Tab switching ──────────────────────────────────────────────────────────
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

  // ── Show offers button ─────────────────────────────────────────────────────
  const showOffersButton = document.getElementById("transferShowOffersButton");
  if (showOffersButton) {
    showOffersButton.addEventListener("click", function () {
      const params = new URLSearchParams();

      const allTextInputs = document.querySelectorAll(".transfer-field input[type='text']");
      const pickupLocInput = allTextInputs[0];
      const destinationInput = allTextInputs[1];

      var isoDate = selectedTransferDate || todayIso();
      var cleanTime = selectedTransferTime || "10:00";

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
