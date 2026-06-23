let insurancePackages = [];
let selectedInsuranceId = null;
let selectedCar = null;

const insuranceMileageOption = new URLSearchParams(window.location.search).get("mileageOption") || "INCLUDED";

document.addEventListener("DOMContentLoaded", () => {
  loadInsurancePage();
  document.getElementById("continueButton")?.addEventListener("click", goToAddons);
});

async function loadInsurancePage() {
  const params = new URLSearchParams(window.location.search);
  const carId = params.get("carId");

  if (!carId) {
    showInsuranceError(t('error.noCarSelected'));
    return;
  }

  try {
    const lang = typeof getLanguage === "function" ? getLanguage() : "en";
    const [carRes, insuranceRes] = await Promise.all([
      fetch(`/api/cars/${encodeURIComponent(carId)}${window.location.search}`),
      fetch(`/api/insurance-packages/active?lang=${encodeURIComponent(lang)}`)
    ]);

    if (!carRes.ok) {
      showInsuranceError(t('error.failedToLoadCar'));
      return;
    }
    if (!insuranceRes.ok) {
      showInsuranceError(t('protection.loadError'));
      return;
    }

    selectedCar = await carRes.json();
    insurancePackages = await insuranceRes.json();
    selectedInsuranceId = Number(params.get("insurancePackageId")) || recommendedInsuranceId();
  } catch (err) {
    console.error("Failed to load insurance page:", err);
    showInsuranceError(t('error.failedToLoadPage'));
    return;
  }

  renderInsuranceCards();
  renderInsuranceSummary();
}

function recommendedInsuranceId() {
  const recommended = insurancePackages.find(item => item.recommended);
  const first = insurancePackages[0];
  return Number((recommended || first)?.id) || null;
}

function renderInsuranceCards() {
  const container = document.getElementById("insuranceList");
  if (!container) return;

  if (!insurancePackages.length) {
    container.innerHTML = `<p class="text-15 text-light-1">${t('protection.noPackages')}</p>`;
    return;
  }

  container.innerHTML = insurancePackages.map(pkg => renderInsuranceCard(pkg)).join("");
}

function renderInsuranceCard(pkg) {
  const selected = Number(pkg.id) === Number(selectedInsuranceId);
  const daily = Number(pkg.pricePerDay || 0);
  const coverage = Array.isArray(pkg.coverageItems) ? pkg.coverageItems : [];
  return `
    <label class="rentcar-protection-card${selected ? ' is-selected' : ''}" data-insurance-id="${esc(pkg.id)}">
      <input type="radio" name="insurancePackageId" value="${esc(pkg.id)}" ${selected ? 'checked' : ''} onchange="selectInsurance(${esc(pkg.id)})">
      <span class="rentcar-protection-radio"></span>
      <div class="rentcar-protection-card__top">
        <div>
          ${pkg.badge ? `<span class="rentcar-protection-badge">${esc(pkg.badge)}</span>` : ""}
          <h2>${esc(pkg.name)}</h2>
          <p>${esc(pkg.description)}</p>
        </div>
        <div class="rentcar-protection-price">
          <strong>${formatMoney(daily)}</strong>
          <span>${t('car.perDay')}</span>
        </div>
      </div>
      <div class="rentcar-protection-deposit">
        <span>${t('protection.deposit')}</span>
        <strong>${formatMoney(pkg.depositAmount)}</strong>
      </div>
      <ul class="rentcar-protection-coverage">
        ${coverage.map(item => `
          <li class="${item.included ? 'is-included' : 'is-excluded'}">
            <span>${item.included ? '✓' : '−'}</span>
            <div>
              <strong>${esc(item.title)}</strong>
              <small>${esc(item.description)}</small>
            </div>
          </li>
        `).join("")}
      </ul>
    </label>
  `;
}

function selectInsurance(id) {
  selectedInsuranceId = Number(id);
  document.querySelectorAll(".rentcar-protection-card").forEach(card => {
    card.classList.toggle("is-selected", Number(card.dataset.insuranceId) === selectedInsuranceId);
  });
  renderInsuranceSummary();
}

function renderInsuranceSummary() {
  if (!selectedCar) return;
  const params = new URLSearchParams(window.location.search);
  const selectedInsurance = insurancePackages.find(item => Number(item.id) === Number(selectedInsuranceId));
  renderBookingSummary({
    car: selectedCar,
    params,
    mileageOption: insuranceMileageOption,
    selectedAddonIds: [],
    availableAddons: [],
    selectedInsurance,
    pageType: 'addons'
  });

  const { total } = calcBookingTotal(selectedCar, insuranceMileageOption, [], [], selectedInsurance);
  setText("insuranceHeaderTotal", formatMoney(total));
}

function goToAddons() {
  if (!selectedInsuranceId) {
    showInsuranceError(t('protection.required'));
    return;
  }
  const params = new URLSearchParams(window.location.search);
  params.set("insurancePackageId", selectedInsuranceId);
  window.location.href = `addons.html?${params.toString()}`;
}

function showInsuranceError(message) {
  const container = document.getElementById("insuranceList");
  if (container) {
    container.innerHTML = `
      <div style="padding:40px 0;text-align:center">
        <p class="text-16 text-danger mb-20">${esc(message)}</p>
        <a href="cars.html${window.location.search}" class="button h-50 px-30 bg-dark-1 text-white rounded-8 fw-600">
          ${t('addon.backToSearch')}
        </a>
      </div>
    `;
  }
  const continueBtn = document.getElementById("continueButton");
  if (continueBtn) {
    continueBtn.disabled = true;
    continueBtn.style.opacity = "0.4";
  }
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function formatMoney(value) {
  return `€${Number(value || 0).toFixed(2)}`;
}

function esc(value) {
  return typeof escapeHtml === "function" ? escapeHtml(value) : String(value ?? "");
}

window.selectInsurance = selectInsurance;

document.addEventListener('languageChanged', function () {
  if (selectedCar) loadInsurancePage();
});
