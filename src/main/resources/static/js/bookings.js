(function(){
  async function fetchBookings(){
    try{
      const res = await fetch('/api/auth/bookings', { credentials: 'same-origin' });
      if(!res.ok) return null;
      return await res.json();
    }catch(e){
      console.error('Failed to load bookings', e);
      return null;
    }
  }

  function formatDateTime(dtStr){
    if(!dtStr) return '';
    try{
      const d = new Date(dtStr);
      return d.toLocaleString();
    }catch(e){ return dtStr; }
  }

  function statusClass(status){
    if(!status) return 'status-pill--unknown';
    if(status === 'CONFIRMED') return 'status-pill--confirmed';
    if(status === 'PENDING') return 'status-pill--pending';
    if(status === 'CANCELLED' || status === 'FAILED') return 'status-pill--cancelled';
    return 'status-pill--unknown';
  }

  function escapeHtml(str){
    if(!str) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
  }

  function renderBookingCard(b){
    const div = document.createElement('div');
    div.className = 'booking-card';
    // Prepare image URL or fallback to inline SVG placeholder
    const imgUrl = (b.car && b.car.imageUrl) ? b.car.imageUrl : null;
    const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="260" viewBox="0 0 400 260"><rect rx="20" ry="20" width="400" height="260" fill="%23f6f0ea"/><g fill="%23d1b87a"><path d="M60 170c0 0 8-30 30-30h220c22 0 30 30 30 30v10H60v-10z"/></g><g fill="%23999"><circle cx="120" cy="190" r="16"/><circle cx="280" cy="190" r="16"/></g></svg>';

    const carName = ((b.car && (b.car.brand || b.car.model)) ? `${b.car.brand || ''} ${b.car.model || ''}`.trim() : 'Vehicle');

    div.innerHTML = `
      <div class="booking-card__media">
        <img src="${imgUrl || placeholder}" alt="${escapeHtml ? escapeHtml(carName) : carName}" onerror="this.onerror=null;this.src='${placeholder}';" />
      </div>
      <div class="booking-card__content">
        <div class="booking-card__top">
          <div class="booking-car">${escapeHtml ? escapeHtml(carName) : carName}</div>
          <div class="booking-ref">${escapeHtml ? escapeHtml(b.bookingReference) : b.bookingReference}</div>
        </div>

        <div class="booking-timeline">
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div class="timeline-body"><div class="timeline-label">Pickup</div><div class="timeline-value">${escapeHtml ? escapeHtml(b.pickupLocation || '') : (b.pickupLocation || '')}</div><div class="timeline-datetime">${formatDateTime(b.pickupDateTime)}</div></div>
          </div>

          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div class="timeline-body"><div class="timeline-label">Return</div><div class="timeline-value">${escapeHtml ? escapeHtml(b.dropoffLocation || '') : (b.dropoffLocation || '')}</div><div class="timeline-datetime">${formatDateTime(b.dropoffDateTime)}</div></div>
          </div>
        </div>
      </div>

      <div class="booking-card__aside">
        <div class="status-pill ${statusClass(b.status)}">${b.status}</div>
        <div class="booking-price">${b.totalPrice ? ('€' + b.totalPrice) : ''}</div>
        <a class="booking-manage" href="/manage-booking.html?ref=${encodeURIComponent(b.bookingReference)}">Manage booking</a>
      </div>
    `;
    return div;
  }

  document.addEventListener('DOMContentLoaded', async function(){
    const bookings = await fetchBookings();
    const main = document.querySelector('main.rc-container');
    if(!bookings){
      // leave existing empty state and log
      console.warn('Bookings not loaded');
      return;
    }
    if(!Array.isArray(bookings) || bookings.length === 0){
      // leave empty state
      return;
    }

    // Replace main content with list
    main.innerHTML = '';
    const container = document.createElement('div');
    container.className = 'bookings-list';
    bookings.forEach(b => container.appendChild(renderBookingCard(b)));
    main.appendChild(container);
  });
})();
