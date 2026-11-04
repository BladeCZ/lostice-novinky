const LABELS = {hlaseni: 'Hlášení městského rozhlasu', rm: 'Usnesení rady města', zm: 'Usnesení zastupitelstva'};
const $ = id => document.getElementById(id);
const store = {
  get: (k, d) => { const v = localStorage.getItem(k); return v === null ? d : parseFloat(v); },
  set: (k, v) => localStorage.setItem(k, v)
};
let tab = 'hlaseni', feeds = {}, current = null;
let fontScale = store.get('fontScale', 1), rate = store.get('speechRate', 1);
let chunks = [], idx = 0, speaking = false;

const fmtDate = d => { if (!d) return ''; const [y, m, dd] = d.split('-'); return `${+dd}. ${+m}. ${y}`; };
const r1 = v => Math.round(v * 10) / 10;

async function load(kind, force) {
  try {
    const r = await fetch(`data/${kind}.json` + (force ? `?t=${Date.now()}` : ''));
    feeds[kind] = await r.json();
  } catch (e) { /* offline – ponecháme, co máme */ }
}

function renderList() {
  $('title').textContent = LABELS[tab];
  const list = $('list'); list.innerHTML = '';
  const f = feeds[tab];
  if (!f) { list.innerHTML = '<div class="msg">Načítání…</div>'; return; }
  f.items.forEach(it => {
    const c = document.createElement('div'); c.className = 'card';
    const prev = it.text.replace(/\s+/g, ' ').slice(0, 160);
    c.innerHTML = `<div class="d"></div><div class="t"></div><div class="p"></div>`;
    c.children[0].textContent = fmtDate(it.date);
    c.children[1].textContent = it.title;
    c.children[2].textContent = prev;
    c.onclick = () => openItem(it);
    list.appendChild(c);
  });
}

function applyFont() {
  $('d-text').style.fontSize = (17 * fontScale) + 'px';
  $('font-val').textContent = Math.round(fontScale * 100) + ' %';
  $('rate-val').textContent = rate.toFixed(1) + '×';
}

function openItem(it) {
  current = it; history.pushState({d: 1}, '');
  $('list-view').hidden = true; $('detail-view').hidden = false; $('back').hidden = false;
  $('title').textContent = it.title;
  $('d-date').textContent = fmtDate(it.date);
  $('d-text').textContent = it.text || 'Text se nepodařilo získat. Otevřete původní PDF.';
  $('pdf').href = it.url; applyFont(); window.scrollTo(0, 0);
}

function closeItem() {
  stop(); current = null;
  $('list-view').hidden = false; $('detail-view').hidden = true; $('back').hidden = true;
  renderList();
}

function split(text, max = 1500) {
  const out = []; let buf = '';
  for (const para of text.split('\n')) {
    for (const s of para.split(/(?<=[.!?;:])\s+/)) {
      if (buf.length + s.length + 1 > max) { if (buf.trim()) out.push(buf.trim()); buf = ''; }
      buf += s + ' ';
    }
    if (buf.trim()) out.push(buf.trim()); buf = '';
  }
  return out;
}

function speakFrom(i) {
  speechSynthesis.cancel(); speaking = true; idx = i; $('speak').textContent = '■ Zastavit čtení';
  chunks.slice(i).forEach((c, k) => {
    const u = new SpeechSynthesisUtterance(c);
    u.lang = 'cs-CZ'; u.rate = rate;
    u.onstart = () => { idx = i + k; };
    u.onend = () => { if (i + k === chunks.length - 1) stop(); };
    speechSynthesis.speak(u);
  });
}
function stop() { speechSynthesis.cancel(); speaking = false; $('speak').textContent = '▶ Přečíst nahlas'; }

$('speak').onclick = () => {
  if (!('speechSynthesis' in window)) return alert('Prohlížeč nepodporuje předčítání.');
  if (speaking) return stop();
  chunks = split(current.title + '.\n' + current.text); speakFrom(0);
};
$('font-minus').onclick = () => { fontScale = Math.max(0.7, r1(fontScale - 0.1)); store.set('fontScale', fontScale); applyFont(); };
$('font-plus').onclick = () => { fontScale = Math.min(2.5, r1(fontScale + 0.1)); store.set('fontScale', fontScale); applyFont(); };
function setRate(v) { rate = v; store.set('speechRate', rate); applyFont(); if (speaking) speakFrom(idx); }
$('rate-minus').onclick = () => setRate(Math.max(0.5, r1(rate - 0.1)));
$('rate-plus').onclick = () => setRate(Math.min(2, r1(rate + 0.1)));
$('back').onclick = () => history.back();
window.onpopstate = () => { if (current) closeItem(); };
$('refresh').onclick = async () => { await load(tab, true); if (!current) renderList(); };
document.querySelectorAll('nav button').forEach(b => b.onclick = async () => {
  document.querySelectorAll('nav button').forEach(x => x.classList.remove('active'));
  b.classList.add('active'); tab = b.dataset.tab;
  if (current) closeItem(); else renderList();
  if (!feeds[tab]) { await load(tab); renderList(); }
});

(async () => { renderList(); await load(tab); renderList(); Object.keys(LABELS).filter(k => k !== tab).forEach(k => load(k)); })();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
