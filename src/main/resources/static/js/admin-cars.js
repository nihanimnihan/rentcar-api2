/* ─── Admin Cars JS ──────────────────────────────────────────────────────────
   Manages the /admin/cars.html page:
   - Loads car list from GET /api/admin/cars
   - Renders table rows
   - Handles create/edit modal with chauffeur field toggle
   - Toggles active state via PATCH /api/admin/cars/{id}/active
   - Soft-deletes via DELETE /api/admin/cars/{id}
*/

const API_BASE = '/api/admin/cars';
const CAT_API  = '/api/admin/chauffeur-categories';

let chauffeurCategories = [];

// ── Init ─────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  await loadChauffeurCategories();
  await loadCars();

  document.getElementById('btn-new-car').addEventListener('click', () => openModal());
  document.getElementById('btn-cancel').addEventListener('click', closeModal);
  document.getElementById('modal-close').addEventListener('click', closeModal);
  document.getElementById('btn-save').addEventListener('click', saveCar);
  document.getElementById('field-chauffeur-available').addEventListener('change', toggleChauffeurFields);

  // Close modal when clicking the backdrop (outside the modal card)
  document.getElementById('modal-overlay').addEventListener('click', function (e) {
    if (e.target === this) closeModal();
  });
});

// ── Load chauffeur categories ─────────────────────────────────────────────────
async function loadChauffeurCategories() {
  try {
    const res = await fetch(CAT_API);
    if (res.ok) {
      chauffeurCategories = await res.json();
      const sel = document.getElementById('field-chauffeur-category');
      sel.innerHTML = '<option value="">-- select category --</option>';
      chauffeurCategories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id;
        opt.textContent = `${c.name} (${c.code})`;
        sel.appendChild(opt);
      });
    }
  } catch (e) {
    console.warn('Could not load chauffeur categories', e);
  }
}

// ── Load & render cars ────────────────────────────────────────────────────────
async function loadCars() {
  const loadingEl      = document.getElementById('loading');
  const emptyEl        = document.getElementById('empty');
  const tableContainer = document.getElementById('table-container');
  const errorEl        = document.getElementById('load-error');

  loadingEl.hidden = false;
  emptyEl.hidden = true;
  tableContainer.hidden = true;
  errorEl.hidden = true;

  try {
    const res = await fetch(API_BASE);
    if (!res.ok) throw new Error(`Status ${res.status}`);
    const cars = await res.json();

    loadingEl.hidden = true;
    if (cars.length === 0) {
      emptyEl.hidden = false;
    } else {
      renderTable(cars);
      tableContainer.hidden = false;
    }
  } catch (err) {
    loadingEl.hidden = true;
    errorEl.textContent = `Failed to load vehicles: ${err.message}`;
    errorEl.hidden = false;
  }
}

