/* ──────────────────────────────────────────────────────────────────────────
 * manage-booking.js
 * Handles the Manage Booking lookup form:
 *   1. User enters bookingReference + lastName
 *   2. GET /api/bookings/manage?bookingReference=...&lastName=...
 *   3. Renders a booking details card on success, or a .rc-alert--error
 *      panel (css/components/alerts.css) on failure
 * ─────────────────────────────────────────────────────────────────────────*/

'use strict';

// ── Module-level state ────────────────────────────────────────────────────────
// Stored after a successful lookup so the cancel flow can authenticate without
// re-prompting the user and without exposing the numeric booking id.
let _currentRef      = null;
let _currentLastName = null;

document.addEventListener('DOMContentLoaded', () => {
  const btn = document.getElementById('mfSubmitBtn');
  if (btn) btn.addEventListener('click', lookupBooking);

  // Allow Enter key in either input to submit.
  ['mfReference', 'mfLastName'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('keydown', e => { if (e.key === 'Enter') lookupBooking(); });
  });

  // "Change" button in collapsed header → re-expand the form (result stays visible).
  const changeBtn = document.getElementById('mfChangeBtn');
  if (changeBtn) changeBtn.addEventListener('click', expandForm);
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

    // Store for the cancel flow — no numeric id, just the reference + lastName.
    _currentRef      = bookingReference;
    _currentLastName = lastName;

    // Fetch cancellation policy for this booking. Runs after the main lookup so
    // a policy failure never prevents the booking card from rendering.
    let policy = null;
    try {
      const policyUrl = `/api/bookings/manage/cancellation-policy?bookingReference=${encodeURIComponent(bookingReference)}&lastName=${encodeURIComponent(lastName)}`;
      const policyResp = await fetch(policyUrl);
      if (policyResp.ok) policy = await policyResp.json();
    } catch (_) { /* policy unavailable — section will be hidden */ }

    renderResult(booking, policy);
    collapseForm(bookingReference);
  } catch (_err) {
    showError('', trans('manage.networkError'));
  } finally {
    setLoading(submitBtn, false);
  }
}

function renderResult(booking, policy) {
  const section = document.getElementById('mfResultSection');
  if (!section) return;

  const ref    = booking.bookingReference ? escHtml(booking.bookingReference) : '—';
  const car    = booking.car
    ? `${booking.car.brand ?? ''} ${booking.car.model ?? ''}`.trim()
    : '—';
  const pickup   = booking.pickupDateTime  ? formatDatetime(booking.pickupDateTime)  : '—';
  const dropoff  = booking.dropoffDateTime ? formatDatetime(booking.dropoffDateTime) : '—';
  const pickupLoc  = booking.pickupLocation  ? escHtml(booking.pickupLocation)  : null;
  const dropoffLoc = booking.dropoffLocation ? escHtml(booking.dropoffLocation) : null;
  const days    = booking.rentalDays ?? '—';
  const dayWord = booking.rentalDays === 1 ? 'day' : 'days';
  const total   = booking.totalPrice != null
    ? `€${Number(booking.totalPrice).toFixed(2)}`
    : '—';
  const daily   = booking.effectiveDailyPrice != null
    ? `€${Number(booking.effectiveDailyPrice).toFixed(2)} / day`
    : '';

  section.innerHTML = `
    <div class="manage-booking-card">

      <!-- ① Header: title + status badge + reference -->
      <div class="manage-booking-section">
        <div class="d-flex justify-between items-start" style="gap:10px">
          <div>
            <div class="text-18 fw-700">${trans('manage.bookingDetails')}</div>
            <div class="manage-booking-meta__ref">${ref}</div>
          </div>
          ${statusBadgeHtml(booking.status)}
        </div>
      </div>

      <!-- ② Vehicle -->
      <div class="manage-booking-section">
        <div class="manage-booking-meta__label">${trans('manage.vehicle')}</div>
        <div class="manage-booking-meta__value">${escHtml(car)}</div>
      </div>

      <!-- ③ Pick-up | Return -->
      <div class="manage-booking-grid">
        <div class="manage-booking-grid__cell">
          <div class="manage-booking-meta__label">${trans('manage.pickupDate')}</div>
          <div class="manage-booking-meta__value">${pickupLoc ?? '—'}</div>
          <div class="manage-booking-meta__sub">${pickup}</div>
        </div>
        <div class="manage-booking-grid__cell">
          <div class="manage-booking-meta__label">${trans('manage.returnDate')}</div>
          <div class="manage-booking-meta__value">${dropoffLoc ?? '—'}</div>
          <div class="manage-booking-meta__sub">${dropoff}</div>
        </div>
      </div>

      <!-- ④ Duration | Total -->
      <div class="manage-booking-grid">
        <div class="manage-booking-grid__cell">
          <div class="manage-booking-meta__label">${trans('manage.rentalDays')}</div>
          <div class="manage-booking-meta__value">${days} ${dayWord}</div>
          ${daily ? `<div class="manage-booking-meta__sub">${escHtml(daily)}</div>` : ''}
        </div>
        <div class="manage-booking-grid__cell">
          <div class="manage-booking-meta__label">Total</div>
          <div class="manage-booking-meta__value manage-booking-meta__value--price">${total}</div>
        </div>
      </div>

      <!-- ⑤ Payment status -->
      ${paymentSectionHtml(booking)}

      <!-- ⑥ Cancellation policy (conditionally rendered) -->
      ${cancellationPolicySectionHtml(policy)}

    </div>`;

  section.style.display = 'block';
}

