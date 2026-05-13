/**
 * RentCar Date Picker post-processor.
 *
 * Runs after Calendar.init() + calendarInteraction2() have set up all calendar
 * cells, via the 'rentcar:calendar-ready' custom event dispatched by main.js.
 *
 * Responsibilities:
 *   1. Disable past calendar cells (visual + pointer-events:none).
 *   2. Restore the visual selected state for already-chosen pickup/dropoff dates.
 */
(function () {

  window.addEventListener('rentcar:calendar-ready', function () {
    disablePastCells();
    markInitialDates();
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

    var monthNum  = parseInt(parts[1], 10);
    var dayNum    = parseInt(parts[2], 10);  // strips leading zero for text comparison
    var monthAbbr = ['jan','feb','mar','apr','may','jun','jul','aug','sep','oct','nov','dec'][monthNum - 1];
    if (!monthAbbr) return;

    var selector = '.elCalendar__sell:not(.-dark)[data-month="' + monthAbbr + '"]';
    var cells = wrapper.querySelectorAll(selector);
    var target = null;

    cells.forEach(function (cell) {
      var jsDate = cell.querySelector('.js-date');
      if (jsDate && parseInt(jsDate.textContent.trim(), 10) === dayNum) {
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
    markDateInCalendar: markDateInCalendar,
    disablePastCells:   disablePastCells,
    markInitialDates:   markInitialDates,
    isoFromDate:        isoFromDate
  };

})();
