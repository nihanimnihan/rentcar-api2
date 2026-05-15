/**
 * RentCar Date Picker post-processor.
 *
 * Runs after Calendar.init() + calendarInteraction2() have set up all calendar
 * cells, via the 'rentcar:calendar-ready' custom event dispatched by main.js.
 *
 * Responsibilities:
 *   1. Disable past calendar cells (visual + pointer-events:none).
 *   2. Restore the visual selected state for already-chosen pickup/dropoff dates.
 *   3. Apply locale to calendar month names and weekday headers.
 */
(function () {

  // ── Locale data ────────────────────────────────────────────────────────────
  var CAL_MONTHS_EN = ['january','february','march','april','may','june','july','august','september','october','november','december'];
  var CAL_MONTHS_ES = ['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
  var CAL_DAYS_EN   = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
  var CAL_DAYS_ES   = ['Dom','Lun','Mar','Mié','Jue','Vie','Sáb'];

  /**
   * Translate visible month name headers and weekday column headers.
   * Data attributes (data-month, data-week) are intentionally left as English
   * because buildIsoDate() and markDateInCalendar() rely on them for ISO logic.
   * The original English text is cached in data-en-* on the first call so
   * repeated EN↔ES switches are idempotent.
   */
  function applyCalendarLocale() {
    var lang = (typeof getLanguage === 'function') ? getLanguage() : 'en';
    var isES = lang === 'es';

    // Weekday column headers: "Sun" → "Dom"
    document.querySelectorAll('.elCalendar__header__sell').forEach(function (el) {
      if (!el.hasAttribute('data-en-day')) {
        el.setAttribute('data-en-day', el.textContent.trim());
      }
      var idx = CAL_DAYS_EN.indexOf(el.getAttribute('data-en-day'));
      if (idx !== -1) el.textContent = isES ? CAL_DAYS_ES[idx] : CAL_DAYS_EN[idx];
    });

    // Month name headers: "january 2026" → "enero 2026"
    document.querySelectorAll('.js-calendar-slider .swiper-slide > div:first-child').forEach(function (el) {
      if (!el.hasAttribute('data-en-month')) {
        el.setAttribute('data-en-month', el.textContent.trim().toLowerCase());
      }
      var original  = el.getAttribute('data-en-month'); // "january 2026"
      var spaceIdx  = original.indexOf(' ');
      if (spaceIdx === -1) return;
      var enMonth   = original.slice(0, spaceIdx);      // "january"
      var yearPart  = original.slice(spaceIdx);          // " 2026"
      var mIdx = CAL_MONTHS_EN.indexOf(enMonth);
      if (mIdx !== -1) {
        el.textContent = (isES ? CAL_MONTHS_ES[mIdx] : CAL_MONTHS_EN[mIdx]) + yearPart;
      }
    });
  }

  window.addEventListener('rentcar:calendar-ready', function () {
    disablePastCells();
    markInitialDates();
    applyCalendarLocale();
  });

  document.addEventListener('languageChanged', function () {
    applyCalendarLocale();
  });

  /** Mark all calendar cells before today as disabled. */
  function disablePastCells() {
    if (typeof window.buildCalendarIsoDate !== 'function') return;

    var todayIso = isoFromDate(new Date());

    document.querySelectorAll('.js-calendar-el .elCalendar__sell').forEach(function (cell) {
      if (!cell.querySelector('.js-date')) return;
      try {
        var cellIso = window.buildCalendarIsoDate(cell);
        if (cellIso < todayIso) {
          cell.classList.add('-dark');
          cell.style.pointerEvents = 'none';
          cell.style.opacity = '0.35';
          cell.style.cursor = 'not-allowed';
        }
      } catch (e) {
        // cell cannot be parsed (e.g. blank spacer) — ignore
      }
    });
  }

  /**
   * For each .js-calendar-el wrapper, read the data-date attribute from its
   * .js-first-date trigger element and mark the matching cell as -is-active.
   */
  function markInitialDates() {
    document.querySelectorAll('.js-calendar-el').forEach(function (wrapper) {
      var trigger = wrapper.querySelector('.js-first-date');
      if (!trigger) return;
      var isoDate = trigger.getAttribute('data-date');
      if (!isoDate) return;
      markDateInCalendar(wrapper, isoDate);
    });
  }

  /**
   * Find the non-dark cell matching the given ISO date inside a specific
   * calendar wrapper and mark it -is-active.
   *
   * @param {Element} wrapper  - a .js-calendar-el element
   * @param {string}  isoDate  - YYYY-MM-DD
   */
  function markDateInCalendar(wrapper, isoDate) {
    if (!isoDate || !wrapper) return;

    var parts = isoDate.split('-');
    if (parts.length !== 3) return;

    var yearNum   = parseInt(parts[0], 10);
    var monthNum  = parseInt(parts[1], 10);
    var dayNum    = parseInt(parts[2], 10);  // strips leading zero for text comparison
    var monthAbbr = ['jan','feb','mar','apr','may','jun','jul','aug','sep','oct','nov','dec'][monthNum - 1];
    if (!monthAbbr) return;

    var selector = '.elCalendar__sell:not(.-dark)[data-month="' + monthAbbr + '"]';
    var cells = wrapper.querySelectorAll(selector);
    var target = null;

    cells.forEach(function (cell) {
      var jsDate   = cell.querySelector('.js-date');
      var cellYear = parseInt(cell.getAttribute('data-year'), 10);
      if (jsDate && parseInt(jsDate.textContent.trim(), 10) === dayNum &&
          (isNaN(cellYear) || cellYear === yearNum)) {
        target = cell;
      }
    });

    if (target) {
      // Clear any stale selection in this wrapper first
      wrapper.querySelectorAll('.-is-active').forEach(function (el) {
        el.classList.remove('-is-active');
      });
      target.classList.add('-is-active');
    }
  }

  /** Format a Date object as YYYY-MM-DD. */
  function isoFromDate(date) {
    return date.getFullYear() + '-' +
      String(date.getMonth() + 1).padStart(2, '0') + '-' +
      String(date.getDate()).padStart(2, '0');
  }

  // Expose for use by main-search.js (dropoff auto-advance after pickup change)
  window.RentCarDatePicker = {
    markDateInCalendar:  markDateInCalendar,
    disablePastCells:    disablePastCells,
    markInitialDates:    markInitialDates,
    isoFromDate:         isoFromDate,
    applyCalendarLocale: applyCalendarLocale,
  };

})();
