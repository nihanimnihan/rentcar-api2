// booking-summary.js — shared booking summary card renderer
// Renders a unified summary card into #bookingSummaryCard for both Addons and Review

// ── Total calculation (unchanged) ──────────────────────────────────────────
function calcBookingTotal(car, mileageOption, selectedAddonIds, availableAddons, selectedInsurance) {
  const rentalDays = car?.priceBreakdown?.rentalDays || 1;
  const base = Number(car?.totalPrice || 0);

  const unlimitedKmCharge = mileageOption === "UNLIMITED"
    ? Number(car?.priceBreakdown?.unlimitedKmDailyPrice || 0) * rentalDays
    : 0;

  const addonsTotal = Array.from(selectedAddonIds || []).reduce((sum, id) => {
    const addon = (availableAddons || []).find(a => a.id === id);
    if (!addon) return sum;
    const addonPrice = addon.pricingType === "DAILY"
      ? Number(addon.price) * rentalDays
      : Number(addon.price);
    return sum + addonPrice;
  }, 0);

  const insuranceTotal = selectedInsurance
    ? Number(selectedInsurance.pricePerDay || selectedInsurance.insuranceDailyPriceSnapshot || 0) * rentalDays
    : 0;

  return {
    total: base + unlimitedKmCharge + addonsTotal + insuranceTotal,
    rentalDays,
    base,
    unlimitedKmCharge,
    addonsTotal,
    insuranceTotal
  };
}

// ── Renderer ───────────────────────────────────────────────────────────────
/**
 * renderBookingSummary({ car, params, mileageOption, selectedAddonIds, availableAddons, pageType })
 * pageType: 'addons' | 'review' (controls whether Total block appears)
 */
function renderBookingSummary({ car, params, mileageOption, selectedAddonIds, availableAddons, selectedInsurance, pageType = 'addons' } = {}) {
  if (!car) return;
  const container = document.getElementById('bookingSummaryCard');
  if (!container) return;

  // If already rendered for this page type, avoid re-creating markup.
  if (container.dataset.page !== pageType) {
    container.dataset.page = pageType;
    container.innerHTML = _buildSummaryHtml(pageType);
  }

  // Populate core fields
  const carName = `${car.brand || ''} ${car.model || ''}`.trim();
  _setText('summaryCarName', carName || t('summary.car'));
  _setText('summaryCarSubtitle', `${car.segment || ''} ${car.vehicleType || ''}`.trim() || t('summary.orSimilar'));

  const imgEl = document.getElementById('summaryCarImage');
  if (imgEl) {
    imgEl.src = car.imageUrl || 'img/lists/car/1/1.png';
    imgEl.alt = carName || 'car';
    imgEl.onerror = () => { imgEl.src = 'img/lists/car/1/1.png'; };
  }

  _setText('summaryPickupLocation', params?.get('pickupLocation') || '-');
  _setText('summaryDropoffLocation', params?.get('dropoffLocation') || '-');
  _setText('summaryPickupDate', _formatDT(params?.get('pickupDateTime')));
  _setText('summaryDropoffDate', _formatDT(params?.get('dropoffDateTime')));

  const totals = calcBookingTotal(car, mileageOption, selectedAddonIds || [], availableAddons || [], selectedInsurance);

  _renderInsurance(selectedInsurance, totals.insuranceTotal);
  _renderAddedFeatures(mileageOption, totals.unlimitedKmCharge, selectedAddonIds || [], availableAddons || [], totals.rentalDays);

  // Only render total for review pageType
  if (pageType === 'review') {
    const totalEl = document.getElementById('summaryTotal');
    if (totalEl) totalEl.textContent = _formatMoney(totals.total);
  }

  // Conditionally render Included features if backend provides them
  const included = car.includedFeatures || car.included || [];
  const includedEl = document.getElementById('summaryIncludedFeatures');
  // Always show the INCLUDED section placeholder; populate it only if backend provides data
  if (includedEl) {
    if (Array.isArray(included) && included.length > 0) {
      includedEl.innerHTML = included.map(it => `<div class="text-15 mb-8">✓ ${_esc(it)}</div>`).join('');
    } else {
      includedEl.innerHTML = '';
    }
  }
}

