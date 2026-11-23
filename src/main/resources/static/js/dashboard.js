/* Dashboard: cartoes + grafico de barras e donut desenhados em SVG (sem bibliotecas). */
(async function () {
  const COLORS = { DRAFT: '#9ca3af', IN_APPROVAL: '#2563eb', ACTIVE: '#0f7b4f', REJECTED: '#8a1c13', TERMINATED: '#333333', CLOSED: '#cbd0d6' };
  const LABELS = { DRAFT: 'Draft', IN_APPROVAL: 'In approval', ACTIVE: 'Active', REJECTED: 'Rejected', TERMINATED: 'Terminated', CLOSED: 'Closed' };
  let d;
  try { d = await Api.get('/api/dashboard'); } catch (e) { UI.toast(e.message, 'error'); return; }

  document.querySelectorAll('#contract-stats [data-k]').forEach(el => el.textContent = d.contracts[el.dataset.k] ?? 0);
  document.querySelectorAll('#vendor-stats [data-k]').forEach(el => el.textContent = d.vendors[el.dataset.k] ?? 0);
  document.getElementById('expiring').textContent = d.expiring;
  document.getElementById('mine').textContent = d.myPending;
  document.getElementById('exp-label').textContent = 'Expiring in ' + d.expiryWarningDays + ' days';

  // barras
  const bars = document.getElementById('bars');
  const max = Math.max(1, ...d.vendorsByArea.map(a => a.n));
  document.getElementById('areas-count').textContent = d.vendorsByArea.length + ' areas';
  bars.innerHTML = d.vendorsByArea.length ? d.vendorsByArea.map(a =>
    '<div class="bar-row"><span>' + UI.esc(a.name) + '</span><div class="bar-track"><div class="bar-fill" style="width:' + (a.n / max * 100) + '%"></div></div><b>' + a.n + '</b></div>'
  ).join('') : '<div class="empty">No vendors yet.</div>';

  // donut
  const entries = Object.entries(d.contracts).filter(([, n]) => n > 0);
  const total = entries.reduce((s, [, n]) => s + n, 0);
  const R = 70, C = 2 * Math.PI * R;
  let off = 0, arcs = '';
  entries.forEach(([k, n]) => {
    const len = n / total * C;
    arcs += '<circle r="' + R + '" cx="100" cy="100" fill="none" stroke="' + COLORS[k] + '" stroke-width="28" stroke-dasharray="' + len + ' ' + (C - len) + '" stroke-dashoffset="' + (-off) + '" transform="rotate(-90 100 100)"/>';
    off += len;
  });
  document.getElementById('donut').innerHTML = '<svg viewBox="0 0 200 200" width="190" height="190">' +
    (total ? arcs : '<circle r="' + R + '" cx="100" cy="100" fill="none" stroke="#eee" stroke-width="28"/>') +
    '<text x="100" y="108" text-anchor="middle" font-size="30" font-weight="600" fill="#1f2328">' + total + '</text></svg>';
  document.getElementById('legend').innerHTML = Object.entries(d.contracts).map(([k, n]) =>
    '<div><i style="background:' + COLORS[k] + '"></i>' + LABELS[k] + '<b>' + n + '</b></div>').join('');
})();