/* ── helpers ─────────────────────────────────────────────────────────────*/

/**
 * Renders the cancellation policy + optional actions embedded at the
 * bottom of the booking card.
 * Returns '' when policy is null (fetch failed — sections silently omitted).
 *
 * Colour logic:
 *   cancellable + refundEligible  → success (green)
 *   cancellable + !refundEligible → info    (blue)
 *   !cancellable                  → info    (blue, neutral)
 */
function cancellationPolicySectionHtml(policy) {
  if (!policy) return '';

  const isSuccess  = policy.cancellable && policy.refundEligible;
  const rowVariant = isSuccess ? 'success' : 'info';
  const icon       = isSuccess ? 'icon-check' : 'icon-notification';

  const refundHtml = (policy.cancellable && policy.refundEligible && policy.refundAmount != null)
    ? `<div class="manage-booking-policy__refund">
         <span class="manage-booking-policy__refund-label">Refund amount</span>
         <span class="manage-booking-policy__refund-amount">€${Number(policy.refundAmount).toFixed(2)}</span>
       </div>`
    : '';

  const actionsHtml = policy.cancellable
    ? `<div class="manage-booking-actions">
         <button class="manage-booking-cancel-btn manage-booking-cancel-btn--active"
                 onclick="confirmAndCancelBooking()">
           Cancel booking
         </button>
       </div>`
    : '';

  return `
    <div class="manage-booking-policy">
      <div class="manage-booking-meta__label">Cancellation</div>
      <div class="manage-booking-policy-row manage-booking-policy-row--${rowVariant}">
        <div class="manage-booking-policy-row__icon"><i class="${icon}"></i></div>
        <span>${escHtml(policy.policyMessage ?? '')}</span>
      </div>
      ${refundHtml}
    </div>
    ${actionsHtml}`;
}

/**
 * Called when the customer clicks "Cancel booking".
 * Prompts for confirmation then calls POST /api/bookings/manage/cancel.
 * Authentication is by bookingReference + lastName stored at lookup time.
 */
