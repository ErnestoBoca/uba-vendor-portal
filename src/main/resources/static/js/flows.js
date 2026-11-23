(function () {
  const scope = document.getElementById('page').dataset.scope;
  const box = document.getElementById('steps');
  function renumber() { [...box.querySelectorAll('.step-row')].forEach((r, i) => r.querySelector('.step-no').textContent = i + 1); }
  renumber();
  document.getElementById('add-step').addEventListener('click', () => {
    box.appendChild(document.getElementById('step-tpl').content.cloneNode(true));
    renumber();
  });
  box.addEventListener('click', e => {
    const row = e.target.closest('.step-row');
    if (!row) return;
    if (e.target.matches('[data-rm]')) row.remove();
    else if (e.target.matches('[data-up]') && row.previousElementSibling) box.insertBefore(row, row.previousElementSibling);
    else if (e.target.matches('[data-down]') && row.nextElementSibling) box.insertBefore(row.nextElementSibling, row);
    renumber();
  });
  document.getElementById('save-flow').addEventListener('click', ev => UI.run(ev.currentTarget, 'f-err', async () => {
    const steps = [...box.querySelectorAll('.step-row')].map(r => ({
      name: r.querySelector('[data-s=name]').value.trim(),
      approverUserId: r.querySelector('[data-s=user]').value ? Number(r.querySelector('[data-s=user]').value) : null
    }));
    await Api.put('/api/flows/' + scope, steps);
    UI.toast('Flow saved.', 'ok');
    setTimeout(() => location.reload(), 600);
  }));
  const del = document.getElementById('del-flow');
  if (del) del.addEventListener('click', async () => {
    if (!confirm('Remove the specific flow and use the Default flow for this service type?')) return;
    try { await Api.del('/api/flows/' + scope); location.reload(); } catch (e) { UI.toast(e.message, 'error'); }
  });
})();
