/* ──────────────────────────────────────────────────────────────────────────
 * manage-booking.js
 * Handles the Manage Booking lookup form:
 *   1. User opens a tokenized email link, or enters bookingReference + lastName
 *   2. GET /api/bookings/manage/token?token=... or /api/bookings/manage?bookingReference=...&lastName=...
 *   3. Renders a booking details card on success, or a .rc-alert--error
 *      panel (css/components/alerts.css) on failure
 * ─────────────────────────────────────────────────────────────────────────*/

'use strict';

// ── Module-level state ────────────────────────────────────────────────────────
// Stored after a successful lookup so the cancel flow can authenticate without
// re-prompting the user and without exposing the numeric booking id.
let _currentRef      = null;
let _currentLastName = null;
let _currentManageToken = null;
let _currentBooking  = null;
let _currentPolicy   = null;

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  const prefillRef = params.get('ref') || params.get('bookingReference');
  const refInput = document.getElementById('mfReference');
  if (refInput && prefillRef) {
    refInput.value = prefillRef.trim().toUpperCase();
  }

  const btn = document.getElementById('mfSubmitBtn');
  if (btn) btn.addEventListener('click', lookupBooking);

  // Allow Enter key in either input to submit.
  ['mfReference', 'mfLastName'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('keydown', e => { if (e.key === 'Enter') lookupBooking(); });
  });

  // "Change" button in collapsed header toggles the lookup form (result stays visible).
  const changeBtn = document.getElementById('mfChangeBtn');
  if (changeBtn) changeBtn.addEventListener('click', toggleLookupForm);

  document.addEventListener('languageChanged', () => {
    const collapsed = document.getElementById('mfCollapsedHeader');
    setLookupToggleLabel(collapsed?.classList.contains('is-editing'));
    if (_currentBooking && document.getElementById('mfResultSection')?.style.display !== 'none') {
      renderResult(_currentBooking, _currentPolicy);
    }
    refreshCancelConfirmationText();
  });

  const manageToken = params.get('token');
  if (manageToken && manageToken.trim()) {
    lookupBookingByToken(manageToken.trim());
  }
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
        if (body?.message) msg = localizeBackendMessage(body.message, msg);
      } catch (_) { /* ignore parse errors */ }
      showError(trans('manage.notFoundTitle'), msg);
      return;
    }

    if (!resp.ok) {
      let msg = trans('manage.networkError');
      try {
        const body = await resp.json();
        if (body?.message) msg = localizeBackendMessage(body.message, msg);
      } catch (_) { /* ignore parse errors */ }
      showError('', msg);
      return;
    }

    const booking = await resp.json();

    // Store for the cancel flow — no numeric id, just the reference + lastName.
    _currentRef      = bookingReference;
    _currentLastName = lastName;
    _currentManageToken = null;

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

async function lookupBookingByToken(token) {
  const submitBtn = document.getElementById('mfSubmitBtn');

  hideError();
  hideResult();
  setLoading(submitBtn, true);

  try {
    const resp = await fetch(`/api/bookings/manage/token?token=${encodeURIComponent(token)}`);

    if (resp.status === 404) {
      let msg = trans('manage.secureLinkInvalid');
      try {
        const body = await resp.json();
        if (body?.message) msg = localizeBackendMessage(body.message, msg);
      } catch (_) { /* ignore parse errors */ }
      _currentManageToken = null;
      showError(trans('manage.secureLinkInvalidTitle'), msg);
      return;
    }

    if (!resp.ok) {
      let msg = trans('manage.networkError');
      try {
        const body = await resp.json();
        if (body?.message) msg = localizeBackendMessage(body.message, msg);
      } catch (_) { /* ignore parse errors */ }
      _currentManageToken = null;
      showError('', msg);
      return;
    }

    const booking = await resp.json();
    const bookingReference = booking.bookingReference ?? '';

    _currentRef = bookingReference;
    _currentLastName = null;
    _currentManageToken = token;

    const refInput = document.getElementById('mfReference');
    if (refInput && bookingReference) refInput.value = bookingReference;

    const policy = await fetchPolicyForCurrentLookup();

    renderResult(booking, policy);
    collapseForm(bookingReference);
  } catch (_err) {
    _currentManageToken = null;
    showError('', trans('manage.networkError'));
  } finally {
    setLoading(submitBtn, false);
  }
}

async function fetchPolicyForCurrentLookup() {
  try {
    let policyUrl = null;
    if (_currentManageToken) {
      policyUrl = `/api/bookings/manage/cancellation-policy/token?token=${encodeURIComponent(_currentManageToken)}`;
    } else if (_currentRef && _currentLastName) {
      policyUrl = `/api/bookings/manage/cancellation-policy?bookingReference=${encodeURIComponent(_currentRef)}&lastName=${encodeURIComponent(_currentLastName)}`;
    }
    if (!policyUrl) return null;

    const policyResp = await fetch(policyUrl);
    return policyResp.ok ? await policyResp.json() : null;
  } catch (_) {
    return null;
  }
}

