// Input per docs/PROTOCOL.md "Input mapping".
//   gamepad 1: first connected controller (standard mapping, or a stored calibration),
//              keyboard fallback T/G/F/H/R/Y while no controller is connected.
//   gamepad 2: keyboard (Q, B, ArrowUp, ArrowDown, W/S, I/K).
// Values go out RAW: no deadzone here, the engine applies the SDK's.
import { $ } from './util.js';
import { isTyping } from './ui.js';

const STD_BUTTONS = ['a', 'b', 'x', 'y', 'left_bumper', 'right_bumper', null, null, 'back', 'start',
  'left_stick_button', 'right_stick_button', 'dpad_up', 'dpad_down', 'dpad_left', 'dpad_right', 'guide'];
const KEYS = new Set(['KeyQ', 'KeyB', 'ArrowUp', 'ArrowDown', 'KeyW', 'KeyS', 'KeyI', 'KeyK',
  'KeyT', 'KeyG', 'KeyF', 'KeyH', 'KeyR', 'KeyY']);
const LS_PREFIX = 'biobuzz.padmap.';

const CAL_STEPS = [
  { key: 'ly', type: 'axis', sign: -1, text: 'Push the LEFT stick UP (away from you)' },
  { key: 'lx', type: 'axis', sign: 1, text: 'Push the LEFT stick RIGHT' },
  { key: 'ry', type: 'axis', sign: -1, text: 'Push the RIGHT stick UP (away from you)' },
  { key: 'rx', type: 'axis', sign: 1, text: 'Push the RIGHT stick RIGHT' },
  { key: 'lt', type: 'trig', text: 'Squeeze the LEFT trigger all the way' },
  { key: 'rt', type: 'trig', text: 'Squeeze the RIGHT trigger all the way' },
  ...['a', 'b', 'x', 'y', 'dpad_up', 'dpad_down', 'dpad_left', 'dpad_right', 'left_bumper', 'right_bumper',
    'left_stick_button', 'right_stick_button', 'start', 'back', 'guide']
    .map((n) => ({ key: n, type: 'btn', text: `Press ${n.replace(/_/g, ' ').toUpperCase()}` })),
];

const zero = () => ({ lx: 0, ly: 0, rx: 0, ry: 0, lt: 0, rt: 0, b: [] });
const r3 = (v) => Math.round((Number(v) || 0) * 1000) / 1000;
const isZero = (s) => !s.lx && !s.ly && !s.rx && !s.ry && !s.lt && !s.rt && s.b.length === 0;

function loadCal(id) {
  try { return JSON.parse(localStorage.getItem(LS_PREFIX + id) || 'null'); } catch (e) { return null; }
}

function firstPad() {
  const pads = navigator.getGamepads ? navigator.getGamepads() : [];
  for (const p of pads) if (p && p.connected) return p;
  return null;
}

function readStandard(gp) {
  const s = zero();
  const ax = gp.axes, bt = gp.buttons;
  s.lx = r3(ax[0]); s.ly = r3(ax[1]); s.rx = r3(ax[2]); s.ry = r3(ax[3]);
  s.lt = r3(bt[6] ? bt[6].value : 0);
  s.rt = r3(bt[7] ? bt[7].value : 0);
  for (let i = 0; i < STD_BUTTONS.length; i++) {
    if (STD_BUTTONS[i] && bt[i] && bt[i].pressed) s.b.push(STD_BUTTONS[i]);
  }
  return s;
}

function readCalibrated(gp, cal) {
  const s = zero();
  const ax = gp.axes, bt = gp.buttons;
  for (const k of ['lx', 'ly', 'rx', 'ry']) {
    const m = cal[k];
    if (m) s[k] = r3((ax[m.i] || 0) * m.sign);
  }
  for (const k of ['lt', 'rt']) {
    const m = cal[k];
    if (!m) continue;
    if (m.kind === 'b') s[k] = r3(bt[m.i] ? bt[m.i].value : 0);
    else s[k] = r3(Math.min(1, Math.max(0, ((ax[m.i] || 0) - m.rest) / ((m.full - m.rest) || 1))));
  }
  for (const name of STD_BUTTONS) {
    const m = name && cal[name];
    if (!m) continue;
    const down = m.kind === 'b' ? !!(bt[m.i] && bt[m.i].pressed) : Math.abs((ax[m.i] || 0) - m.v) < 0.12;
    if (down) s.b.push(name);
  }
  return s;
}

