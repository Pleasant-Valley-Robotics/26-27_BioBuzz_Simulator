// Mock engine for ?mock=1: speaks docs/PROTOCOL.md with fabricated data so the page can
// be built and checked with no Java engine. Nothing here is real physics or real team code.
// AUTO flavors drive a demo loop and shoot on their own. TELEOP does the same until the
// first gamepad input arrives, then gamepad 1 drives and gamepad 2 runs the launcher.
import { DEFAULT_FIELD as F, DEFAULT_ROBOT as R, rad, deg, clamp } from './util.js';
import { cellPoint } from './hive.js';

const G = 386.09;                      // in/s^2
const HZ = 30;
const LOOP_A = 52, LOOP_B = 50;        // demo loop half axes (field x, y)
const START = { BLUE: [-63.02, 13.65, Math.PI], RED: [63.02, -13.65, 0] };
const OPMODES = [
  { name: 'BlueHiveFacingAUTO', group: 'Autonomous', flavor: 'AUTO' },
  { name: 'RedHiveFacingAUTO', group: 'Autonomous', flavor: 'AUTO' },
  { name: 'BlueParkAUTO', group: 'Autonomous', flavor: 'AUTO' },
  { name: 'RedParkAUTO', group: 'Autonomous', flavor: 'AUTO' },
  { name: 'compTeleOp', group: 'Robot', flavor: 'TELEOP' },
];
const KNOB_META = [
  { key: 'launchAngleDeg', label: 'Launch angle (deg)', group: 'Launcher', min: 30, max: 89, step: 0.5 },
  { key: 'exitSpeedPerTick', label: 'Exit speed per tick/s (in/s)', group: 'Launcher', min: 0.05, max: 0.4, step: 0.005 },
  { key: 'flywheelSpinUp', label: 'Flywheel spin-up time (s)', group: 'Launcher', min: 0.05, max: 2, step: 0.05 },
  { key: 'demoSpeed', label: 'Demo loop speed (in/s)', group: 'Mock', min: 5, max: 70, step: 1 },
  { key: 'odoDrift', label: 'Localizer drift (in)', group: 'Mock', min: 0, max: 6, step: 0.1 },
];