function renderResult(booking, policy) {
  const section = document.getElementById('mfResultSection');
  if (!section) return;
  _currentBooking = booking;
  _currentPolicy = policy;

  const ref    = booking.bookingReference ? escHtml(booking.bookingReference) : '—';
  const car    = booking.car
    ? `${booking.car.brand ?? ''} ${booking.car.model ?? ''}`.trim()
    : '—';
  const pickup   = booking.pickupDateTime  ? formatDatetime(booking.pickupDateTime)  : '—';
  const dropoff  = booking.dropoffDateTime ? formatDatetime(booking.dropoffDateTime) : '—';
  const pickupLoc  = booking.pickupLocation  ? escHtml(booking.pickupLocation)  : null;
  const dropoffLoc = booking.dropoffLocation ? escHtml(booking.dropoffLocation) : null;
  const days    = booking.rentalDays ?? '—';
  const dayWord = booking.rentalDays === 1 ? trans('manage.day') : trans('manage.days');
  const total   = booking.totalPrice != null
    ? `€${Number(booking.totalPrice).toFixed(2)}`
    : '—';
  const daily   = booking.effectiveDailyPrice != null
    ? `€${Number(booking.effectiveDailyPrice).toFixed(2)} ${trans('manage.perDay')}`
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
          <div class="manage-booking-meta__label">${trans('manage.totalLabel')}</div>
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
         <span class="manage-booking-policy__refund-label">${trans('manage.refundAmount')}</span>
         <span class="manage-booking-policy__refund-amount">€${Number(policy.refundAmount).toFixed(2)}</span>
       </div>`
    : '';

  const actionsHtml = policy.cancellable
    ? `<div class="manage-booking-actions">
         <button class="manage-booking-cancel-btn manage-booking-cancel-btn--active"
                 onclick="confirmAndCancelBooking()">
           ${trans('manage.cancelBooking')}
         </button>
       </div>`
    : '';

  return `
    <div class="manage-booking-policy">
      <div class="manage-booking-meta__label">${trans('manage.cancellation')}</div>
      <div class="manage-booking-policy-row manage-booking-policy-row--${rowVariant}">
        <div class="manage-booking-policy-row__icon"><i class="${icon}"></i></div>
        <span>${escHtml(localizePolicyMessage(policy.policyMessage))}</span>
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
  if (!_currentManageToken && (!_currentRef || !_currentLastName)) return;

  const bookingReference = _currentRef || _currentBooking?.bookingReference || '';
  const confirmed = await showCancelConfirmation(bookingReference);
  if (!confirmed) return;

  const btn = document.querySelector('.manage-booking-cancel-btn--active');
  if (btn) { btn.disabled = true; btn.textContent = trans('manage.cancelling'); }

  try {
    const requestBody = _currentManageToken
      ? { token: _currentManageToken }
      : { bookingReference: _currentRef, lastName: _currentLastName };

    const resp = await fetch('/api/bookings/manage/cancel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody),
    });

    if (resp.ok) {
      const updated = await resp.json();
      // Re-render the card with the updated booking (status=CANCELLED).
      // Fetch a fresh policy (now not cancellable) to update the cancellation section.
      const freshPolicy = await fetchPolicyForCurrentLookup();

      renderResult(updated, freshPolicy);

      // Show a success banner above the card.
      const section = document.getElementById('mfResultSection');
      if (section) {
        const banner = document.createElement('div');
        banner.innerHTML = buildErrorPanel(
          trans('manage.cancelSuccessTitle'),
          trans('manage.cancelSuccessMessage', { reference: bookingReference }),
          'success'
        );
        section.prepend(banner);
      }
    } else {
      let msg = trans('manage.cancelError');
      try {
        const body = await resp.json();
        if (body?.message) msg = localizeBackendMessage(body.message, msg);
      } catch (_) { /* ignore */ }
      // Re-enable the button so the user can retry.
      if (btn) { btn.disabled = false; btn.textContent = trans('manage.cancelBooking'); }
      showCancelError(msg);
    }
  } catch (_err) {
    if (btn) { btn.disabled = false; btn.textContent = trans('manage.cancelBooking'); }
    showCancelError(trans('manage.networkError'));
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

let _cancelConfirmResolve = null;

function showCancelConfirmation(reference) {
  const modal = ensureCancelConfirmModal();
  const refEl = document.getElementById('manageCancelModalRef');
  if (refEl) refEl.textContent = reference;
  refreshCancelConfirmationText();

  modal.classList.add('is-open');
  modal.setAttribute('aria-hidden', 'false');
  document.body.classList.add('manage-cancel-modal-open');

  return new Promise(resolve => {
    _cancelConfirmResolve = resolve;
    setTimeout(() => {
      modal.querySelector('[data-manage-cancel-confirm]')?.focus();
    }, 0);
  });
}

function ensureCancelConfirmModal() {
  let modal = document.getElementById('manageCancelModal');
  if (modal) return modal;

  const wrapper = document.createElement('div');
  wrapper.innerHTML = `
    <div id="manageCancelModal"
         class="manage-cancel-modal"
         aria-hidden="true">
      <div class="manage-cancel-modal__backdrop" data-manage-cancel-close></div>
      <div class="manage-cancel-modal__card"
           role="dialog"
           aria-modal="true"
           aria-labelledby="manageCancelModalTitle">
        <button type="button"
                class="manage-cancel-modal__close"
                aria-label="${trans('manage.close')}"
                data-manage-cancel-close>
          <i class="icon-close"></i>
        </button>

        <div class="manage-cancel-modal__icon">
          <i class="icon-notification"></i>
        </div>

        <h2 id="manageCancelModalTitle" class="manage-cancel-modal__title" data-manage-cancel-title>
          ${trans('manage.cancelModalTitle')}
        </h2>
        <p class="manage-cancel-modal__copy" data-manage-cancel-copy>
          ${trans('manage.cancelModalCopy')}
        </p>

        <div class="manage-cancel-modal__reference">
          <span data-manage-cancel-reference-label>${trans('manage.reference')}</span>
          <strong id="manageCancelModalRef"></strong>
        </div>

        <p class="manage-cancel-modal__warning" data-manage-cancel-warning>
          ${trans('manage.cancelModalWarning')}
        </p>

        <div class="manage-cancel-modal__actions">
          <button type="button"
                  class="manage-cancel-modal__btn manage-cancel-modal__btn--secondary"
                  data-manage-cancel-close>
            <span data-manage-cancel-keep-label>${trans('manage.keepBooking')}</span>
          </button>
          <button type="button"
                  class="manage-cancel-modal__btn manage-cancel-modal__btn--danger"
                  data-manage-cancel-confirm>
            <span data-manage-cancel-confirm-label>${trans('manage.cancelBooking')}</span>
          </button>
        </div>
      </div>
    </div>`;

  document.body.appendChild(wrapper.firstElementChild);
  modal = document.getElementById('manageCancelModal');

  modal.querySelectorAll('[data-manage-cancel-close]').forEach(el => {
    el.addEventListener('click', () => settleCancelConfirmation(false));
  });
  modal.querySelector('[data-manage-cancel-confirm]')?.addEventListener('click', () => {
    settleCancelConfirmation(true);
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && modal.classList.contains('is-open')) {
      settleCancelConfirmation(false);
    }
  });

  return modal;
}

