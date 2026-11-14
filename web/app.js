const LABELS = {
  aktuality: 'Aktuality',
  hlaseni: 'Hlášení městského rozhlasu',
  rm: 'Usnesení rady města',
  zm: 'Usnesení zastupitelstva',
  hlas: 'Hlas Loštic (zpravodaj)'
};
const $ = id => document.getElementById(id);
const store = {
  get: (k, d) => { const v = localStorage.getItem(k); return v === null ? d : parseFloat(v); },
  set: (k, v) => localStorage.setItem(k, v)
};
let tab = 'aktuality', feeds = {}, current = null;
let fontScale = store.get('fontScale', 1), rate = store.get('speechRate', 1);
let chunks = [], idx = 0, speaking = false;
let detailTab = 'summary'; // 'summary' nebo 'full'
let searchQuery = '';

// Uchování pozice scrollování v přehledu
let summaryScrollPos = 0;

// Správa přečtených záznamů
function getReadIds() {
  try {
    const raw = localStorage.getItem('lostice_read_ids');
    return new Set(raw ? JSON.parse(raw) : []);
  } catch (e) {
    return new Set();
  }
}
function saveReadIds(set) {
  try {
    localStorage.setItem('lostice_read_ids', JSON.stringify(Array.from(set)));
  } catch (e) {}
}
let readIds = getReadIds();

function isFirstRun() {
  return localStorage.getItem('lostice_first_run_done') === null;
}
function setFirstRunDone() {
  localStorage.setItem('lostice_first_run_done', 'true');
}

function checkInitialReadState() {
  if (!isFirstRun()) return;
  let changed = false;
  for (const cat of Object.keys(LABELS)) {
    const feed = feeds[cat];
    if (feed && feed.items && feed.items.length > 2) {
      for (let i = 2; i < feed.items.length; i++) {
        if (!readIds.has(feed.items[i].id)) {
          readIds.add(feed.items[i].id);
          changed = true;
        }
      }
    }
  }
  if (changed) {
    saveReadIds(readIds);
  }
  if (Object.keys(feeds).length > 0) {
    setFirstRunDone();
  }
}

function markAsRead(id) {
  if (!readIds.has(id)) {
    readIds.add(id);
    saveReadIds(readIds);
    updateBadges();
  }
}

function markAllAsRead() {
  let anyAdded = false;
  for (const cat of Object.keys(LABELS)) {
    const f = feeds[cat];
    if (f && f.items) {
      for (const it of f.items) {
        if (!readIds.has(it.id)) {
          readIds.add(it.id);
          anyAdded = true;
        }
      }
    }
  }
  if (anyAdded) {
    saveReadIds(readIds);
  }
  renderList();
  updateBadges();
}

function updateBadges() {
  let totalUnread = 0;
  for (const cat of Object.keys(LABELS)) {
    const f = feeds[cat];
    const badgeEl = $(`badge-${cat}`);
    if (!f || !f.items) {
      if (badgeEl) badgeEl.hidden = true;
      continue;
    }
    const unreadCount = f.items.filter(it => !readIds.has(it.id)).length;
    totalUnread += unreadCount;
    if (badgeEl) {
      if (unreadCount > 0) {
        badgeEl.textContent = unreadCount > 99 ? '99+' : unreadCount;
        badgeEl.hidden = false;
      } else {
        badgeEl.hidden = true;
      }
    }
  }
  const markAllBtn = $('mark-all-read');
  if (markAllBtn) {
    markAllBtn.disabled = totalUnread === 0;
    markAllBtn.style.opacity = totalUnread === 0 ? '0.35' : '1';
  }
}

const fmtDate = d => { if (!d) return ''; const [y, m, dd] = d.split('-'); return `${+dd}. ${+m}. ${y}`; };
const r1 = v => Math.round(v * 10) / 10;

async function load(kind, force) {
  try {
    const r = await fetch(`data/${kind}.json` + (force ? `?t=${Date.now()}` : ''));
    feeds[kind] = await r.json();
  } catch (e) { /* offline – ponecháme data */ }
}