export function createMock({ broken = false } = {}) {
  const handlers = [];
  const emit = (m) => {
    const copy = JSON.parse(JSON.stringify(m));   // only JSON-able data reaches the page
    for (const h of handlers) {
      try { h(copy); } catch (e) { console.error('handler failed for', m.type, e); }
    }
  };
  const event = (kind, msg, extra = {}) => emit({ type: 'event', t: Number(t.toFixed(2)), kind, msg, ...extra });

  let t = 0, speed = 1, alliance = 'BLUE', nextId = 1, tick = 0;
  let op = { name: 'BlueHiveFacingAUTO', status: 'NONE' };
  let phase = 'IDLE', clock = 0, full = null, manual = false;
  const knobs = { launchAngleDeg: 62, exitSpeedPerTick: 0.165, flywheelSpinUp: 0.35, demoSpeed: 34, odoDrift: 1.2 };
  const pads = { 1: null, 2: null };
  const robot = { x: 0, y: 0, h: 0, wheels: [0, 0, 0, 0], intake: 0, feedL: 0, feedR: 0, feedLaunch: 0,
                  flywheel: 0, flyVel: 0, flyTarget: 0, held: 4 };
  let loopS = 0, balls = [], hives = {}, score = {}, stats = {};
  let shotTimer = 0, emptyTimer = 0, tipTimer = 0, tipNext = 'blue', left = false, startPos = [0, 0];
  let history = [], prevB2 = [];

  const flavorOf = (name) => (OPMODES.find((o) => o.name === name) || {}).flavor || 'TELEOP';
  const mine = () => (alliance === 'RED' ? 'red' : 'blue');
  const hiveY = (name) => (name === 'blue' ? F.hive.blueY : F.hive.redY);

  function addBall(kind, x, y, z, mode = 'rest') {
    const b = { id: nextId++, kind, x, y, z, vx: 0, vy: 0, vz: 0, mode };
    balls.push(b);
    return b;
  }

  // Resting place number k for a ball of this kind inside a CELL, in seesaw-local (u, v, w).
  function cellSlot(kind, k) {
    const h = F.hive;
    if (kind !== 0) return [h.cellInner + 2.0, (k - 1) * 3.9, h.cellFloorW + 1.8];
    const row = Math.floor(k / 6) % 2, col = k % 6, layer = Math.floor(k / 12);
    return [h.cellInner + 5.6 + row * 2.9, (col - 2.5) * 3.0, h.cellFloorW + 1.4 + layer * 2.5];
  }
  function upEnd(name) { return hives[name].target > 0 ? -1 : 1; }   // -1 = back cell is the raised one

  function resetField() {
    balls = [];
    nextId = 1;
    hives = { blue: { phi: 30, target: 30, pollen: 0, nectar: 3, tips: 0 }, red: { phi: -30, target: -30, pollen: 0, nectar: 3, tips: 0 } };
    const blank = () => ({ total: 0, leave: 0, park: 0, tips: 0, tipPts: 0, cell: 0 });
    score = { blue: blank(), red: blank() };
    stats = { shots: 0, scored: 0, missed: 0, tips: 0 };
    for (const f of F.flowers) for (let i = 0; i < 6; i++) addBall(0, f.x, f.y, 1.4 + i * 2.8, 'stack');
    for (let i = 0; i < 5; i++) {
      addBall(0, -70.6, 52 + i * 4.4, 1.4);
      addBall(0, 70.6, -52 - i * 4.4, 1.4);
    }
    for (const [x, y] of [[-30, 40], [-12, 52], [18, 44], [36, -38], [-20, -50], [8, -46]]) addBall(0, x, y, 1.4);
    for (const name of ['blue', 'red']) {
      for (let k = 0; k < 3; k++) {
        const b = addBall(name === 'blue' ? 2 : 1, 0, 0, 0, 'cell');
        b.cell = { hive: name, end: upEnd(name), slot: cellSlot(1, k) };
      }
    }
    const [x, y, h] = START[alliance];
    Object.assign(robot, { x, y, h, wheels: [0, 0, 0, 0], intake: 0, feedL: 0, feedR: 0, feedLaunch: 0, flywheel: 0, flyVel: 0, flyTarget: 0, held: 4 });
    history = [];
    placeCellBalls();
  }

  function placeCellBalls() {
    for (const b of balls) {
      if (b.mode !== 'cell') continue;
      const [u, v, w] = b.cell.slot;
      const p = cellPoint(F.hive, hiveY(b.cell.hive), hives[b.cell.hive].phi, b.cell.end * u, v, w);
      b.x = p[0]; b.y = p[1]; b.z = p[2];
    }
  }

  // ===== demo loop =====
  function loopPoint(s) {
    return [LOOP_A * Math.cos(s), LOOP_B * Math.sin(s), Math.atan2(LOOP_B * Math.cos(s), -LOOP_A * Math.sin(s))];
  }
  function nearestLoopS(x, y) {
    let best = 0, bd = 1e9;
    for (let i = 0; i < 360; i++) {
      const s = (i / 360) * Math.PI * 2;
      const d = Math.hypot(LOOP_A * Math.cos(s) - x, LOOP_B * Math.sin(s) - y);
      if (d < bd) { bd = d; best = s; }
    }
    return best;
  }
  function sendPreview() {
    const xs = [], ys = [];
    for (let i = 0; i <= 120; i++) {
      const p = loopPoint(loopS + (i / 120) * Math.PI * 2);
      xs.push(p[0]); ys.push(p[1]);
    }
    emit({ type: 'overlay', kind: 'preview', ops: [
      { op: 'stroke', color: '#4CAF507A' }, { op: 'width', w: 1 },
      { op: 'polyline', x: [robot.x, xs[0]], y: [robot.y, ys[0]] },
      { op: 'polyline', x: xs, y: ys },
    ] });
  }

  // Robot-frame velocities (forward, left, ccw) to wheel angle steps [FL, FR, BL, BR].
  function spinWheels(vx, vy, w, dt) {
    const k = (R.length + R.width) / 2 * 0.8;
    const v = [vx - vy - k * w, vx + vy + k * w, vx + vy - k * w, vx - vy + k * w];
    for (let i = 0; i < 4; i++) robot.wheels[i] += (v[i] / R.wheelRadius) * dt;
  }

  function driveDemo(dt) {
    const [tx, ty] = loopPoint(loopS);
    const v = knobs.demoSpeed;
    const turnTo = (want, rate) => {
      const dh = Math.atan2(Math.sin(want - robot.h), Math.cos(want - robot.h));
      const w = clamp(dh / dt, -rate, rate);
      robot.h += w * dt;
      return [w, dh];
    };
    if (Math.hypot(tx - robot.x, ty - robot.y) > 3) {
      // not on the loop yet: turn toward it, then drive straight there
      const [w, dh] = turnTo(Math.atan2(ty - robot.y, tx - robot.x), 3);
      const vx = Math.abs(dh) < 0.4 ? v : 0;
      robot.x += Math.cos(robot.h) * vx * dt;
      robot.y += Math.sin(robot.h) * vx * dt;
      spinWheels(vx, 0, w, dt);
    } else {
      loopS += (v * dt) / Math.hypot(LOOP_A * Math.sin(loopS), LOOP_B * Math.cos(loopS));
      const p = loopPoint(loopS);
      const [w] = turnTo(p[2], 4);
      robot.x = p[0]; robot.y = p[1];
      spinWheels(v, 0, w, dt);
    }
  }

  function driveManual(dt) {
    const p = pads[1] || {};
    const dz = (v) => (Math.abs(v || 0) < 0.05 ? 0 : v);
    const vx = -dz(p.ly) * 60, vy = -dz(p.lx) * 50, w = -dz(p.rx) * 3.2;
    robot.h += w * dt;
    const c = Math.cos(robot.h), s = Math.sin(robot.h);
    robot.x = clamp(robot.x + (vx * c - vy * s) * dt, -62.5, 62.5);
    robot.y = clamp(robot.y + (vx * s + vy * c) * dt, -62.5, 62.5);
    spinWheels(vx, vy, w, dt);
  }

  // ===== launcher =====
  function exitPoint() {
    const c = Math.cos(robot.h), s = Math.sin(robot.h);
    return [robot.x + R.launchExitX * c, robot.y + R.launchExitX * s, R.launchExitZ];
  }
  function openingCenter(name) {
    const h = F.hive;
    return cellPoint(h, hiveY(name), hives[name].phi, upEnd(name) * (h.cellOuter + 0.5), 0, h.cellFloorW + 5.5);
  }

  function shootAt(name) {
    const from = exitPoint(), to = openingCenter(name);
    const dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
    const d = Math.hypot(dx, dy);
    let a = rad(knobs.launchAngleDeg);
    while (d * Math.tan(a) - dz < 0.15 * d && a < rad(86)) a += rad(3);
    let v = Math.sqrt((G * d * d) / (2 * Math.cos(a) ** 2 * Math.max(1, d * Math.tan(a) - dz)));
    // the raised CELL only opens toward its own end of the field
    const side = upEnd(name);
    const rightSide = Math.sign(from[0]) === side && Math.abs(from[0]) > 26;
    let why = null;
    const roll = Math.random();
    let yaw = Math.atan2(dy, dx);
    if (!rightSide) why = 'wrongside';
    else if (roll < 0.12) { why = 'short'; v *= 0.86; }
    else if (roll < 0.22) { why = 'long'; v *= 1.13; }
    else if (roll < 0.3) { why = 'wide'; yaw += (Math.random() < 0.5 ? -1 : 1) * rad(9); }
    const b = addBall(0, from[0], from[1], from[2], 'free');
    b.vx = Math.cos(yaw) * Math.cos(a) * v; b.vy = Math.sin(yaw) * Math.cos(a) * v; b.vz = Math.sin(a) * v;
    b.aim = { hive: name, end: side, at: t + d / (v * Math.cos(a)), why };
    fired(v);
  }

  function shootManual() {
    const a = rad(knobs.launchAngleDeg), v = robot.flyVel * knobs.exitSpeedPerTick;
    const from = exitPoint();
    const b = addBall(0, from[0], from[1], from[2], 'free');
    b.vx = -Math.cos(robot.h) * Math.cos(a) * v; b.vy = -Math.sin(robot.h) * Math.cos(a) * v; b.vz = Math.sin(a) * v;
    b.live = true;   // scores if it drops into a raised opening
    fired(v);
  }

  function fired(v) {
    robot.held--;
    robot.flyVel *= 0.93;
    stats.shots++;
    event('shot', `shot at ${robot.flyVel.toFixed(0)} ticks/s, exit ${v.toFixed(0)} in/s`, { flyVel: Number(robot.flyVel.toFixed(1)), exitSpeed: Number(v.toFixed(1)) });
  }

  function intoCell(b, name, end) {
    const hv = hives[name];
    b.mode = 'cell';
    b.cell = { hive: name, end, slot: cellSlot(0, hv.pollen) };
    b.aim = null; b.live = false;
    hv.pollen++;
    stats.scored++;
    score[name].cell += 1;
    event('score', `POLLEN in the ${name} HIVE (${hv.pollen} in the CELL)`, { hive: name });
  }

  function startTip(name) {
    const hv = hives[name];
    if (hv.phi !== hv.target) return;
    hv.target = -hv.target;
    const period = phase === 'AUTO' ? 'AUTO' : 'TELEOP';
    const points = period === 'AUTO' ? 20 : 10;
    hv.tips++;
    stats.tips++;
    score[name].tips++;
    score[name].tipPts += points;
    for (const b of balls) if (b.mode === 'cell' && b.cell.hive === name) b.sliding = true;
    event('tip', `${name.toUpperCase()} HIVE TIP, +${points} (${period})`, { hive: name, points, period });
  }

  function stepHives(dt) {
    for (const name of ['blue', 'red']) {
      const hv = hives[name];
      if (hv.phi === hv.target) continue;
      const before = hv.phi;
      const step = 75 * dt * Math.sign(hv.target - hv.phi);
      hv.phi = Math.abs(hv.target - hv.phi) <= Math.abs(step) ? hv.target : hv.phi + step;
      // once the old raised end passes level, its balls slide out of the opening
      if (Math.sign(before) !== Math.sign(hv.phi) || hv.phi === hv.target) {
        const p = rad(hv.phi), out = 34;
        for (const b of balls) {
          if (b.mode !== 'cell' || b.cell.hive !== name || !b.sliding) continue;
          b.mode = 'free'; b.sliding = false;
          const e = b.cell.end;
          b.vx = e * Math.cos(p) * out + (Math.random() - 0.5) * 14;
          b.vy = (Math.random() - 0.5) * 26;
          b.vz = -e * Math.sin(p) * out;
          b.cell = null;
        }
        hv.pollen = 0; hv.nectar = 0;
      }
    }
    placeCellBalls();
  }

  function stepBalls(dt) {
    for (const b of balls) {
      if (b.mode !== 'free') continue;
      const r = b.kind === 0 ? 1.4 : 1.8;
      b.vz -= G * dt;
      b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt;
      if (b.aim && t >= b.aim.at) {
        const a = b.aim;
        b.aim = null;
        if (!a.why && upEnd(a.hive) === a.end && hives[a.hive].phi === hives[a.hive].target) { intoCell(b, a.hive, a.end); continue; }
        stats.missed++;
        event('miss', `missed the ${a.hive} HIVE (${a.why || 'tipped'})`, { why: a.why || 'short' });
      }
      if (b.live && b.vz < 0) {
        for (const name of ['blue', 'red']) {
          const o = openingCenter(name);
          if (Math.hypot(b.x - o[0], b.y - o[1], b.z - o[2]) < 5.5 && hives[name].phi === hives[name].target) { intoCell(b, name, upEnd(name)); break; }
        }
        if (b.mode !== 'free') continue;
      }
      if (b.z < r) {
        b.z = r;
        if (b.live) { b.live = false; stats.missed++; event('miss', 'shot landed on the floor', { why: 'short' }); }
        b.vz = Math.abs(b.vz) > 25 ? -b.vz * 0.42 : 0;
        const f = Math.exp(-2.2 * dt);
        b.vx *= f; b.vy *= f;
        if (b.vz === 0 && Math.hypot(b.vx, b.vy) < 2) { b.vx = b.vy = 0; b.mode = 'rest'; }
      }
      const lim = F.half - r;
      if (Math.abs(b.x) > lim) { b.x = Math.sign(b.x) * lim; b.vx *= -0.5; }
      if (Math.abs(b.y) > lim) { b.y = Math.sign(b.y) * lim; b.vy *= -0.5; }
    }
  }

  function grabLoose(max, reach) {
    const c = Math.cos(robot.h), s = Math.sin(robot.h);
    let got = 0;
    const loose = balls.filter((b) => b.kind === 0 && b.mode === 'rest')
      .map((b) => ({ b, fx: (b.x - robot.x) * c + (b.y - robot.y) * s, fy: -(b.x - robot.x) * s + (b.y - robot.y) * c }))
      .filter((o) => (reach ? Math.hypot(o.fx, o.fy) < reach : (o.fx > 5 && o.fx < 13 && Math.abs(o.fy) < 7)))
      .sort((p, q) => Math.hypot(p.fx, p.fy) - Math.hypot(q.fx, q.fy));
    for (const o of loose) {
      if (robot.held >= 4 || got >= max) break;
      balls.splice(balls.indexOf(o.b), 1);
      robot.held++; got++;
    }
    return got;
  }

  function stepLauncher(dt) {
    const p2 = pads[2] || { b: [] };
    const b2 = p2.b || [];
    if (manual) {
      const hit = (n) => b2.includes(n) && !prevB2.includes(n);
      if (hit('left_bumper')) robot.flyTarget = 1325;
      if (hit('b')) robot.flyTarget = 0;
      if (hit('dpad_up')) robot.flyTarget += 25;
      if (hit('dpad_down')) robot.flyTarget = Math.max(0, robot.flyTarget - 25);
      const intaking = (p2.ry || 0) < -0.3, feeding = (p2.ly || 0) > 0.3;
      if (intaking) {
        robot.intake += 14 * dt; robot.feedL += 9 * dt; robot.feedR -= 9 * dt;
        const got = grabLoose(1, 0);
        if (got) event('intake', `intake: ${robot.held} held`);
      } else if ((p2.ry || 0) > 0.3) {
        robot.intake -= 14 * dt;
      }
      shotTimer -= dt;
      if (feeding) {
        robot.feedLaunch += 12 * dt;
        if (robot.held > 0 && shotTimer <= 0 && robot.flyTarget > 0 && robot.flyVel > robot.flyTarget * 0.9) { shootManual(); shotTimer = 0.45; }
      }
    } else {
      robot.flyTarget = 1325;
      robot.intake += 14 * dt; robot.feedL += 9 * dt; robot.feedR -= 9 * dt;
      shotTimer -= dt;
      const ex = exitPoint()[0];
      const goodSide = Math.sign(ex) === upEnd(mine()) && Math.abs(ex) > 26;
      if (robot.held > 0 && shotTimer <= 0 && robot.flyVel > 1290 && (goodSide || shotTimer < -4)) {
        robot.feedLaunch += 2.5;
        shootAt(mine());
        shotTimer = 1.4;
      }
      if (robot.held === 0) {
        emptyTimer += dt;
        if (emptyTimer > 1.6) {
          emptyTimer = 0;
          const got = grabLoose(4, 60);
          robot.held = Math.max(robot.held, 3);
          event('intake', `intake: picked up ${Math.max(got, 3)} POLLEN`);
        }
      }
    }
    prevB2 = b2.slice();
    const k = 1 - Math.exp(-dt / Math.max(0.02, knobs.flywheelSpinUp));
    robot.flyVel += (robot.flyTarget - robot.flyVel) * k;
    if (robot.flyTarget > 0) robot.flyVel += (Math.random() - 0.5) * 6;
    robot.flyVel = Math.max(0, robot.flyVel);
    robot.flywheel += (robot.flyVel / 28) * 2 * Math.PI * dt;
  }

  // ===== OpMode and match flow =====
  function setStatus(s) {
    if (op.status === s) return;
    op.status = s;
    event('opmode', `${op.name}: ${s}`);
  }
  function runOp(name, seconds) {
    op.name = name;
    phase = flavorOf(name) === 'AUTO' ? 'AUTO' : 'TELEOP';
    clock = seconds;
    manual = false;
    shotTimer = 1.0; tipTimer = 0;
    left = false; startPos = [robot.x, robot.y];
    loopS = nearestLoopS(robot.x, robot.y);
    setStatus('RUNNING');
    if (phase === 'AUTO') sendPreview(); else emit({ type: 'overlay', kind: 'clear' });
  }
  function stopOp(reason) {
    if (op.status !== 'RUNNING' && op.status !== 'INIT') return;
    const was = phase;
    setStatus('STOPPED');
    robot.flyTarget = 0;
    if (was === 'AUTO' || was === 'TELEOP') {
      emit({ type: 'report', text: reportText(reason), data: { ...stats, pose: [robot.x, robot.y, robot.h] } });
    }
  }
  function reportText(reason) {
    const s = score[mine()];
    return [
      `${op.name} (${reason})   MOCK DATA`,
      `sim time ${t.toFixed(1)} s, alliance ${alliance}`,
      `shots ${stats.shots}, scored ${stats.scored}, missed ${stats.missed}`,
      `HIVE tips ${stats.tips}`,
      `score: leave ${s.leave}, park ${s.park}, tips ${s.tips} (${s.tipPts} pts), cell ${s.cell}, total ${s.total}`,
      `final TRUE pose: x ${robot.x.toFixed(1)}, y ${robot.y.toFixed(1)}, heading ${deg(robot.h).toFixed(0)} deg`,
    ].join('\n');
  }

  function stepMatch(dt) {
    if (phase === 'IDLE' || phase === 'DONE') return;
    clock -= dt;
    if (clock > 0) return;
    if (phase === 'AUTO') {
      stopOp('30 s AUTO ended');
      if (full) { phase = 'TRANSITION'; clock = 8; } else { phase = 'IDLE'; clock = 0; }
    } else if (phase === 'TRANSITION') {
      runOp(full.teleop, 120);
    } else if (phase === 'TELEOP') {
      const lz = F.lz[mine()];
      if (robot.x > lz.x0 && robot.x < lz.x1 && robot.y > lz.y0 && robot.y < lz.y1) { score[mine()].park = 5; event('park', 'parked in the LOADING ZONE'); }
      stopOp('TELEOP ended');
      phase = full ? 'DONE' : 'IDLE'; clock = 0; full = null;
    }
  }

  function step(dt) {
    t += dt;
    if (op.status === 'RUNNING') {
      if (manual) driveManual(dt); else driveDemo(dt);
      stepLauncher(dt);
      if (!left && Math.hypot(robot.x - startPos[0], robot.y - startPos[1]) > 14 && phase === 'AUTO') {
        left = true; score[mine()].leave = 3; event('leave', 'LEAVE: robot left its start spot');
      }
      tipTimer += dt;
      if (tipTimer >= 10) { tipTimer = 0; startTip(tipNext); tipNext = tipNext === 'blue' ? 'red' : 'blue'; }
    } else {
      const k = 1 - Math.exp(-dt / 0.5);
      robot.flyVel += (0 - robot.flyVel) * k;
      robot.flywheel += (robot.flyVel / 28) * 2 * Math.PI * dt;
    }
    robot.h = Math.atan2(Math.sin(robot.h), Math.cos(robot.h));
    stepHives(dt);
    stepBalls(dt);
    stepMatch(dt);
    for (const a of ['blue', 'red']) { const s = score[a]; s.total = s.leave + s.park + s.tipPts + s.cell; }
  }

  function estimate() {
    if (flavorOf(op.name) !== 'AUTO' || op.status === 'NONE') return null;
    const d = knobs.odoDrift;
    return [robot.x + d * Math.sin(t * 0.31), robot.y + d * 0.7 * Math.cos(t * 0.23), robot.h + 0.012 * d * Math.sin(t * 0.2)];
  }

  function publish() {
    const est = estimate();
    const n = (v, p = 3) => Number(v.toFixed(p));
    emit({
      type: 'state', t: n(t), speed,
      robot: { x: n(robot.x), y: n(robot.y), h: n(robot.h, 4), est: est ? est.map((v) => n(v, 4)) : null,
               wheels: robot.wheels.map((v) => n(v)), intake: n(robot.intake), feedL: n(robot.feedL), feedR: n(robot.feedR),
               feedLaunch: n(robot.feedLaunch), flywheel: n(robot.flywheel), flyVel: n(robot.flyVel, 1),
               flyTarget: robot.flyTarget, held: robot.held },
      balls: balls.map((b) => [b.id, b.kind, n(b.x, 2), n(b.y, 2), n(b.z, 2)]),
      hives: { blue: { phi: n(hives.blue.phi, 2), pollen: hives.blue.pollen, nectar: hives.blue.nectar, tips: hives.blue.tips },
               red: { phi: n(hives.red.phi, 2), pollen: hives.red.pollen, nectar: hives.red.nectar, tips: hives.red.tips } },
      match: { phase, clock: n(Math.max(0, clock), 2), alliance, blue: score.blue, red: score.red },
      op,
    });
    if (tick % 3 === 0 && op.status !== 'NONE') {
      emit({ type: 'telemetry', lines: [
        `Velocity : ${robot.flyVel.toFixed(1)}`, `Target : ${robot.flyTarget.toFixed(1)}`,
        `x : ${robot.x.toFixed(2)}`, `y : ${robot.y.toFixed(2)}`, `heading (deg) : ${deg(robot.h).toFixed(1)}`,
        `held : ${robot.held}`, manual ? 'mode : gamepad' : 'mode : demo loop (touch the gamepad in TELEOP to drive)',
      ] });
    }
    if (tick % 2 === 0 && op.status === 'RUNNING' && est) {
      history.push([est[0], est[1]]);
      if (history.length > 120) history.shift();
      const tgt = loopPoint(loopS);
      const aim = openingCenter(mine());
      emit({ type: 'overlay', kind: 'live', ops: [
        { op: 'width', w: 1 }, { op: 'stroke', color: '#3F51B5' },
        { op: 'polyline', x: history.map((p) => p[0]), y: history.map((p) => p[1]) },
        { op: 'stroke', color: '#4CAF50' },
        { op: 'circle', x: tgt[0], y: tgt[1], r: 9, fill: false },
        { op: 'polyline', x: [tgt[0], tgt[0] + 9 * Math.cos(tgt[2])], y: [tgt[1], tgt[1] + 9 * Math.sin(tgt[2])] },
        { op: 'fill', color: '#7C4DFF55' },
        { op: 'circle', x: aim[0], y: aim[1], r: 4, fill: true },
        { op: 'stroke', color: '#FFC10799' },
        { op: 'polygon', x: [aim[0] - 6, aim[0] + 6, aim[0] + 6, aim[0] - 6], y: [aim[1] - 11, aim[1] - 11, aim[1] + 11, aim[1] + 11], fill: false },
      ] });
    }
    tick++;
  }

  function hello() {
    const code = { path: '(mock, no engine)', branch: 'my-branch', commit: 'mock000', dirty: true,
                   compiledAt: new Date().toTimeString().slice(0, 8), ok: !broken, errors: [] };
    if (broken) {
      code.errors = [
        { file: 'utility/Robot.java', line: 12, msg: "cannot find symbol: class Limelite (mock error)" },
        { file: 'opmodes/compTeleOp.java', line: 48, msg: "';' expected (mock error)" },
      ];
    }
    emit({ type: 'hello', code, opModes: broken ? [] : OPMODES, knobs, knobMeta: KNOB_META, field: F, robot: R });
  }

  function startFull(auto, teleop) {
    resetField();
    emit({ type: 'overlay', kind: 'clear' });
    full = { auto, teleop };
    runOp(auto, 30);
  }

  function send(m) {
    switch (m.type) {
      case 'select': if (op.status !== 'RUNNING' && op.status !== 'INIT') op = { name: m.opMode, status: 'NONE' }; break;
      case 'init': if (!broken && op.status !== 'RUNNING') { full = null; phase = 'IDLE'; clock = 0; setStatus('INIT'); } break;
      case 'start': if (op.status === 'INIT') runOp(op.name, flavorOf(op.name) === 'AUTO' ? 30 : 120); break;
      case 'stop': stopOp('stopped by hand'); phase = 'IDLE'; clock = 0; full = null; break;
      case 'gamepad':
        pads[m.id] = m;
        if (flavorOf(op.name) === 'TELEOP' && op.status === 'RUNNING' && !manual &&
            (m.lx || m.ly || m.rx || m.ry || m.lt || m.rt || (m.b && m.b.length))) {
          manual = true; robot.flyTarget = 0;
          event('log', 'gamepad input seen: demo loop off, you are driving');
        }
        break;
      case 'reset':
        full = null; phase = 'IDLE'; clock = 0; op = { name: op.name, status: 'NONE' };
        resetField();
        emit({ type: 'overlay', kind: 'clear' });
        event('log', 'field reset');
        break;
      case 'place': if (op.status !== 'RUNNING') { robot.x = m.x; robot.y = m.y; robot.h = m.h; } break;
      case 'alliance': alliance = m.value === 'RED' ? 'RED' : 'BLUE'; break;
      case 'knobs': Object.assign(knobs, m.values || {}); break;
      case 'reload': setTimeout(() => { hello(); event('log', 'code reloaded (mock)'); }, 500); break;
      case 'speed': speed = Number(m.value) || 0; break;
      case 'match': if (m.action === 'full' && !broken) startFull(m.auto, m.teleop); break;
      default: break;
    }
  }

  function start() {
    resetField();
    hello();
    if (!broken) startFull('BlueHiveFacingAUTO', 'compTeleOp');
    setInterval(() => {
      let remaining = speed / HZ;
      while (remaining > 1e-9) {
        const dt = Math.min(remaining, 1 / 120);
        step(dt);
        remaining -= dt;
      }
      publish();
    }, 1000 / HZ);
  }

  return {
    url: 'mock',
    isMock: true,
    start,
    send,
    onMessage(h) { handlers.push(h); },
    onStatus(cb) { cb(true); },
  };
}
