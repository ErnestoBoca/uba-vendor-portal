/* Botoes [data-decide] e [data-reassign] das tabelas de aprovacao. */
(function () {
  let taskId = null, decision = null;
  const titles = { APPROVE: 'Approve', RETURN: 'Return for changes', REJECT: 'Reject' };
  const hints = { APPROVE: 'Optional. The request moves to the next step (or the contract is activated if this is the last one).',
                  RETURN: 'The contract goes back to Draft so it can be corrected and resubmitted.',
                  REJECT: 'The contract is rejected and the flow stops.' };
  document.addEventListener('click', e => {
    const d = e.target.closest('[data-decide]');
    if (d) {
      taskId = d.dataset.task; decision = d.dataset.decide;
      document.getElementById('decide-title').textContent = titles[decision];
      document.getElementById('decide-hint').textContent = hints[decision];
      document.getElementById('decide-req').style.display = decision === 'APPROVE' ? 'none' : 'inline';
      document.getElementById('decide-comment').value = '';
      UI.clearError('decide-err'); UI.open('decide-modal');
      return;
    }
    const r = e.target.closest('[data-reassign]');
    if (r) {
      taskId = r.dataset.task;
      const sel = document.getElementById('reassign-to');
      sel.value = '';
      [...sel.options].forEach(o => o.hidden = (o.value !== '' && o.value === r.dataset.current));
      document.getElementById('reassign-reason').value = '';
      UI.clearError('reassign-err'); UI.open('reassign-modal');
    }
  });
  document.getElementById('overlay').addEventListener('click', () => document.querySelectorAll('.modal.open').forEach(m => UI.close(m.id)));
  document.getElementById('decide-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'decide-err', async () => {
    await Api.post('/api/approvals/tasks/' + taskId + '/decide', { decision: decision, comment: UI.orNull(UI.val('decide-comment')) });
    location.reload();
  }));
  document.getElementById('reassign-save').addEventListener('click', ev => UI.run(ev.currentTarget, 'reassign-err', async () => {
    if (!UI.val('reassign-to')) throw new Error('Seleccione a nova pessoa.');
    await Api.post('/api/approvals/tasks/' + taskId + '/reassign', { toUserId: Number(UI.val('reassign-to')), reason: UI.val('reassign-reason') });
    location.reload();
  }));
})();
