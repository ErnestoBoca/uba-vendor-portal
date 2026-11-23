(function () {
  let editId = null;
  const $ = id => document.getElementById(id);
  $('overlay').addEventListener('click', () => UI.close('d-modal'));
  function show(title) { $('d-title').textContent = title; UI.clearError('d-err'); UI.open('d-modal'); }

  $('d-new').addEventListener('click', () => {
    editId = null;
    $('d-code').value = ''; $('d-code').readOnly = false;
    $('d-name').value = '';
    $('d-officers').checked = false; $('d-officers').disabled = false;
    $('d-active').checked = true; $('d-active').disabled = false;
    $('d-active-row').style.display = 'none';
    show('New department');
  });
  document.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
    editId = b.dataset.id;
    const system = b.dataset.system === 'true';
    $('d-code').value = b.dataset.code; $('d-code').readOnly = true;
    $('d-name').value = b.dataset.name;
    $('d-officers').checked = b.dataset.officers === 'true'; $('d-officers').disabled = system;
    $('d-active').checked = b.dataset.active === 'true'; $('d-active').disabled = system;
    $('d-active-row').style.display = 'block';
    show('Edit department');
  }));
  $('d-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'd-err', async () => {
    const body = { name: UI.val('d-name'), hasOfficers: $('d-officers').checked };
    if (editId) { body.active = $('d-active').checked; await Api.put('/api/departments/' + editId, body); }
    else { body.code = UI.val('d-code').toUpperCase(); await Api.post('/api/departments', body); }
    location.reload();
  }));
})();
