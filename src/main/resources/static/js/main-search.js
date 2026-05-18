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
    const locale = (typeof getLanguage === 'function' && getLanguage() === 'es') ? 'es-ES' : 'en-GB';
    return date.toLocaleDateString(locale, { weekday: 'short', day: 'numeric', month: 'short' });
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
// Re-format displayed date labels when language changes
document.addEventListener("languageChanged", function () {
  const pickupDateText  = document.getElementById("pickupDateText");
  const dropoffDateText = document.getElementById("dropoffDateText");

  function redisplayDate(el) {
    if (!el) return;
    const iso = el.getAttribute("data-date");
    if (!iso) return;
    const date = new Date(iso + "T00:00:00");
    if (isNaN(date.getTime())) return;
    const locale = (typeof getLanguage === "function" && getLanguage() === "es") ? "es-ES" : "en-GB";
    el.innerText = date.toLocaleDateString(locale, { weekday: "short", day: "numeric", month: "short" });
  }

  redisplayDate(pickupDateText);
  redisplayDate(dropoffDateText);
});

// ─── Premium Search Panel UI (index.html rcss-* elements) ────────────────────
(function () {
  'use strict';

  // Build time slots every 30 min (00:00–23:30)
  var RCSS_SLOTS = [];
  for (var _h = 0; _h < 24; _h++) {
    for (var _m = 0; _m < 60; _m += 30) {
      RCSS_SLOTS.push(
        String(_h).padStart(2, '0') + ':' + String(_m).padStart(2, '0')
      );
    }
  }

  function rcssCloseAll() {
    document.querySelectorAll('.rcss-popup').forEach(function (p) {
      p.style.display = 'none';
    });
  }

  /**
   * Open popup at viewport-fixed position below anchorEl.
   * alignRight: align right edge of popup to right edge of anchor.
   */
  function rcssOpenBelow(popup, anchorEl, alignRight) {
    var rect = anchorEl.getBoundingClientRect();
    popup.style.top = (rect.bottom + 10) + 'px';
    if (alignRight) {
      popup.style.left  = 'auto';
      popup.style.right = Math.max(0, window.innerWidth - rect.right) + 'px';
    } else {
      popup.style.left  = rect.left + 'px';
      popup.style.right = 'auto';
    }
    popup.style.display = 'block';
  }

  // Location dropdown
  function rcssInitLocDropdown(btnId, popupId, textId, hiddenId, isPrimary) {
    var btn   = document.getElementById(btnId);
    var popup = document.getElementById(popupId);
    if (!btn || !popup) return;

    // Mark initially-selected item
    var initLoc = (document.getElementById(hiddenId) || {}).value || 'BCN Airport T1';
    popup.querySelectorAll('.rcss-loc-item').forEach(function (el) {
      if (el.getAttribute('data-loc') === initLoc) el.classList.add('is-selected');
    });

    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var wasOpen = popup.style.display !== 'none';
      rcssCloseAll();
      if (!wasOpen) rcssOpenBelow(popup, btn, false);
    });
    popup.addEventListener('click', function (e) { e.stopPropagation(); });

    popup.querySelectorAll('.rcss-loc-item').forEach(function (item) {
      item.addEventListener('click', function () {
        var loc      = item.getAttribute('data-loc');
        var textEl   = document.getElementById(textId);
        var hiddenEl = document.getElementById(hiddenId);
        if (textEl)   textEl.textContent = loc;
        if (hiddenEl) hiddenEl.value     = loc;

        // Keep dropoff in sync with pickup when "different return" is unchecked
        if (isPrimary) {
          var cb = document.getElementById('rcssDiffReturn');
          if (cb && !cb.checked) {
            var dt = document.getElementById('rcssDropoffLocText');
            var dh = document.getElementById('dropoffLocation');
            if (dt) dt.textContent = loc;
            if (dh) dh.value = loc;
          }
        }

        popup.querySelectorAll('.rcss-loc-item').forEach(function (el) {
          el.classList.remove('is-selected');
        });
        item.classList.add('is-selected');
        popup.style.display = 'none';
      });
    });
  }

  // "Return to different location" checkbox
  function rcssInitDiffReturn() {
    var cb          = document.getElementById('rcssDiffReturn');
    var dropoffCell = document.getElementById('rcssDropoffLocCell');
    if (!cb || !dropoffCell) return;

    cb.addEventListener('change', function () {
      if (cb.checked) {
        dropoffCell.classList.remove('rcss-cell-loc--synced');
      } else {
        dropoffCell.classList.add('rcss-cell-loc--synced');
        var pickupLoc = (document.getElementById('pickupLocation') || {}).value || 'BCN Airport T1';
        var dt = document.getElementById('rcssDropoffLocText');
        var dh = document.getElementById('dropoffLocation');
        if (dt) dt.textContent = pickupLoc;
        if (dh) dh.value = pickupLoc;
        var dPopup = document.getElementById('rcssDropoffLocPopup');
        if (dPopup) {
          dPopup.querySelectorAll('.rcss-loc-item').forEach(function (el) {
            el.classList.toggle('is-selected', el.getAttribute('data-loc') === pickupLoc);
          });
        }
      }
    });
  }

  // Calendar popup
  function rcssInitCalendarPopup(dateBtnId, calPopupId, calWrapId, dateFieldId) {
    var btn     = document.getElementById(dateBtnId);
    var popup   = document.getElementById(calPopupId);
    var calWrap = document.getElementById(calWrapId);
    if (!btn || !popup) return;

    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var wasOpen = popup.style.display !== 'none';
      rcssCloseAll();
      if (!wasOpen) {
        // Sync calendar to the field's current date every time it opens.
        // Guards against the popup being moved to <body> before initAll() ran.
        var dateEl = dateFieldId ? document.getElementById(dateFieldId) : null;
        var iso    = dateEl && dateEl.getAttribute('data-date');
        if (iso) {
          var host = popup.querySelector('.rc-calendar-host');
          if (host && host._rcCalInstance) {
            host._rcCalInstance.setDate(iso);
          }
        }
        rcssOpenBelow(popup, btn, false);
      }
    });
    popup.addEventListener('click', function (e) { e.stopPropagation(); });

    if (calWrap) {
      calWrap.addEventListener('rentcar:date-selected', function () {
        popup.style.display = 'none';
      });
    }
  }

  // Time popup
  function rcssInitTimePopup(timeBtnId, popupId, listId, hiddenId, displayId, defaultTime) {
    var btn  = document.getElementById(timeBtnId);
    var popup = document.getElementById(popupId);
    var list  = document.getElementById(listId);
    if (!btn || !popup || !list) return;

    var currentTime = (document.getElementById(hiddenId) || {}).value || defaultTime;

    RCSS_SLOTS.forEach(function (slot) {
      var item = document.createElement('div');
      item.className = 'rcss-time-item';
      if (slot === currentTime) item.classList.add('is-selected');
      item.textContent = slot;
      item.addEventListener('click', function () {
        list.querySelectorAll('.rcss-time-item').forEach(function (el) {
          el.classList.remove('is-selected');
        });
        item.classList.add('is-selected');
        var displayEl = document.getElementById(displayId);
        if (displayEl) displayEl.textContent = slot;
        var hiddenEl  = document.getElementById(hiddenId);
        if (hiddenEl) hiddenEl.value = slot;
        popup.style.display = 'none';
      });
      list.appendChild(item);
    });

    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var wasOpen = popup.style.display !== 'none';
      rcssCloseAll();
      if (!wasOpen) {
        rcssOpenBelow(popup, btn, true);
        setTimeout(function () {
          var sel = list.querySelector('.rcss-time-item.is-selected');
          if (sel) sel.scrollIntoView({ block: 'center' });
        }, 0);
      }
    });
    popup.addEventListener('click', function (e) { e.stopPropagation(); });

    // Trap wheel scroll inside popup so the page doesn't scroll
    popup.addEventListener('wheel', function (e) {
      var atTop    = popup.scrollTop === 0 && e.deltaY < 0;
      var atBottom = popup.scrollTop + popup.clientHeight >= popup.scrollHeight && e.deltaY > 0;
      if (!atTop && !atBottom) {
        e.stopPropagation();
      } else {
        e.preventDefault();
      }
    }, { passive: false });
  }

  // Click anywhere outside → close all popups
  document.addEventListener('click', function () { rcssCloseAll(); });
  // Close on resize so fixed-position popups don't misalign
  window.addEventListener('resize', function () { rcssCloseAll(); });
  // Close floating popups when the page scrolls (they're fixed-position and would detach)
  window.addEventListener('scroll', function () { rcssCloseAll(); }, { passive: true });

  document.addEventListener('DOMContentLoaded', function () {
    // ── Store wrapperEl on each calendar host BEFORE moving popups to <body> ──
    // After appendChild, host.closest('.js-calendar-el') returns null because
    // the element is no longer a descendant. rentcar-calendar.js initAll() reads
    // host._rcCalWrapperEl as a fallback so it can still find the wrapper, set
    // initialDate correctly, and fire rentcar:date-selected on the right element.
    document.querySelectorAll('.rcss-popup .rc-calendar-host').forEach(function (host) {
      var wrapper = host.closest ? host.closest('.js-calendar-el') : null;
      if (wrapper) host._rcCalWrapperEl = wrapper;
    });

    // ── Move all rcss-popup elements to <body> ───────────────────────────────
    // This escapes the parent's animation stacking context (transform + opacity)
    // so popups render fully opaque against the viewport, not the hero section.
    document.querySelectorAll('.rcss-popup').forEach(function (p) {
      document.body.appendChild(p);
    });

    rcssInitLocDropdown('rcssPickupLocBtn',  'rcssPickupLocPopup',  'rcssPickupLocText',  'pickupLocation',  true);
    rcssInitLocDropdown('rcssDropoffLocBtn', 'rcssDropoffLocPopup', 'rcssDropoffLocText', 'dropoffLocation', false);
    rcssInitDiffReturn();
    rcssInitCalendarPopup('rcssPickupDateBtn',  'rcssPickupCalPopup',  'rcssPickupDateCell',  'pickupDateText');
    rcssInitCalendarPopup('rcssDropoffDateBtn', 'rcssDropoffCalPopup', 'rcssDropoffDateCell', 'dropoffDateText');
    rcssInitTimePopup('rcssPickupTimeBtn',  'rcssPickupTimePopup',  'rcssPickupTimeList',  'pickupHour',  'rcssPickupTimeDisplay',  '10:00');
    rcssInitTimePopup('rcssDropoffTimeBtn', 'rcssDropoffTimePopup', 'rcssDropoffTimeList', 'dropoffHour', 'rcssDropoffTimeDisplay', '10:00');
  });
})();
