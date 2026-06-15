// Lightweight Google Places Autocomplete helper for RentCar (frontend-only MVP)
// Usage: add a trigger element with class "js-places-trigger" and attributes:
//  data-target="<inputId>" — the input or hidden field to populate with formatted_address
//  data-display-target="<elId>" — optional: element to also show the chosen address (for index popup text)

(function(){
  const queue = [];
  let mapsLoaded = false;

  function loadScript(key) {
    if (!key) return;
    if (window.__rentcar_maps_loading) return;
    window.__rentcar_maps_loading = true;
    const s = document.createElement('script');
    s.src = 'https://maps.googleapis.com/maps/api/js?key=' + encodeURIComponent(key) + '&libraries=places&callback=__rentcar_init_maps&region=ES';
    s.async = true;
    s.defer = true;
    document.head.appendChild(s);
  }

  window.__rentcar_init_maps = function(){
    mapsLoaded = true;
    while(queue.length) {
      const fn = queue.shift();
      try{ fn(); }catch(e){ console.error('rentcar: places init', e); }
    }
  };

  function whenReady(fn){
    const key = (window.RC_CONFIG && window.RC_CONFIG.GOOGLE_MAPS_API_KEY) ? window.RC_CONFIG.GOOGLE_MAPS_API_KEY : '';
    if(!key){ console.warn('GOOGLE_MAPS_API_KEY not set in RC_CONFIG; Places autocomplete disabled'); return; }
    if(mapsLoaded) return fn();
    queue.push(fn);
    loadScript(key);
  }

  function createOverlayInput(triggerEl){
    // Create a floating input near triggerEl
    const rect = triggerEl.getBoundingClientRect();
    const container = document.createElement('div');
    container.className = 'rentcar-places-overlay';
    container.style.position = 'absolute';
    container.style.zIndex = 99999;
    container.style.left = (window.scrollX + rect.left) + 'px';
    container.style.top  = (window.scrollY + rect.bottom + 8) + 'px';
    container.innerHTML = `
      <div class="rentcar-places-inner">
        <input class="rentcar-places-input" type="text" placeholder="Search address" />
        <button class="rentcar-places-close" type="button">✕</button>
      </div>
    `;
    document.body.appendChild(container);
    const inp = container.querySelector('.rentcar-places-input');
    const close = container.querySelector('.rentcar-places-close');

    function remove(){ container.remove(); window.removeEventListener('keydown', onKey); }
    close.addEventListener('click', remove);
    function onKey(e){ if(e.key === 'Escape') remove(); }
    window.addEventListener('keydown', onKey);

    // focus
    setTimeout(()=> inp.focus(), 50);
    return {container, input: inp, remove};
  }

  function attachAutocompleteToInput(inpEl, onSelect){
    whenReady(() => {
      try{
        const options = { componentRestrictions: { country: 'es' }, types: ['geocode'] };
        const ac = new google.maps.places.Autocomplete(inpEl, options);
        ac.addListener('place_changed', function(){
          const place = ac.getPlace();
          if(!place) return;
          const formatted = place.formatted_address || (place.name || '');
          onSelect(formatted, place);
        });
      }catch(e){ console.error('rentcar: attachAutocompleteToInput', e); }
    });
  }

  function initTriggers(){
    document.querySelectorAll('.js-places-trigger').forEach(function(trigger){
      if(trigger.__rentcar_places_bound) return;
      trigger.__rentcar_places_bound = true;
      trigger.addEventListener('click', function(e){
        e.preventDefault(); e.stopPropagation();
        const targetId = trigger.getAttribute('data-target');
        const displayId = trigger.getAttribute('data-display-target');
        if(!targetId) return;
        const targetEl = document.getElementById(targetId);
        // Create overlay input
        const overlay = createOverlayInput(trigger);
        attachAutocompleteToInput(overlay.input, function(formatted, place){
          // set target value
          if(targetEl){
            targetEl.value = formatted;
            // dispatch events
            targetEl.dispatchEvent(new Event('input', { bubbles: true }));
            targetEl.dispatchEvent(new Event('change', { bubbles: true }));
          }
          // update display element (for index rcss text)
          if(displayId){
            const d = document.getElementById(displayId);
            if(d) d.textContent = formatted;
          }
          overlay.remove();
        });
      });
    });
  }

  // Auto-init on DOMContentLoaded
  document.addEventListener('DOMContentLoaded', function(){
    initTriggers();
    // also re-init on DOM mutations (in case components are injected)
    const mo = new MutationObserver(function(){ initTriggers(); });
    mo.observe(document.body, { childList: true, subtree: true });
  });

})();