function settleCancelConfirmation(confirmed) {
  const modal = document.getElementById('manageCancelModal');
  if (modal) {
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
  }
  document.body.classList.remove('manage-cancel-modal-open');

  if (_cancelConfirmResolve) {
    const resolve = _cancelConfirmResolve;
    _cancelConfirmResolve = null;
    resolve(confirmed);
  }
}

function refreshCancelConfirmationText() {
  const modal = document.getElementById('manageCancelModal');
  if (!modal) return;
  setText(modal.querySelector('[data-manage-cancel-title]'), trans('manage.cancelModalTitle'));
  setText(modal.querySelector('[data-manage-cancel-copy]'), trans('manage.cancelModalCopy'));
  setText(modal.querySelector('[data-manage-cancel-reference-label]'), trans('manage.reference'));
  setText(modal.querySelector('[data-manage-cancel-warning]'), trans('manage.cancelModalWarning'));
  setText(modal.querySelector('[data-manage-cancel-keep-label]'), trans('manage.keepBooking'));
  setText(modal.querySelector('[data-manage-cancel-confirm-label]'), trans('manage.cancelBooking'));
  modal.querySelector('.manage-cancel-modal__close')?.setAttribute('aria-label', trans('manage.close'));
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
    PAID:       { label: trans('manage.paymentPaid'),      variant: 'success' },
    PENDING:    { label: trans('manage.paymentPending'),   variant: 'warning' },
    FAILED:     { label: trans('manage.paymentFailed'),    variant: 'error'   },
    REFUNDED:   { label: trans('manage.paymentRefunded'),  variant: 'info'    },
    CANCELLED:  { label: trans('manage.paymentCancelled'), variant: 'neutral' },
  };
  const { label, variant } = map[s] ?? { label: s, variant: 'neutral' };

  const methodLabel = booking.paymentMethod === 'CARD'  ? trans('manage.paymentMethodCard')
                    : booking.paymentMethod === 'POST'   ? trans('manage.paymentMethodPost')
                    : null;
  const methodHtml = methodLabel
    ? `<span class="manage-booking-meta__sub">${escHtml(methodLabel)}</span>`
    : '';

  return `
    <div class="manage-booking-section manage-booking-section--payment">
      <div class="manage-booking-meta__label">${trans('manage.payment')}</div>
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
    CONFIRMED: { label: trans('manage.statusConfirmed'), cls: 'rc-badge rc-badge--success' },
    PENDING:   { label: trans('manage.statusPending'),   cls: 'rc-badge rc-badge--warning' },
    FAILED:    { label: trans('manage.statusFailed'),    cls: 'rc-badge rc-badge--error' },
    CANCELLED: { label: trans('manage.statusCancelled'), cls: 'rc-badge rc-badge--warning' },
    COMPLETED: { label: trans('manage.statusCompleted'), cls: 'rc-badge rc-badge--success' },
  };
  const s = map[status] ?? { label: status ?? '—', cls: 'rc-badge rc-badge--warning' };
  return `<span class="${s.cls}">${escHtml(s.label)}</span>`;
}

function formatDatetime(dt) {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString(localeForCurrentLanguage(), {
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
  if (collapsed) {
    collapsed.style.display = 'flex';
    collapsed.classList.remove('is-editing');
  }
  setLookupToggleLabel(false);
}

/**
 * Re-expands the lookup form (e.g. when user clicks "Change").
 * Input values are preserved; the existing result stays visible until a
 * new search is submitted.
 */
function expandForm() {
  const panel    = document.getElementById('mfFormPanel');
  const collapsed = document.getElementById('mfCollapsedHeader');
  if (collapsed && _currentRef) {
    collapsed.style.display = 'flex';
    collapsed.classList.add('is-editing');
  }
  setLookupToggleLabel(true);
  if (panel)     panel.style.display     = 'block';
  hideError();
  // Focus first input for quick re-entry.
  document.getElementById('mfReference')?.focus();
}

function toggleLookupForm() {
  const panel = document.getElementById('mfFormPanel');
  const isExpanded = panel && panel.style.display !== 'none';
  if (isExpanded) {
    collapseForm(_currentRef || document.getElementById('mfReference')?.value || '');
  } else {
    expandForm();
  }
}

function setLookupToggleLabel(isEditing) {
  const changeBtn = document.getElementById('mfChangeBtn');
  if (!changeBtn) return;
  changeBtn.textContent = isEditing ? trans('manage.done') : trans('manage.change');
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
function trans(key, params) {
  if (typeof window.t === 'function') {
    return window.t(key, params);
  }
  let value = key.split('.').pop();
  if (params) {
    Object.keys(params).forEach(k => {
      value = value.replace(new RegExp('{' + k + '}', 'g'), params[k]);
    });
  }
  return value;
}

function setText(el, value) {
  if (el) el.textContent = value;
}

function localizePolicyMessage(message) {
  const keyByMessage = {
    'Booking is already cancelled.': 'manage.policyAlreadyCancelledReason',
    'This booking has already been cancelled and cannot be modified.': 'manage.policyAlreadyCancelled',
    'Your pickup date has passed.': 'manage.policyPickupPassedReason',
    'The booking can no longer be modified after the pickup date.': 'manage.policyPickupPassed',
    'Full refund will be applied.': 'manage.policyFullRefund',
    'Cancellation within 24 hours of pickup — no refund applies.': 'manage.policyNoRefundWithin24h',
    'Cancellation window has expired.': 'manage.policyWindowExpiredReason',
    'Cancellation window has expired. This booking is no longer refundable.': 'manage.policyWindowExpired',
    'Your booking has not been paid — no charge applies.': 'manage.policyNoCharge',
  };
  const key = keyByMessage[message];
  return key ? trans(key) : (message ?? '');
}

function localizeBackendMessage(message, fallback) {
  if (!message) return fallback ?? '';

  const policyMessage = localizePolicyMessage(message);
  if (policyMessage !== message) return policyMessage;

  const keyByMessage = {
    "We couldn't find a booking with these details. Please check your reference and last name.": 'manage.notFound',
    'This secure booking link is invalid or expired. Please look up your booking with your reference and last name.': 'manage.secureLinkInvalid',
    'Booking not found': 'manage.notFoundTitle',
    'Booking cannot be cancelled': 'manage.cancelError',
  };
  const key = keyByMessage[message];
  return key ? trans(key) : (fallback ?? message);
}

function localeForCurrentLanguage() {
  const lang = typeof window.getLanguage === 'function' ? window.getLanguage() : document.documentElement.lang;
  if (lang === 'tr') return 'tr-TR';
  if (lang === 'es') return 'es-ES';
  return 'en-US';
}