export function createInput({ send }) {
  const keys = new Set();
  const sentKey = { 1: JSON.stringify(zero()), 2: JSON.stringify(zero()) };
  const sentAt = { 1: 0, 2: 0 };
  let hadPad = false;
  let cal = null;       // running calibration, or null

  function push(id, s, now, force) {
    const key = JSON.stringify(s);
    if (force || key !== sentKey[id] || (!isZero(s) && now - sentAt[id] >= 90)) {
      send({ type: 'gamepad', id, lx: s.lx, ly: s.ly, rx: s.rx, ry: s.ry, lt: s.lt, rt: s.rt, b: s.b });
      sentKey[id] = key;
      sentAt[id] = now;
    }
  }
  function releaseAll() {
    keys.clear();
    const now = performance.now();
    push(1, zero(), now, true);
    push(2, zero(), now, true);
    show(1, zero()); show(2, zero());
  }

  window.addEventListener('keydown', (e) => {
    if (isTyping(e) || e.metaKey || e.ctrlKey || e.altKey) return;
    if (!KEYS.has(e.code)) return;
    if (!keys.has(e.code)) downAt.set(e.code, performance.now());
    keys.add(e.code);
    e.preventDefault();
  });
  // A quick tap must still reach the OpMode, so a key stays down for at least 90 ms.
  const downAt = new Map();
  window.addEventListener('keyup', (e) => {
    const held = performance.now() - (downAt.get(e.code) || 0);
    if (held >= 90) keys.delete(e.code);
    else setTimeout(() => keys.delete(e.code), 90 - held);
  });
  window.addEventListener('blur', releaseAll);
  document.addEventListener('visibilitychange', () => { if (document.hidden) releaseAll(); });
  window.addEventListener('gamepaddisconnected', () => { push(1, zero(), performance.now(), true); });
  // a field that takes focus must not leave keys stuck down
  document.addEventListener('focusin', (e) => { if (isTyping(e)) keys.clear(); });

  const k = (code) => (keys.has(code) ? 1 : 0);
  function padText(text) {
    const el = $('padId');
    if (el._t !== text) { el._t = text; el.textContent = text; el.title = text; }
  }
  // after a (re)connect the engine knows nothing: make the next poll send both pads
  function resync() { sentKey[1] = ''; sentKey[2] = ''; }

  function show(id, s) {
    const l = $(`g${id}l`).firstElementChild, r = $(`g${id}r`).firstElementChild;
    l.style.transform = `translate(${s.lx * 14}px, ${s.ly * 14}px)`;
    r.style.transform = `translate(${s.rx * 14}px, ${s.ry * 14}px)`;
    const parts = s.b.slice();
    if (s.lt > 0.02) parts.push(`lt ${s.lt.toFixed(2)}`);
    if (s.rt > 0.02) parts.push(`rt ${s.rt.toFixed(2)}`);
    const text = parts.join(' ');
    const el = $(`g${id}b`);
    if (el._t !== text) { el._t = text; el.textContent = text; }
  }

  // ===== calibration dialog (only offered when the pad's mapping is not "standard") =====
  function snapshot(gp) {
    return { axes: [...gp.axes], down: gp.buttons.map((b) => b.pressed || b.value > 0.5) };
  }
  function calStart() {
    const gp = firstPad();
    if (!gp) return;
    cal = { id: gp.id, i: 0, base: snapshot(gp), wait: true, out: {} };
    $('calib').classList.remove('hidden');
    calShow();
  }
  function calShow() {
    const step = CAL_STEPS[cal.i];
    $('calibStep').textContent = cal.wait ? 'Let go of everything...' : step.text;
    $('calibProg').textContent = `step ${cal.i + 1} of ${CAL_STEPS.length}`;
  }
  function calEnd(save) {
    if (cal && save) {
      try { localStorage.setItem(LS_PREFIX + cal.id, JSON.stringify(cal.out)); } catch (e) { /* private mode */ }
    }
    cal = null;
    $('calib').classList.add('hidden');
  }
  function calNext() {
    cal.i++;
    cal.wait = true;
    if (cal.i >= CAL_STEPS.length) calEnd(true); else calShow();
  }
  function calPoll(gp) {
    if (!gp || gp.id !== cal.id) { $('calibStep').textContent = 'Controller not found. Reconnect it or cancel.'; return; }
    if (cal.wait) {
      // rest = no button down and no axis moving; take a fresh baseline then
      const now = snapshot(gp);
      const still = now.axes.every((v, i) => Math.abs(v - (cal.prev ? cal.prev.axes[i] : v)) < 0.02);
      cal.prev = now;
      if (still && !now.down.some(Boolean)) {
        cal.restFrames = (cal.restFrames || 0) + 1;
        if (cal.restFrames > 12) { cal.base = now; cal.wait = false; cal.restFrames = 0; calShow(); }
      } else {
        cal.restFrames = 0;
      }
      return;
    }
    const step = CAL_STEPS[cal.i];
    let btn = -1;
    gp.buttons.forEach((b, i) => { if (btn < 0 && (b.pressed || b.value > 0.6) && !cal.base.down[i]) btn = i; });
    // a hat switch (dpad on one axis) moves less than a stick, so buttons use a lower bar
    let axis = -1, best = step.type === 'btn' ? 0.4 : 0.6;
    gp.axes.forEach((v, i) => { const d = Math.abs(v - cal.base.axes[i]); if (d > best) { best = d; axis = i; } });
    if (step.type === 'axis') {
      if (axis < 0) return;
      const delta = gp.axes[axis] - cal.base.axes[axis];
      cal.out[step.key] = { i: axis, sign: step.sign * Math.sign(delta) };
    } else if (step.type === 'trig') {
      if (btn >= 0) cal.out[step.key] = { kind: 'b', i: btn };
      else if (axis >= 0) cal.out[step.key] = { kind: 'a', i: axis, rest: cal.base.axes[axis], full: gp.axes[axis] };
      else return;
    } else {
      if (btn >= 0) cal.out[step.key] = { kind: 'b', i: btn };
      else if (axis >= 0) cal.out[step.key] = { kind: 'a', i: axis, v: gp.axes[axis] };
      else return;
    }
    calNext();
  }
  $('calibBtn').addEventListener('click', calStart);
  $('calibCancel').addEventListener('click', () => calEnd(false));
  $('calibSkip').addEventListener('click', () => { if (cal) calNext(); });

  // ===== per-frame poll =====
  function poll(now) {
    const focused = document.hasFocus();
    const gp = firstPad();
    let g1 = zero();
    let mapText = 'keyboard', mapCls = 'badge', needCal = false;

    if (gp) {
      hadPad = true;
      const stored = gp.mapping === 'standard' ? null : loadCal(gp.id);
      if (gp.mapping === 'standard') { g1 = readStandard(gp); mapText = 'standard mapping'; mapCls = 'badge ok'; }
      else if (stored) { g1 = readCalibrated(gp, stored); mapText = 'calibrated'; mapCls = 'badge ok'; needCal = true; }
      else { g1 = readStandard(gp); mapText = 'not standard: calibrate'; mapCls = 'badge warn'; needCal = true; }
      padText(gp.id);
    } else {
      if (hadPad) { hadPad = false; push(1, zero(), now, true); }
      padText('no controller (keys T G F H R Y)');
      g1.ly = k('KeyG') - k('KeyT');
      g1.lx = k('KeyH') - k('KeyF');
      g1.rx = k('KeyY') - k('KeyR');
    }
    const badge = $('padMap');
    if (badge._t !== mapText) { badge._t = mapText; badge.textContent = mapText; badge.className = mapCls; }
    $('calibBtn').classList.toggle('hidden', !needCal);

    const g2 = zero();
    g2.ly = k('KeyS') - k('KeyW');
    g2.ry = k('KeyK') - k('KeyI');
    if (k('KeyQ')) g2.b.push('left_bumper');
    if (k('KeyB')) g2.b.push('b');
    if (k('ArrowUp')) g2.b.push('dpad_up');
    if (k('ArrowDown')) g2.b.push('dpad_down');

    if (cal) { calPoll(gp); g1 = zero(); }
    if (!focused) { g1 = zero(); }

    push(1, g1, now, false);
    push(2, g2, now, false);
    show(1, g1);
    show(2, g2);
  }

  return { poll, releaseAll, resync };
}
