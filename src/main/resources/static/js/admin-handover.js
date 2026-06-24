let handoverState = null;
let signatureDirty = false;
let signatureHasInk = false;

document.addEventListener('DOMContentLoaded', () => {
  initSignaturePad();
  bindHandoverActions();
  loadHandover();
});

document.addEventListener('languageChanged', () => {
  if (handoverState) renderHandover(handoverState);
});

function bookingId() {
  return new URLSearchParams(window.location.search).get('bookingId');
}

async function loadHandover() {
  const id = bookingId();
  if (!id) return showError('Missing bookingId');
  try {
    const res = await fetch(`/api/admin/bookings/${encodeURIComponent(id)}/handover`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    handoverState = await res.json();
    document.getElementById('handover-loading').hidden = true;
    document.getElementById('handover-content').hidden = false;
    renderHandover(handoverState);
  } catch (err) {
    document.getElementById('handover-loading').hidden = true;
    showError(`Could not load handover: ${err.message}`);
  }
}

function renderHandover(state) {
  const b = state.booking;
  const d = state.deposit;
  const h = state.handover;
  document.getElementById('handover-subtitle').textContent = `${b.bookingReference} - ${b.customerName}`;
  document.getElementById('handover-booking-summary').innerHTML = [
    item('Reference', b.bookingReference),
    item('Status', b.status),
    item('Pickup', formatDateTime(b.pickupDateTime)),
    item('Drop-off', formatDateTime(b.dropoffDateTime))
  ].join('');
  document.getElementById('handover-customer').innerHTML = [
    item('Name', b.customerName),
    item('Email', b.customerEmail)
  ].join('');
  document.getElementById('handover-vehicle').innerHTML = [
    item('Vehicle', `${b.carBrand} ${b.carModel}`),
    item('Pickup location', b.pickupAddress || b.pickupLocation),
    item('Drop-off location', b.dropoffAddress || b.dropoffLocation),
    item('Included km', b.includedKmSnapshot != null ? `${b.includedKmSnapshot} km` : '-')
  ].join('');
  document.getElementById('handover-insurance').innerHTML = [
    item('Protection', b.insuranceNameSnapshot || '-'),
    item('Insurance total', formatPrice(b.insuranceTotalSnapshot)),
    item('Required deposit', formatPrice(b.depositAmountSnapshot)),
    item('Deposit status', d ? d.status : 'NOT_COLLECTED'),
    item('Refund deadline', d ? formatDateTime(d.refundDeadlineAt) : '-')
  ].join('');
  document.getElementById('handover-damages').innerHTML = renderDamages(state.damages || []);
  document.getElementById('deposit-status').textContent = d
    ? `Required ${formatPrice(d.amount)} - ${d.status} - remaining ${formatPrice(d.remainingAmount)}`
    : `Required ${formatPrice(b.depositAmountSnapshot)} - NOT_COLLECTED`;
  if (d && d.method) document.getElementById('deposit-method').value = d.method;
  if (d && d.adminNote) document.getElementById('deposit-note').value = d.adminNote;
  if (d && d.stripePaymentLinkUrl) showPaymentLink(d.stripePaymentLinkUrl, d.stripePaymentIntentId);
  renderRefundHistory(d);
  if (h) {
    document.getElementById('kmOut').value = h.kmOut ?? '';
    document.getElementById('fuelLevelOut').value = h.fuelLevelOut || '';
    document.getElementById('batteryLevelOut').value = h.batteryLevelOut || '';
    document.getElementById('handoverNotes').value = h.notes || '';
    signatureHasInk = Boolean(h.customerSignaturePresent);
  }
  renderReadiness(state);
}

function renderDamages(damages) {
  if (!damages.length) return '<div class="admin-empty">No active damage records for this vehicle.</div>';
  return damages.map(d => `
    <div class="admin-damage-item">
      <strong>${esc(d.title)}</strong>
      <span>${esc(d.damageCode)} · ${esc(d.location || '-')} · ${esc(d.severity || '')}</span>
      <p>${esc(d.description || '')}</p>
    </div>
  `).join('');
}

function renderRefundHistory(deposit) {
  const container = document.getElementById('refund-history');
  if (!deposit || !deposit.refunds || !deposit.refunds.length) {
    container.innerHTML = '<div class="admin-empty">No deposit refunds yet.</div>';
    return;
  }
  container.innerHTML = deposit.refunds.map(r => `
    <div class="admin-refund-item">
      <strong>${esc(formatPrice(r.amount))} · ${esc(r.type)}</strong>
      <span>${esc(formatDateTime(r.createdAt))}${r.stripeRefundId ? ` · ${esc(r.stripeRefundId)}` : ''}</span>
      <p>${esc(r.note || '')}</p>
    </div>
  `).join('');
}

function renderReadiness(state) {
  const ready = state.canMarkPickedUp;
  const readiness = document.getElementById('pickup-readiness');
  readiness.className = ready ? 'admin-alert admin-alert--success' : 'admin-alert admin-alert--error';
  readiness.textContent = ready
    ? 'Ready to mark vehicle picked up.'
    : 'Complete handover fields, collect deposit, and capture customer signature before pickup.';
  document.getElementById('mark-picked-up').disabled = !ready;
}

function bindHandoverActions() {
  document.getElementById('save-handover').addEventListener('click', saveHandover);
  document.getElementById('manual-deposit').addEventListener('click', markManualDeposit);
  document.getElementById('stripe-deposit').addEventListener('click', generateStripeDeposit);
  document.getElementById('mark-picked-up').addEventListener('click', markPickedUp);
  document.getElementById('deposit-refund-form').addEventListener('submit', refundDeposit);
}

async function saveHandover() {
  const payload = handoverPayload();
  if (!payload) return;
  await postJson(`/api/admin/bookings/${bookingId()}/handover`, payload);
  signatureDirty = false;
  await loadHandover();
}

async function markManualDeposit() {
  const method = document.getElementById('deposit-method').value;
  if (method === 'STRIPE') return showError('Use Generate Deposit Payment Link for Stripe.');
  await postJson(`/api/admin/bookings/${bookingId()}/deposit/manual-collection`, {
    method,
    note: document.getElementById('deposit-note').value
  });
  await loadHandover();
}

async function generateStripeDeposit() {
  const body = await postJson(`/api/admin/bookings/${bookingId()}/deposit/stripe-intent`, {});
  showPaymentLink(body.paymentLinkUrl, body.paymentIntentId);
  await loadHandover();
}

async function refundDeposit(event) {
  event.preventDefault();
  const amount = document.getElementById('refundAmount').value;
  await postJson(`/api/admin/bookings/${bookingId()}/deposit/refund`, {
    refundAmount: amount,
    type: document.getElementById('refundType').value,
    note: document.getElementById('refundNote').value
  });
  event.target.reset();
  await loadHandover();
}

async function markPickedUp() {
  const payload = handoverPayload();
  if (!payload) return;
  await postJson(`/api/admin/bookings/${bookingId()}/handover`, payload);
  await postJson(`/api/admin/bookings/${bookingId()}/picked-up`, {});
  await loadHandover();
}

function handoverPayload() {
  const kmOut = document.getElementById('kmOut').value;
  const fuelLevelOut = document.getElementById('fuelLevelOut').value;
  const batteryLevelOut = document.getElementById('batteryLevelOut').value;
  const signatureData = signatureDirty ? document.getElementById('signature-pad').toDataURL('image/png') : null;
  if (!kmOut || !fuelLevelOut || !batteryLevelOut) {
    showError('Km, fuel level, and battery level are required.');
    return null;
  }
  if (!signatureHasInk && !signatureData) {
    showError('Customer signature is required.');
    return null;
  }
  return {
    kmOut: Number(kmOut),
    fuelLevelOut,
    batteryLevelOut,
    customerSignatureData: signatureData,
    notes: document.getElementById('handoverNotes').value
  };
}

async function postJson(url, payload) {
  hideError();
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      message = body.message || message;
    } catch (e) {
      // ignore non-JSON errors
    }
    showError(message);
    throw new Error(message);
  }
  return res.json();
}