// ── Markup builder ─────────────────────────────────────────────────────────
function _buildSummaryHtml(pageType) {
  // Keep the Addons summary design as source of truth — classes reused from brand-theme.css
  const showTotal = pageType === 'review';

  return `
    <div class="rentcar-addon-summary">
      <div class="d-flex items-center mb-20">
        <img id="summaryCarImage" class="rentcar-summary-car-image" src="" alt="">
        <div>
          <h3 id="summaryCarName" class="rentcar-summary-title">Car</h3>
          <div id="summaryCarSubtitle" class="rentcar-summary-subtitle">${t('summary.orSimilar')}</div>
        </div>
      </div>

      <div class="rentcar-summary-divider"></div>

      <div class="rentcar-summary-route">
        <div>
          <div class="rentcar-summary-section-label" data-i18n="summary.pickup">${t('summary.pickup')}</div>
          <div id="summaryPickupLocation" class="rentcar-summary-main-text">-</div>
          <div id="summaryPickupDate" class="rentcar-summary-muted-text">-</div>
        </div>

        <div class="rentcar-summary-arrow">↓</div>

        <div>
          <div class="rentcar-summary-section-label" data-i18n="summary.return">${t('summary.return')}</div>
          <div id="summaryDropoffLocation" class="rentcar-summary-main-text">-</div>
          <div id="summaryDropoffDate" class="rentcar-summary-muted-text">-</div>
        </div>
      </div>

      <div class="rentcar-summary-divider"></div>

      <div id="includedBlock">
        <div class="rentcar-summary-section-label" data-i18n="summary.addedIncludedFeatures">${t('summary.includedFeatures')}</div>
        <div id="summaryIncludedFeatures" class="rentcar-summary-included-list"></div>
      </div>

      <div class="rentcar-summary-divider"></div>

      <div>
        <div class="rentcar-summary-section-label" data-i18n="summary.protection">${t('summary.protection')}</div>
        <div id="summaryInsurance" class="rentcar-summary-added-list">${t('summary.noProtection')}</div>
      </div>

      <div class="rentcar-summary-divider"></div>

      <div>
        <div class="rentcar-summary-section-label" data-i18n="summary.addedFeatures">${t('summary.addedFeatures')}</div>
        <div id="summaryAddedFeatures" class="rentcar-summary-added-list">${t('summary.noAddons')}</div>
      </div>

      ${showTotal ? `
        <div class="rentcar-summary-divider"></div>
        <div>
          <div class="d-flex justify-between items-center">
            <span class="text-18 fw-700" data-i18n="summary.total">${t('summary.total')}</span>
            <strong id="summaryTotal" class="text-26 fw-800">€-</strong>
          </div>
          <button type="button" id="reviewSummaryPriceDetailsBtn" data-summary-price-details class="rentcar-price-details mt-8" data-i18n="price.title">${t('price.title')}</button>
        </div>
      ` : ''}

    </div>
  `;
}

// ── Helpers ─────────────────────────────────────────────────────────────────
function _renderAddedFeatures(mileageOption, unlimitedKmCharge, selectedAddonIds, availableAddons, rentalDays) {
  const container = document.getElementById('summaryAddedFeatures');
  if (!container) return;

  const lines = [];
  if (mileageOption === 'UNLIMITED') {
    lines.push(`<div class="d-flex justify-between mb-8"><span>✓ ${t('car.unlimitedKm')}</span><strong>${_formatMoney(unlimitedKmCharge)}</strong></div>`);
  }

  Array.from(selectedAddonIds || []).forEach(addonId => {
    const addon = (availableAddons || []).find(a => a.id === addonId);
    if (!addon) return;
    const price = addon.pricingType === 'DAILY' ? Number(addon.price) * rentalDays : Number(addon.price);
    lines.push(`<div class="d-flex justify-between mb-8"><span>✓ ${_esc(localAddonName(addon))}</span><strong>${_formatMoney(price)}</strong></div>`);
  });

  container.innerHTML = lines.length > 0 ? lines.join('') : t('summary.noAddons');
}

function _renderInsurance(selectedInsurance, insuranceTotal) {
  const container = document.getElementById('summaryInsurance');
  if (!container) return;
  if (!selectedInsurance) {
    container.innerHTML = t('summary.noProtection');
    return;
  }
  const name = selectedInsurance.name || selectedInsurance.insuranceNameSnapshot || selectedInsurance.code || t('summary.protection');
  const deposit = selectedInsurance.depositAmount ?? selectedInsurance.depositAmountSnapshot;
  container.innerHTML = `
    <div class="d-flex justify-between mb-8"><span>✓ ${_esc(name)}</span><strong>${_formatMoney(insuranceTotal || 0)}</strong></div>
    <div class="text-13 text-light-1">${t('protection.deposit')}: ${_formatMoney(deposit || 0)}</div>
  `;
}

function _setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function _formatMoney(amount) { return `€${Number(amount).toFixed(2)}`; }

function _formatDT(isoString) {
  if (!isoString) return '-';
  try {
    const d = new Date(isoString);
    return d.toLocaleString(getLanguage() === 'es' ? 'es-ES' : 'en-GB', {
      weekday: 'short', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  } catch { return isoString; }
}

function _esc(str) { if (typeof escapeHtml === 'function') return escapeHtml(str); if (!str) return ''; return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
