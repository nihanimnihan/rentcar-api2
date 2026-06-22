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

  function formatShortDisplayDate(date, locale) {
    return date.toLocaleDateString(locale, {
      day: '2-digit',
      month: 'short'
    }).replace(/\./g, '')
      .replace(/\s+/g, ' ')
      .trim()
      .split(' ')
      .map(function (part) {
        if (!part || /\d/.test(part)) return part;
        return part.charAt(0).toLocaleUpperCase(locale) + part.slice(1).toLocaleLowerCase(locale);
      })
      .join(' ');
  }

  function displayFromIso(isoDate) {
    const date = new Date(isoDate + 'T00:00:00');
    if (isNaN(date.getTime())) return isoDate;

    const lang = (typeof getLanguage === 'function') ? getLanguage() : 'en';

    const locale =
      lang === 'tr' ? 'tr-TR' :
      lang === 'es' ? 'es-ES' :
      'en-GB';

    return formatShortDisplayDate(date, locale);
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
      pickupDateTime: selectedPickupDate + 'T' + pickupHour,
      dropoffDateTime: selectedDropoffDate + 'T' + dropoffHour,

      pickupAddress: pickupInput?.dataset.address || "",
      pickupPlaceId: pickupInput?.dataset.placeId || "",
      dropoffAddress: dropoffInput?.dataset.address || "",
      dropoffPlaceId: dropoffInput?.dataset.placeId || ""
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

    const lang = (typeof getLanguage === "function") ? getLanguage() : "en";
    const locale =
      lang === "tr" ? "tr-TR" :
      lang === "es" ? "es-ES" :
      "en-GB";

    el.innerText = formatShortDisplayDate(date, locale);
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
  rcssActivePopup = null;
  rcssActiveAnchor = null;
  rcssActiveAlignRight = false;
  document.querySelectorAll('.rcss-popup').forEach(function (p) {
    p.classList.remove('is-open');
    p.style.display = 'none';
  });
  if (window.RentcarLocationPicker) window.RentcarLocationPicker.closeAll();
}

function rcssNudgeHomeSearchIntoView(anchorEl) {
  if (!anchorEl || window.innerWidth <= 575) return;

  var panel = anchorEl.closest ? anchorEl.closest('.mainSearch') : null;
  if (!panel || !panel.closest || !panel.closest('.rentcar-home-hero')) return;

  var rect = panel.getBoundingClientRect();
  var targetTop = 150;

  if (rect.top > targetTop + 24) {
    window.scrollBy({
      top: rect.top - targetTop,
      behavior: 'smooth'
    });
  }
}

  /**
   * Open popup at viewport-fixed position below anchorEl.
   * alignRight: align right edge of popup to right edge of anchor.
   */
