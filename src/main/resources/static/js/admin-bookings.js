/**
 * Admin bookings list page.
 *
 * Prompts the browser for HTTP Basic credentials via a native dialog
 * (triggered by the 401 response). The browser caches them for the session.
 *
 * TODO: add client-side filtering by bookingReference, status, email
 * TODO: add pagination when booking volume grows
 */

const STATUS_LABELS = {
  PENDING:   { text: 'Pending',   cls: 'badge--pending'   },
  CONFIRMED: { text: 'Confirmed', cls: 'badge--confirmed' },
  CANCELLED: { text: 'Cancelled', cls: 'badge--cancelled' },
};

const PAYMENT_LABELS = {
  PENDING:  { text: 'Pending',  cls: 'badge--pending'  },
  PAID:     { text: 'Paid',     cls: 'badge--confirmed' },
  FAILED:   { text: 'Failed',   cls: 'badge--failed'   },
  REFUNDED: { text: 'Refunded', cls: 'badge--neutral'  },
  CANCELLED:{ text: 'Voided',   cls: 'badge--neutral'  },
};

async function loadBookings() {
  const loading      = document.getElementById('loading');
  const tableContainer = document.getElementById('table-container');
  const tbody        = document.getElementById('bookings-tbody');
  const authError    = document.getElementById('auth-error');
  const loadError    = document.getElementById('load-error');
  const empty        = document.getElementById('empty');

  try {
    const res = await fetch('/api/admin/bookings');

    if (res.status === 401 || res.status === 403) {
      loading.hidden = true;
      authError.hidden = false;
      return;
    }

    if (!res.ok) {
      throw new Error(`Server error: ${res.status}`);
    }

    const bookings = await res.json();
    loading.hidden = true;

    if (bookings.length === 0) {
      empty.hidden = false;
      return;
    }

    tbody.innerHTML = bookings.map(renderRow).join('');
    tableContainer.hidden = false;

  } catch (err) {
    loading.hidden = true;
    loadError.textContent = `Failed to load bookings: ${err.message}`;
    loadError.hidden = false;
  }
}

function renderRow(b) {
  const bookingStatus = STATUS_LABELS[b.status]  || { text: b.status,        cls: 'badge--neutral' };
  const paymentStatus = b.paymentStatus
    ? (PAYMENT_LABELS[b.paymentStatus] || { text: b.paymentStatus, cls: 'badge--neutral' })
    : { text: '—', cls: 'badge--neutral' };

  return `
    <tr>
      <td><span class="monospace">${esc(b.bookingReference)}</span></td>
      <td><span class="badge ${bookingStatus.cls}">${bookingStatus.text}</span></td>
      <td><span class="badge ${paymentStatus.cls}">${paymentStatus.text}</span></td>
      <td>${esc(b.customerName)}</td>
      <td>${esc(b.customerEmail)}</td>
      <td>${esc(b.carBrand)} ${esc(b.carModel)}</td>
      <td>${formatDateTime(b.pickupDateTime)}</td>
      <td>${formatDateTime(b.dropoffDateTime)}</td>
      <td>${formatPrice(b.totalPrice)}</td>
      <td>
        <a class="admin-link" href="/api/bookings/${b.id}" target="_blank">Detail ↗</a>
      </td>
    </tr>
  `;
}

function esc(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' });
}

function formatPrice(amount) {
  if (amount == null) return '—';
  return new Intl.NumberFormat('en-EU', { style: 'currency', currency: 'EUR' }).format(amount);
}

loadBookings();
