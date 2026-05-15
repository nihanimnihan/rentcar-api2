function buildPriceDetailsModalHtml(modalId, price, addonLines = []) {
  if (!price) {
    return "";
  }

  const feeRows = [];

  if (Number(price.oneWayFee) > 0) {
    feeRows.push(`
      <div class="rentcar-price-row-line">
        <span>${t('price.oneWayFee')}</span>
        <strong>€${price.oneWayFee}</strong>
      </div>
    `);
  }

  if (Number(price.premiumLocationFee) > 0) {
    feeRows.push(`
      <div class="rentcar-price-row-line">
        <span>${t('price.premiumLocationFee')}</span>
        <strong>€${price.premiumLocationFee}</strong>
      </div>
    `);
  }

  addonLines.forEach(addon => {
    if (Number(addon.totalPrice) > 0) {
      const safeName = typeof escapeHtml === "function" ? escapeHtml(String(addon.name)) : String(addon.name);
      feeRows.push(`
        <div class="rentcar-price-row-line">
          <span>${safeName}</span>
          <strong>€${addon.totalPrice}</strong>
        </div>
      `);
    }
  });

  return `
    <div class="rentcar-price-modal" id="${modalId}">
      <div class="rentcar-price-modal-backdrop" onclick="closePriceDetailsModal('${modalId}')"></div>

      <div class="rentcar-price-modal-card">
        <button class="rentcar-price-modal-close" onclick="closePriceDetailsModal('${modalId}')">
          <i class="icon-close"></i>
        </button>

        <h2 class="rentcar-price-modal-title">${t('price.title')}</h2>

        <div class="rentcar-price-section">
          <div class="rentcar-price-section-title">${t('price.rentalCharges')}</div>

          <div class="rentcar-price-row-line">
            <span>
              ${price.rentalDays} ${price.rentalDays > 1 ? t('price.rentalDays') : t('price.rentalDay')}
              x €${price.effectiveDailyPrice}
              ${Number(price.discountPercentage) > 0 ? `<span class="text-12 text-light-1"> (${price.discountPercentage}% off)</span>` : ""}
            </span>
            <strong>€${price.rentalCharge}</strong>
          </div>
        </div>

        ${feeRows.length > 0 ? `
          <div class="rentcar-price-divider"></div>
          <div class="rentcar-price-section">
            <div class="rentcar-price-section-title">${t('price.fees')}</div>
            ${feeRows.join("")}
          </div>
        ` : ""}

        <div class="rentcar-price-divider"></div>

        <div class="rentcar-price-total">
          <span>${t('price.total')}</span>
          <strong>€${calculatePriceDetailsTotal(price, addonLines)}</strong>
        </div>
      </div>
    </div>
  `;
}

function calculatePriceDetailsTotal(price, addonLines = []) {
  const addonsTotal = addonLines.reduce((sum, addon) => {
    return sum + Number(addon.totalPrice || 0);
  }, 0);

  return (Number(price.totalPrice || 0) + addonsTotal).toFixed(2);
}

function openPriceDetailsModal(modalId) {
  document.getElementById(modalId)?.classList.add("is-active");
}

function closePriceDetailsModal(modalId) {
  document.getElementById(modalId)?.classList.remove("is-active");
}

window.buildPriceDetailsModalHtml = buildPriceDetailsModalHtml;
window.openPriceDetailsModal = openPriceDetailsModal;
window.closePriceDetailsModal = closePriceDetailsModal;