async function confirmAndCancelBooking() {
  if (!_currentRef || !_currentLastName) return;

  const confirmed = window.confirm(
    'Are you sure you want to cancel this booking?\n\n' +
    'Reference: ' + _currentRef + '\n\n' +
    'This action cannot be undone.'
  );
  if (!confirmed) return;

  const btn = document.querySelector('.manage-booking-cancel-btn--active');
  if (btn) { btn.disabled = true; btn.textContent = 'Cancelling…'; }

  try {
    const resp = await fetch('/api/bookings/manage/cancel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ bookingReference: _currentRef, lastName: _currentLastName }),
    });

    if (resp.ok) {
      const updated = await resp.json();
      // Re-render the card with the updated booking (status=CANCELLED).
      // Fetch a fresh policy (now not cancellable) to update the cancellation section.
      let freshPolicy = null;
      try {
        const pr = await fetch(
          `/api/bookings/manage/cancellation-policy?bookingReference=${encodeURIComponent(_currentRef)}&lastName=${encodeURIComponent(_currentLastName)}`
        );
        if (pr.ok) freshPolicy = await pr.json();
      } catch (_) { /* ignore */ }

      renderResult(updated, freshPolicy);

      // Show a success banner above the card.
      const section = document.getElementById('mfResultSection');
      if (section) {
        const banner = document.createElement('div');
        banner.innerHTML = buildErrorPanel(
          'Booking cancelled',
          'Your booking ' + escHtml(_currentRef) + ' has been successfully cancelled.',
          'success'
        );
        section.prepend(banner);
      }
    } else {
      let msg = 'Your booking could not be cancelled. Please try again.';
      try {
        const body = await resp.json();
        if (body?.message) msg = body.message;
      } catch (_) { /* ignore */ }
      // Re-enable the button so the user can retry.
      if (btn) { btn.disabled = false; btn.textContent = 'Cancel booking'; }
      showCancelError(msg);
    }
  } catch (_err) {
    if (btn) { btn.disabled = false; btn.textContent = 'Cancel booking'; }
    showCancelError('Network error. Please check your connection and try again.');
  }
}

function showCancelError(msg) {
  const section = document.getElementById('mfResultSection');
  if (!section) return;
  // Remove any existing cancel error banner first.
  section.querySelectorAll('.manage-cancel-error').forEach(el => el.remove());
  const div = document.createElement('div');
  div.className = 'manage-cancel-error';
  div.innerHTML = buildErrorPanel('', msg, 'error');
  section.prepend(div);
}

/**
 * Renders a compact Payment section embedded in the booking card.
 * Returns '' when paymentStatus is absent (e.g. transfer bookings or legacy rows).
 *
 * Status → label + badge variant mapping:
 *   PAID        → "Paid"             success (green)
 *   PENDING     → "Payment pending"  warning (yellow)
 *   FAILED      → "Payment failed"   error   (red)
 *   REFUNDED    → "Refunded"         info    (blue)
 *   CANCELLED   → "Payment cancelled" neutral (grey)
 *   NOT_STARTED → omit section
 */
function paymentSectionHtml(booking) {
  const s = booking.paymentStatus;
  if (!s || s === 'NOT_STARTED') return '';

  const map = {
    PAID:       { label: 'Paid',              variant: 'success' },
    PENDING:    { label: 'Payment pending',   variant: 'warning' },
    FAILED:     { label: 'Payment failed',    variant: 'error'   },
    REFUNDED:   { label: 'Refunded',          variant: 'info'    },
    CANCELLED:  { label: 'Payment cancelled', variant: 'neutral' },
  };
  const { label, variant } = map[s] ?? { label: s, variant: 'neutral' };

  const methodLabel = booking.paymentMethod === 'CARD'  ? 'Card'
                    : booking.paymentMethod === 'POST'   ? 'Post'
                    : null;
  const methodHtml = methodLabel
    ? `<span class="manage-booking-meta__sub">${escHtml(methodLabel)}</span>`
    : '';

  return `
    <div class="manage-booking-section manage-booking-section--payment">
      <div class="manage-booking-meta__label">Payment</div>
      <div class="manage-booking-payment-row">
        <span class="rc-badge rc-badge--${variant}">${escHtml(label)}</span>
        ${methodHtml}
      </div>
    </div>`;
}

/**
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

/**
 * Collapses the full lookup form into a compact summary row after a
 * successful booking lookup.  The result card appears immediately below.
 */
function collapseForm(bookingReference) {
  const panel    = document.getElementById('mfFormPanel');
  const collapsed = document.getElementById('mfCollapsedHeader');
  const refLabel  = document.getElementById('mfCollapsedRef');
  if (panel)    panel.style.display    = 'none';
  if (refLabel) refLabel.textContent   = bookingReference;
  if (collapsed) collapsed.style.display = 'flex';
}

/**
 * Re-expands the lookup form (e.g. when user clicks "Change").
 * Input values are preserved; the existing result stays visible until a
 * new search is submitted.
 */
function expandForm() {
  const panel    = document.getElementById('mfFormPanel');
  const collapsed = document.getElementById('mfCollapsedHeader');
  if (collapsed) collapsed.style.display = 'none';
  if (panel)     panel.style.display     = 'block';
  hideError();
  // Focus first input for quick re-entry.
  document.getElementById('mfReference')?.focus();
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