function renderList() {
  $('title').textContent = LABELS[tab];
  const list = $('list'); list.innerHTML = '';
  const f = feeds[tab];
  if (!f) { list.innerHTML = '<div class="msg">Načítání…</div>'; return; }
  f.items.forEach(it => {
    const isUnread = !readIds.has(it.id);
    const c = document.createElement('div');
    c.className = 'card' + (isUnread ? ' unread' : '');
    const prev = it.text.replace(/\s+/g, ' ').slice(0, 160);
    let countBadge = '';
    if (it.resolutions && it.resolutions.length) {
      countBadge = `<span style="font-size:0.8rem;color:var(--p);font-weight:600">${it.resolutions.length} bodů</span>`;
    } else if (tab === 'hlas' && it.pages_count) {
      countBadge = `<span style="font-size:0.8rem;color:var(--p);font-weight:600">${it.pages_count} stran A5</span>`;
    } else if (it.images && it.images.length) {
      countBadge = `<span style="font-size:0.8rem;color:var(--p);font-weight:600">📷 ${it.images.length}</span>`;
    } else if (it.files && it.files.length) {
      countBadge = `<span style="font-size:0.8rem;color:var(--p);font-weight:600">📎 ${it.files.length}</span>`;
    }
    const newBadge = isUnread ? '<span class="new-tag">NOVÉ</span>' : '';
    c.innerHTML = `<div class="d"><span>${newBadge}${fmtDate(it.date)}</span>${countBadge}</div><div class="t"></div><div class="p"></div>`;
    c.children[1].textContent = it.title;
    c.children[2].textContent = prev;
    c.onclick = () => {
      markAsRead(it.id);
      openItem(it);
    };
    list.appendChild(c);
  });
  updateBadges();
}

function applyFont() {
  const content = $('d-content');
  if (content) content.style.fontSize = (16 * fontScale) + 'px';
  $('font-val').textContent = Math.round(fontScale * 100) + ' %';
  $('rate-val').textContent = rate.toFixed(1) + '×';
}

function getBadgeClass(decision) {
  if (['SCHVALUJE', 'SOUHLASÍ', 'VOLÍ', 'JMENUJE'].includes(decision)) return 'schvaluje';
  if (decision === 'NESCHVALUJE') return 'neschvaluje';
  if (decision === 'BERE NA VĚDOMÍ') return 'vedomi';
  if (['UKLÁDÁ', 'POVĚŘUJE'].includes(decision)) return 'uklada';
  return 'ostatni';
}

function highlight(text, q) {
  if (!q) return escapeHtml(text);
  const regex = new RegExp(`(${escapeRegex(q)})`, 'gi');
  return escapeHtml(text).replace(regex, '<mark>$1</mark>');
}

