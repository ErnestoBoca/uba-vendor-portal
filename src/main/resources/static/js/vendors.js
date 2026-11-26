/* Vendor Onboarding: drawer de registo (contactos e documentos dinamicos) */
(function () {
  const contactsBox = document.getElementById('contacts');
  const docsBox = document.getElementById('docs');
  const files = [];   // ficheiros escolhidos (indexados por fileIndex)

  function contactHtml(primary) {
    return '<div class="subcard contact">' +
      '<div class="tag">' + (primary ? '● PRIMARY CONTACT' : 'CONTACT') + '</div>' +
      (primary ? '' : '<button class="btn sm rm" type="button" data-rm>Remove</button>') +
      '<div class="grid2"><div class="field"><label>Full Name <span class="req">*</span></label><input data-c="name" placeholder="Full name"' + (primary ? ' readonly' : '') + '></div>' +
      '<div class="field"><label>Contact <span class="req">*</span></label><div class="phone">' + ccSelect() + '<input data-c="phone" placeholder="841234567" inputmode="numeric"' + (primary ? ' readonly' : '') + '></div></div></div>' +
      '<div class="grid2"><div class="field"><label>Function <span class="req">*</span></label><input data-c="job" placeholder="Job title / role in the company"></div>' +
      '<div class="field"><label>Email</label><input data-c="email" type="email" placeholder="Optional"></div></div></div>';
  }
  function ccSelect() {
    const src = document.getElementById('f-mobile-cc');
    return '<select data-c="cc">' + src.innerHTML + '</select>';
  }
  function docHtml(idx) {
    return '<div class="subcard doc" data-idx="' + idx + '"><button class="btn sm rm" type="button" data-rm>Remove</button>' +
      '<div class="grid2"><div class="field"><label>Document name <span class="req">*</span></label><input data-d="name" placeholder="e.g. Tax clearance 2026"></div>' +
      '<div class="field"><label>File <span class="req">*</span></label><input data-d="file" type="file" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx,.xls,.xlsx"></div></div>' +
      '<div class="grid2"><div class="field"><label>Issue date</label><input data-d="issue" type="date"></div>' +
      '<div class="field"><label>Expiry date</label><input data-d="expiry" type="date"></div></div></div>';
  }

  function reset() {
    ['f-name', 'f-email', 'f-contact', 'f-reg', 'f-mobile', 'f-phone', 'f-nuit', 'f-address', 'f-descr'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('f-cat').value = '';
    contactsBox.innerHTML = contactHtml(true);
    docsBox.innerHTML = '';
    files.length = 0;
    UI.clearError('v-err');
    syncPrimary();
  }
  function open() { reset(); UI.open('drawer'); document.getElementById('f-name').focus(); }

  // o contacto principal espelha Contact + Mobile da empresa
  function syncPrimary() {
    const first = contactsBox.querySelector('.contact');
    if (!first) return;
    first.querySelector('[data-c=name]').value = document.getElementById('f-contact').value;
    first.querySelector('[data-c=phone]').value = document.getElementById('f-mobile').value;
    first.querySelector('[data-c=cc]').value = document.getElementById('f-mobile-cc').value;
  }
  ['f-contact', 'f-mobile', 'f-mobile-cc'].forEach(id => document.getElementById(id).addEventListener('input', syncPrimary));

  const btnNew = document.getElementById('btn-new');   // so existe para Corporate Services
  if (btnNew) {
    btnNew.addEventListener('click', open);
    if (new URLSearchParams(location.search).get('new')) open();
  }
  document.getElementById('overlay').addEventListener('click', () => UI.close('drawer'));

  document.getElementById('add-contact').addEventListener('click', () => contactsBox.insertAdjacentHTML('beforeend', contactHtml(false)));
  document.getElementById('add-doc').addEventListener('click', () => { docsBox.insertAdjacentHTML('beforeend', docHtml(files.length)); files.push(null); });
  document.getElementById('drawer').addEventListener('click', e => {
    if (e.target.matches('[data-rm]')) e.target.closest('.subcard').remove();
  });
  document.getElementById('drawer').addEventListener('change', e => {
    if (e.target.matches('[data-d=file]')) files[Number(e.target.closest('.doc').dataset.idx)] = e.target.files[0] || null;
  });

  // verificacao de NUIT duplicado ao sair do campo
  document.getElementById('f-nuit').addEventListener('blur', async e => {
    const hint = document.getElementById('nuit-hint');
    const v = e.target.value.trim();
    if (!/^\d{9}$/.test(v)) { hint.textContent = 'Exactly 9 digits, unique per supplier'; hint.style.color = ''; return; }
    try {
      const r = await Api.get('/api/vendors/check-nuit?nuit=' + v);
      hint.textContent = r.exists ? 'This NUIT is already registered.' : 'NUIT available.';
      hint.style.color = r.exists ? '#DA291C' : '#0f7b4f';
    } catch (err) { /* ignora */ }
  });

  document.getElementById('save-vendor').addEventListener('click', ev => UI.run(ev.currentTarget, 'v-err', async () => {
    const mobile = UI.val('f-mobile'), phone = UI.val('f-phone');
    const contacts = [...contactsBox.querySelectorAll('.contact')].map(c => ({
      fullName: c.querySelector('[data-c=name]').value.trim(),
      phone: (c.querySelector('[data-c=cc]').value + ' ' + c.querySelector('[data-c=phone]').value.trim()).trim(),
      jobTitle: c.querySelector('[data-c=job]').value.trim(),
      email: UI.orNull(c.querySelector('[data-c=email]').value.trim())
    }));
    if (!contacts[0].fullName) throw new Error('Indique o nome da pessoa de contacto (campo Contact).');
    const fd = new FormData();
    const docs = [];
    [...docsBox.querySelectorAll('.doc')].forEach(d => {
      const f = files[Number(d.dataset.idx)];
      if (!f) throw new Error('Seleccione o ficheiro de cada documento adicionado (ou remova a linha).');
      docs.push({ name: d.querySelector('[data-d=name]').value.trim(), issueDate: UI.orNull(d.querySelector('[data-d=issue]').value),
                  expiryDate: UI.orNull(d.querySelector('[data-d=expiry]').value), fileIndex: docs.length });
      fd.append('files', f);
    });
    const catVal = UI.val('f-cat');
    const data = {
      supplierName: UI.val('f-name'), email: UI.val('f-email'),
      mobile: mobile ? document.getElementById('f-mobile-cc').value + ' ' + mobile : '',
      phone: phone ? document.getElementById('f-phone-cc').value + ' ' + phone : null,
      address: UI.val('f-address'), nuit: UI.val('f-nuit'), serviceCategoryId: catVal ? Number(catVal) : null,
      serviceDescription: UI.orNull(UI.val('f-descr')), companyRegNumber: UI.orNull(UI.val('f-reg')),
      contacts: contacts, documents: docs
    };
    fd.append('data', JSON.stringify(data));
    const r = await Api.request('POST', '/api/vendors', fd);
    UI.toast('Vendor registered.', 'ok');
    window.location.href = '/vendors/' + r.id;
  }));
})();
