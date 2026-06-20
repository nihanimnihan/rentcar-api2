/**
 * Admin bookings list page with in-page booking detail modal.
 */

let currentBookings = [];
let currentDetail = null;

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
};

document.addEventListener('DOMContentLoaded', () => {
  bindBookingDetailModal();
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

function renderBookings(bookings) {
  const tbody = document.getElementById('bookings-tbody');
  tbody.innerHTML = bookings.map(renderRow).join('');
}

function renderRow(b) {
  const bookingStatus = statusLabel(b.status);
  const paymentStatus = paymentLabel(b.paymentStatus);

  return `
    <tr>
      <td><span class="monospace">${esc(b.bookingReference)}</span></td>
      <td><span class="badge ${bookingStatus.cls}">${esc(bookingStatus.text)}</span></td>
      <td><span class="badge ${paymentStatus.cls}">${esc(paymentStatus.text)}</span></td>
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
      pickupLocation: booking.pickupLocation,
      dropoffLocation: booking.dropoffLocation,
      paymentMethod: booking.paymentMethod,
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
      detailItem('admin.detail.pickupLocation', b.pickupLocation || null),
      detailItem('admin.detail.dropoffDate', formatDateTime(b.dropoffDateTime)),
      detailItem('admin.detail.dropoffLocation', b.dropoffLocation || null)
    ])}

    ${detailSection('admin.detail.pricing', [
      detailItem('admin.detail.baseDailyPrice', formatPrice(b.baseDailyPrice)),
      detailItem('admin.detail.effectiveDailyPrice', formatPrice(b.effectiveDailyPrice)),
      detailItem('admin.detail.discount', formatPercent(b.discountPercentage)),
      detailItem('admin.detail.rentalCharge', formatPrice(b.rentalCharge)),
      detailItem('admin.detail.oneWayFee', formatPrice(b.oneWayFee)),
      detailItem('admin.detail.premiumLocationFee', formatPrice(b.premiumLocationFee)),
      detailItem('admin.detail.tax', formatPrice(b.tax)),
      detailItem('admin.detail.addonCharge', formatPrice(b.addonCharge)),
      detailItem('admin.detail.bookingOptionFee', formatPrice(b.bookingOptionDailyFee)),
      detailItem('admin.detail.totalPrice', formatPrice(b.totalPrice), true)
    ])}

    ${detailSection('admin.detail.paymentRefund', [
      detailItem('admin.detail.paymentStatus', payment.text),
      detailItem('admin.detail.paymentMethod', enumText(b.paymentMethod))
    ])}

    ${detailSection('admin.detail.cancellation', [
      detailItem('admin.detail.cancellationReason', b.cancellationReason || tt('admin.detail.none')),
      detailItem('admin.detail.cancelledAt', formatDateTime(b.cancelledAt)),
      detailItem('admin.detail.notes', b.notes || tt('admin.detail.none'))
    ])}
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
