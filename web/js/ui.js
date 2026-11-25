// HTML panels: Driver Station, match, launcher, telemetry, events, report, knobs.
import { $, clamp, rad } from './util.js';

const LS_COLLAPSED = 'biobuzz.collapsed';

function setText(el, text) {
  if (el._t !== text) { el._t = text; el.textContent = text; }
}
function setClass(el, cls) {
  if (el._c !== cls) { el._c = cls; el.className = cls; }
}
export function isTyping(e) {
  const t = e.target;
  return !!t && (t.tagName === 'INPUT' || t.tagName === 'SELECT' || t.tagName === 'TEXTAREA' || t.isContentEditable);
}
function fmtClock(sec) {
  const s = Math.max(0, sec || 0);
  const m = Math.floor(s / 60);
  const r = s - m * 60;
  return s < 10 ? `0:0${r.toFixed(1)}` : `${m}:${String(Math.floor(r)).padStart(2, '0')}`;
}

export function createUI({ send, scene, isMock }) {
  let hello = null;
  let state = null;
  let status = 'NONE';
  let codeOk = false;
  let connected = !!isMock;
  let reloading = false;

  // ===== collapsible panels =====
  let collapsed = [];
  try { collapsed = JSON.parse(localStorage.getItem(LS_COLLAPSED) || '[]'); } catch (e) { collapsed = []; }
  for (const p of document.querySelectorAll('.panel')) {
    if (collapsed.includes(p.id)) p.classList.add('collapsed');
    p.querySelector('h3').addEventListener('click', () => {
      p.classList.toggle('collapsed');
      const ids = [...document.querySelectorAll('.panel.collapsed')].map((x) => x.id);
      try { localStorage.setItem(LS_COLLAPSED, JSON.stringify(ids)); } catch (e) { /* private mode */ }
    });
  }
  // a focused button or select would swallow the driving keys, so hand focus back
  document.addEventListener('click', (e) => {
    const b = e.target.closest && e.target.closest('button');
    if (b) b.blur();
  });
  document.addEventListener('change', (e) => {
    if (e.target.tagName === 'SELECT' || e.target.type === 'range') e.target.blur();
  });

  // ===== connection =====
  function setConn(open, url) {
    const el = $('conn');
    if (isMock) { setText(el, 'mock engine'); setClass(el, 'badge warn'); $('mockBadge').classList.remove('hidden'); return; }
    connected = open;
    setText(el, open ? 'connected' : 'disconnected');
    setClass(el, open ? 'badge ok' : 'badge bad');
    el.title = url || '';
    if (!open) status = 'NONE';
    refreshButtons();
  }

  // ===== Driver Station =====
  const opSelect = $('opSelect'), fmAuto = $('fmAuto'), fmTeleop = $('fmTeleop');

  function fillSelect(sel, modes, grouped) {
    const keep = sel.value;
    sel.textContent = '';
    if (grouped) {
      const flavors = [...new Set(modes.map((m) => m.flavor || 'OTHER'))];
      flavors.sort((a, b) => (a === 'AUTO' ? -1 : b === 'AUTO' ? 1 : a.localeCompare(b)));
      for (const f of flavors) {
        const og = document.createElement('optgroup');
        og.label = f;
        for (const m of modes.filter((x) => (x.flavor || 'OTHER') === f)) og.appendChild(new Option(m.name, m.name));
        sel.appendChild(og);
      }
    } else {
      for (const m of modes) sel.appendChild(new Option(m.name, m.name));
    }
    if (keep && modes.some((m) => m.name === keep)) sel.value = keep;
  }

  function pickDefaults(alliance) {
    if (!hello) return;
    const autos = hello.opModes.filter((m) => m.flavor === 'AUTO');
    const teles = hello.opModes.filter((m) => m.flavor === 'TELEOP');
    const side = alliance === 'RED' ? /^red/i : /^blue/i;
    const best = autos.find((m) => side.test(m.name) && /hive/i.test(m.name)) || autos.find((m) => side.test(m.name)) || autos[0];
    if (best) fmAuto.value = best.name;
    const tele = teles.find((m) => /comp/i.test(m.name)) || teles[0];
    if (tele && !fmTeleop._touched) fmTeleop.value = tele.name;
  }

  function onHello(h) {
    hello = h;
    const c = h.code || {};
    codeOk = !!c.ok;
    setText($('codeBranch'), `${c.branch || '?'} @ ${c.commit || '?'}`);
    $('codeDirty').classList.toggle('hidden', !c.dirty);
    setText($('codeOk'), codeOk ? 'compiled' : 'COMPILE FAILED');
    setClass($('codeOk'), codeOk ? 'badge ok' : 'badge bad');
    $('codeBox').classList.toggle('bad', !codeOk);
    setText($('codePath'), c.path || '');
    $('codePath').title = c.path || '';
    setText($('codeTime'), c.compiledAt ? `compiled at ${c.compiledAt}` : '');
    const ul = $('codeErrors');
    ul.textContent = '';
    const errs = c.errors || [];
    ul.classList.toggle('hidden', codeOk || errs.length === 0);
    for (const e of errs) {
      const li = document.createElement('li');
      li.textContent = `${e.file || '?'}:${e.line != null ? e.line : '?'}  ${e.msg || ''}`;
      ul.appendChild(li);
    }
    setText($('reloadBtn'), 'Reload code');
    reloading = false;

    const modes = h.opModes || [];
    fillSelect(opSelect, modes, true);
    fillSelect(fmAuto, modes.filter((m) => m.flavor === 'AUTO'), false);
    fillSelect(fmTeleop, modes.filter((m) => m.flavor === 'TELEOP'), false);
    pickDefaults(state && state.match ? state.match.alliance : 'BLUE');
    buildKnobs(h.knobs || {}, h.knobMeta || []);
    refreshButtons();
  }

  function refreshButtons() {
    const hasOp = !!opSelect.value;
    const busy = status === 'INIT' || status === 'RUNNING';
    $('initBtn').disabled = !connected || !codeOk || !hasOp || busy;
    $('startBtn').disabled = status !== 'INIT';
    $('stopBtn').disabled = !busy;
    opSelect.disabled = busy || !codeOk;
    $('placeBtn').disabled = !connected || status === 'RUNNING';
    $('fullMatchBtn').disabled = !connected || !codeOk || busy || !fmAuto.value || !fmTeleop.value;
    for (const id of ['reloadBtn', 'resetBtn', 'allyBlue', 'allyRed']) $(id).disabled = !connected;
  }

  opSelect.addEventListener('change', () => { send({ type: 'select', opMode: opSelect.value }); refreshButtons(); });
  fmTeleop.addEventListener('change', () => { fmTeleop._touched = true; });
  $('initBtn').addEventListener('click', () => {
    if (!opSelect.value) return;
    send({ type: 'select', opMode: opSelect.value });
    send({ type: 'init' });
  });
  $('startBtn').addEventListener('click', () => send({ type: 'start' }));
  $('stopBtn').addEventListener('click', () => send({ type: 'stop' }));
  $('reloadBtn').addEventListener('click', () => {
    if (reloading) return;
    send({ type: 'reload' });
    reloading = true;
    setText($('reloadBtn'), 'Compiling...');
    setTimeout(() => { reloading = false; setText($('reloadBtn'), 'Reload code'); }, 20000);
  });
  $('resetBtn').addEventListener('click', () => { send({ type: 'reset' }); scene.clearTrail(); });
  $('allyBlue').addEventListener('click', () => { send({ type: 'alliance', value: 'BLUE' }); $('poseSelect').selectedIndex = 0; pickDefaults('BLUE'); });
  $('allyRed').addEventListener('click', () => { send({ type: 'alliance', value: 'RED' }); $('poseSelect').selectedIndex = 2; pickDefaults('RED'); });
  $('placeBtn').addEventListener('click', () => {
    const [x, y, hDeg] = $('poseSelect').value.split(',').map(Number);
    send({ type: 'place', x, y, h: rad(hDeg) });
    scene.clearTrail();
  });
  $('speedSelect').addEventListener('change', () => send({ type: 'speed', value: Number($('speedSelect').value) }));
  $('fullMatchBtn').addEventListener('click', () => {
    send({ type: 'match', action: 'full', auto: fmAuto.value, teleop: fmTeleop.value });
  });

  // ===== cameras =====
  const camButtons = [...document.querySelectorAll('#cams button')];
  for (const b of camButtons) b.addEventListener('click', () => scene.setCamera(b.dataset.cam));
  scene.onCameraMode((mode) => {
    for (const b of camButtons) b.classList.toggle('on', b.dataset.cam === mode);
    const hint = mode === 'top' ? 'drag: pan, wheel: zoom'
      : mode === 'follow' ? 'drag: look around the robot, wheel: zoom'
      : 'drag: orbit, right-drag or shift-drag: pan, wheel: zoom';
    setText($('hint'), `${hint}, keys 1 to 4: cameras`);
  });
  window.addEventListener('keydown', (e) => {
    if (isTyping(e) || e.metaKey || e.ctrlKey || e.altKey) return;
    const cams = { Digit1: 'orbit', Digit2: 'follow', Digit3: 'top', Digit4: 'driver' };
    if (cams[e.code]) scene.setCamera(cams[e.code]);
  });
  scene.setCamera(scene.cameraMode);

  // ===== state driven panels =====
  const scoreKeys = ['leave', 'park', 'tips', 'tipPts', 'cell', 'total'];
  function onState(s) {
    state = s;
    const op = s.op || { name: '', status: 'NONE' };
    if (op.status !== status) { status = op.status; refreshButtons(); }
    setText($('opStatus'), status);
    setClass($('opStatus'), 'badge ' + ({ RUNNING: 'ok', INIT: 'warn', ERROR: 'bad' }[status] || ''));
    setText($('opName'), op.name || '');
    if ((status === 'INIT' || status === 'RUNNING') && op.name && opSelect.value !== op.name) opSelect.value = op.name;

    const m = s.match || {};
    const ally = m.alliance || 'BLUE';
    $('allyBlue').classList.toggle('on', ally === 'BLUE');
    $('allyRed').classList.toggle('on', ally === 'RED');
    setText($('phase'), m.phase || 'IDLE');
    setClass($('phase'), 'badge ' + ({ AUTO: 'ok', TELEOP: 'ok', TRANSITION: 'warn' }[m.phase] || ''));
    setText($('clock'), fmtClock(m.clock));
    for (const k of scoreKeys) {
      setText($('sb-' + k), String((m.blue && m.blue[k]) ?? 0));
      setText($('sr-' + k), String((m.red && m.red[k]) ?? 0));
    }
    const table = document.querySelector('table.score');
    setClass(table, 'score ' + (ally === 'RED' ? 'redOurs' : 'blueOurs'));
    const mine = (ally === 'RED' ? m.red : m.blue) || {};
    const tips = mine.tips || 0;
    setText($('rp1'), `${Math.min(tips, 4)}/4`);
    setClass($('rp1'), 'badge ' + (tips >= 4 ? 'ok' : ''));
    setText($('rp2'), `${Math.min(tips, 7)}/7`);
    setClass($('rp2'), 'badge ' + (tips >= 7 ? 'ok' : ''));
    if (s.hives && s.hives.blue && s.hives.red) {
      const d = (h) => `${h.pollen || 0} pollen, ${h.nectar || 0} nectar, ${h.phi > 0 ? 'back' : 'audience'} end up`;
      setText($('hiveRow'), `Blue HIVE: ${d(s.hives.blue)}\nRed HIVE: ${d(s.hives.red)}`);
    }

    const r = s.robot || {};
    const target = r.flyTarget || 0, vel = r.flyVel || 0;
    const scale = Math.max(1800, target * 1.25, vel * 1.05);
    $('flyFill').style.width = `${clamp(vel / scale, 0, 1) * 100}%`;
    $('flyMark').style.left = `${clamp(target / scale, 0, 1) * 100}%`;
    $('flyMark').style.display = target > 0 ? '' : 'none';
    setText($('flyText'), `${vel.toFixed(0)} / ${target.toFixed(0)} ticks/s`);
    const dots = $('heldDots').children;
    for (let i = 0; i < dots.length; i++) dots[i].classList.toggle('on', i < (r.held || 0));

    const sp = $('speedSelect');
    if (document.activeElement !== sp && s.speed != null && Number(sp.value) !== s.speed) {
      const opt = [...sp.options].find((o) => Number(o.value) === s.speed);
      if (opt) sp.value = opt.value;
    }
  }

  function onTelemetry(m) {
    setText($('telemetry'), (m.lines || []).join('\n') || '(empty)');
  }

  // ===== events and report =====
  const evList = $('events');
  function onEvent(e) {
    const li = document.createElement('li');
    li.className = `k-${e.kind || 'log'}`;
    const t = document.createElement('span');
    t.className = 't';
    t.textContent = (e.t != null ? Number(e.t).toFixed(1) : '') + ' ' + (e.kind || '');
    li.appendChild(t);
    li.appendChild(document.createTextNode(e.msg || ''));
    if (e.stack) li.title = e.stack;
    evList.insertBefore(li, evList.firstChild);
    while (evList.children.length > 200) evList.removeChild(evList.lastChild);
    if (e.kind === 'tip' || e.kind === 'error') {
      li.classList.add('flash');
      const p = $('p-events');
      const cls = e.kind === 'tip' ? 'flash-tip' : 'flash-error';
      p.classList.remove('flash-tip', 'flash-error');
      void p.offsetWidth;   // restart the CSS animation
      p.classList.add(cls);
    }
  }
  function onReport(m) {
    if (m.clear) { $('report').classList.add('hidden'); return; }
    setText($('reportText'), m.text || '(empty report)');
    $('report').classList.remove('hidden');
  }
  $('reportClose').addEventListener('click', () => $('report').classList.add('hidden'));

  // ===== knobs drawer =====
  let pending = {};
  let knobTimer = null;
  function queueKnob(key, value) {
    pending[key] = value;
    scene.setKnobs({ [key]: value });
    clearTimeout(knobTimer);
    knobTimer = setTimeout(() => { send({ type: 'knobs', values: pending }); pending = {}; }, 150);
  }
  function buildKnobs(values, meta) {
    const body = $('knobsBody');
    body.textContent = '';
    const metas = meta.slice();
    for (const k of Object.keys(values)) {
      if (!metas.some((m) => m.key === k)) metas.push({ key: k, label: k, group: 'Other' });
    }
    if (!metas.length) { body.textContent = 'The engine sent no knobs.'; return; }
    const groups = [...new Set(metas.map((m) => m.group || 'Other'))];
    for (const gname of groups) {
      const head = document.createElement('div');
      head.className = 'kgroup';
      head.textContent = gname;
      body.appendChild(head);
      for (const m of metas.filter((x) => (x.group || 'Other') === gname)) {
        const row = document.createElement('div');
        row.className = 'knob';
        const label = document.createElement('span');
        label.textContent = m.label || m.key;
        label.title = m.key;
        const num = document.createElement('input');
        num.type = 'number';
        num.step = m.step != null ? m.step : 'any';
        if (m.min != null) num.min = m.min;
        if (m.max != null) num.max = m.max;
        num.value = values[m.key] != null ? values[m.key] : '';
        let range = document.createElement('span');
        if (m.min != null && m.max != null) {
          range = document.createElement('input');
          range.type = 'range';
          range.min = m.min; range.max = m.max;
          range.step = m.step != null ? m.step : (m.max - m.min) / 200;
          range.value = num.value;
          range.addEventListener('input', () => { num.value = range.value; queueKnob(m.key, Number(range.value)); });
        }
        num.addEventListener('input', () => {
          const v = Number(num.value);
          if (num.value === '' || !Number.isFinite(v)) return;
          if (range.type === 'range') range.value = v;
          queueKnob(m.key, v);
        });
        row.append(label, range, num);
        body.appendChild(row);
      }
    }
  }
  $('knobsBtn').addEventListener('click', () => $('knobs').classList.toggle('hidden'));
  $('knobsClose').addEventListener('click', () => $('knobs').classList.add('hidden'));

  refreshButtons();
  return { setConn, onHello, onState, onTelemetry, onEvent, onReport };
}
