/**
 * Admin bookings list page with in-page booking detail modal.
 */

let currentBookings = [];
let currentDetail = null;
let adminVehicles = [];
let adminAddons = [];
let adminInsurancePackages = [];
let adminCalculatedTotal = null;
let adminPriceRequestSeq = 0;
let adminManualPriceOverride = false;

const STATUS_CLASSES = {
  PENDING: 'badge--pending',
  CONFIRMED: 'badge--confirmed',
  FAILED: 'badge--failed',
  CANCELLED: 'badge--cancelled',
  EXPIRED: 'badge--neutral',
};

const PAYMENT_CLASSES = {
  PENDING: 'badge--pending',
  PAID: 'badge--confirmed',
  FAILED: 'badge--failed',
  REFUND_PENDING: 'badge--pending',
  REFUNDED: 'badge--neutral',
  CANCELLED: 'badge--neutral',
  NO_REFUND: 'badge--neutral',
};

document.addEventListener('DOMContentLoaded', () => {
  bindBookingDetailModal();
  bindCreateBookingModal();
  bindBookingTable();
  loadBookings();
});

document.addEventListener('languageChanged', () => {
  if (currentBookings.length) renderBookings(currentBookings);
  if (currentDetail) renderBookingDetail(currentDetail);
});

async function loadBookings() {
  const loading = document.getElementById('loading');
  const tableContainer = document.getElementById('table-container');
  const authError = document.getElementById('auth-error');
  const loadError = document.getElementById('load-error');
  const empty = document.getElementById('empty');

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

    currentBookings = await res.json();
    loading.hidden = true;

    if (currentBookings.length === 0) {
      empty.hidden = false;
      return;
    }

    renderBookings(currentBookings);
    tableContainer.hidden = false;
  } catch (err) {
    loading.hidden = true;
    loadError.textContent = tt('admin.bookings.loadError', { message: err.message });
    loadError.hidden = false;
  }
}

async function reloadBookings() {
  document.getElementById('table-container').hidden = true;
  document.getElementById('empty').hidden = true;
  document.getElementById('load-error').hidden = true;
  document.getElementById('loading').hidden = false;
  await loadBookings();
}

function renderBookings(bookings) {
  const tbody = document.getElementById('bookings-tbody');
  tbody.innerHTML = bookings.map(renderRow).join('');
}

function renderRow(b) {
  const bookingStatus = statusLabel(b.status);
  const paymentStatus = paymentLabel(b.paymentStatus);
  const paymentText = paymentDisplay(b.paymentStatus, b.paymentMethod);
  const lifecycle = lifecycleBadges(b);

  return `
    <tr>
      <td><span class="monospace">${esc(b.bookingReference)}</span></td>
      <td>
        <span class="badge ${bookingStatus.cls}">${esc(bookingStatus.text)}</span>
        <div class="admin-lifecycle-badges">${lifecycle.status}</div>
      </td>
      <td>
        <span class="badge ${paymentStatus.cls}">${esc(paymentText)}</span>
        <div class="admin-lifecycle-badges">${lifecycle.refund}</div>
      </td>
      <td>${esc(b.customerName)}</td>
      <td>${esc(b.customerEmail)}</td>
      <td>${esc(b.carBrand)} ${esc(b.carModel)}</td>
      <td>${cell(formatDateTime(b.pickupDateTime))}</td>
      <td>${cell(formatDateTime(b.dropoffDateTime))}</td>
      <td>${cell(formatPrice(b.totalPrice))}</td>
      <td>
        <button type="button" class="admin-link admin-link-button" data-booking-detail-id="${b.id}">
          ${esc(tt('admin.bookings.details'))}
        </button>
      </td>
    </tr>
  `;
}

function bindBookingTable() {
  const tbody = document.getElementById('bookings-tbody');
  tbody.addEventListener('click', (event) => {
    const button = event.target.closest('[data-booking-detail-id]');
    if (!button) return;
    openBookingDetail(button.getAttribute('data-booking-detail-id'));
  });
}

function bindBookingDetailModal() {
  const overlay = document.getElementById('booking-detail-overlay');
  document.getElementById('booking-detail-close').addEventListener('click', closeBookingDetail);
  document.getElementById('booking-detail-footer-close').addEventListener('click', closeBookingDetail);
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) closeBookingDetail();
  });
  document.getElementById('booking-detail-body').addEventListener('click', handleLifecycleActionClick);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !overlay.hidden) closeBookingDetail();
  });
}