function renderTable(cars) {
  const tbody = document.getElementById('cars-tbody');
  tbody.innerHTML = '';

  cars.forEach(car => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${car.id}</td>
      <td>
        ${car.imageUrl
          ? `<img src="/${car.imageUrl}" alt="${car.brand}" class="admin-car-thumb">`
          : '<span class="admin-no-img">—</span>'}
      </td>
      <td>
        <strong>${esc(car.brand)} ${esc(car.model)}</strong>
        <br><small class="text-muted">${esc(car.displayClass || '')}</small>
      </td>
      <td>${esc(car.segment || '—')}</td>
      <td>${esc(car.vehicleType || '—')}</td>
      <td>${car.seats ?? '—'}</td>
      <td>${car.bags ?? '—'}</td>
      <td>€${Number(car.dailyPrice).toFixed(2)}</td>
      <td>
        ${car.chauffeurAvailable
          ? `<span class="admin-badge admin-badge--info">${car.chauffeurCategory ? esc(car.chauffeurCategory.code) : 'Yes'}</span>`
          : '<span class="admin-badge admin-badge--neutral">No</span>'}
      </td>
      <td>
        <span class="admin-badge ${car.active ? 'admin-badge--success' : 'admin-badge--danger'}">
          ${car.active ? 'Active' : 'Inactive'}
        </span>
      </td>
      <td class="admin-table-actions">
        <button class="admin-btn admin-btn--sm admin-btn--ghost" onclick="editCar(${car.id})">Edit</button>
        <button class="admin-btn admin-btn--sm ${car.active ? 'admin-btn--warning' : 'admin-btn--success'}"
                onclick="toggleActive(${car.id}, ${!car.active})">
          ${car.active ? 'Deactivate' : 'Activate'}
        </button>
        <button class="admin-btn admin-btn--sm admin-btn--danger" onclick="deleteCar(${car.id})">Delete</button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// ── Toggle active ─────────────────────────────────────────────────────────────
async function toggleActive(id, newValue) {
  try {
    const res = await fetch(`${API_BASE}/${id}/active?value=${newValue}`, { method: 'PATCH' });
    if (!res.ok) throw new Error(`Status ${res.status}`);
    await loadCars();
  } catch (err) {
    alert(`Failed to update status: ${err.message}`);
  }
}

// ── Soft delete ───────────────────────────────────────────────────────────────
async function deleteCar(id) {
  if (!confirm('Soft-delete this vehicle? (will set active=false and hide from customers)')) return;
  try {
    const res = await fetch(`${API_BASE}/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Status ${res.status}`);
    await loadCars();
  } catch (err) {
    alert(`Failed to delete: ${err.message}`);
  }
}

// ── Modal: open for create ────────────────────────────────────────────────────
function openModal(car = null) {
  const isEdit = car !== null;
  document.getElementById('modal-title').textContent = isEdit ? 'Edit Vehicle' : 'New Vehicle';
  document.getElementById('field-id').value            = isEdit ? car.id : '';
  document.getElementById('field-brand').value         = isEdit ? car.brand : '';
  document.getElementById('field-model').value         = isEdit ? car.model : '';
  document.getElementById('field-display-class').value = isEdit ? (car.displayClass || '') : '';
  document.getElementById('field-segment').value       = isEdit ? (car.segment || '') : '';
  document.getElementById('field-vehicle-type').value  = isEdit ? (car.vehicleType || '') : '';
  document.getElementById('field-transmission').value  = isEdit ? (car.transmission || '') : '';
  document.getElementById('field-fuel-type').value     = isEdit ? (car.fuelType || '') : '';
  document.getElementById('field-seats').value         = isEdit ? car.seats : '';
  document.getElementById('field-bags').value          = isEdit ? car.bags : '';
  document.getElementById('field-doors').value         = isEdit ? car.doors : '';
  document.getElementById('field-min-driver-age').value= isEdit ? car.minDriverAge : '';
  document.getElementById('field-daily-price').value   = isEdit ? car.dailyPrice : '';
  document.getElementById('field-image-url').value     = isEdit ? (car.imageUrl || '') : '';
  document.getElementById('field-air-conditioning').checked  = isEdit ? !!car.airConditioning : false;
  document.getElementById('field-premium').checked           = isEdit ? !!car.premium : false;
  document.getElementById('field-guaranteed-model').checked  = isEdit ? !!car.guaranteedModel : false;
  document.getElementById('field-active').checked            = isEdit ? !!car.active : true;
  document.getElementById('field-chauffeur-available').checked = isEdit ? !!car.chauffeurAvailable : false;

  const catSel = document.getElementById('field-chauffeur-category');
  catSel.value = isEdit && car.chauffeurCategory ? car.chauffeurCategory.id : '';
  document.getElementById('field-hourly-price').value = isEdit ? (car.hourlyPrice || '') : '';

  toggleChauffeurFields();
  document.getElementById('form-error').hidden = true;

  const overlay = document.getElementById('modal-overlay');
  overlay.hidden = false;
  requestAnimationFrame(() => overlay.classList.add('-visible'));
}

// ── Modal: open for edit ──────────────────────────────────────────────────────
async function editCar(id) {
  try {
    const res = await fetch(`${API_BASE}/${id}`);
    if (!res.ok) throw new Error(`Status ${res.status}`);
    const car = await res.json();
    openModal(car);
  } catch (err) {
    alert(`Failed to load car: ${err.message}`);
  }
}

// ── Modal: close ──────────────────────────────────────────────────────────────
function closeModal() {
  const overlay = document.getElementById('modal-overlay');
  overlay.classList.remove('-visible');
  overlay.hidden = true;
}

// ── Chauffeur fields visibility ───────────────────────────────────────────────
function toggleChauffeurFields() {
  const enabled = document.getElementById('field-chauffeur-available').checked;
  const section = document.getElementById('chauffeur-fields');
  section.style.display = enabled ? 'grid' : 'none';
}

// ── Save (create or update) ───────────────────────────────────────────────────
async function saveCar() {
  const errorEl = document.getElementById('form-error');
  errorEl.hidden = true;

  const id   = document.getElementById('field-id').value;
  const isEdit = !!id;
  const isChauffeur = document.getElementById('field-chauffeur-available').checked;

  // Client-side validation
  const brand       = document.getElementById('field-brand').value.trim();
  const model       = document.getElementById('field-model').value.trim();
  const displayClass= document.getElementById('field-display-class').value.trim();
  const segment     = document.getElementById('field-segment').value;
  const vehicleType = document.getElementById('field-vehicle-type').value;
  const transmission= document.getElementById('field-transmission').value;
  const fuelType    = document.getElementById('field-fuel-type').value;
  const seats       = parseInt(document.getElementById('field-seats').value, 10);
  const bags        = parseInt(document.getElementById('field-bags').value, 10);
  const doors       = parseInt(document.getElementById('field-doors').value, 10);
  const minDriverAge= parseInt(document.getElementById('field-min-driver-age').value, 10);
  const dailyPrice  = parseFloat(document.getElementById('field-daily-price').value);
  const imageUrl    = document.getElementById('field-image-url').value.trim();
  const airConditioning  = document.getElementById('field-air-conditioning').checked;
  const premium          = document.getElementById('field-premium').checked;
  const guaranteedModel  = document.getElementById('field-guaranteed-model').checked;
  const active           = document.getElementById('field-active').checked;
  const chauffeurCategoryId = isChauffeur
    ? (parseInt(document.getElementById('field-chauffeur-category').value, 10) || null)
    : null;
  const hourlyPrice = isChauffeur
    ? (parseFloat(document.getElementById('field-hourly-price').value) || null)
    : null;

  if (!brand || !model || !displayClass || !segment || !vehicleType || !transmission || !fuelType) {
    showFormError(errorEl, 'Please fill in all required fields.');
    return;
  }
  if (isNaN(seats) || seats < 1) { showFormError(errorEl, 'Seats must be ≥ 1.'); return; }
  if (isNaN(bags)  || bags  < 0) { showFormError(errorEl, 'Bags must be ≥ 0.');  return; }
  if (isNaN(doors) || doors < 2) { showFormError(errorEl, 'Doors must be ≥ 2.'); return; }
  if (isNaN(minDriverAge) || minDriverAge < 18) { showFormError(errorEl, 'Min driver age must be ≥ 18.'); return; }
  if (isNaN(dailyPrice) || dailyPrice <= 0) { showFormError(errorEl, 'Daily price must be > 0.'); return; }
  if (isChauffeur && !chauffeurCategoryId) { showFormError(errorEl, 'Chauffeur category is required.'); return; }
  if (isChauffeur && (!hourlyPrice || hourlyPrice <= 0)) { showFormError(errorEl, 'Hourly price must be > 0 for chauffeur cars.'); return; }

  const body = {
    brand, model, displayClass, segment, vehicleType, transmission, fuelType,
    seats, bags, doors, minDriverAge, dailyPrice,
    imageUrl: imageUrl || null,
    airConditioning, premium, guaranteedModel, active,
    chauffeurAvailable: isChauffeur,
    chauffeurCategoryId,
    hourlyPrice
  };

  try {
    const url    = isEdit ? `${API_BASE}/${id}` : API_BASE;
    const method = isEdit ? 'PUT' : 'POST';
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      showFormError(errorEl, data.message || `Server error ${res.status}`);
      return;
    }
    closeModal();
    await loadCars();
  } catch (err) {
    showFormError(errorEl, `Request failed: ${err.message}`);
  }
}

function showFormError(el, msg) {
  el.textContent = msg;
  el.hidden = false;
}

function esc(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