function escapeHtml(s) {
  return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function scrollToResolution(idx) {
  // Zapamatovat si aktuální scroll pozici v přehledu
  summaryScrollPos = window.scrollY;
  
  // Přepnout na Celé znění
  detailTab = 'full';
  $('tab-full').classList.add('active');
  $('tab-summary').classList.remove('active');
  renderDetailContent();

  setTimeout(() => {
    const target = document.getElementById(`res-block-${idx}`);
    if (target) {
      target.scrollIntoView({behavior: 'smooth', block: 'center'});
      target.classList.add('highlight');
      setTimeout(() => target.classList.remove('highlight'), 1500);
    }
  }, 50);
}

function renderDetailContent() {
  const it = current;
  if (!it) return;
  const container = $('d-content');
  container.innerHTML = '';

  const hasRes = it.resolutions && it.resolutions.length > 0;
  $('sub-tabs').hidden = !hasRes;
  $('search-box').hidden = !hasRes;
  $('jump-bar').hidden = !hasRes || detailTab !== 'full';

  // Hlas Loštic ovládání
  const isHlas = Boolean(it.a5_url);
  $('controls-reading').hidden = isHlas;
  $('controls-hlas').hidden = !isHlas;
  if (isHlas) {
    $('btn-a5').href = it.a5_url;
  }

  if (!hasRes) {
    let html = '';
    
    // Obrázky
    if (it.images && it.images.length > 0) {
      html += '<div style="display:flex;gap:10px;overflow-x:auto;padding-bottom:12px;margin-bottom:12px">';
      it.images.forEach(img => {
        html += `<a href="${img}" target="_blank" rel="noopener"><img src="${img}" style="max-height:220px;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.15);object-fit:cover" loading="lazy"></a>`;
      });
      html += '</div>';
    }

    if (it.toc && it.toc.length > 0) {
      html += `<div style="margin-bottom:12px;font-weight:700;color:var(--p)">📑 Přehled článků (${it.toc.length}):</div><div style="display:flex;flex-direction:column;gap:8px">`;
      it.toc.forEach(item => {
        html += `
          <a href="${it.a5_url || it.url}#page=${item.page}" target="_blank" class="res-summary-card" style="text-decoration:none;color:inherit;display:flex;align-items:center;justify-content:space-between">
            <span style="font-weight:600">${escapeHtml(item.title)}</span>
            <span class="res-badge ostatni" style="white-space:nowrap;margin-left:8px">str. ${item.page}</span>
          </a>
        `;
      });
      html += `</div>`;
    } else {
      html += `<div style="white-space:pre-wrap;line-height:1.6">${escapeHtml(it.text || 'Text se nepodařilo získat. Otevřete původní odkaz.')}</div>`;
    }

    // Přílohy a soubory
    if (it.files && it.files.length > 0) {
      html += '<div style="margin-top:16px;padding:12px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px">';
      html += `<div style="font-weight:700;margin-bottom:8px;color:var(--p)">📎 Přílohy a soubory (${it.files.length}):</div>`;
      it.files.forEach(f => {
        html += `
          <a href="${f.url}" target="_blank" rel="noopener" class="res-summary-card" style="display:flex;align-items:center;justify-content:space-between;text-decoration:none;color:inherit;margin-bottom:6px">
            <span style="font-weight:600">${escapeHtml(f.name)}</span>
            <span style="color:var(--s);font-size:0.85rem;font-weight:bold">Otevřít ↗</span>
          </a>
        `;
      });
      html += '</div>';
    }

    container.innerHTML = html;
    applyFont();
    return;
  }

  const q = searchQuery.trim().toLowerCase();

  if (detailTab === 'summary') {
    const list = it.resolutions.map((r, idx) => ({res: r, origIdx: idx}))
      .filter(x => !x.res.is_procedural && (!q || x.res.num.toLowerCase().includes(q) || x.res.summary.toLowerCase().includes(q) || x.res.text.toLowerCase().includes(q)));
    
    if (list.length === 0) {
      container.innerHTML = '<div class="msg">Žádné usnesení neodpovídá hledání.</div>';
    } else {
      list.forEach(({res, origIdx}) => {
        const card = document.createElement('div');
        card.className = 'res-summary-card';
        card.style.cursor = 'pointer';
        card.title = 'Klepnutím zobrazíte celé znění tohoto usnesení';
        card.innerHTML = `
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
            <b style="color:var(--p)">Usnesení ${highlight(res.num, q)}</b>
            <span class="res-badge ${getBadgeClass(res.decision)}">${escapeHtml(res.decision)}</span>
          </div>
          <div>${highlight(res.summary, q)}</div>
          <div style="text-align:right;font-size:0.8rem;color:var(--s);font-weight:700;margin-top:4px">Zobrazit celé znění →</div>
        `;
        card.onclick = () => scrollToResolution(origIdx);
        container.appendChild(card);
      });
    }
  } else {
    // Záložka Celé znění
    if (it.header && !q) {
      const hDiv = document.createElement('div');
      hDiv.style.cssText = 'color:#64748b;margin-bottom:14px;padding:10px;background:#f1f5f9;border-radius:8px;font-size:0.95em';
      hDiv.textContent = it.header;
      container.appendChild(hDiv);
    }

    // Render jump bar
    const jumpBar = $('jump-bar');
    jumpBar.innerHTML = '';
    it.resolutions.forEach((res, idx) => {
      const chip = document.createElement('button');
      chip.className = 'jump-chip';
      const shortNum = res.num.split('/')[1] || res.num;
      chip.textContent = `Bod ${shortNum}`;
      chip.onclick = () => {
        const target = document.getElementById(`res-block-${idx}`);
        if (target) {
          target.scrollIntoView({behavior: 'smooth', block: 'center'});
          target.classList.add('highlight');
          setTimeout(() => target.classList.remove('highlight'), 1200);
        }
      };
      jumpBar.appendChild(chip);
    });

    const list = it.resolutions.map((res, idx) => ({res, idx})).filter(x => !q || x.res.num.toLowerCase().includes(q) || x.res.text.toLowerCase().includes(q));

    if (list.length === 0) {
      container.innerHTML = '<div class="msg">Žádné usnesení neodpovídá hledání.</div>';
    } else {
      list.forEach(({res, idx}) => {
        const block = document.createElement('div');
        block.className = 'res-block';
        block.id = `res-block-${idx}`;
        block.innerHTML = `
          <div class="res-header">
            <span class="res-num">Usnesení ${highlight(res.num, q)}:</span>
            <span class="res-badge ${getBadgeClass(res.decision)}">${escapeHtml(res.decision)}</span>
          </div>
          <div class="res-body">${highlight(res.text, q)}</div>
        `;
        container.appendChild(block);
      });
    }
  }

  applyFont();
}

function openItem(it) {
  current = it; history.pushState({d: 1}, '');
  detailTab = (it.resolutions && it.resolutions.length > 0) ? 'summary' : 'full';
  searchQuery = '';
  summaryScrollPos = 0;
  $('search-input').value = '';
  $('list-view').hidden = true; $('detail-view').hidden = false; $('back').hidden = false;
  $('title').textContent = it.title;
  $('d-date').textContent = fmtDate(it.date);
  $('pdf').href = it.url;
  $('pdf').textContent = (tab === 'aktuality') ? 'Původní odkaz' : 'Původní PDF';
  
  $('tab-summary').classList.toggle('active', detailTab === 'summary');
  $('tab-full').classList.toggle('active', detailTab === 'full');
  
  renderDetailContent();
  window.scrollTo(0, 0);
}

function closeItem() {
  stop(); current = null;
  $('list-view').hidden = false; $('detail-view').hidden = true; $('back').hidden = true;
  renderList();
}

$('tab-summary').onclick = () => {
  detailTab = 'summary';
  $('tab-summary').classList.add('active');
  $('tab-full').classList.remove('active');
  renderDetailContent();
  // Návrat na původní pozici scrollování v přehledu
  window.scrollTo({top: summaryScrollPos, behavior: 'instant'});
};

$('tab-full').onclick = () => {
  detailTab = 'full';
  $('tab-full').classList.add('active');
  $('tab-summary').classList.remove('active');
  renderDetailContent();
};

$('search-input').oninput = e => {
  searchQuery = e.target.value;
  renderDetailContent();
};

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
  let speakText = '';
  if (current.resolutions && current.resolutions.length && detailTab === 'summary') {
    speakText = `${current.title}.\nStručný přehled schválených usnesení:\n` +
      current.resolutions.filter(r => !r.is_procedural).map(r => `Usnesení ${r.num}: ${r.summary}`).join('\n');
  } else {
    speakText = `${current.title}.\n${current.text}`;
  }
  chunks = split(speakText);
  speakFrom(0);
};

