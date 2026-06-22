(function () {
  'use strict';

  var DEFAULT_LOCATIONS = [
    {
      type: 'airport',
      icon: '✈',
      name: 'BCN Airport T1',
      subtitle: 'El Prat Airport, Barcelona',
      address: 'Terminal 1, 08820 El Prat de Llobregat',
      hours: 'Open 24 hours'
    },
    {
      type: 'airport',
      icon: '✈',
      name: 'BCN Airport T2',
      subtitle: 'El Prat Airport, Barcelona',
      address: 'Terminal 2, 08820 El Prat de Llobregat',
      hours: 'Open 24 hours'
    },
    {
      type: 'office',
      icon: '⌂',
      name: 'RentCar Paradise Office',
      subtitle: 'Company office',
      address: 'Carrer de la Marina, 136, Barcelona',
      hours: 'Mon - Sun 09:00 - 20:00'
    }
  ];

  function initLocationPicker(options) {
    var btn = document.getElementById(options.buttonId);
    var popup = document.getElementById(options.popupId);
    if (!btn || !popup) return null;
    if (popup._rentcarLocationPicker) return popup._rentcarLocationPicker;

    var textId = options.textId;
    var hiddenId = options.hiddenId;
    var isPrimary = Boolean(options.isPrimary);
    var mirrorTextId = options.mirrorTextId;
    var mirrorHiddenId = options.mirrorHiddenId;
    var shouldMirror = typeof options.shouldMirror === 'function' ? options.shouldMirror : function () { return false; };
    var locations = (options.locations || DEFAULT_LOCATIONS).slice();
    var googlePredictions = [];

    function getHiddenEl() {
      return document.getElementById(hiddenId);
    }

    function getCurrentValue() {
      return (getHiddenEl() || {}).value || locations[0].name;
    }

    function matches(loc, term) {
      var q = (term || '').toLowerCase().trim();
      if (!q) return true;
      return (
        loc.name.toLowerCase().includes(q) ||
        loc.subtitle.toLowerCase().includes(q) ||
        loc.address.toLowerCase().includes(q)
      );
    }

    function setLocation(hiddenEl, textEl, loc) {
      var label = loc.name || loc.address || '';
      if (textEl) textEl.textContent = label;
      if (!hiddenEl) return;

      hiddenEl.value = label;
      hiddenEl.dataset.label = label;
      hiddenEl.dataset.address = loc.address || '';
      hiddenEl.dataset.placeId = loc.placeId || '';
      hiddenEl.dataset.lat = loc.lat || '';
      hiddenEl.dataset.lng = loc.lng || '';
      hiddenEl.dispatchEvent(new Event('input', { bubbles: true }));
      hiddenEl.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function selectLocation(loc) {
      setLocation(
        getHiddenEl(),
        document.getElementById(textId),
        loc
      );

      if (isPrimary && mirrorHiddenId && shouldMirror()) {
        setLocation(
          document.getElementById(mirrorHiddenId),
          document.getElementById(mirrorTextId),
          loc
        );
      }

      close();
    }

    function renderDetail(loc) {
      var detail = popup.querySelector('[data-location-detail]');
      if (!detail || !loc) return;

      detail.innerHTML = [
        '<div class="rc-location-detail__icon">' + escapeHtml(loc.icon) + '</div>',
        '<h3>' + escapeHtml(loc.name) + '</h3>',
        '<p>' + escapeHtml(loc.subtitle) + '</p>',
        '<div class="rc-location-detail__address">' + escapeHtml(loc.address) + '</div>',
        '<div class="rc-location-tags">',
        '<span>Barcelona service area</span>',
        '<span>' + escapeHtml(loc.hours) + '</span>',
        '</div>',
        '<div class="rc-location-hours">',
        '<div><strong>Opening hours</strong><span>' + escapeHtml(loc.hours) + '</span></div>',
        '</div>'
      ].join('');
    }

    function renderList(term) {
      var list = popup.querySelector('[data-location-list]');
      if (!list) return;

      var filtered = locations.filter(function (loc) {
        return matches(loc, term);
      });

      googlePredictions
        .filter(function (p) {
          return (p.description || '').toLowerCase().includes('barcelona');
        })
        .forEach(function (p) {
          filtered.push({
            type: 'google',
            icon: '⌖',
            name: (p.structured_formatting && p.structured_formatting.main_text) || p.description,
            subtitle: (p.structured_formatting && p.structured_formatting.secondary_text) || 'Barcelona',
            address: p.description,
            hours: 'Pickup by appointment',
            placeId: p.place_id
          });
        });

      var currentValue = getCurrentValue();
      var selectedIndex = filtered.findIndex(function (loc) {
        return loc.name === currentValue || loc.address === currentValue;
      });

      list.innerHTML = filtered.map(function (loc, index) {
        return [
          '<button type="button" class="rc-location-option ' + (selectedIndex === index ? 'is-selected' : '') + '" data-location-index="' + index + '">',
          '<span class="rc-location-option__icon">' + escapeHtml(loc.icon) + '</span>',
          '<span><strong>' + escapeHtml(loc.name) + '</strong><small>' + escapeHtml(loc.subtitle) + '</small></span>',
          '</button>'
        ].join('');
      }).join('');

      var detailLocation = selectedIndex >= 0 ? filtered[selectedIndex] : filtered[0];
      renderDetail(detailLocation);

      list.querySelectorAll('.rc-location-option').forEach(function (item) {
        item.addEventListener('mouseenter', function () {
          var loc = filtered[Number(item.getAttribute('data-location-index'))];
          renderDetail(loc);
        });

        item.addEventListener('click', function () {
          var loc = filtered[Number(item.getAttribute('data-location-index'))];
          if (loc) selectLocation(loc);
        });
      });
    }

    function buildPopup() {
      popup.classList.add('rc-location-picker');
      popup.innerHTML = [
        '<div class="rc-location-picker__left">',
        '<div class="rc-location-search">',
        '<span>⌕</span>',
        '<input type="text" placeholder="Airport, city or address" autocomplete="off">',
        '<button type="button" data-location-clear>×</button>',
        '</div>',
        '<div class="rc-location-section-title">Popular locations</div>',
        '<div data-location-list></div>',
        '<div class="rc-location-section-title rc-location-section-title--recent">Recent searches</div>',
        '</div>',
        '<div class="rc-location-picker__right" data-location-detail></div>'
      ].join('');

      var input = popup.querySelector('.rc-location-search input');
      var clear = popup.querySelector('[data-location-clear]');

      input.addEventListener('input', function () {
        fetchGooglePredictions(input.value);
      });

      clear.addEventListener('click', function () {
        input.value = '';
        input.focus();
        renderList('');
      });

      attachGooglePlaces();
      window.addEventListener('google-places-ready', attachGooglePlaces);
      renderList('');
    }

    function attachGooglePlaces() {
      var input = popup.querySelector('.rc-location-search input');
      if (!window.google || !google.maps || !google.maps.places || !input) return;
      if (input._rentcarGooglePlacesAttached) return;

      input._rentcarGooglePlacesAttached = true;

      var autocomplete = new google.maps.places.Autocomplete(input, {
        fields: ['formatted_address', 'geometry', 'name', 'place_id'],
        componentRestrictions: { country: 'es' },
        bounds: {
          north: 41.50,
          south: 41.30,
          east: 2.30,
          west: 2.05
        },
        strictBounds: true,
        types: ['address']
      });

      autocomplete.addListener('place_changed', function () {
        var place = autocomplete.getPlace();
        var address = place.formatted_address || place.name || input.value;
        selectLocation({
          type: 'google',
          icon: '⌖',
          name: address,
          subtitle: 'Barcelona',
          address: address,
          hours: 'Pickup by appointment',
          placeId: place.place_id || '',
          lat: place.geometry && place.geometry.location ? place.geometry.location.lat() : '',
          lng: place.geometry && place.geometry.location ? place.geometry.location.lng() : ''
        });
      });
    }

    function fetchGooglePredictions(term) {
      if (!term || term.trim().length < 3) {
        googlePredictions = [];
        renderList(term);
        return;
      }

      if (!window.google || !google.maps || !google.maps.places) {
        renderList(term);
        return;
      }

      var service = new google.maps.places.AutocompleteService();
      var sw = new google.maps.LatLng(41.30, 2.05);
      var ne = new google.maps.LatLng(41.50, 2.30);
      var bounds = new google.maps.LatLngBounds(sw, ne);

      service.getPlacePredictions({
        input: term,
        componentRestrictions: { country: 'es' },
        bounds: bounds,
        strictBounds: true,
        types: ['address']
      }, function (predictions, status) {
        if (status === google.maps.places.PlacesServiceStatus.OK && predictions) {
          googlePredictions = predictions.slice(0, 5);
        } else {
          googlePredictions = [];
        }

        renderList(term);
      });
    }

    function open() {
      var wasOpen = popup.style.display !== 'none' && popup.classList.contains('is-open');
      closeAllLocationPickers();
      if (wasOpen) return;

      renderList('');
      popup.classList.add('is-open');
      positionPopup(popup, btn);

      setTimeout(function () {
        var input = popup.querySelector('.rc-location-search input');
        if (input) input.focus();
      }, 0);
    }

    function close() {
      popup.classList.remove('is-open');
      popup.style.display = 'none';
    }

    function reposition() {
      if (!popup.classList.contains('is-open')) return;
      positionPopup(popup, btn);
    }

    buildPopup();

    btn.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      open();
    });

    popup.addEventListener('click', function (event) {
      event.stopPropagation();
    });

    document.addEventListener('click', close);
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, { passive: true });

    popup._rentcarLocationPicker = {
      open: open,
      close: close,
      reposition: reposition,
      selectLocation: selectLocation
    };

    return popup._rentcarLocationPicker;
  }

  function closeAllLocationPickers() {
    document.querySelectorAll('.rc-location-picker').forEach(function (picker) {
      picker.classList.remove('is-open');
      picker.style.display = 'none';
    });
  }

  function positionPopup(popup, anchorEl) {
    var rect = anchorEl.getBoundingClientRect();
    var isMobile = window.innerWidth <= 575;
    var width = isMobile ? window.innerWidth - 24 : Math.min(800, window.innerWidth - 32);
    var left = isMobile ? 12 : Math.max(16, Math.min(rect.left, window.innerWidth - width - 16));
    var top = rect.bottom + 8;

    if (popup.parentNode !== document.body) {
      document.body.appendChild(popup);
    }

    popup.style.position = 'fixed';
    popup.style.display = 'block';
    popup.style.width = width + 'px';
    popup.style.maxWidth = 'calc(100vw - 24px)';
    popup.style.left = left + 'px';
    popup.style.top = top + 'px';

    var height = popup.offsetHeight || 0;
    if (top + height > window.innerHeight - 8) {
      top = Math.max(8, window.innerHeight - height - 8);
      popup.style.top = top + 'px';
    }
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  window.RentcarLocationPicker = {
    init: initLocationPicker,
    closeAll: closeAllLocationPickers,
    locations: DEFAULT_LOCATIONS
  };
})();
