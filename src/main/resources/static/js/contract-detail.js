(function () {
  const id = document.getElementById('page').dataset.id;
  document.querySelectorAll('.tabs [data-tab]').forEach(b => b.addEventListener('click', () => {
    document.querySelectorAll('.tabs button').forEach(x => x.classList.remove('on'));
    document.querySelectorAll('.tabpane').forEach(x => x.classList.remove('on'));
    b.classList.add('on');
    document.getElementById(b.dataset.tab).classList.add('on');
    history.replaceState(null, '', '#' + b.dataset.tab);
  }));
  const hash = location.hash.replace('#', '');
  const initial = document.querySelector('.tabs [data-tab="' + hash + '"]');
  if (initial) initial.click();

  const submit = document.getElementById('btn-submit');
  if (submit) submit.addEventListener('click', ev => UI.run(ev.currentTarget, 'c-err', async () => {
    if (!confirm('Submit this contract for approval? It cannot be edited while in approval.')) return;
    await Api.post('/api/contracts/' + id + '/submit');
    location.href = '/contracts/' + id + '#t-approval';
    location.reload();
  }));
})();