$('font-minus').onclick = () => { fontScale = Math.max(0.7, r1(fontScale - 0.1)); store.set('fontScale', fontScale); applyFont(); };
$('font-plus').onclick = () => { fontScale = Math.min(2.5, r1(fontScale + 0.1)); store.set('fontScale', fontScale); applyFont(); };
function setRate(v) { rate = v; store.set('speechRate', rate); applyFont(); if (speaking) speakFrom(idx); }
$('rate-minus').onclick = () => setRate(Math.max(0.5, r1(rate - 0.1)));
$('rate-plus').onclick = () => setRate(Math.min(2, r1(rate + 0.1)));
$('back').onclick = () => history.back();
window.onpopstate = () => { if (current) closeItem(); };
$('mark-all-read').onclick = () => { markAllAsRead(); };
$('refresh').onclick = async () => { await load(tab, true); if (!current) renderList(); updateBadges(); };
document.querySelectorAll('nav button').forEach(b => b.onclick = async () => {
  document.querySelectorAll('nav button').forEach(x => x.classList.remove('active'));
  b.classList.add('active'); tab = b.dataset.tab;
  if (current) closeItem(); else renderList();
  if (!feeds[tab]) { await load(tab); renderList(); }
});

(async () => {
  renderList();
  await load(tab);
  checkInitialReadState();
  renderList();
  for (const k of Object.keys(LABELS).filter(k => k !== tab)) {
    load(k).then(() => {
      checkInitialReadState();
      updateBadges();
    });
  }
})();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
