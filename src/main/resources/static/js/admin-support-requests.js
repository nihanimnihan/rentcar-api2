/**
 * Admin support request management page.
 */

let currentSupportRequests = [];
let currentSupportFilter = 'ALL';
let currentSupportDetail = null;
let currentSupportPage = 0;
let currentSupportTotalPages = 0;
const SUPPORT_PAGE_SIZE = 20;

const SUPPORT_STATUS_CLASSES = {
  OPEN: 'badge--pending',
  RESOLVED: 'badge--confirmed',
};

document.addEventListener('DOMContentLoaded', () => {
  bindSupportFilters();
  bindSupportTable();
  bindSupportPagination();
  bindSupportDetailModal();
  loadSupportRequests();
});

document.addEventListener('languageChanged', () => {
  renderSupportRequests();
  renderSupportPagination();
  if (currentSupportDetail) renderSupportDetail(currentSupportDetail);
});

async function loadSupportRequests() {
  const loading = document.getElementById('loading');
  const tableContainer = document.getElementById('table-container');
  const empty = document.getElementById('empty');
  const pagination = document.getElementById('support-pagination');
  const authError = document.getElementById('auth-error');
  const loadError = document.getElementById('load-error');

  loading.hidden = false;
  tableContainer.hidden = true;
  empty.hidden = true;
  pagination.hidden = true;
  loadError.hidden = true;

  try {
    const params = new URLSearchParams({
      page: String(currentSupportPage),
      size: String(SUPPORT_PAGE_SIZE),
    });
    if (currentSupportFilter !== 'ALL') {
      params.set('status', currentSupportFilter);
    }

    const res = await fetch(`/api/admin/support-requests?${params.toString()}`);

    if (res.status === 401 || res.status === 403) {
      loading.hidden = true;
      authError.hidden = false;
      return;
    }

    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const page = await res.json();
    currentSupportRequests = page.content || [];
    currentSupportPage = page.page || 0;
    currentSupportTotalPages = page.totalPages || 0;
    loading.hidden = true;
    renderSupportRequests();
    renderSupportPagination();
  } catch (err) {
    loading.hidden = true;
    loadError.textContent = tt('admin.support.loadError', { message: err.message });
    loadError.hidden = false;
  }
}

function bindSupportFilters() {
  document.querySelectorAll('[data-support-filter]').forEach((button) => {
    button.addEventListener('click', () => {
      currentSupportFilter = button.getAttribute('data-support-filter');
      currentSupportPage = 0;
      document.querySelectorAll('[data-support-filter]').forEach((item) => {
        item.classList.toggle('-active', item === button);
      });
      loadSupportRequests();
    });
  });
}

function bindSupportPagination() {
  document.getElementById('support-prev-page').addEventListener('click', () => {
    if (currentSupportPage <= 0) return;
    currentSupportPage -= 1;
    loadSupportRequests();
  });

  document.getElementById('support-next-page').addEventListener('click', () => {
    if (currentSupportPage >= currentSupportTotalPages - 1) return;
    currentSupportPage += 1;
    loadSupportRequests();
  });
}

function bindSupportTable() {
  document.getElementById('support-requests-tbody').addEventListener('click', (event) => {
    const detailButton = event.target.closest('[data-support-detail-id]');
    if (detailButton) {
      openSupportDetail(detailButton.getAttribute('data-support-detail-id'));
      return;
    }

    const resolveButton = event.target.closest('[data-support-resolve-id]');
    if (resolveButton) {
      resolveSupportRequest(resolveButton.getAttribute('data-support-resolve-id'), resolveButton);
    }
  });
}

function bindSupportDetailModal() {
  const overlay = document.getElementById('support-detail-overlay');
  document.getElementById('support-detail-close').addEventListener('click', closeSupportDetail);
  document.getElementById('support-detail-footer-close').addEventListener('click', closeSupportDetail);
  document.getElementById('support-detail-resolve').addEventListener('click', () => {
    if (currentSupportDetail) resolveSupportRequest(currentSupportDetail.id);
  });
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) closeSupportDetail();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !overlay.hidden) closeSupportDetail();
  });
}

function renderSupportRequests() {
  const tableContainer = document.getElementById('table-container');
  const empty = document.getElementById('empty');
  const tbody = document.getElementById('support-requests-tbody');
  const requests = currentSupportRequests;

  tableContainer.hidden = requests.length === 0;
  empty.hidden = requests.length !== 0;
  empty.textContent = currentSupportTotalPages === 0 && currentSupportFilter === 'ALL'
    ? tt('admin.support.empty')
    : tt('admin.support.filteredEmpty');
  tbody.innerHTML = requests.map(renderSupportRow).join('');
}

function renderSupportPagination() {
  const pagination = document.getElementById('support-pagination');
  const prev = document.getElementById('support-prev-page');
  const next = document.getElementById('support-next-page');
  const info = document.getElementById('support-page-info');

  pagination.hidden = currentSupportTotalPages <= 1;
  prev.disabled = currentSupportPage <= 0;
  next.disabled = currentSupportPage >= currentSupportTotalPages - 1;
  info.textContent = tt('admin.support.pagination.page', {
    page: currentSupportTotalPages === 0 ? 0 : currentSupportPage + 1,
    totalPages: currentSupportTotalPages,
  });
}

