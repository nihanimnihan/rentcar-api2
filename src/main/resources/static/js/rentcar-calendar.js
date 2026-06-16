/**
 * RentCar shared calendar component.
 *
 * Replaces the Swiper-based Calendar.init() + calendarInteraction2() system
 * from main.js and the date-picker.js post-processor.
 *
 * Usage (auto, on page load via main.js):
 *   RentCarCalendar.initAll()
 *   — mounts a calendar into every .rc-calendar-host element found on the page.
 *
 * Usage (manual, e.g. inside a hidden panel):
 *   var instance = RentCarCalendar.mount(hostEl, { initialDate: 'YYYY-MM-DD' });
 *   instance.setDate('2026-06-15');
 *   instance.getDate(); // 'YYYY-MM-DD'
 *
 * Events fired (on the .js-calendar-el ancestor, bubbles:true):
 *   CustomEvent('rentcar:date-selected', { detail: { isoDate: 'YYYY-MM-DD' } })
 *
 * Window event fired once all calendars are initialised:
 *   CustomEvent('rentcar:calendar-ready')
 *
 * Backward-compat alias:
 *   window.RentCarDatePicker  ← same object as window.RentCarCalendar
 */
(function () {
  'use strict';

  // ── i18n data ────────────────────────────────────────────────────────────
  var MONTHS = {
    en: ['January','February','March','April','May','June',
         'July','August','September','October','November','December'],
    es: ['Enero','Febrero','Marzo','Abril','Mayo','Junio',
         'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'],
    tr: ['Ocak','Şubat','Mart','Nisan','Mayıs','Haziran',
         'Temmuz','Ağustos','Eylül','Ekim','Kasım','Aralık'],
  };

  var DAYS = {
    en: ['Su','Mo','Tu','We','Th','Fr','Sa'],
    es: ['Do','Lu','Ma','Mi','Ju','Vi','Sá'],
    tr: ['Pz','Pt','Sa','Ça','Pe','Cu','Ct'],
  };

  // ── Helpers ───────────────────────────────────────────────────────────────
  function getLang() {
    return (typeof getLanguage === 'function') ? getLanguage() : 'en';
  }

  function padZ(n) { return String(n).padStart(2, '0'); }

  function isoFromDate(d) {
    return d.getFullYear() + '-' + padZ(d.getMonth() + 1) + '-' + padZ(d.getDate());
  }

  function displayFromIso(iso) {
    var d = new Date(iso + 'T00:00:00');
    if (isNaN(d.getTime())) return iso;
    var lang = getLang();
    try {
      var locale =
        lang === 'tr' ? 'tr-TR' :
        lang === 'es' ? 'es-ES' :
        'en-GB';

      return d.toLocaleDateString(locale, {
        month: 'short',
        day: '2-digit'
      });
    } catch (e) {
      return iso;
    }
  }

  // Registry of all mounted instances for language-change re-render.
  var allInstances = [];

  // ── mount ─────────────────────────────────────────────────────────────────
  /**
   * Mount a calendar into hostEl and return an instance handle.
   *
   * opts:
   *   initialDate   string   YYYY-MM-DD  (default: today)
   *   wrapperEl     Element  .js-calendar-el ancestor — receives
   *                          rentcar:date-selected events and has
   *                          .js-first-date updated. Auto-detected if omitted.
   *   onDateSelected  function(iso)  optional callback
   */
  function mount(hostEl, opts) {
    if (!hostEl) return null;
    opts = opts || {};

    var selectedDate = opts.initialDate || isoFromDate(new Date());
    var wrapperEl    = opts.wrapperEl
                    || (hostEl.closest ? hostEl.closest('.js-calendar-el') : null)
                    || null;

    // calYear / calMonth track which month is currently *displayed* (0-based month).
    var calYear, calMonth;
    _setDisplayMonthFromIso(selectedDate);

    function _setDisplayMonthFromIso(iso) {
      var p = iso ? iso.split('-') : [];
      if (p.length === 3) {
        calYear  = parseInt(p[0], 10);
        calMonth = parseInt(p[1], 10) - 1;
      } else {
        var now = new Date();
        calYear  = now.getFullYear();
        calMonth = now.getMonth();
      }
    }

    function render() {
      var lang   = getLang();
      var months = MONTHS[lang] || MONTHS.en;
      var days   = DAYS[lang]   || DAYS.en;

      var today = new Date();
      today.setHours(0, 0, 0, 0);
      var todayMs = today.getTime();

      var prevDisabled = (calYear < today.getFullYear()) ||
                         (calYear === today.getFullYear() && calMonth <= today.getMonth());

      var firstDow  = new Date(calYear, calMonth, 1).getDay();
      var daysInMon = new Date(calYear, calMonth + 1, 0).getDate();

      var html = '<div class="rc-cal-header">' +
        '<button class="rc-cal-nav rc-cal-prev" type="button"' +
          (prevDisabled ? ' disabled' : '') + '>&#8249;</button>' +
        '<span class="rc-cal-title">' + months[calMonth] + ' ' + calYear + '</span>' +
        '<button class="rc-cal-nav rc-cal-next" type="button">&#8250;</button>' +
        '</div>';

      html += '<div class="rc-cal-weekdays">';
      days.forEach(function (d) { html += '<div class="rc-cal-wd">' + d + '</div>'; });
      html += '</div>';

      html += '<div class="rc-cal-days">';
      for (var e = 0; e < firstDow; e++) {
        html += '<div class="rc-cal-day rc-cal-empty"></div>';
      }
      for (var day = 1; day <= daysInMon; day++) {
        var iso = calYear + '-' + padZ(calMonth + 1) + '-' + padZ(day);
        var ms  = new Date(calYear, calMonth, day).getTime();
        var cls = 'rc-cal-day';
        if (ms < todayMs)       cls += ' rc-cal-disabled';
        if (iso === selectedDate) cls += ' rc-cal-active';
        html += '<div class="' + cls + '" data-date="' + iso + '">' + day + '</div>';
      }
      html += '</div>';

      hostEl.innerHTML = html;
      _wireEvents();
    }

    function _wireEvents() {
      // Month navigation
      var prevBtn = hostEl.querySelector('.rc-cal-prev');
      var nextBtn = hostEl.querySelector('.rc-cal-next');

      if (prevBtn) {
        prevBtn.addEventListener('click', function (e) {
          e.stopPropagation();
          calMonth--;
          if (calMonth < 0) { calMonth = 11; calYear--; }
          render();
        });
      }
      if (nextBtn) {
        nextBtn.addEventListener('click', function (e) {
          e.stopPropagation();
          calMonth++;
          if (calMonth > 11) { calMonth = 0; calYear++; }
          render();
        });
      }

      // Day selection
      hostEl.querySelectorAll('.rc-cal-day:not(.rc-cal-disabled):not(.rc-cal-empty)').forEach(function (cell) {
        cell.addEventListener('click', function (e) {
          e.stopPropagation();
          var iso = this.getAttribute('data-date');
          selectedDate = iso;

          // Update .js-first-date inside the wrapper (for main-search.js compatibility)
          if (wrapperEl) {
            var firstDateEl = wrapperEl.querySelector('.js-first-date');
            if (firstDateEl) {
              firstDateEl.setAttribute('data-date', iso);
              firstDateEl.innerText = displayFromIso(iso);
            }
            // Close dropdown popup if open
            var popup = wrapperEl.querySelector('.searchMenu-date__field');
            if (popup) popup.classList.remove('-is-active');
            wrapperEl.classList.remove('-is-dd-wrap-active');

            // Fire event from wrapper (bubbles for main-search.js / airport-transfer-offers.js)
            wrapperEl.dispatchEvent(new CustomEvent('rentcar:date-selected', {
              detail: { isoDate: iso },
              bubbles: true,
            }));
          }

          if (typeof opts.onDateSelected === 'function') {
            opts.onDateSelected(iso);
          }

          render(); // re-render to move active highlight
        });
      });
    }

    render();

    var instance = {
      /** Select a date and navigate to its month. */
      setDate: function (iso) {
        if (!iso) return;
        selectedDate = iso;
        _setDisplayMonthFromIso(iso);
        render();
      },
      getDate:  function () { return selectedDate; },
      rerender: function () { render(); },
    };

    // Store on the host element for external access
    hostEl._rcCalInstance = instance;
    allInstances.push(instance);
    return instance;
  }

  // ── initAll ───────────────────────────────────────────────────────────────
  /**
   * Auto-mount all .rc-calendar-host elements found on the page.
   * Reads initialDate from the nearest .js-first-date[data-date].
   * Fires 'rentcar:calendar-ready' on window when done (same contract
   * as the old calendarInteraction2()).
   */
  function initAll() {
    document.querySelectorAll('.rc-calendar-host').forEach(function (host) {
      if (host._rcCalInstance) return; // already mounted
      // _rcCalWrapperEl is set by main-search.js before the popup is moved to
      // <body> (where closest() can no longer find the .js-calendar-el wrapper).
      var wrapper     = host._rcCalWrapperEl
                     || (host.closest ? host.closest('.js-calendar-el') : null);
      var firstDateEl = wrapper ? wrapper.querySelector('.js-first-date') : null;
      var initial     = firstDateEl ? (firstDateEl.getAttribute('data-date') || null) : null;
      mount(host, { initialDate: initial, wrapperEl: wrapper });
    });

    window.dispatchEvent(new CustomEvent('rentcar:calendar-ready'));
  }

  // ── markDate ──────────────────────────────────────────────────────────────
  /**
   * Find the mounted calendar instance inside wrapperEl and update its date.
   * API-compatible with the old RentCarDatePicker.markDateInCalendar().
   */
  function markDate(wrapperEl, iso) {
    if (!wrapperEl || !iso) return;
    var host = wrapperEl.querySelector('.rc-calendar-host');
    if (host && host._rcCalInstance) {
      host._rcCalInstance.setDate(iso);
    }
  }

  // ── Language change — re-render all instances ─────────────────────────────
  document.addEventListener('languageChanged', function () {
    allInstances.forEach(function (inst) { inst.rerender(); });
  });

  // ── Public API ────────────────────────────────────────────────────────────
  var api = {
    mount:   mount,
    initAll: initAll,
    markDate: markDate,

    // Backward-compat names used by main-search.js and old date-picker.js consumers
    markDateInCalendar: markDate,
    disablePastCells:   function () { /* built into render — no-op */ },
    isoFromDate:        isoFromDate,
  };

  window.RentCarCalendar = api;
  window.RentCarDatePicker = api; // backward compat alias

}());
