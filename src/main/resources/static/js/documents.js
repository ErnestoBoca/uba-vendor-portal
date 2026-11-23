/* Upload / remocao de documentos (partilhado por vendor e contrato). Requer fragmento uploadModal na pagina. */
(function () {
  const open = document.getElementById('doc-open');
  if (open) open.addEventListener('click', () => {
    ['doc-name', 'doc-file', 'doc-issue', 'doc-expiry'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('doc-type').value = '';
    UI.clearError('doc-err');
    UI.open('doc-modal');
  });
  const save = document.getElementById('doc-save');
  if (save) save.addEventListener('click', ev => UI.run(ev.currentTarget, 'doc-err', async () => {
    const f = document.getElementById('doc-file').files[0];
    if (!f) throw new Error('Seleccione um ficheiro.');
    const fd = new FormData();
    fd.append('entityType', UI.val('doc-entity-type'));
    fd.append('entityId', UI.val('doc-entity-id'));
    fd.append('name', UI.val('doc-name'));
    if (UI.val('doc-type')) fd.append('documentTypeId', UI.val('doc-type'));
    if (UI.val('doc-issue')) fd.append('issueDate', UI.val('doc-issue'));
    if (UI.val('doc-expiry')) fd.append('expiryDate', UI.val('doc-expiry'));
    fd.append('file', f);
    await Api.request('POST', '/api/documents', fd);
    location.reload();
  }));
  document.querySelectorAll('[data-doc-del]').forEach(b => b.addEventListener('click', async () => {
    if (!confirm('Remove this document?')) return;
    try { await Api.del('/api/documents/' + b.dataset.docDel); location.reload(); } catch (e) { UI.toast(e.message, 'error'); }
  }));
})();
