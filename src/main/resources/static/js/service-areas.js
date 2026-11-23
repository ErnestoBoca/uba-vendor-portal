(function () {
  let editId = null;
  document.getElementById('overlay').addEventListener('click', () => UI.close('a-modal'));
  function show(title) { document.getElementById('a-title').textContent = title; UI.clearError('a-err'); UI.open('a-modal'); }

  document.getElementById('a-new').addEventListener('click', () => {
    editId = null;
    document.getElementById('a-name').value = '';
    document.getElementById('a-desc').value = '';
    document.getElementById('a-active').checked = true;
    show('New service area');
  });
  document.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
    editId = b.dataset.id;
    document.getElementById('a-name').value = b.dataset.name;
    document.getElementById('a-desc').value = b.dataset.description || '';
    document.getElementById('a-active').checked = b.dataset.active === 'true';
    show('Edit service area');
  }));
  document.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', async () => {
    if (!confirm('Delete the service area "' + b.dataset.name + '"?')) return;
    try { await Api.del('/api/service-areas/' + b.dataset.del); location.reload(); }
    catch (e) { UI.toast(e.message, 'error'); }
  }));
  document.getElementById('a-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'a-err', async () => {
    const body = { name: UI.val('a-name'), description: UI.orNull(UI.val('a-desc')), active: document.getElementById('a-active').checked };
    if (editId) await Api.put('/api/service-areas/' + editId, body);
    else await Api.post('/api/service-areas', body);
    location.reload();
  }));
})();
