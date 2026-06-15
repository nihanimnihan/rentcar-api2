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

  function renderBookingCard(b){
    const div = document.createElement('div');
    div.className = 'booking-card';
    div.innerHTML = `
      <div class="booking-card__header">
        <div class="booking-ref">${b.bookingReference}</div>
        <div class="status-pill ${statusClass(b.status)}">${b.status}</div>
      </div>
      <div class="booking-card__body">
        <div class="booking-car">${b.car?.brand || ''} ${b.car?.model || ''}</div>
        <div class="booking-locs">
          <div class="booking-loc"><strong>Pickup:</strong> ${b.pickupLocation || ''}</div>
          <div class="booking-loc"><strong>Dropoff:</strong> ${b.dropoffLocation || ''}</div>
        </div>
        <div class="booking-dates">
          <div><strong>Pickup:</strong> ${formatDateTime(b.pickupDateTime)}</div>
          <div><strong>Return:</strong> ${formatDateTime(b.dropoffDateTime)}</div>
        </div>
      </div>
      <div class="booking-card__footer">
        <div class="booking-price">${b.totalPrice ? ('€' + b.totalPrice) : ''}</div>
        <a class="rc-cta" href="/manage-booking.html?ref=${encodeURIComponent(b.bookingReference)}">Manage</a>
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
