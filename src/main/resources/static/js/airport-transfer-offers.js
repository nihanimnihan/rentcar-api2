document.addEventListener("DOMContentLoaded", function () {
  const params = new URLSearchParams(window.location.search);
  const transferType = params.get("transferType");
  const durationHours = params.get("durationHours");
  const includedKm = params.get("includedKm");

  const durationEl = document.getElementById("selectedTransferDuration");

  if (durationEl && transferType === "HOURLY" && durationHours && includedKm) {
    const h = parseInt(durationHours, 10);
    const km = parseInt(includedKm, 10);
    const hourText = h === 1 ? "1 hour" : h + " hours";
    durationEl.textContent = hourText + " (" + km + " km included)";
  }
});
