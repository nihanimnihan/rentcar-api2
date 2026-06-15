(function () {
  async function fetchBookings() {
    try {
      const res = await fetch('/api/auth/bookings', { credentials: 'same-origin' });
      if (!res.ok) return null;
      return await res.json();
    } catch (e) {
      console.error('Failed to load bookings', e);
      return null;
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDate(dtStr) {
    if (!dtStr) return '';
    try {
      return new Date(dtStr).toLocaleDateString(undefined, {
        weekday: 'short',
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      });
    } catch (e) {
      return dtStr;
    }
  }

  function formatTime(dtStr) {
    if (!dtStr) return '';
    try {
      return new Date(dtStr).toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (e) {
      return '';
    }
  }

  function money(value) {
    if (value == null || value === '') return '€-';
    return `€${Number(value).toFixed(2)}`;
  }

  function statusClass(status) {
    if (status === 'CONFIRMED') return 'status-pill--confirmed';
    if (status === 'PENDING') return 'status-pill--pending';
    if (status === 'CANCELLED' || status === 'FAILED') return 'status-pill--cancelled';
    return 'status-pill--unknown';
  }

  function carName(b) {
    if (!b.car) return 'Vehicle';
    return `${b.car.brand || ''} ${b.car.model || ''}`.trim() || 'Vehicle';
  }

  function carSubtitle(b) {
    const type = b.car?.vehicleType || b.car?.segment || '';
    return type ? String(type).replaceAll('_', ' ') : 'Premium rental';
  }

  function placeholderSvg() {
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(`
      <svg xmlns="http://www.w3.org/2000/svg" width="420" height="260" viewBox="0 0 420 260">
        <rect width="420" height="260" rx="24" fill="#fbf7ec"/>
        <path d="M74 160c8-32 30-52 66-52h128c36 0 60 20 72 52l12 5v25H61v-25l13-5z" fill="#f8d448"/>
        <circle cx="130" cy="190" r="22" fill="#191B1E"/>
        <circle cx="292" cy="190" r="22" fill="#191B1E"/>
        <path d="M145 117h115c21 0 38 14 48 38H96c10-24 27-38 49-38z" fill="#191B1E" opacity=".78"/>
      </svg>
    `);
  }

  function renderSummary(bookings) {
    const count = bookings.length;
    const total = bookings.reduce((sum, b) => sum + Number(b.totalPrice || 0), 0);

    return `
      <section class="bookings-hero">
        <div>
          <h1>My bookings</h1>
          <p>View and manage all your car rental bookings.</p>
        </div>

        <div class="bookings-stats">
          <div class="bookings-stat">
            <span class="bookings-stat__icon">🚗</span>
            <div>
              <strong>${count}</strong>
              <span>Active bookings</span>
            </div>
          </div>

          <div class="bookings-stat">
            <span class="bookings-stat__icon">€</span>
            <div>
              <strong>${money(total)}</strong>
              <span>Total value</span>
            </div>
          </div>

          <div class="bookings-stat">
            <span class="bookings-stat__icon">✓</span>
            <div>
              <strong>100%</strong>
              <span>Secure bookings</span>
            </div>
          </div>
        </div>
      </section>
    `;
  }

  function renderBookingCard(b) {
    const div = document.createElement('article');
    div.className = 'booking-card';

    const name = carName(b);
    const imgUrl = b.car?.imageUrl || placeholderSvg();
    const ref = b.bookingReference || '—';

    div.innerHTML = `
      <div class="booking-card__media">
        <img src="${escapeHtml(imgUrl)}"
             alt="${escapeHtml(name)}"
             onerror="this.onerror=null;this.src='${placeholderSvg()}';">
      </div>

      <div class="booking-card__identity">
        <div class="booking-ref-label">Booking ref.</div>
        <div class="booking-ref-row">
          <strong>${escapeHtml(ref)}</strong>
        </div>

        <h2>${escapeHtml(name)}</h2>
        <p>${escapeHtml(carSubtitle(b))}</p>
      </div>

      <div class="booking-card__timeline">
        <div class="booking-timepoint">
          <span class="booking-dot"></span>
          <div>
            <strong>${escapeHtml(b.pickupLocation || '')}</strong>
            <span>${escapeHtml(formatDate(b.pickupDateTime))} · ${escapeHtml(formatTime(b.pickupDateTime))}</span>
          </div>
        </div>

        <div class="booking-timepoint">
          <span class="booking-dot"></span>
          <div>
            <strong>${escapeHtml(b.dropoffLocation || '')}</strong>
            <span>${escapeHtml(formatDate(b.dropoffDateTime))} · ${escapeHtml(formatTime(b.dropoffDateTime))}</span>
          </div>
        </div>
      </div>

      <div class="booking-card__aside">
        <span class="status-pill ${statusClass(b.status)}">✓ ${escapeHtml(b.status || '')}</span>

        <div class="booking-price-block">
          <span>Total price</span>
          <strong>${money(b.totalPrice)}</strong>
        </div>

        <a class="booking-manage" href="/manage-booking.html?ref=${encodeURIComponent(ref)}">
          Manage booking <span>→</span>
        </a>
      </div>
    `;

    return div;
  }

  document.addEventListener('DOMContentLoaded', async function () {
    const bookings = await fetchBookings();
    const main = document.querySelector('main.rc-container');

    if (!main || !bookings) return;
    if (!Array.isArray(bookings) || bookings.length === 0) return;

    main.innerHTML = renderSummary(bookings);

    const list = document.createElement('section');
    list.className = 'bookings-list';

    bookings.forEach(b => list.appendChild(renderBookingCard(b)));
    main.appendChild(list);
  });
})();