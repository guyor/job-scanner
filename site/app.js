(() => {
  'use strict';

  /* ---------- helpers ---------- */
  const $ = (sel, root = document) => root.querySelector(sel);
  const MIN = 60 * 1000, HOUR = 60 * MIN, DAY = 24 * HOUR;
  const REFRESH_MS = 5 * MIN;
  const PAGE_SIZE = 40;
  const params = new URLSearchParams(location.search);
  const DEMO = params.has('demo');

  const store = {
    get(key, fallback) {
      try { const v = localStorage.getItem(key); return v == null ? fallback : JSON.parse(v); } catch { return fallback; }
    },
    set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ } },
  };

  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style') el.style.cssText = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(kid));
    }
    return el;
  }

  const ICONS = {
    open: '<path d="M17 17 7 7"/><path d="M7 15V7h8"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    eyeoff: '<path d="M3 3l18 18"/><path d="M10.6 6.2A9.6 9.6 0 0 1 12 6c5 0 8.5 4.2 9.5 6-.5.9-1.6 2.4-3.2 3.7M6.6 7.6C4.6 9 3.3 10.8 2.5 12c1 1.8 4.5 6 9.5 6 1.5 0 2.8-.4 4-1"/><path d="M9.9 10a3 3 0 0 0 4.1 4.1"/>',
    pin: '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z"/><circle cx="12" cy="10" r="2.3"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>',
    moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
  };
  function ico(name) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'ico');
    svg.setAttribute('aria-hidden', 'true');
    svg.innerHTML = ICONS[name]; // static markup, never data
    return svg;
  }

  /* ---------- Hebrew time ---------- */
  function agoLong(ms, now) {
    const s = Math.max(0, (now - ms) / 1000);
    if (s < 60) return 'הרגע';
    const m = Math.floor(s / 60);
    if (m < 60) return m === 1 ? 'לפני דקה' : m === 2 ? 'לפני 2 דקות' : `לפני ${m} דקות`;
    const hr = Math.floor(m / 60);
    if (hr < 24) return hr === 1 ? 'לפני שעה' : hr === 2 ? 'לפני שעתיים' : `לפני ${hr} שעות`;
    const d = Math.floor(hr / 24);
    if (d === 1) return 'אתמול';
    if (d === 2) return 'לפני יומיים';
    if (d < 7) return `לפני ${d} ימים`;
    const w = Math.floor(d / 7);
    return w === 1 ? 'לפני שבוע' : w === 2 ? 'לפני שבועיים' : `לפני ${w} שבועות`;
  }
  function agoShort(ms, now) {
    const s = Math.max(0, (now - ms) / 1000);
    if (s < 60) return 'הרגע';
    const m = Math.floor(s / 60);
    if (m < 60) return `${m} דק׳`;
    const hr = Math.floor(m / 60);
    if (hr < 24) return `${hr} שע׳`;
    const d = Math.floor(hr / 24);
    if (d === 1) return 'אתמול';
    if (d < 7) return `${d} ימים`;
    return `${Math.floor(d / 7)} שב׳`;
  }
  function gapText(minutes) {
    if (minutes < 60) return `${minutes} דקות`;
    const hr = Math.round(minutes / 60);
    if (hr < 24) return hr === 1 ? 'שעה' : hr === 2 ? 'שעתיים' : `${hr} שעות`;
    const d = Math.round(hr / 24);
    return d === 1 ? 'יום' : d === 2 ? 'יומיים' : `${d} ימים`;
  }
  function groupOf(ms, now) {
    const age = now - ms;
    if (age < HOUR) return 'עכשיו';
    const startToday = new Date(now); startToday.setHours(0, 0, 0, 0);
    if (ms >= startToday.getTime()) return 'היום';
    if (ms >= startToday.getTime() - DAY) return 'אתמול';
    if (age < 7 * DAY) return 'השבוע';
    return 'קודם';
  }
  function ageFactor(ms, now) {
    const hours = Math.max(0, (now - ms) / HOUR);
    return Math.min(1, Math.pow(hours / 72, 0.55));
  }

  /* ---------- cities and levels ---------- */
  const CITIES = [
    ['tel aviv', 'תל אביב'], ['ramat gan', 'רמת גן'], ['givatayim', 'גבעתיים'], ['herzliya', 'הרצליה'],
    ['haifa', 'חיפה'], ['jerusalem', 'ירושלים'], ['petah tikva', 'פתח תקווה'], ['petach tikva', 'פתח תקווה'],
    ['raanana', 'רעננה'], ['ra anana', 'רעננה'], ['netanya', 'נתניה'], ['rehovot', 'רחובות'],
    ['kfar saba', 'כפר סבא'], ['hod hasharon', 'הוד השרון'], ['beer sheva', 'באר שבע'], ['be er sheva', 'באר שבע'],
    ['rosh haayin', 'ראש העין'], ['rosh ha ayin', 'ראש העין'], ['yokneam', 'יקנעם'], ['caesarea', 'קיסריה'],
    ['holon', 'חולון'], ['rishon', 'ראשון לציון'], ['modiin', 'מודיעין'], ['modi in', 'מודיעין'],
    ['remote', 'עבודה מרחוק'], ['תל אביב', 'תל אביב'], ['רמת גן', 'רמת גן'], ['הרצליה', 'הרצליה'],
    ['חיפה', 'חיפה'], ['ירושלים', 'ירושלים'], ['פתח תקווה', 'פתח תקווה'], ['רעננה', 'רעננה'],
    ['נתניה', 'נתניה'], ['רחובות', 'רחובות'], ['כפר סבא', 'כפר סבא'], ['הוד השרון', 'הוד השרון'],
    ['באר שבע', 'באר שבע'], ['מרחוק', 'עבודה מרחוק'],
  ];
  function cityOf(location) {
    const norm = String(location || '').toLowerCase().replace(/[’'`\-–—.,]/g, ' ').replace(/\s+/g, ' ');
    for (const [needle, he] of CITIES) if (norm.includes(needle)) return he;
    const first = String(location || '').split(',')[0].trim();
    return !first || /^israel$|^ישראל$/i.test(first) ? 'ישראל' : first;
  }
  const LEVELS = [['junior', 'Junior'], ['mid', 'Mid'], ['senior', 'Senior'], ['experienced', 'Experienced']];
  const RANGES = [['24', '24 שעות', DAY], ['72', '3 ימים', 3 * DAY], ['168', 'שבוע', 7 * DAY], ['all', 'הכול', Infinity]];

  function hueOf(text) {
    let x = 0;
    for (const ch of text) x = (x * 31 + ch.codePointAt(0)) % 360;
    return x;
  }
  function initialOf(text) {
    const m = String(text).trim().match(/[A-Za-z֐-׿0-9]/);
    return m ? m[0].toUpperCase() : '•';
  }
  function safeUrl(u) {
    try { const url = new URL(u); return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null; } catch { return null; }
  }

  /* ---------- state ---------- */
  const marks = store.get('marks', {});       // key -> { s: 'applied' | 'hidden', t: ms }
  const prevVisit = store.get('lastVisit', 0);
  const state = {
    data: null,
    jobs: [],
    now: Date.now(),
    tab: params.get('tab') === 'companies' ? 'companies' : 'new',
    q: params.get('q') || '',
    levels: new Set((params.get('level') || '').split(',').filter(Boolean)),
    city: params.get('city') || '',
    company: params.get('company') || '',
    range: RANGES.some(r => r[0] === params.get('range')) ? params.get('range') : 'all',
    showHidden: false,
    limit: PAGE_SIZE,
    pending: null,
  };

  const el = {
    status: $('#status'), statusText: $('#status-text'), stale: $('#stale'), theme: $('#theme'),
    q: $('#q'), levels: $('#levels'), range: $('#range'), cities: $('#cities'), company: $('#company'),
    showHidden: $('#show-hidden'), showHiddenLabel: $('#show-hidden-label'), clear: $('#clear'),
    list: $('#list'), count: $('#count'), more: $('#more'), moreBtn: $('#more-btn'), state: $('#state'),
    nNew: $('#n-new'), nCompanies: $('#n-companies'), filtersCount: $('#filters-count'),
    toast: $('#toast'), toastMsg: $('#toast-msg'), toastAct: $('#toast-act'),
  };

  /* ---------- data ---------- */
  function normalize(raw, now, demo) {
    const first = demo ? now - raw.ageMinutes * MIN : Date.parse(raw.firstSeenAt);
    const published = demo
      ? (raw.publishedGapMinutes ? first - raw.publishedGapMinutes * MIN : null)
      : (raw.publishedAt ? Date.parse(raw.publishedAt) : null);
    const url = safeUrl(raw.url);
    if (!raw.title || !raw.company || !url || Number.isNaN(first)) return null;
    return {
      key: String(raw.key || `${raw.company}:${raw.title}:${url}`),
      title: String(raw.title),
      company: String(raw.company),
      location: String(raw.location || ''),
      city: cityOf(raw.location),
      url,
      first,
      published: Number.isFinite(published) ? published : null,
      level: ['junior', 'mid', 'senior', 'experienced'].includes(raw.seniority) ? raw.seniority : '',
      tab: raw.tab === 'companies' ? 'companies' : 'new',
    };
  }

  async function fetchFeed() {
    const url = DEMO ? 'demo/feed.sample.json' : `feed.json?t=${Date.now()}`;
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!data || !Array.isArray(data.jobs)) throw new Error('bad feed');
    const now = Date.now();
    const jobs = data.jobs.map(j => normalize(j, now, DEMO)).filter(Boolean);
    const lastScan = DEMO ? now - (data.scanAgeMinutes ?? 4) * MIN : (data.lastScanAt ? Date.parse(data.lastScanAt) : NaN);
    return { data: { ...data, lastScan: Number.isFinite(lastScan) ? lastScan : null }, jobs, now };
  }

  /* ---------- filtering ---------- */
  function isHidden(job) { return marks[job.key]?.s === 'hidden'; }
  function matches(job, skip) {
    if (job.tab !== state.tab) return false;
    if (!state.showHidden && isHidden(job)) return false;
    if (skip !== 'q' && state.q) {
      const hay = `${job.title} ${job.company} ${job.location} ${job.city}`.toLowerCase();
      for (const word of state.q.toLowerCase().split(/\s+/).filter(Boolean)) if (!hay.includes(word)) return false;
    }
    if (skip !== 'level' && state.levels.size && !state.levels.has(job.level)) return false;
    if (skip !== 'city' && state.city && job.city !== state.city) return false;
    if (skip !== 'company' && state.company && job.company !== state.company) return false;
    if (skip !== 'range' && state.range !== 'all') {
      const span = RANGES.find(r => r[0] === state.range)[2];
      if (state.now - job.first > span) return false;
    }
    return true;
  }
  const filtered = skip => state.jobs.filter(j => matches(j, skip)).sort((a, b) => b.first - a.first);
  function activeFilterCount() {
    return (state.q ? 1 : 0) + state.levels.size + (state.city ? 1 : 0) + (state.company ? 1 : 0) + (state.range !== 'all' ? 1 : 0);
  }

  /* ---------- render: filters ---------- */
  function countBy(list, fn) {
    const map = new Map();
    for (const j of list) { const k = fn(j); map.set(k, (map.get(k) || 0) + 1); }
    return [...map.entries()].sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0]), 'he'));
  }

  function renderFilters() {
    el.levels.replaceChildren(...LEVELS.map(([val, label]) => h('button', {
      class: 'chip', type: 'button', 'aria-pressed': String(state.levels.has(val)),
      onclick: () => { state.levels.has(val) ? state.levels.delete(val) : state.levels.add(val); update(); },
    }, label)));

    el.range.replaceChildren(...RANGES.map(([val, label]) => h('button', {
      class: 'seg__btn', type: 'button', 'aria-pressed': String(state.range === val),
      onclick: () => { state.range = val; update(); },
    }, label)));

    const cityCounts = countBy(filtered('city'), j => j.city);
    let top = cityCounts.slice(0, 8);
    if (state.city && !top.some(([c]) => c === state.city)) top.push([state.city, 0]);
    const allCount = cityCounts.reduce((n, [, c]) => n + c, 0);
    el.cities.replaceChildren(
      h('button', { class: 'opt', type: 'button', 'aria-pressed': String(!state.city), onclick: () => { state.city = ''; update(); } },
        h('span', {}, 'כל הארץ'), h('span', { class: 'opt__n' }, String(allCount))),
      ...top.map(([city, n]) => h('button', {
        class: 'opt', type: 'button', 'aria-pressed': String(state.city === city),
        onclick: () => { state.city = state.city === city ? '' : city; update(); },
      }, h('span', {}, city), h('span', { class: 'opt__n' }, String(n)))));

    const companyCounts = countBy(filtered('company'), j => j.company);
    if (state.company && !companyCounts.some(([c]) => c === state.company)) companyCounts.push([state.company, 0]);
    el.company.replaceChildren(
      h('option', { value: '' }, 'כל החברות'),
      ...companyCounts.map(([c, n]) => h('option', { value: c }, `${c} (${n})`)));
    el.company.value = state.company;

    const hiddenN = state.jobs.filter(j => j.tab === state.tab && isHidden(j)).length;
    el.showHidden.checked = state.showHidden;
    el.showHiddenLabel.textContent = hiddenN ? `הצגת משרות שסימנתי כלא מעניינות (${hiddenN})` : 'הצגת משרות שסימנתי כלא מעניינות';
    el.showHidden.closest('.fgroup').hidden = hiddenN === 0 && !state.showHidden;

    const n = activeFilterCount();
    el.filtersCount.hidden = n === 0;
    el.filtersCount.textContent = String(n);
    el.clear.hidden = n === 0;
  }

  /* ---------- render: list ---------- */
  function jobEntry(job, i, arr) {
    const now = state.now;
    const mark = marks[job.key]?.s;
    const isNew = prevVisit > 0 && job.first > prevVisit && job.tab === 'new';
    const age = ageFactor(job.first, now);
    const next = arr[i + 1] ? ageFactor(arr[i + 1].first, now) : age;
    const isNow = now - job.first < HOUR;
    const gap = job.published && job.first - job.published > 5 * MIN ? Math.round((job.first - job.published) / MIN) : 0;

    const timeEl = cls => h('time', {
      class: `job__time ${cls}`, datetime: new Date(job.first).toISOString(),
      title: new Date(job.first).toLocaleString('he-IL'), 'data-ts': job.first, 'data-short': cls ? '' : '1',
    }, cls ? agoLong(job.first, now) : agoShort(job.first, now));

    const title = h('h3', { class: 'job__title' },
      h('a', { href: job.url, target: '_blank', rel: 'noopener noreferrer' }, h('bdi', {}, job.title)));

    const metaLevel = job.level ? h('span', { class: 'tag' }, LEVELS.find(l => l[0] === job.level)[1]) : null;
    const meta = h('div', { class: 'job__meta' },
      h('span', { class: 'job__company' }, h('bdi', {}, job.company)),
      job.location ? h('span', { class: 'job__loc' }, ico('pin'), h('bdi', {}, job.city)) : null,
      metaLevel,
      timeEl('job__time--inline'),
      gap ? h('span', { class: 'job__speed' }, `פורסמה ${gapText(gap)} לפני שנמצאה`) : null);

    const applied = mark === 'applied';
    const hiddenMark = mark === 'hidden';
    const appliedLabel = applied ? 'ביטול סימון ההגשה' : 'הגשתי';
    const hideLabel = hiddenMark ? 'החזרה לרשימה' : 'לא מעניין';
    const actions = h('div', { class: 'job__actions' },
      h('a', { class: 'btn btn--primary', href: job.url, target: '_blank', rel: 'noopener noreferrer' }, 'פתיחת המשרה', ico('open')),
      h('button', {
        class: 'btn btn--icon', type: 'button', 'aria-pressed': String(applied), title: appliedLabel, 'aria-label': appliedLabel,
        onclick: () => toggleMark(job, 'applied'),
      }, ico('check')),
      h('button', {
        class: 'btn btn--icon', type: 'button', 'aria-pressed': String(hiddenMark), title: hideLabel, 'aria-label': hideLabel,
        onclick: () => toggleMark(job, 'hidden'),
      }, ico('eyeoff')));

    const main = h('div', { class: 'job__main' }, h('div', { class: 'job__head' }, title, isNew ? h('span', { class: 'pill' }, 'חדש') : null, applied ? h('span', { class: 'tag tag--done' }, ico('check'), 'הגשתי') : null), meta);

    return h('li', {
      class: `job${isNow ? ' job--now' : ''}${applied ? ' is-applied' : ''}${mark === 'hidden' ? ' is-hidden' : ''}`,
      style: `--age:${age.toFixed(3)};--age-next:${next.toFixed(3)}`,
    },
      timeEl(''),
      h('div', { class: 'job__rail' }, h('span', { class: 'job__node' })),
      h('article', { class: 'job__body' },
        h('div', { class: 'avatar', 'aria-hidden': 'true', style: `--h:${hueOf(job.company)}` }, initialOf(job.company)),
        main, actions));
  }

  function renderList() {
    const jobs = filtered();
    const shown = jobs.slice(0, state.limit);
    const frag = document.createDocumentFragment();
    let lastGroup = '';
    shown.forEach((job, i) => {
      const g = groupOf(job.first, state.now);
      if (g !== lastGroup) {
        lastGroup = g;
        frag.append(h('li', { class: 'group', role: 'presentation' }, h('div', { class: 'group__rail' }), h('span', { class: 'group__label' }, g)));
      }
      frag.append(jobEntry(job, i, shown));
    });
    el.list.replaceChildren(frag);
    el.more.hidden = jobs.length <= shown.length;
    $('#filters-apply').textContent = jobs.length === 1 ? 'הצגת משרה אחת' : `הצגת ${jobs.length} משרות`;

    const tabJobs = state.jobs.filter(j => j.tab === state.tab && !isHidden(j));
    el.count.textContent = jobs.length === tabJobs.length
      ? (jobs.length === 1 ? 'משרה אחת' : `${jobs.length} משרות`)
      : `${jobs.length} מתוך ${tabJobs.length} משרות`;

    el.state.hidden = jobs.length > 0;
    if (!jobs.length) {
      if (tabJobs.length && activeFilterCount()) {
        showState('אין משרות שמתאימות לסינון', 'אפשר להסיר חלק מהסינונים או לנקות את כולם.', 'ניקוי סינונים', clearFilters);
      } else if (state.tab === 'companies') {
        showState('אין עדיין חברות חדשות במאגר', 'חברות שייכנסו למאגר יופיעו כאן עם המשרות שכבר היו פתוחות אצלן.');
      } else {
        showState('עדיין אין משרות חדשות', 'הסריקה בודקת את החברות שוב ושוב. משרה חדשה תופיע כאן ברגע שתימצא.');
      }
    }
  }

  function showState(title, text, actLabel, act) {
    el.state.replaceChildren(
      h('h2', { class: 'state__title' }, title),
      h('p', { class: 'state__text' }, text),
      actLabel ? h('button', { class: 'btn btn--primary', type: 'button', onclick: act }, actLabel) : null);
    el.state.hidden = false;
  }

  function renderTabs() {
    const base = j => !isHidden(j) || state.showHidden;
    el.nNew.textContent = String(state.jobs.filter(j => j.tab === 'new' && base(j)).length);
    el.nCompanies.textContent = String(state.jobs.filter(j => j.tab === 'companies' && base(j)).length);
    for (const btn of document.querySelectorAll('.tab')) btn.setAttribute('aria-selected', String(btn.dataset.tab === state.tab));
  }

  function renderStatus() {
    const { data } = state;
    if (!data) return;
    const stale = data.lastScan && state.now - data.lastScan > DAY;
    el.status.classList.toggle('is-live', !!data.lastScan && !stale);
    el.status.classList.toggle('is-stale', !!stale);
    const parts = [];
    parts.push(data.lastScan ? `נסרק ${agoLong(data.lastScan, state.now)}` : 'עדיין לא נסרק');
    if (data.stats?.companies) parts.push(`${Number(data.stats.companies).toLocaleString('he-IL')} חברות במעקב`);
    el.statusText.textContent = parts.join('. ');
    el.stale.hidden = !stale;
    if (stale) el.stale.textContent = `הסריקה האחרונה הייתה ${agoLong(data.lastScan, state.now)}. ייתכן שהסריקה נעצרה, והמשרות שמוצגות כאן אינן עדכניות.`;
    const fresh = state.jobs.filter(j => j.tab === 'new' && prevVisit > 0 && j.first > prevVisit).length;
    document.title = fresh ? `(${fresh}) סורק משרות` : 'סורק משרות';
  }

  function renderAll() {
    renderStatus(); renderTabs(); renderFilters(); renderList(); syncUrl();
  }
  function update() { state.limit = PAGE_SIZE; renderAll(); }

  function syncUrl() {
    const p = new URLSearchParams();
    if (DEMO) p.set('demo', '');
    if (state.tab !== 'new') p.set('tab', state.tab);
    if (state.q) p.set('q', state.q);
    if (state.levels.size) p.set('level', [...state.levels].join(','));
    if (state.city) p.set('city', state.city);
    if (state.company) p.set('company', state.company);
    if (state.range !== 'all') p.set('range', state.range);
    const qs = p.toString().replace('demo=', 'demo');
    try { history.replaceState(null, '', qs ? `?${qs}` : location.pathname); } catch { /* ignore */ }
  }

  function clearFilters() {
    state.q = ''; state.levels.clear(); state.city = ''; state.company = ''; state.range = 'all';
    el.q.value = '';
    update();
  }

  /* ---------- marks, toast ---------- */
  function saveMarks() {
    const entries = Object.entries(marks);
    if (entries.length > 1500) {
      entries.sort((a, b) => b[1].t - a[1].t);
      for (const [k] of entries.slice(1500)) delete marks[k];
    }
    store.set('marks', marks);
  }
  function toggleMark(job, kind) {
    const was = marks[job.key]?.s;
    if (was === kind) delete marks[job.key]; else marks[job.key] = { s: kind, t: Date.now() };
    saveMarks();
    update();
    if (kind === 'hidden' && was !== 'hidden') {
      showToast('סימנת את המשרה כלא מעניינת', 'ביטול', () => { delete marks[job.key]; saveMarks(); update(); });
    }
  }
  let toastTimer = 0;
  function showToast(message, actLabel, act, ms = 6000) {
    clearTimeout(toastTimer);
    el.toastMsg.textContent = message;
    el.toastAct.textContent = actLabel;
    el.toastAct.onclick = () => { hideToast(); act(); };
    el.toast.hidden = false;
    el.toast.style.animation = 'none'; void el.toast.offsetWidth; el.toast.style.animation = '';
    if (ms) toastTimer = setTimeout(hideToast, ms);
  }
  function hideToast() { clearTimeout(toastTimer); el.toast.hidden = true; }

  /* ---------- loading and refresh ---------- */
  function showSkeleton() {
    const frag = document.createDocumentFragment();
    for (let i = 0; i < 4; i++) {
      frag.append(h('li', { class: 'job skel', style: `--age:${i / 4};--age-next:${(i + 1) / 4}`, 'aria-hidden': 'true' },
        h('span', { class: 'job__time' }),
        h('div', { class: 'job__rail' }, h('span', { class: 'job__node' })),
        h('div', { class: 'job__body' }, h('div', { class: 'avatar' }), h('div', {},
          h('span', { class: 'line', style: 'width:55%' }), h('span', { class: 'line', style: 'width:35%' })))));
    }
    el.list.replaceChildren(frag);
  }

  async function load(initial) {
    try {
      const next = await fetchFeed();
      if (!initial && state.data) {
        const known = new Set(state.jobs.map(j => j.key));
        const added = next.jobs.filter(j => !known.has(j.key) && j.tab === 'new' && !isHidden(j)).length;
        if (added && window.scrollY > 240) {
          state.pending = next;
          showToast(added === 1 ? 'נמצאה משרה חדשה' : `נמצאו ${added} משרות חדשות`, 'הצגה', () => { applyFeed(state.pending); window.scrollTo({ top: 0 }); }, 0);
          return;
        }
      }
      applyFeed(next);
    } catch (err) {
      if (initial || !state.data) {
        el.list.replaceChildren();
        el.more.hidden = true;
        el.statusText.textContent = 'הפיד לא נטען';
        showState('לא הצלחנו לטעון את הפיד', DEMO ? 'קובץ הדמו לא נמצא.' : 'ייתכן שהסריקה הראשונה עוד לא הסתיימה, או שיש תקלה זמנית.', 'ניסיון חוזר', () => { showSkeleton(); load(true); });
      }
    }
  }
  function applyFeed(next) {
    state.data = next.data; state.jobs = next.jobs; state.now = next.now; state.pending = null;
    renderAll();
  }

  function tickTimes() {
    state.now = Date.now();
    for (const t of document.querySelectorAll('time[data-ts]')) {
      const ts = Number(t.dataset.ts);
      t.textContent = t.dataset.short ? agoShort(ts, state.now) : agoLong(ts, state.now);
    }
    renderStatus();
  }

  /* ---------- theme, filter sheet ---------- */
  function paintTheme() {
    const dark = document.documentElement.dataset.theme === 'dark';
    el.theme.replaceChildren(ico(dark ? 'sun' : 'moon'));
    el.theme.setAttribute('aria-label', dark ? 'מעבר למצב בהיר' : 'מעבר למצב כהה');
  }
  function openFilters() {
    document.body.classList.add('filters-open');
    $('#scrim').hidden = false;
    $('#filters-close').focus();
  }
  function closeFilters() {
    document.body.classList.remove('filters-open');
    $('#scrim').hidden = true;
    $('#filters-open').focus();
  }

  /* ---------- wiring ---------- */
  el.q.value = state.q;
  let qTimer = 0;
  el.q.addEventListener('input', () => {
    clearTimeout(qTimer);
    qTimer = setTimeout(() => { state.q = el.q.value.trim(); update(); }, 120);
  });
  el.company.addEventListener('change', () => { state.company = el.company.value; update(); });
  el.showHidden.addEventListener('change', () => { state.showHidden = el.showHidden.checked; update(); });
  el.clear.addEventListener('click', clearFilters);
  el.moreBtn.addEventListener('click', () => { state.limit += PAGE_SIZE; renderList(); });
  for (const btn of document.querySelectorAll('.tab')) {
    btn.addEventListener('click', () => { state.tab = btn.dataset.tab; state.company = ''; state.city = ''; update(); });
  }
  el.theme.addEventListener('click', () => {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    store.set('theme', next);
    paintTheme();
  });
  $('#filters-open').addEventListener('click', openFilters);
  $('#filters-close').addEventListener('click', closeFilters);
  $('#filters-apply').addEventListener('click', closeFilters);
  $('#scrim').addEventListener('click', closeFilters);
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.body.classList.contains('filters-open')) closeFilters(); });

  const saveVisit = () => store.set('lastVisit', Date.now());
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') saveVisit(); });
  addEventListener('pagehide', saveVisit);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') load(false);
  });

  $('#filters-close').append(ico('close'));
  paintTheme();
  showSkeleton();
  load(true);
  setInterval(() => load(false), REFRESH_MS);
  setInterval(tickTimes, 30 * 1000);
})();
