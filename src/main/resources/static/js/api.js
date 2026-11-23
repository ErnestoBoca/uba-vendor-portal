/* api.js - fetch wrapper (CSRF + erros JSON) e utilitarios de UI. JS puro. */
const Api = {
  csrf() {
    const t = document.querySelector('meta[name="_csrf"]');
    const h = document.querySelector('meta[name="_csrf_header"]');
    return t && h ? { [h.content]: t.content } : {};
  },
  async request(method, url, body) {
    const opts = { method, headers: { ...Api.csrf() }, credentials: 'same-origin' };
    if (body instanceof FormData) {
      opts.body = body;                       // multipart: o browser define o Content-Type
    } else if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    let data = null;
    try { data = await res.json(); } catch (e) { /* sem corpo */ }
    if (!res.ok) {
      const msg = (data && data.error) || (res.status === 403 ? 'Sessao expirada ou sem permissao. Recarregue a pagina.' : 'Erro ' + res.status);
      throw new Error(msg);
    }
    return data;
  },
  get(url) { return Api.request('GET', url); },
  post(url, body) { return Api.request('POST', url, body === undefined ? {} : body); },
  put(url, body) { return Api.request('PUT', url, body); },
  del(url) { return Api.request('DELETE', url); }
};

const UI = {
  toast(msg, type) {
    const box = document.getElementById('toasts');
    if (!box) { alert(msg); return; }
    const t = document.createElement('div');
    t.className = 'toast ' + (type || '');
    t.textContent = msg;
    box.appendChild(t);
    setTimeout(() => t.remove(), 4500);
  },
  open(id) { document.getElementById(id).classList.add('open'); document.getElementById('overlay').classList.add('open'); },
  close(id) { document.getElementById(id).classList.remove('open'); document.getElementById('overlay').classList.remove('open'); },
  showError(boxId, msg) { const b = document.getElementById(boxId); b.textContent = msg; b.classList.add('show'); b.scrollIntoView({ block: 'nearest' }); },
  clearError(boxId) { const b = document.getElementById(boxId); b.textContent = ''; b.classList.remove('show'); },
  /** Executa fn com o botao desactivado e mostra o erro (se houver) na caixa errBox. */
  async run(btn, errBox, fn) {
    if (errBox) UI.clearError(errBox);
    if (btn) btn.disabled = true;
    try { return await fn(); }
    catch (e) { if (errBox) UI.showError(errBox, e.message); else UI.toast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  },
  val(id) { const el = document.getElementById(id); return el ? el.value.trim() : ''; },
  orNull(v) { return v === '' || v === undefined ? null : v; },
  esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
};

document.addEventListener('keydown', e => {
  if (e.key === 'Escape') document.querySelectorAll('.drawer.open, .modal.open').forEach(el => UI.close(el.id));
});