function initSignaturePad() {
  const canvas = document.getElementById('signature-pad');
  const ctx = canvas.getContext('2d');
  ctx.lineWidth = 3;
  ctx.lineCap = 'round';
  ctx.strokeStyle = '#191B1E';
  let drawing = false;

  function pos(event) {
    const rect = canvas.getBoundingClientRect();
    const point = event.touches ? event.touches[0] : event;
    return {
      x: (point.clientX - rect.left) * (canvas.width / rect.width),
      y: (point.clientY - rect.top) * (canvas.height / rect.height)
    };
  }
  function start(event) {
    event.preventDefault();
    drawing = true;
    const p = pos(event);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
  }
  function move(event) {
    if (!drawing) return;
    event.preventDefault();
    const p = pos(event);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
    signatureDirty = true;
    signatureHasInk = true;
  }
  function end() {
    drawing = false;
  }
  canvas.addEventListener('mousedown', start);
  canvas.addEventListener('mousemove', move);
  window.addEventListener('mouseup', end);
  canvas.addEventListener('touchstart', start, { passive: false });
  canvas.addEventListener('touchmove', move, { passive: false });
  window.addEventListener('touchend', end);
  document.getElementById('clear-signature').addEventListener('click', () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    signatureDirty = true;
    signatureHasInk = false;
  });
}

function showPaymentLink(url, intentId) {
  const el = document.getElementById('deposit-payment-link');
  el.hidden = false;
  el.innerHTML = `<strong>Payment reference:</strong> ${esc(intentId || '')}<br><a class="admin-link" href="${esc(url)}" target="_blank" rel="noopener">${esc(url)}</a>`;
}

function item(label, value) {
  return `<div class="admin-detail-item"><dt>${esc(label)}</dt><dd>${esc(value == null || value === '' ? '-' : value)}</dd></div>`;
}

function formatPrice(value) {
  if (value == null || value === '') return '-';
  return new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' }).format(Number(value));
}

function formatDateTime(value) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function showError(message) {
  const error = document.getElementById('handover-error');
  error.textContent = message;
  error.hidden = false;
}

function hideError() {
  document.getElementById('handover-error').hidden = true;
}

function esc(value) {
  if (window.escapeHtml) return window.escapeHtml(String(value ?? ''));
  return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}