function rcssOpenBelow(popup, anchorEl, alignRight) {
  rcssActivePopup = popup;
  rcssActiveAnchor = anchorEl;
  rcssActiveAlignRight = alignRight;
  var rect = anchorEl.getBoundingClientRect();

  var isCalendarPopup =
    popup.id === 'rcssPickupCalPopup' ||
    popup.id === 'rcssDropoffCalPopup';


  // Treat location pickers as viewport-fixed popups appended to <body> so
  // they are not clipped by parent overflow and can render full two-column layout.
  var isLocationPicker = popup.classList.contains('rc-location-picker') || popup.id === 'rcssPickupLocPopup' || popup.id === 'rcssDropoffLocPopup';

  if (isLocationPicker) {
    popup.style.position = 'fixed';
    popup.style.display = 'block';
    popup.classList.add('is-open');

    // Preferred width around 900px but constrained by viewport
    var preferredWidth = 900;
    var available = Math.max(300, window.innerWidth - 40);
    var width = Math.min(preferredWidth, available);
    popup.style.width = width + 'px';
    popup.style.maxWidth = (window.innerWidth - 16) + 'px';

    var searchPanel = document.querySelector('.rentcar-sixt-search')
      || document.querySelector('.rentcar-cars-search')
      || document.querySelector('.rentcar-home-hero .mainSearch');

    var panelRect = searchPanel ? searchPanel.getBoundingClientRect() : rect;
    var anchorCell = anchorEl.closest ? (anchorEl.closest('.rcss-cell') || anchorEl) : anchorEl;
    var anchorCellRect = anchorCell.getBoundingClientRect ? anchorCell.getBoundingClientRect() : rect;

    var isMobileSearch = window.innerWidth <= 575;
    var top = isMobileSearch ? rect.bottom + 8 : anchorCellRect.bottom + 8;
    var popupWidth = isMobileSearch
      ? Math.min(window.innerWidth - 32, panelRect.width)
      : Math.min(800, Math.max(430, panelRect.right - anchorCellRect.left));
    var left = isMobileSearch
      ? Math.max(16, panelRect.left)
      : Math.max(16, Math.min(anchorCellRect.left, window.innerWidth - popupWidth - 16));

    popup.style.left = left + 'px';
    popup.style.top = top + 'px';
    popup.style.width = popupWidth + 'px';


    // Ensure popup is attached to <body> so it's not clipped by parents
    if (popup.parentNode !== document.body) {
      try { document.body.appendChild(popup); } catch (e) { /* ignore */ }
    }

    // Trap wheel scroll inside popup so the page doesn't scroll; keep popup open on window scroll
    popup.addEventListener('wheel', function () {
      // Allow page scroll while mouse is over location picker.
    }, { passive: true });

    return;
  }

  if (isCalendarPopup) {
    popup.style.position = 'fixed';
    popup.style.display = 'block';
    popup.classList.add('is-open');

    var searchPanel = document.querySelector('.rentcar-sixt-search')
      || document.querySelector('.rentcar-cars-search')
      || document.querySelector('.rentcar-home-hero .mainSearch');

    var panelRect = searchPanel ? searchPanel.getBoundingClientRect() : rect;

    var width = Math.min(680, window.innerWidth - 32);
    var left = panelRect.left + (panelRect.width - width) / 2;
    var top = panelRect.bottom + 8;

    popup.style.width = width + 'px';
    popup.style.maxWidth = 'calc(100vw - 32px)';
    popup.style.left = Math.max(16, left) + 'px';
    popup.style.top = top + 'px';

    if (popup.parentNode !== document.body) {
      document.body.appendChild(popup);
    }

    return;
  }

  var isTimePopup =
    popup.id === 'rcssPickupTimePopup' ||
    popup.id === 'rcssDropoffTimePopup';

  if (isTimePopup) {
    popup.style.position = 'fixed';
    popup.style.display = 'block';
    popup.classList.add('is-open');

    var timeRect = anchorEl.getBoundingClientRect();

    popup.style.width = '150px';
    popup.style.maxWidth = '150px';
    popup.style.left = timeRect.left + 'px';
    popup.style.top = (timeRect.bottom + 8) + 'px';

    if (popup.parentNode !== document.body) {
      document.body.appendChild(popup);
    }

    return;
  }

  // Default behaviour for non-location popups (calendar/time)
  popup.style.position = 'fixed';
  popup.style.display = 'block';
  popup.classList.add('is-open');

  // Preferred width but constrained by viewport
  var preferredWidth = 980;
  var available = Math.max(200, window.innerWidth - 40);
  var width = Math.min(preferredWidth, available);
  popup.style.width = width + 'px';
  popup.style.maxWidth = (window.innerWidth - 16) + 'px';

  var left;
  if (alignRight) {
    left = rect.right - width;
  } else {
    left = rect.left;
  }
  left = Math.max(8, Math.min(left, window.innerWidth - width - 8));

  var top = rect.bottom + 12;
  // Place temporarily to measure height
  popup.style.left = left + 'px';
  popup.style.top = top + 'px';

  // If overflowing bottom, try place above
  var popupH = popup.offsetHeight || 0;
  if (top + popupH > window.innerHeight - 8) {
    var altTop = rect.top - popupH - 12;
    if (altTop >= 8) {
      top = altTop;
    } else {
      top = Math.max(8, window.innerHeight - popupH - 8);
    }
    popup.style.top = top + 'px';
  }

  // Ensure popup captures internal scrolls and clicks (do not close)
  popup.addEventListener('wheel', function(e){ e.stopPropagation(); }, { passive: true });
}


  // Location dropdown