async function openBookingDetail(id) {
  const overlay = document.getElementById('booking-detail-overlay');
  const body = document.getElementById('booking-detail-body');
  const error = document.getElementById('booking-detail-error');
  const loading = document.getElementById('booking-detail-loading');

  currentDetail = null;
  body.innerHTML = '';
  error.hidden = true;
  loading.hidden = false;
  overlay.hidden = false;
  requestAnimationFrame(() => overlay.classList.add('-visible'));

  try {
    const res = await fetch(`/api/admin/bookings/${encodeURIComponent(id)}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const detail = await res.json();
    const extra = await loadBookingExtra(id);
    currentDetail = Object.assign({}, detail, extra);
    loading.hidden = true;
    renderBookingDetail(currentDetail);
  } catch (err) {
    loading.hidden = true;
    error.textContent = tt('admin.detail.loadError', { message: err.message });
    error.hidden = false;
  }
}

async function loadBookingExtra(id) {
  try {
    const res = await fetch(`/api/bookings/${encodeURIComponent(id)}`);
    if (!res.ok) return {};
    const booking = await res.json();
    return {
      cancelledAt: booking.cancelledAt,
    };
  } catch (err) {
    return {};
  }
}

function renderBookingDetail(b) {
  const body = document.getElementById('booking-detail-body');
  const status = statusLabel(b.status);
  const payment = paymentLabel(b.paymentStatus);
  const paymentSummary = paymentDisplay(b.paymentStatus, b.paymentMethod);
  const pickupDisplay = bestLocationDisplay(b.pickupAddress, b.pickupLocation);
  const dropoffDisplay = bestLocationDisplay(b.dropoffAddress, b.dropoffLocation);

  body.innerHTML = `
    <div class="admin-detail-hero">
      <div>
        <span class="admin-detail-kicker">${esc(tt('admin.bookings.reference'))}</span>
        <h3 class="admin-detail-reference">${esc(b.bookingReference)}</h3>
      </div>
      <div class="admin-detail-badges">
        <span class="badge ${status.cls}">${esc(status.text)}</span>
        <span class="badge ${payment.cls}">${esc(payment.text)}</span>
      </div>
    </div>

    ${detailSection('admin.detail.overview', [
      detailItem('admin.detail.source', enumText(b.source)),
      detailItem('admin.detail.rentalDays', valueOrDash(b.rentalDays)),
      detailItem('admin.detail.bookingOption', enumText(b.bookingOptionType)),
      detailItem('admin.detail.cancellationPolicy', enumText(b.cancellationPolicyType))
    ])}

    ${detailSection('admin.detail.customer', [
      detailItem('admin.bookings.customer', b.customerName),
      detailItem('admin.bookings.email', b.customerEmail)
    ])}

    ${detailSection('admin.detail.vehicle', [
      detailItem('admin.bookings.car', [b.carBrand, b.carModel].filter(Boolean).join(' ')),
      detailItem('admin.detail.mileage', enumText(b.mileageOption)),
      detailItem('admin.detail.includedKm', b.includedKmSnapshot != null ? `${b.includedKmSnapshot} km` : null),
      detailItem('admin.detail.unlimitedKmPrice', formatPrice(b.unlimitedKmPriceSnapshot))
    ])}

    ${detailSection('admin.detail.trip', [
      detailItem('admin.detail.pickupDate', formatDateTime(b.pickupDateTime)),
      detailItem('admin.detail.pickupLocation', pickupDisplay),
      detailItem('admin.detail.dropoffDate', formatDateTime(b.dropoffDateTime)),
      detailItem('admin.detail.dropoffLocation', dropoffDisplay)
    ])}

    ${detailSection('admin.detail.pricing', [
      detailItem('admin.detail.baseDailyPrice', formatPrice(b.baseDailyPrice)),
      detailItem('admin.detail.effectiveDailyPrice', formatPrice(b.effectiveDailyPrice)),
      detailItem('admin.detail.discount', formatPercent(b.discountPercentage)),
      detailItem('admin.detail.rentalCharge', formatPrice(b.rentalCharge)),
      detailItem('admin.detail.oneWayFee', formatPrice(b.oneWayFee)),
      detailItem('admin.detail.premiumLocationFee', formatPrice(b.premiumLocationFee)),
      detailItem('admin.detail.tax', formatPrice(b.tax)),
      detailItem('admin.detail.insurance', b.insuranceNameSnapshot),
      detailItem('admin.detail.insuranceDailyPrice', formatPrice(b.insuranceDailyPriceSnapshot)),
      detailItem('admin.detail.insuranceTotal', formatPrice(b.insuranceTotalSnapshot)),
      detailItem('admin.detail.deposit', formatPrice(b.depositAmountSnapshot)),
      detailItem('admin.detail.addonCharge', formatPrice(b.addonCharge)),
      detailItem('admin.detail.bookingOptionFee', formatPrice(b.bookingOptionDailyFee)),
      detailItem('admin.detail.totalPrice', formatPrice(b.totalPrice), true)
    ])}

    ${detailSection('admin.detail.paymentRefund', [
      detailItem('admin.detail.paymentStatus', paymentSummary),
      detailItem('admin.detail.paymentMethod', paymentMethodLabel(b.paymentMethod))
    ])}

    ${lifecycleDetailSection(b)}

    ${addonDetailSection(b.addons || [])}

    ${detailSection('admin.detail.cancellation', [
      detailItem('admin.detail.cancellationReason', b.cancellationReason || tt('admin.detail.none')),
      detailItem('admin.detail.cancelledAt', formatDateTime(b.cancelledAt)),
      detailItem('admin.detail.notes', b.notes || tt('admin.detail.none'))
    ])}
  `;
}

function lifecycleDetailSection(b) {
  return `
    <section class="admin-detail-section">
      <h4>${esc(tt('admin.detail.lifecycle'))}</h4>
      <dl class="admin-detail-grid">
        ${detailItem('admin.detail.cancellationStatus', lifecycleStatusText(b))}
        ${detailItem('admin.detail.cancellationEligibility', b.cancellationAllowed ? tt('admin.lifecycle.cancelable') : tt('admin.lifecycle.notCancelable'))}
        ${detailItem('admin.detail.refundEligibility', b.refundEligible ? tt('admin.lifecycle.refundEligible') : tt('admin.lifecycle.notRefundable'))}
        ${detailItem('admin.detail.refundAmount', formatPrice(b.refundAmount), true)}
        ${detailItem('admin.detail.cancellationPolicyExplanation', b.cancellationPolicyMessage)}
      </dl>
      ${lifecycleActions(b)}
    </section>
  `;
}

function lifecycleActions(b) {
  if (b.status === 'CANCELLED') return '';
  const buttons = [];
  if (b.refundEligible) {
    buttons.push(`
      <button type="button" class="admin-btn admin-btn--primary" data-booking-lifecycle-action="cancel-refund" data-booking-id="${esc(b.id)}">
        ${esc(tt('admin.lifecycle.cancelRefund'))}
      </button>
    `);
  }
  if (b.adminOperationalCancellationAllowed) {
    buttons.push(`
      <button type="button" class="admin-btn" data-booking-lifecycle-action="cancel" data-booking-id="${esc(b.id)}">
        ${esc(tt('admin.lifecycle.cancelBooking'))}
      </button>
    `);
    buttons.push(`
      <button type="button" class="admin-btn admin-btn--danger" data-booking-lifecycle-action="no-show" data-booking-id="${esc(b.id)}">
        ${esc(tt('admin.lifecycle.markNoShow'))}
      </button>
    `);
  }
  if (!buttons.length) return '';
  return `<div class="admin-detail-actions">${buttons.join('')}</div>`;
}

async function handleLifecycleActionClick(event) {
  const button = event.target.closest('[data-booking-lifecycle-action]');
  if (!button) return;
  const id = button.getAttribute('data-booking-id');
  const action = button.getAttribute('data-booking-lifecycle-action');
  const endpoint = {
    cancel: `/api/admin/bookings/${encodeURIComponent(id)}/cancel`,
    'cancel-refund': `/api/admin/bookings/${encodeURIComponent(id)}/cancel-refund`,
    'no-show': `/api/admin/bookings/${encodeURIComponent(id)}/no-show`,
  }[action];
  if (!endpoint) return;

  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = tt('admin.lifecycle.processing');
  try {
    const res = await fetch(endpoint, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await openBookingDetail(id);
    await reloadBookings();
  } catch (err) {
    const error = document.getElementById('booking-detail-error');
    error.textContent = tt('admin.lifecycle.actionError', { message: err.message });
    error.hidden = false;
    button.disabled = false;
    button.textContent = originalText;
  }
}

function addonDetailSection(addons) {
  if (!addons.length) {
    return detailSection('admin.detail.addons', [
      detailItem('admin.detail.selectedAddons', tt('admin.detail.none'))
    ]);
  }
  return `
    <section class="admin-detail-section">
      <h4>${esc(tt('admin.detail.addons'))}</h4>
      <dl class="admin-detail-grid">
        ${addons.map(addon => `
          <div class="admin-detail-item">
            <dt>${esc(addon.pricingTypeSnapshot || '')}</dt>
            <dd>${esc(addon.name)} - ${esc(formatPrice(addon.lineTotal))}</dd>
          </div>
        `).join('')}
      </dl>
    </section>
  `;
}

function detailSection(titleKey, rows) {
  return `
    <section class="admin-detail-section">
      <h4>${esc(tt(titleKey))}</h4>
      <dl class="admin-detail-grid">
        ${rows.join('')}
      </dl>
    </section>
  `;
}

function detailItem(labelKey, rawValue, strong) {
  const value = rawValue == null || rawValue === '' ? tt('admin.detail.unavailable') : rawValue;
  return `
    <div class="admin-detail-item${strong ? ' admin-detail-item--strong' : ''}">
      <dt>${esc(tt(labelKey))}</dt>
      <dd>${esc(value)}</dd>
    </div>
  `;
}

function closeBookingDetail() {
  const overlay = document.getElementById('booking-detail-overlay');
  overlay.classList.remove('-visible');
  overlay.hidden = true;
  currentDetail = null;
}

function bindCreateBookingModal() {
  const overlay = document.getElementById('create-booking-overlay');
  const form = document.getElementById('create-booking-form');
  document.getElementById('create-booking-open').addEventListener('click', openCreateBooking);
  document.getElementById('create-booking-close').addEventListener('click', closeCreateBooking);
  document.getElementById('create-booking-cancel').addEventListener('click', closeCreateBooking);
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) closeCreateBooking();
  });
  form.addEventListener('submit', submitCreateBooking);
  initAdminLocationPickers();
  initCreatePricingListeners();
}

async function openCreateBooking() {
  clearCreateErrors();
  const overlay = document.getElementById('create-booking-overlay');
  document.getElementById('create-booking-form').reset();
  document.getElementById('admin-booking-phone-code').value = '+34';
  document.getElementById('admin-booking-payment-source').value = 'OFFICE';
  adminCalculatedTotal = null;
  adminManualPriceOverride = false;
  updateOverrideIndicator();
  resetAdminLocation('pickup', 'BCN Airport T1');
  resetAdminLocation('return', 'BCN Airport T1');
  updateAdminPriceSummary();
  overlay.hidden = false;
  requestAnimationFrame(() => overlay.classList.add('-visible'));
  try {
    await Promise.all([loadAdminVehicles(), loadAdminAddons(), loadAdminInsurance()]);
    updateAdminPriceSummary();
  } catch (err) {
    showCreateError(err.message);
  }
}

function closeCreateBooking() {
  const overlay = document.getElementById('create-booking-overlay');
  if (window.RentcarLocationPicker) window.RentcarLocationPicker.closeAll();
  overlay.classList.remove('-visible');
  overlay.hidden = true;
}

async function loadAdminVehicles() {
  const select = document.getElementById('admin-booking-vehicle');
  select.innerHTML = '<option value="">Loading vehicles...</option>';
  const res = await fetch('/api/admin/cars');
  if (!res.ok) throw new Error(`Could not load vehicles: HTTP ${res.status}`);
  adminVehicles = await res.json();
  const activeVehicles = adminVehicles.filter(vehicle => vehicle.active !== false);
  select.innerHTML = '<option value="">Select vehicle</option>' + activeVehicles.map(vehicle => `
    <option value="${esc(vehicle.id)}">${esc(vehicle.brand)} ${esc(vehicle.model)} - ${esc(formatPrice(vehicle.dailyPrice))}/day</option>
  `).join('');
}

async function loadAdminInsurance() {
  const container = document.getElementById('admin-booking-insurance');
  container.innerHTML = `<div class="admin-field-help">${esc(tt('admin.create.loadingProtection'))}</div>`;
  const lang = window.getLanguage ? window.getLanguage() : 'en';
  const res = await fetch(`/api/insurance-packages/active?lang=${encodeURIComponent(lang)}`);
  if (!res.ok) throw new Error(`Could not load protection packages: HTTP ${res.status}`);
  adminInsurancePackages = await res.json();
  if (!adminInsurancePackages.length) {
    container.innerHTML = `<div class="admin-field-help">${esc(tt('admin.create.noProtectionPackages'))}</div>`;
    return;
  }
  container.innerHTML = adminInsurancePackages.map(pkg => `
    <label class="admin-addon-option">
      <input type="radio" name="insurancePackageId" value="${esc(pkg.id)}" ${pkg.recommended ? 'checked' : ''}>
      <span>
        <span class="admin-addon-option__name">${esc(pkg.name)}${pkg.badge ? ` · ${esc(pkg.badge)}` : ''}</span>
        <span class="admin-addon-option__meta">${esc(formatPrice(pkg.pricePerDay))} / ${esc(tt('admin.create.day'))} · ${esc(tt('admin.create.deposit'))}: ${esc(formatPrice(pkg.depositAmount))}</span>
      </span>
    </label>
  `).join('');
  if (!container.querySelector('input[name="insurancePackageId"]:checked')) {
    const first = container.querySelector('input[name="insurancePackageId"]');
    if (first) first.checked = true;
  }
  container.querySelectorAll('input[name="insurancePackageId"]').forEach(input => {
    input.addEventListener('change', updateAdminPriceSummary);
  });
}

async function loadAdminAddons() {
  const container = document.getElementById('admin-booking-addons');
  container.innerHTML = '<div class="admin-field-help">Loading add-ons...</div>';
  const res = await fetch('/api/addons/active');
  if (!res.ok) throw new Error(`Could not load add-ons: HTTP ${res.status}`);
  adminAddons = await res.json();
  if (!adminAddons.length) {
    container.innerHTML = '<div class="admin-field-help">No active add-ons.</div>';
    return;
  }
  container.innerHTML = adminAddons.map(addon => `
    <label class="admin-addon-option">
      <input type="checkbox" name="addonIds" value="${esc(addon.id)}">
      <span>
        <span class="admin-addon-option__name">${esc(addon.name)}</span>
        <span class="admin-addon-option__meta">${esc(formatPrice(addon.price))} / ${esc(enumText(addon.pricingType))}</span>
      </span>
    </label>
  `).join('');
  container.querySelectorAll('input[name="addonIds"]').forEach(input => {
    input.addEventListener('change', updateAdminPriceSummary);
  });
}

async function submitCreateBooking(event) {
  event.preventDefault();
  clearCreateErrors();
  const payload = createBookingPayload();
  const validationErrors = validateCreatePayload(payload);
  if (Object.keys(validationErrors).length) {
    showFieldErrors(validationErrors);
    return;
  }

  const submitButton = document.getElementById('create-booking-submit');
  submitButton.disabled = true;
  submitButton.textContent = 'Creating...';
  try {
    const res = await fetch('/api/admin/bookings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      let message = `HTTP ${res.status}`;
      try {
        const body = await res.json();
        message = body.message || message;
      } catch (err) {}
      throw new Error(message);
    }
    closeCreateBooking();
    await reloadBookings();
  } catch (err) {
    showCreateError(err.message);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = 'Create Booking';
  }
}

function initAdminLocationPickers() {
  if (!window.RentcarLocationPicker) return;
  window.RentcarLocationPicker.init({
    buttonId: 'admin-booking-pickup-location-button',
    popupId: 'admin-booking-pickup-location-popup',
    textId: 'admin-booking-pickup-location-text',
    hiddenId: 'admin-booking-pickup-location'
  });
  window.RentcarLocationPicker.init({
    buttonId: 'admin-booking-return-location-button',
    popupId: 'admin-booking-return-location-popup',
    textId: 'admin-booking-return-location-text',
    hiddenId: 'admin-booking-return-location'
  });
}

function resetAdminLocation(kind, label) {
  const input = document.getElementById(`admin-booking-${kind}-location`);
  const text = document.getElementById(`admin-booking-${kind}-location-text`);
  if (text) text.textContent = label;
  if (!input) return;
  input.value = label;
  input.dataset.label = label;
  input.dataset.address = '';
  input.dataset.placeId = '';
  input.dataset.lat = '';
  input.dataset.lng = '';
}

function initCreatePricingListeners() {
  [
    'admin-booking-vehicle',
    'admin-booking-pickup-date',
    'admin-booking-pickup-time',
    'admin-booking-return-date',
    'admin-booking-return-time',
    'admin-booking-pickup-location',
    'admin-booking-return-location'
  ].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('change', updateAdminPriceSummary);
  });
  const totalInput = document.getElementById('admin-booking-total');
  if (totalInput) {
    totalInput.addEventListener('input', () => {
      adminManualPriceOverride = true;
      updateOverrideIndicator();
    });
  }
}

async function updateAdminPriceSummary() {
  const baseEl = document.getElementById('admin-booking-base-price');
  const addonsEl = document.getElementById('admin-booking-addons-price');
  const insuranceEl = document.getElementById('admin-booking-insurance-price');
  const depositEl = document.getElementById('admin-booking-deposit-price');
  const totalEl = document.getElementById('admin-booking-calculated-total');
  if (!baseEl || !addonsEl || !totalEl) return;

  const requestSeq = ++adminPriceRequestSeq;
  const rentalDays = calculateAdminRentalDays();
  const vehicle = selectedAdminVehicle();
  const addonsTotal = calculateSelectedAddonsTotal(rentalDays || 1);
  const insurancePackage = selectedAdminInsurance();
  const insuranceTotal = calculateSelectedInsuranceTotal(rentalDays || 1);
  addonsEl.textContent = formatPrice(addonsTotal);
  if (insuranceEl) insuranceEl.textContent = insurancePackage ? formatPrice(insuranceTotal) : '-';
  if (depositEl) depositEl.textContent = insurancePackage ? formatPrice(insurancePackage.depositAmount) : '-';

  if (!vehicle || !rentalDays || !insurancePackage) {
    adminCalculatedTotal = null;
    baseEl.textContent = '-';
    totalEl.textContent = '-';
    updateOverrideIndicator();
    return;
  }

  baseEl.textContent = 'Calculating...';
  totalEl.textContent = 'Calculating...';

  try {
    const price = await fetchAdminBasePrice(vehicle.id);
    if (requestSeq !== adminPriceRequestSeq) return;
    const baseTotal = Number(price.totalPrice || 0);
    const rentalCharge = Number(price.rentalCharge || baseTotal);
    const calculatedTotal = roundMoney(baseTotal + addonsTotal + insuranceTotal);
    adminCalculatedTotal = calculatedTotal;

    baseEl.textContent = `${formatPrice(rentalCharge)} (${rentalDays} day${rentalDays === 1 ? '' : 's'})`;
    addonsEl.textContent = formatPrice(addonsTotal);
    totalEl.textContent = formatPrice(calculatedTotal);

    const totalInput = document.getElementById('admin-booking-total');
    if (totalInput && !adminManualPriceOverride) {
      totalInput.value = calculatedTotal.toFixed(2);
    }
    updateOverrideIndicator();
  } catch (err) {
    if (requestSeq !== adminPriceRequestSeq) return;
    adminCalculatedTotal = null;
    baseEl.textContent = '-';
    totalEl.textContent = '-';
    updateOverrideIndicator();
  }
}

async function fetchAdminBasePrice(vehicleId) {
  const pickupDate = document.getElementById('admin-booking-pickup-date')?.value;
  const pickupTime = document.getElementById('admin-booking-pickup-time')?.value;
  const returnDate = document.getElementById('admin-booking-return-date')?.value;
  const returnTime = document.getElementById('admin-booking-return-time')?.value;
  const pickupLocation = document.getElementById('admin-booking-pickup-location')?.value || '';
  const returnLocation = document.getElementById('admin-booking-return-location')?.value || '';
  const params = new URLSearchParams({
    pickupDateTime: `${pickupDate}T${pickupTime}`,
    dropoffDateTime: `${returnDate}T${returnTime}`,
    pickupLocation,
    dropoffLocation: returnLocation
  });
  const res = await fetch(`/api/cars/${encodeURIComponent(vehicleId)}?${params.toString()}`);
  if (!res.ok) throw new Error(`Pricing failed: HTTP ${res.status}`);
  const car = await res.json();
  return car.priceBreakdown || {};
}

function updateOverrideIndicator() {
  const indicator = document.getElementById('admin-booking-override-indicator');
  const deltaEl = document.getElementById('admin-booking-override-delta');
  const totalInput = document.getElementById('admin-booking-total');
  if (!indicator || !deltaEl || !totalInput) return;
  const entered = numberOrNull(totalInput.value);
  const active = adminCalculatedTotal != null && entered != null && Math.abs(entered - adminCalculatedTotal) >= 0.01;
  indicator.hidden = !active;
  deltaEl.textContent = active
    ? `${entered > adminCalculatedTotal ? '+' : ''}${formatPrice(roundMoney(entered - adminCalculatedTotal))}`
    : '-';
}

function selectedAdminVehicle() {
  const vehicleId = numberOrNull(document.getElementById('admin-booking-vehicle')?.value);
  if (vehicleId == null) return null;
  return adminVehicles.find(vehicle => Number(vehicle.id) === vehicleId) || null;
}

function selectedAdminInsurance() {
  const selected = numberOrNull(document.querySelector('input[name="insurancePackageId"]:checked')?.value);
  if (selected == null) return null;
  return adminInsurancePackages.find(pkg => Number(pkg.id) === selected) || null;
}

function calculateAdminRentalDays() {
  const pickupDate = document.getElementById('admin-booking-pickup-date')?.value;
  const pickupTime = document.getElementById('admin-booking-pickup-time')?.value;
  const returnDate = document.getElementById('admin-booking-return-date')?.value;
  const returnTime = document.getElementById('admin-booking-return-time')?.value;
  if (!pickupDate || !pickupTime || !returnDate || !returnTime) return null;

  const pickup = new Date(`${pickupDate}T${pickupTime}`);
  const dropoff = new Date(`${returnDate}T${returnTime}`);
  if (!(dropoff > pickup)) return null;

  const msPerDay = 24 * 60 * 60 * 1000;
  return Math.max(1, Math.ceil((dropoff - pickup) / msPerDay));
}

function calculateSelectedAddonsTotal(rentalDays) {
  const selectedIds = Array.from(document.querySelectorAll('input[name="addonIds"]:checked'))
    .map(input => numberOrNull(input.value))
    .filter(value => value != null);
  return selectedIds.reduce((sum, addonId) => {
    const addon = adminAddons.find(item => Number(item.id) === addonId);
    if (!addon) return sum;
    const price = Number(addon.price || 0);
    const multiplier = addon.pricingType === 'DAILY' ? rentalDays : 1;
    return sum + (price * multiplier);
  }, 0);
}

function calculateSelectedInsuranceTotal(rentalDays) {
  const pkg = selectedAdminInsurance();
  if (!pkg) return 0;
  return Number(pkg.pricePerDay || 0) * rentalDays;
}

function createBookingPayload() {
  const form = document.getElementById('create-booking-form');
  const data = new FormData(form);
  const pickupInput = document.getElementById('admin-booking-pickup-location');
  const returnInput = document.getElementById('admin-booking-return-location');
  return {
    vehicleId: numberOrNull(data.get('vehicleId')),
    pickupLocation: stringValue(data.get('pickupLocation')),
    pickupAddress: stringValue(pickupInput?.dataset.address),
    pickupPlaceId: stringValue(pickupInput?.dataset.placeId),
    pickupDate: stringValue(data.get('pickupDate')),
    pickupTime: stringValue(data.get('pickupTime')),
    returnLocation: stringValue(data.get('returnLocation')),
    returnAddress: stringValue(returnInput?.dataset.address),
    returnPlaceId: stringValue(returnInput?.dataset.placeId),
    returnDate: stringValue(data.get('returnDate')),
    returnTime: stringValue(data.get('returnTime')),
    firstName: stringValue(data.get('firstName')),
    lastName: stringValue(data.get('lastName')),
    email: stringValue(data.get('email')),
    phoneCountryCode: stringValue(data.get('phoneCountryCode')),
    phoneNumber: stringValue(data.get('phoneNumber')),
    insurancePackageId: numberOrNull(data.get('insurancePackageId')),
    addonIds: data.getAll('addonIds').map(numberOrNull).filter(value => value != null),
    totalPrice: numberOrNull(data.get('totalPrice')),
    paymentSource: stringValue(data.get('paymentSource')),
    internalNote: stringValue(data.get('internalNote')),
  };
}

function validateCreatePayload(payload) {
  const errors = {};
  [
    'vehicleId', 'firstName', 'lastName', 'email', 'phoneCountryCode', 'phoneNumber',
    'pickupLocation', 'pickupDate', 'pickupTime', 'returnLocation', 'returnDate',
    'returnTime', 'insurancePackageId', 'totalPrice', 'paymentSource'
  ].forEach(field => {
    if (payload[field] == null || payload[field] === '') errors[field] = 'Required field';
  });
  if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) {
    errors.email = 'Enter a valid email address';
  }
  if (payload.totalPrice != null && Number(payload.totalPrice) <= 0) {
    errors.totalPrice = 'Enter a positive total';
  }
  if (payload.pickupDate && payload.pickupTime && payload.returnDate && payload.returnTime) {
    const pickup = new Date(`${payload.pickupDate}T${payload.pickupTime}`);
    const dropoff = new Date(`${payload.returnDate}T${payload.returnTime}`);
    if (!(dropoff > pickup)) {
      errors.returnDate = 'Return must be after pickup';
      errors.returnTime = 'Return must be after pickup';
    }
  }
  return errors;
}

function showFieldErrors(errors) {
  let firstErrorControl = null;
  Object.entries(errors).forEach(([field, message]) => {
    const el = document.querySelector(`[data-field-error="${field}"]`);
    if (!el) return;
    el.textContent = message;
    el.hidden = false;
    if (!firstErrorControl) {
      firstErrorControl = document.querySelector(`[name="${field}"]`)
        || document.getElementById(`admin-booking-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}`);
    }
  });
  if (firstErrorControl) {
    const focusTarget = firstErrorControl.type === 'hidden'
      ? document.getElementById(`${firstErrorControl.id}-button`)
      : firstErrorControl;
    if (focusTarget) focusTarget.focus({ preventScroll: true });
    firstErrorControl.closest('.admin-form-group')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
}

function clearCreateErrors() {
  const error = document.getElementById('create-booking-error');
  error.hidden = true;
  error.textContent = '';
  document.querySelectorAll('[data-field-error]').forEach(el => {
    el.hidden = true;
    el.textContent = '';
  });
}

function showCreateError(message) {
  const error = document.getElementById('create-booking-error');
  error.textContent = message;
  error.hidden = false;
  error.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

function stringValue(value) {
  return value == null ? '' : String(value).trim();
}

function numberOrNull(value) {
  if (value == null || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function statusLabel(value) {
  return {
    text: enumLabel('admin.status', value),
    cls: STATUS_CLASSES[value] || 'badge--neutral',
  };
}

function paymentLabel(value) {
  return {
    text: enumLabel('admin.payment', value),
    cls: PAYMENT_CLASSES[value] || 'badge--neutral',
  };
}

function paymentDisplay(status, method) {
  if (!status && !method) return tt('admin.detail.unavailable');
  const statusText = paymentLabel(status).text;
  const methodText = paymentMethodLabel(method);
  if (!methodText) return statusText;
  return `${statusText} - ${methodText}`;
}

function paymentMethodLabel(method) {
  if (!method) return null;
  const key = `admin.paymentMethod.${method}`;
  return hasTranslation(key) ? tt(key) : enumText(method);
}

function lifecycleBadges(b) {
  const statusLabel = b.noShow
    ? badge('badge--failed', tt('admin.lifecycle.noShow'))
    : badge(b.cancellationAllowed ? 'badge--confirmed' : 'badge--neutral',
        b.cancellationAllowed ? tt('admin.lifecycle.cancelable') : tt('admin.lifecycle.notCancelable'));
  const refundLabel = badge(b.refundEligible ? 'badge--confirmed' : 'badge--neutral',
    b.refundEligible ? tt('admin.lifecycle.refundEligible') : tt('admin.lifecycle.notRefundable'));
  return { status: statusLabel, refund: refundLabel };
}

function lifecycleStatusText(b) {
  if (b.noShow) return tt('admin.lifecycle.noShow');
  if (b.status === 'CANCELLED') return statusLabel(b.status).text;
  return b.cancellationAllowed ? tt('admin.lifecycle.cancelable') : tt('admin.lifecycle.notCancelable');
}

function badge(cls, text) {
  return `<span class="badge ${cls}">${esc(text)}</span>`;
}

function bestLocationDisplay(address, label) {
  return stringValue(address) || stringValue(label) || null;
}

function enumLabel(prefix, value) {
  if (!value) return tt('admin.detail.unavailable');
  const key = `${prefix}.${value}`;
  return hasTranslation(key) ? tt(key) : enumText(value);
}

function hasTranslation(key) {
  const lang = window.getLanguage ? window.getLanguage() : localStorage.getItem('rentcar-lang') || 'en';
  const translations = window.i18nTranslations || {};
  return Boolean(
    (translations[lang] && Object.prototype.hasOwnProperty.call(translations[lang], key)) ||
    (translations.en && Object.prototype.hasOwnProperty.call(translations.en, key))
  );
}

function enumText(value) {
  return value ? String(value).replace(/_/g, ' ') : null;
}

function valueOrDash(value) {
  return value == null || value === '' ? null : String(value);
}

function cell(value) {
  return esc(value == null || value === '' ? tt('admin.detail.unavailable') : value);
}

function esc(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDateTime(iso) {
  if (!iso) return null;
  const locale = localeForLang();
  const d = new Date(iso);
  return d.toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' });
}

function formatPrice(amount) {
  if (amount == null) return null;
  return new Intl.NumberFormat(localeForLang(), { style: 'currency', currency: 'EUR' }).format(Number(amount));
}

function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
}

function formatPercent(amount) {
  if (amount == null) return null;
  return `${new Intl.NumberFormat(localeForLang(), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(amount))}%`;
}

function localeForLang() {
  const lang = window.getLanguage ? window.getLanguage() : localStorage.getItem('rentcar-lang');
  if (lang === 'es') return 'es-ES';
  if (lang === 'tr') return 'tr-TR';
  return 'en-GB';
}

function tt(key, params) {
  return window.t ? window.t(key, params) : key;
}
