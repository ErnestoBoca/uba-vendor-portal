(function () {
  let editId = null;
  document.getElementById('overlay').addEventListener('click', () => UI.close('u-modal'));
  function show(title) { document.getElementById('u-title').textContent = title; UI.clearError('u-err'); UI.open('u-modal'); }

  document.getElementById('u-new').addEventListener('click', () => {
    editId = null;
    ['u-username', 'u-name', 'u-email', 'u-pass'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('u-username').readOnly = false;
    document.getElementById('u-role').value = '';
    document.getElementById('u-active-row').style.display = 'none';
    document.getElementById('u-pass-hint').textContent = 'At least 6 characters.';
    show('New user');
  });
  document.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
    editId = b.dataset.id;
    document.getElementById('u-username').value = b.dataset.username; document.getElementById('u-username').readOnly = true;
    document.getElementById('u-name').value = b.dataset.name;
    document.getElementById('u-email').value = b.dataset.email;
    if (b.dataset.role) document.getElementById('u-role').value = b.dataset.role;
    document.getElementById('u-pass').value = '';
    document.getElementById('u-pass-hint').textContent = 'Leave blank to keep the current password.';
    document.getElementById('u-active').checked = b.dataset.active === 'true';
    document.getElementById('u-active-row').style.display = 'block';
    show('Edit user');
  }));
  document.getElementById('u-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'u-err', async () => {
    const body = { username: UI.val('u-username'), fullName: UI.val('u-name'), email: UI.val('u-email'),
                   password: UI.val('u-pass') || null, roleId: UI.val('u-role') ? Number(UI.val('u-role')) : null };
    if (!editId && !body.roleId) throw new Error('Seleccione o perfil.');
    if (editId) { body.active = document.getElementById('u-active').checked; await Api.put('/api/users/' + editId, body); }
    else await Api.post('/api/users', body);
    location.reload();
  }));
})();