function rcssInitLocDropdown(btnId, popupId, textId, hiddenId, isPrimary) {
  if (!window.RentcarLocationPicker) return;
  window.RentcarLocationPicker.init({
    buttonId: btnId,
    popupId: popupId,
    textId: textId,
    hiddenId: hiddenId,
    isPrimary: isPrimary,
    mirrorTextId: 'rcssDropoffLocText',
    mirrorHiddenId: 'dropoffLocation',
    shouldMirror: function () {
      var cb = document.getElementById('rcssDiffReturn');
      return Boolean(cb && !cb.checked);
    }
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
        rcssNudgeHomeSearchIntoView(btn);
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
        rcssNudgeHomeSearchIntoView(btn);
        rcssOpenBelow(popup, btn, true);
        setTimeout(function () {
          var sel = list.querySelector('.rcss-time-item.is-selected');
          if (sel) sel.scrollIntoView({ block: 'center' });
        }, 0);
      }
    });
    popup.addEventListener('click', function (e) { e.stopPropagation(); });

    // Trap wheel scroll inside popup so the page doesn't scroll
    popup.addEventListener('wheel', function () {
      // Allow page scroll while mouse is over location picker.
    }, { passive: true });
  }

  // Click anywhere outside → close all popups
  document.addEventListener('click', function () { rcssCloseAll(); });
  // Close on resize so fixed-position popups don't misalign
    // Reposition active popup on window scroll/resize to keep it anchored to its trigger.

    let rcssActivePopup = null;
    let rcssActiveAnchor = null;
    let rcssActiveAlignRight = false;

    function rcssRepositionActivePopup() {
      if (!rcssActivePopup || !rcssActiveAnchor) return;
      if (rcssActivePopup.style.display === 'none') return;
      rcssOpenBelow(rcssActivePopup, rcssActiveAnchor, rcssActiveAlignRight);
    }

    window.addEventListener('scroll', function () {
      rcssRepositionActivePopup();
    }, { passive: true });

    window.addEventListener('resize', function () {
      rcssRepositionActivePopup();
    });

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


    rcssInitLocDropdown('rcssPickupLocBtn',  'rcssPickupLocPopup',  'rcssPickupLocText',  'pickupLocation',  true);
    rcssInitLocDropdown('rcssDropoffLocBtn', 'rcssDropoffLocPopup', 'rcssDropoffLocText', 'dropoffLocation', false);
    rcssInitDiffReturn();
    rcssInitCalendarPopup('rcssPickupDateBtn',  'rcssPickupCalPopup',  'rcssPickupDateCell',  'pickupDateText');
    rcssInitCalendarPopup('rcssDropoffDateBtn', 'rcssDropoffCalPopup', 'rcssDropoffDateCell', 'dropoffDateText');
    rcssInitTimePopup('rcssPickupTimeBtn',  'rcssPickupTimePopup',  'rcssPickupTimeList',  'pickupHour',  'rcssPickupTimeDisplay',  '10:00');
    rcssInitTimePopup('rcssDropoffTimeBtn', 'rcssDropoffTimePopup', 'rcssDropoffTimeList', 'dropoffHour', 'rcssDropoffTimeDisplay', '10:00');
  });
})();

document.addEventListener("click", function (event) {
  const editButton = event.target.closest("#editSearchButton");
  if (!editButton) return;

  event.preventDefault();
  event.stopPropagation();

  const drawer = document.getElementById("carsSearchDrawer");
  if (!drawer) return;

  drawer.classList.toggle("is-active");

  // Mobile hamburger menüsü açıksa kapat
  document.querySelector(".js-header")?.classList.remove("is-menu-opened");
  document.documentElement.classList.remove("html-overflow-hidden");
});
