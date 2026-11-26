(function () {
  const page = document.getElementById('page');
  const id = page.dataset.id;
  // separadores (Overview / Documents / Approval flow)
  document.querySelectorAll('.tabs [data-tab]').forEach(b => b.addEventListener('click', () => {
    document.querySelectorAll('.tabs button').forEach(x => x.classList.remove('on'));
    document.querySelectorAll('.tabpane').forEach(x => x.classList.remove('on'));
    b.classList.add('on');
    document.getElementById(b.dataset.tab).classList.add('on');
    history.replaceState(null, '', '#' + b.dataset.tab);
  }));
  const initialTab = document.querySelector('.tabs [data-tab="' + location.hash.replace('#', '') + '"]');
  if (initialTab) initialTab.click();

  document.getElementById('overlay').addEventListener('click', () => document.querySelectorAll('.modal.open').forEach(m => UI.close(m.id)));

  // rever / aprovar / rejeitar
  let action = null;
  const titles = { START: 'Start review', APPROVE: 'Approve vendor', REJECT: 'Reject vendor' };
  document.querySelectorAll('[data-review]').forEach(b => b.addEventListener('click', () => {
    action = b.dataset.review;
    document.getElementById('review-title').textContent = titles[action];
    document.getElementById('review-req').style.display = action === 'REJECT' ? 'inline' : 'none';
    document.getElementById('review-comment').value = '';
    UI.clearError('review-err');
    UI.open('review-modal');
  }));
  document.getElementById('review-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'review-err', async () => {
    await Api.post('/api/vendors/' + id + '/review', { action: action, comment: UI.orNull(UI.val('review-comment')) });
    location.reload();
  }));

  // contactos
  const btnContact = document.getElementById('btn-contact');   // so existe para Corporate Services
  if (btnContact) btnContact.addEventListener('click', () => { UI.clearError('contact-err'); UI.open('contact-modal'); });
  document.getElementById('ct-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'contact-err', async () => {
    await Api.post('/api/vendors/' + id + '/contacts', { fullName: UI.val('ct-name'), phone: UI.val('ct-phone'), jobTitle: UI.val('ct-job'), email: UI.orNull(UI.val('ct-email')) });
    location.reload();
  }));
  document.querySelectorAll('[data-contact-del]').forEach(b => b.addEventListener('click', async () => {
    if (!confirm('Remove this contact?')) return;
    try { await Api.del('/api/vendors/' + id + '/contacts/' + b.dataset.contactDel); location.reload(); } catch (e) { UI.toast(e.message, 'error'); }
  }));

  // editar
  const editBtn = document.getElementById('btn-edit');
  function openEdit() { UI.clearError('edit-err'); UI.open('edit-modal'); }
  if (editBtn) {
    editBtn.addEventListener('click', openEdit);
    if (new URLSearchParams(location.search).get('edit')) openEdit();
  }
  document.getElementById('e-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'edit-err', async () => {
    await Api.put('/api/vendors/' + id, {
      supplierName: UI.val('e-name'), email: UI.val('e-email'), mobile: UI.val('e-mobile'), phone: UI.orNull(UI.val('e-phone')),
      address: UI.val('e-address'), nuit: UI.val('e-nuit'), serviceCategoryId: Number(UI.val('e-cat')),
      serviceDescription: UI.orNull(UI.val('e-descr')), companyRegNumber: UI.orNull(UI.val('e-reg'))
    });
    location.href = '/vendors/' + id;
  }));
})();
