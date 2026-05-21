/* ──────────────────────────────────────────────────────────────────────────
 * manage-booking.js
 * Handles the Manage Booking lookup form:
 *   1. User enters bookingReference + lastName
 *   2. GET /api/bookings/manage?bookingReference=...&lastName=...
 *   3. Renders a booking details card on success, or a .rc-alert--error
 *      panel (css/components/alerts.css) on failure
 * ─────────────────────────────────────────────────────────────────────────*/

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  const btn = document.getElementById('mfSubmitBtn');
  if (btn) btn.addEventListener('click', lookupBooking);

  // Allow Enter key in either input to submit.
  ['mfReference', 'mfLastName'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('keydown', e => { if (e.key === 'Enter') lookupBooking(); });
  });
});

async function lookupBooking() {
  const refInput      = document.getElementById('mfReference');
  const lastNameInput = document.getElementById('mfLastName');
  const submitBtn     = document.getElementById('mfSubmitBtn');

  const bookingReference = (refInput?.value ?? '').trim().toUpperCase();
  const lastName         = (lastNameInput?.value ?? '').trim();

  hideError();
  hideResult();

  if (!bookingReference || !lastName) {
    showError('', trans('manage.enterRequired'));
    return;
  }

  setLoading(submitBtn, true);

  try {
    const url = `/api/bookings/manage?bookingReference=${encodeURIComponent(bookingReference)}&lastName=${encodeURIComponent(lastName)}`;
    const resp = await fetch(url);

    if (resp.status === 404) {
      // Use the backend's business-friendly message; fall back to i18n only if missing.
      let msg = trans('manage.notFound');
      try {
        const body = await resp.json();
        if (body?.message) msg = body.message;
      } catch (_) { /* ignore parse errors */ }
      showError(trans('manage.notFoundTitle'), msg);
      return;
    }

    if (!resp.ok) {
      let msg = trans('manage.networkError');
      try {
        const body = await resp.json();
        if (body?.message) msg = body.message;
      } catch (_) { /* ignore parse errors */ }
      showError('', msg);
      return;
    }

    const booking = await resp.json();
    renderResult(booking);
  } catch (_err) {
    showError('', trans('manage.networkError'));
  } finally {
    setLoading(submitBtn, false);
  }
}

function renderResult(booking) {
  const section = document.getElementById('mfResultSection');
  if (!section) return;

  const ref    = booking.bookingReference ? escHtml(booking.bookingReference) : '—';
  const car    = booking.car
    ? `${booking.car.brand ?? ''} ${booking.car.model ?? ''}`.trim()
    : '—';
  const pickup  = formatDatetime(booking.pickupDateTime);
  const dropoff = formatDatetime(booking.dropoffDateTime);
  const days    = booking.rentalDays ?? '—';
  const total   = booking.totalPrice != null
    ? `€${Number(booking.totalPrice).toFixed(2)}`
    : '—';

  section.innerHTML = `
    <div class="border-light rounded-8 px-24 py-24 mt-30">
      <div class="d-flex justify-between items-center mb-4">
        <h2 class="text-20 fw-700">${trans('manage.bookingDetails')}</h2>
        ${statusBadgeHtml(booking.status)}
      </div>
      <div class="text-13 text-dark-1 mb-20">${ref}</div>

      <div class="text-14 fw-600 text-dark-1 mb-4">${trans('manage.vehicle')}</div>
      <div class="text-16 mb-16">${escHtml(car)}</div>

      <div class="row -sm-gap-y-20">
        <div class="col-6">
          <div class="text-14 fw-600 text-dark-1 mb-4">${trans('manage.pickupDate')}</div>
          <div class="text-15">${pickup}</div>
        </div>
        <div class="col-6">
          <div class="text-14 fw-600 text-dark-1 mb-4">${trans('manage.returnDate')}</div>
          <div class="text-15">${dropoff}</div>
        </div>
      </div>

      <div class="d-flex justify-between items-center border-top-light mt-20 pt-20">
        <div class="text-14 text-dark-1">${trans('manage.rentalDays')}: <strong>${days} ${trans('manage.days')}</strong></div>
        <div class="text-18 fw-700">${total}</div>
      </div>
    </div>`;

  section.style.display = 'block';
}

/* ── helpers ─────────────────────────────────────────────────────────────*/

/**
 * Builds an inline alert panel using the reusable .rc-alert component
 * defined in css/components/alerts.css.
 *
 * @param {string} title   - Bold heading (optional — omit or pass '' to skip).
 * @param {string} msg     - Body message text.
 * @param {string} variant - 'error' | 'success' | 'warning' | 'info'  (default: 'error')
 */
function buildErrorPanel(title, msg, variant = 'error') {
  const iconMap = {
    error:   'icon-close',
    success: 'icon-check',
    warning: 'icon-notification',
    info:    'icon-notification',
  };
  const icon = iconMap[variant] ?? 'icon-notification';
  const titleHtml = title
    ? `<div class="rc-alert__title">${escHtml(title)}</div>`
    : '';
  return `
    <div class="rc-alert rc-alert--${variant}">
      <div class="rc-alert__icon"><i class="${icon}"></i></div>
      <div class="rc-alert__content">
        ${titleHtml}
        <div class="rc-alert__message">${escHtml(msg)}</div>
      </div>
    </div>`;
}

function statusBadgeHtml(status) {
  // bg-green-2 (#008009) gives a strong, readable green — avoids the pale mint
  // of bg-green-1 (#EBFCEA) which has poor contrast against white text.
  const map = {
    CONFIRMED: { label: 'Confirmed', cls: 'bg-green-2 text-white' },
    PENDING:   { label: 'Pending',   cls: 'bg-yellow-1 text-dark-1' },
    FAILED:    { label: 'Failed',    cls: 'bg-red-1 text-white' },
    CANCELLED: { label: 'Cancelled', cls: 'bg-light-2 text-dark-1' },
    COMPLETED: { label: 'Completed', cls: 'bg-blue-1 text-white' },
  };
  const s = map[status] ?? { label: status ?? '—', cls: 'bg-light-2 text-dark-1' };
  return `<span class="rounded-8 px-12 py-6 text-13 fw-600 ${s.cls}">${s.label}</span>`;
}

function formatDatetime(dt) {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  } catch (_) { return dt; }
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showError(title, msg) {
  const el = document.getElementById('mfError');
  if (!el) return;
  el.innerHTML = buildErrorPanel(title, msg);
  el.style.display = 'block';
}

function hideError() {
  const el = document.getElementById('mfError');
  if (el) { el.innerHTML = ''; el.style.display = 'none'; }
}

function hideResult() {
  const el = document.getElementById('mfResultSection');
  if (el) el.style.display = 'none';
}

function setLoading(btn, isLoading) {
  if (!btn) return;
  btn.disabled = isLoading;
  if (isLoading) {
    btn.dataset.originalText = btn.textContent;
    btn.textContent = trans('manage.searching');
  } else {
    btn.textContent = btn.dataset.originalText ?? trans('manage.continue');
  }
}

/**
 * i18n wrapper. window.t is exposed by js/i18n/i18n.js (loaded before this script).
 * Named `trans` to avoid shadowing the global window.t.
 * Falls back to the last key segment when the i18n module is unavailable.
 */
function trans(key) {
  if (typeof window.t === 'function') {
    return window.t(key);
  }
  return key.split('.').pop();
}