function renderSupportRow(request) {
  const status = supportStatusLabel(request.status);
  const resolveAction = request.status === 'OPEN'
    ? `<span class="admin-action-sep">|</span>
       <button type="button" class="admin-link admin-link-button" data-support-resolve-id="${esc(request.id)}">
         ${esc(tt('admin.support.resolve'))}
       </button>`
    : '';

  return `
    <tr>
      <td>${esc(supportTopicLabel(request.topic))}</td>
      <td>${cell(request.email)}</td>
      <td>${cell(request.fullPhone)}</td>
      <td><span class="monospace">${cell(request.bookingReference)}</span></td>
      <td><span class="badge ${status.cls}">${esc(status.text)}</span></td>
      <td>${cell(formatDateTime(request.createdAt))}</td>
      <td>
        <span class="admin-row-actions">
          <button type="button" class="admin-link admin-link-button" data-support-detail-id="${esc(request.id)}">
            ${esc(tt('admin.support.viewDetails'))}
          </button>
          ${resolveAction}
        </span>
      </td>
    </tr>
  `;
}

async function openSupportDetail(id) {
  const overlay = document.getElementById('support-detail-overlay');
  const body = document.getElementById('support-detail-body');
  const error = document.getElementById('support-detail-error');
  const loading = document.getElementById('support-detail-loading');

  currentSupportDetail = null;
  body.innerHTML = '';
  error.hidden = true;
  loading.hidden = false;
  overlay.hidden = false;
  requestAnimationFrame(() => overlay.classList.add('-visible'));

  try {
    const res = await fetch(`/api/admin/support-requests/${encodeURIComponent(id)}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    currentSupportDetail = await res.json();
    loading.hidden = true;
    renderSupportDetail(currentSupportDetail);
  } catch (err) {
    loading.hidden = true;
    error.textContent = tt('admin.support.detailLoadError', { message: err.message });
    error.hidden = false;
  }
}

function renderSupportDetail(request) {
  const body = document.getElementById('support-detail-body');
  const resolveButton = document.getElementById('support-detail-resolve');
  const status = supportStatusLabel(request.status);

  resolveButton.hidden = request.status !== 'OPEN';
  resolveButton.disabled = request.status !== 'OPEN';

  body.innerHTML = `
    <div class="admin-detail-hero">
      <div>
        <span class="admin-detail-kicker">${esc(tt('admin.support.topic'))}</span>
        <h3 class="admin-detail-reference">${esc(supportTopicLabel(request.topic))}</h3>
      </div>
      <div class="admin-detail-badges">
        <span class="badge ${status.cls}">${esc(status.text)}</span>
      </div>
    </div>

    ${detailSection('admin.support.detailTitle', [
      detailItem('admin.support.email', request.email),
      detailItem('admin.support.phoneCountryCode', request.phoneCountryCode),
      detailItem('admin.support.phoneNumber', request.phoneNumber),
      detailItem('admin.support.fullPhone', request.fullPhone),
      detailItem('admin.support.bookingReference', request.bookingReference),
      detailItem('admin.support.createdAt', formatDateTime(request.createdAt)),
      detailItem('admin.support.updatedAt', formatDateTime(request.updatedAt)),
      detailItem('admin.support.status', status.text)
    ])}

    <section class="admin-detail-section">
      <h4>${esc(tt('admin.support.message'))}</h4>
      <p class="admin-detail-message">${esc(request.message || tt('admin.support.unavailable'))}</p>
    </section>
  `;
}

async function resolveSupportRequest(id, button) {
  const error = document.getElementById('resolve-error');
  const originalText = button ? button.textContent : null;
  error.hidden = true;

  if (button) {
    button.disabled = true;
    button.textContent = tt('admin.support.resolving');
  }

  try {
    const res = await fetch(`/api/admin/support-requests/${encodeURIComponent(id)}/resolve`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const updated = await res.json();
    upsertSupportRequest(updated);
    if (currentSupportDetail && String(currentSupportDetail.id) === String(updated.id)) {
      currentSupportDetail = updated;
      renderSupportDetail(currentSupportDetail);
    }
    await loadSupportRequests();
  } catch (err) {
    error.textContent = tt('admin.support.resolveError', { message: err.message });
    error.hidden = false;
    if (button) {
      button.disabled = false;
      button.textContent = originalText;
    }
  }
}

function upsertSupportRequest(updated) {
  currentSupportRequests = currentSupportRequests.map((request) => {
    if (String(request.id) !== String(updated.id)) return request;
    return {
      id: updated.id,
      topic: updated.topic,
      email: updated.email,
      phoneCountryCode: updated.phoneCountryCode,
      phoneNumber: updated.phoneNumber,
      fullPhone: updated.fullPhone,
      bookingReference: updated.bookingReference,
      status: updated.status,
      createdAt: updated.createdAt,
    };
  });
}

function closeSupportDetail() {
  const overlay = document.getElementById('support-detail-overlay');
  overlay.classList.remove('-visible');
  overlay.hidden = true;
  currentSupportDetail = null;
}

function supportStatusLabel(value) {
  return {
    text: enumLabel('admin.support.status', value),
    cls: SUPPORT_STATUS_CLASSES[value] || 'badge--neutral',
  };
}

function supportTopicLabel(value) {
  return enumLabel('admin.support.topic', value);
}

function enumLabel(prefix, value) {
  if (!value) return tt('admin.support.unavailable');
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

function detailItem(labelKey, rawValue) {
  const value = rawValue == null || rawValue === '' ? tt('admin.support.unavailable') : rawValue;
  return `
    <div class="admin-detail-item">
      <dt>${esc(tt(labelKey))}</dt>
      <dd>${esc(value)}</dd>
    </div>
  `;
}

function cell(value) {
  return esc(value == null || value === '' ? tt('admin.support.unavailable') : value);
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
  const d = new Date(iso);
  return d.toLocaleString(localeForLang(), { dateStyle: 'short', timeStyle: 'short' });
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
