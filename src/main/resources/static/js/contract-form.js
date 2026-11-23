(function () {
  const id = document.getElementById('page').dataset.id;
  const vendor = document.getElementById('c-vendor');
  const cat = document.getElementById('c-cat');
  // ao escolher o vendor, sugere o tipo de servico dele (se ainda nao escolhido)
  vendor.addEventListener('change', () => {
    const o = vendor.options[vendor.selectedIndex];
    if (o && o.dataset.cat && !cat.value) cat.value = o.dataset.cat;
  });
  const num = v => (v === '' ? null : Number(v.replace(',', '.')));
  const n = v => (v === '' ? null : Number(v));

  document.getElementById('c-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'c-err', async () => {
    const body = {
      vendorId: n(UI.val('c-vendor')), serviceCategoryId: n(UI.val('c-cat')), contractNumber: UI.orNull(UI.val('c-number')),
      title: UI.val('c-title'), contractType: UI.val('c-type'), objectDescription: UI.orNull(UI.val('c-object')),
      signingDate: UI.orNull(UI.val('c-sign')), startDate: UI.orNull(UI.val('c-start')), endDate: UI.orNull(UI.val('c-end')),
      valueAmount: num(UI.val('c-value')), currency: UI.val('c-cur'), paymentTerms: UI.orNull(UI.val('c-pay')),
      terminationNoticeDays: n(UI.val('c-tnd')), penaltyClause: UI.orNull(UI.val('c-pen')), governingLaw: UI.orNull(UI.val('c-law')),
      slaTerms: UI.orNull(UI.val('c-sla')), dpaStatus: UI.val('c-dpa'), auditRights: document.getElementById('c-audit').checked,
      autoRenew: document.getElementById('c-auto').checked, renewalNoticeDays: n(UI.val('c-rnd')),
      vendorSignatory: UI.orNull(UI.val('c-vsig')), bankSignatory: UI.orNull(UI.val('c-bsig')), ownerUserId: n(UI.val('c-owner'))
    };
    if (body.valueAmount !== null && isNaN(body.valueAmount)) throw new Error('O valor do contrato nao e um numero valido.');
    const r = id ? await Api.put('/api/contracts/' + id, body) : await Api.post('/api/contracts', body);
    UI.toast('Contract saved.', 'ok');
    window.location.href = '/contracts/' + r.id;
  }));
})();
