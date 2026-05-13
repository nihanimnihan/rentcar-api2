document.addEventListener("DOMContentLoaded", function () {
  const DEFAULT_LOCATION = "BCN Airport T1";

  const pickupInput    = document.getElementById("pickupLocation");
  const dropoffInput   = document.getElementById("dropoffLocation");
  const pickupDateText = document.getElementById("pickupDateText");
  const dropoffDateText = document.getElementById("dropoffDateText");
  const pickupHourInput  = document.getElementById("pickupHour");
  const dropoffHourInput = document.getElementById("dropoffHour");

  if (pickupInput  && !pickupInput.value.trim())  pickupInput.value  = DEFAULT_LOCATION;
  if (dropoffInput && !dropoffInput.value.trim()) dropoffInput.value = DEFAULT_LOCATION;

  function isoFromDate(d) {
    return d.getFullYear() + '-' +
      String(d.getMonth() + 1).padStart(2, '0') + '-' +
      String(d.getDate()).padStart(2, '0');
  }

  function displayFromIso(isoDate) {
    const date = new Date(isoDate + 'T00:00:00');
    if (isNaN(date.getTime())) return isoDate;
    return date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
  }

  // Defaults: tomorrow (pickup) and day-after-tomorrow (dropoff).
  // car-list.js will overwrite these with URL params on cars.html.
  const today          = new Date();
  const tomorrow       = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const dayAfterTomorrow = new Date(today); dayAfterTomorrow.setDate(today.getDate() + 2);

  if (pickupDateText) {
    pickupDateText.innerText = displayFromIso(isoFromDate(tomorrow));
    pickupDateText.setAttribute('data-date', isoFromDate(tomorrow));
  }
  if (dropoffDateText) {
    dropoffDateText.innerText = displayFromIso(isoFromDate(dayAfterTomorrow));
    dropoffDateText.setAttribute('data-date', isoFromDate(dayAfterTomorrow));
  }

  // Identify pickup / dropoff calendar wrappers by DOM order.
  // index.html and cars.html both have pickup first, dropoff second.
  const calendarEls       = document.querySelectorAll('.js-calendar-el');
  const pickupCalendarEl  = calendarEls[0] || null;
  const dropoffCalendarEl = calendarEls[1] || null;

  // ── Pickup selected ──────────────────────────────────────────────────────
  if (pickupCalendarEl) {
    pickupCalendarEl.addEventListener('rentcar:date-selected', function (e) {
      const newPickupIso     = e.detail.isoDate;
      const currentDropoffIso = dropoffDateText ? dropoffDateText.getAttribute('data-date') : null;

      // If new pickup is on/after current dropoff, advance dropoff by one day.
      if (currentDropoffIso && currentDropoffIso <= newPickupIso) {
        const d = new Date(newPickupIso + 'T00:00:00');
        d.setDate(d.getDate() + 1);
        const newDropoffIso = isoFromDate(d);

        if (dropoffDateText) {
          dropoffDateText.innerText = displayFromIso(newDropoffIso);
          dropoffDateText.setAttribute('data-date', newDropoffIso);
        }

        // Visually mark the new dropoff date in the dropoff calendar
        if (dropoffCalendarEl && window.RentCarDatePicker) {
          window.RentCarDatePicker.markDateInCalendar(dropoffCalendarEl, newDropoffIso);
        }
      }
    });
  }

  // ── Dropoff selected ─────────────────────────────────────────────────────
  if (dropoffCalendarEl) {
    dropoffCalendarEl.addEventListener('rentcar:date-selected', function (e) {
      const newDropoffIso    = e.detail.isoDate;
      const currentPickupIso = pickupDateText ? pickupDateText.getAttribute('data-date') : null;

      // Reject dropoff that is not after pickup — revert to the previous value.
      if (currentPickupIso && newDropoffIso <= currentPickupIso) {
        const prevDropoffIso = dropoffDateText ? dropoffDateText.getAttribute('data-date') : null;

        // Revert display text (calendarInteraction2 already updated it)
        if (prevDropoffIso && dropoffDateText) {
          dropoffDateText.innerText = displayFromIso(prevDropoffIso);
          dropoffDateText.setAttribute('data-date', prevDropoffIso);
        }

        // Re-mark the old date in the calendar
        if (prevDropoffIso && dropoffCalendarEl && window.RentCarDatePicker) {
          window.RentCarDatePicker.markDateInCalendar(dropoffCalendarEl, prevDropoffIso);
        }
      }
    });
  }

  // ── Search button ─────────────────────────────────────────────────────────
  const searchButton = document.getElementById("searchCarsButton");
  if (!searchButton) return;

  searchButton.addEventListener("click", function () {
    const pickupLocation  = pickupInput?.value.trim()  || DEFAULT_LOCATION;
    const dropoffLocation = dropoffInput?.value.trim() || DEFAULT_LOCATION;
    const selectedPickupDate  = pickupDateText?.getAttribute('data-date')  || isoFromDate(tomorrow);
    const selectedDropoffDate = dropoffDateText?.getAttribute('data-date') || isoFromDate(dayAfterTomorrow);
    const pickupHour  = pickupHourInput?.value  || "10:00";
    const dropoffHour = dropoffHourInput?.value || "10:00";

    const params = new URLSearchParams({
      pickupLocation,
      dropoffLocation,
      pickupDateTime:  selectedPickupDate  + 'T' + pickupHour,
      dropoffDateTime: selectedDropoffDate + 'T' + dropoffHour
    });

    const newUrl = "/cars.html?" + params.toString();
    const isCarsPage = window.location.pathname.endsWith("/cars.html");
    if (isCarsPage) {
      window.history.pushState({}, "", newUrl);
      fillHeaderBookingSummary();
      if (typeof loadCars === "function") loadCars();
      document.getElementById("carsSearchDrawer")?.classList.remove("is-active");
    } else {
      window.location.href = newUrl;
    }
  });
});

document.addEventListener("click", function (event) {
  if (event.target.closest("#editSearchButton")) {
    event.preventDefault();
    document.getElementById("carsSearchDrawer")?.classList.toggle("is-active");
  }
});