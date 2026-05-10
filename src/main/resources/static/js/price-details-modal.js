function buildPriceDetailsModalHtml(modalId, price, addonLines = []) {
  if (!price) {
    return "";
  }

  const feeRows = [];

  if (Number(price.oneWayFee) > 0) {
    feeRows.push(`
      <div class="rentcar-price-row-line">
        <span>One-way fee</span>
        <strong>€${price.oneWayFee}</strong>
      </div>
    `);
  }

  if (Number(price.premiumLocationFee) > 0) {
    feeRows.push(`
      <div class="rentcar-price-row-line">
        <span>Premium location fee</span>
        <strong>€${price.premiumLocationFee}</strong>
      </div>
    `);
  }

  addonLines.forEach(addon => {
    if (Number(addon.totalPrice) > 0) {
      feeRows.push(`
        <div class="rentcar-price-row-line">
          <span>${addon.name}</span>
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

        <h2 class="rentcar-price-modal-title">PRICE DETAILS</h2>

        <div class="rentcar-price-section">
          <div class="rentcar-price-section-title">Rental charges</div>

          <div class="rentcar-price-row-line">
            <span>
              ${price.rentalDays} rental day${price.rentalDays > 1 ? "s" : ""}
              x €${price.discountedDailyPrice}
            </span>
            <strong>€${price.rentalCharge}</strong>
          </div>
        </div>

        ${feeRows.length > 0 ? `
          <div class="rentcar-price-divider"></div>
          <div class="rentcar-price-section">
            <div class="rentcar-price-section-title">Fees</div>
            ${feeRows.join("")}
          </div>
        ` : ""}

        <div class="rentcar-price-divider"></div>

        <div class="rentcar-price-total">
          <span>Total</span>
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