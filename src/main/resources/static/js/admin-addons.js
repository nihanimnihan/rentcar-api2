/* ============================================================
   Admin add-ons management — fetch, render, CRUD via modal
   API: /api/admin/addons
   ============================================================ */

const API = '/api/admin/addons';

// ── State ────────────────────────────────────────────────────────────────────

let editingId = null;

// ── Bootstrap ────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  loadAddons();
  bindModal();
});

// ── Load & Render ─────────────────────────────────────────────────────────────

async function loadAddons() {
  setLoading(true);

  try {
    const res = await fetch(API);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const addons = await res.json();

    setLoading(false);

    if (!addons.length) {
      show('empty');
      return;
    }

    renderTable(addons);
    show('table-container');
  } catch (err) {
    setLoading(false);
    showError('load-error', 'Could not load add-ons: ' + err.message);
  }
}

function renderTable(addons) {
  const tbody = document.getElementById('addons-tbody');
  tbody.innerHTML = '';

  addons.forEach(a => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><span class="monospace">${esc(a.code || '—')}</span></td>
      <td>${esc(a.name)}</td>
      <td>€${Number(a.price).toFixed(2)}</td>
      <td><span class="badge badge--neutral">${esc(a.pricingType)}</span></td>
      <td>${a.recommended ? '<span class="badge badge--confirmed">Yes</span>' : '<span class="badge badge--neutral">No</span>'}</td>
      <td>${a.active
            ? '<span class="badge badge--confirmed">Active</span>'
            : '<span class="badge badge--cancelled">Inactive</span>'}</td>
      <td>
        <button class="admin-link" onclick="openEdit(${a.id})">Edit</button>
        <span class="admin-action-sep">|</span>
        <button class="admin-link" onclick="toggleActive(${a.id}, ${!a.active})">${a.active ? 'Deactivate' : 'Activate'}</button>
        <span class="admin-action-sep">|</span>
        <button class="admin-link admin-link--danger" onclick="softDelete(${a.id}, '${esc(a.name)}')">Delete</button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// ── Actions ──────────────────────────────────────────────────────────────────

async function toggleActive(id, newValue) {
  try {
    const res = await fetch(`${API}/${id}/active?value=${newValue}`, { method: 'PATCH' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    loadAddons();
  } catch (err) {
    alert('Could not update add-on: ' + err.message);
  }
}

async function softDelete(id, name) {
  if (!confirm(`Deactivate "${name}"?\n\nThe add-on will be set to inactive and hidden from customers. Historical booking records are preserved.`)) return;

  try {
    const res = await fetch(`${API}/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    loadAddons();
  } catch (err) {
    alert('Could not delete add-on: ' + err.message);
  }
}

// ── Modal ─────────────────────────────────────────────────────────────────────

function bindModal() {
  document.getElementById('btn-new-addon').addEventListener('click', openCreate);
  document.getElementById('btn-cancel').addEventListener('click', closeModal);
  document.getElementById('modal-close').addEventListener('click', closeModal);
  document.getElementById('btn-save').addEventListener('click', saveAddon);

  // Close on backdrop click
  document.getElementById('modal-overlay').addEventListener('click', function(e) {
    if (e.target === this) closeModal();
  });
}

function openCreate() {
  editingId = null;
  document.getElementById('modal-title').textContent = 'New Add-on';
  document.getElementById('addon-form').reset();
  document.getElementById('field-active').checked = true;
  document.getElementById('field-id').value = '';
  hideError('form-error');
  showModal();
}

async function openEdit(id) {
  editingId = id;
  document.getElementById('modal-title').textContent = 'Edit Add-on';
  hideError('form-error');

  try {
    const res = await fetch(`${API}/${id}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const a = await res.json();

    document.getElementById('field-id').value = a.id;
    document.getElementById('field-name').value = a.name || '';
    document.getElementById('field-name-es').value = a.nameEs || '';
    document.getElementById('field-code').value = a.code || '';
    document.getElementById('field-description').value = a.description || '';
    document.getElementById('field-description-es').value = a.descriptionEs || '';
    document.getElementById('field-price').value = a.price;
    document.getElementById('field-pricing-type').value = a.pricingType;
    document.getElementById('field-image-url').value = a.imageUrl || '';
    document.getElementById('field-recommended').checked = !!a.recommended;
    document.getElementById('field-active').checked = !!a.active;

    showModal();
  } catch (err) {
    alert('Could not load add-on: ' + err.message);
  }
}

async function saveAddon() {
  hideError('form-error');

  const name = document.getElementById('field-name').value.trim();
  const code = document.getElementById('field-code').value.trim();
  const price = document.getElementById('field-price').value;

  if (!name) { showError('form-error', 'Name is required.'); return; }
  if (!code) { showError('form-error', 'Code is required.'); return; }
  if (price === '' || isNaN(Number(price)) || Number(price) < 0) {
    showError('form-error', 'Price must be a number ≥ 0.');
    return;
  }

  const payload = {
    name,
    nameEs: document.getElementById('field-name-es').value.trim() || null,
    code,
    description: document.getElementById('field-description').value.trim() || null,
    descriptionEs: document.getElementById('field-description-es').value.trim() || null,
    price: Number(price),
    pricingType: document.getElementById('field-pricing-type').value,
    imageUrl: document.getElementById('field-image-url').value.trim() || null,
    recommended: document.getElementById('field-recommended').checked,
    active: document.getElementById('field-active').checked,
  };

  const url = editingId ? `${API}/${editingId}` : API;
  const method = editingId ? 'PUT' : 'POST';

  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      showError('form-error', err.message || `HTTP ${res.status}`);
      return;
    }

    closeModal();
    loadAddons();
  } catch (err) {
    showError('form-error', 'Save failed: ' + err.message);
  }
}

function showModal() {
  const overlay = document.getElementById('modal-overlay');
  overlay.hidden = false;
  requestAnimationFrame(() => overlay.classList.add('-visible'));
}

function closeModal() {
  const overlay = document.getElementById('modal-overlay');
  overlay.classList.remove('-visible');
  overlay.hidden = true;
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function setLoading(on) {
  document.getElementById('loading').hidden = !on;
  if (on) {
    hide('table-container');
    hide('empty');
    hide('load-error');
  }
}

function showError(id, msg) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.hidden = false;
}

function hideError(id) {
  document.getElementById(id).hidden = true;
}

function show(id)  { document.getElementById(id).hidden = false; }
function hide(id)  { document.getElementById(id).hidden = true; }

function esc(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
