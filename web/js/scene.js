// Renderer, world assembly, camera modes and the small orbit/pan/zoom control.
import { T, DEFAULT_FIELD, DEFAULT_ROBOT, rad, clamp, disposeTree } from './util.js';
import { buildField } from './field.js';
import { buildHive } from './hive.js';
import { buildRobot } from './robot.js';
import { buildBalls } from './balls.js';
import { buildOverlay } from './overlay.js';

export function createScene(canvas) {
  const renderer = new T.WebGLRenderer({ canvas, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  const scene = new T.Scene();
  scene.background = new T.Color(0x0d1116);
  const camera = new T.PerspectiveCamera(42, 1, 1, 4000);

  scene.add(new T.HemisphereLight(0xffffff, 0x3a4250, 0.95));
  const sun = new T.DirectionalLight(0xffffff, 0.55);
  sun.position.set(80, 180, 120);
  scene.add(sun);

  let world = null;
  let builtKey = '';
  let state = null;
  let alliance = 'BLUE';
  let lastStatus = 'NONE';
  let launchAngle = 60;

  function build(F, R) {
    const key = JSON.stringify([F, R]);
    if (key === builtKey) return;
    builtKey = key;
    if (world) for (const k of ['field', 'hive', 'robot', 'balls', 'overlay']) disposeTree(world[k].group || world[k]);
    const field = buildField(F);
    const hive = buildHive(F);
    const robot = buildRobot(R);
    const balls = buildBalls();
    const overlay = buildOverlay(R);
    scene.add(field, hive.group, robot.group, balls.group, overlay.group);
    world = { field, hive, robot, balls, overlay, F, R };
    robot.setLaunchAngle(launchAngle);
    robot.update(state ? state.robot : { x: -63.02, y: 13.65, h: Math.PI, wheels: [0, 0, 0, 0], held: 0 });
    if (state) onState(state);
  }

  // Fill anything the engine left out of hello.field or hello.robot from the defaults.
  function withDefaults(def, given) {
    if (Array.isArray(def)) return Array.isArray(given) && given.length ? given : def;
    if (def && typeof def === 'object') {
      const out = {};
      for (const k of Object.keys(def)) out[k] = withDefaults(def[k], given ? given[k] : undefined);
      return out;
    }
    return typeof given === typeof def && given !== null ? given : def;
  }

  function onHello(h) {
    build(withDefaults(DEFAULT_FIELD, h.field), withDefaults(DEFAULT_ROBOT, h.robot));
    setKnobs(h.knobs || {});
  }

  function setKnobs(values) {
    for (const [k, v] of Object.entries(values)) {
      if (/launch.*angle/i.test(k) && Number.isFinite(Number(v))) launchAngle = Number(v);
    }
    if (world) world.robot.setLaunchAngle(launchAngle);
  }

  function onState(s) {
    state = s;
    if (!world) return;
    const r = s.robot;
    if (!r) return;
    world.robot.update(r);
    if (s.hives && s.hives.blue && s.hives.red) world.hive.setPhi(s.hives.blue.phi, s.hives.red.phi);
    world.balls.update(s.balls);
    world.overlay.setGhost(r.est);
    const status = s.op ? s.op.status : 'NONE';
    if (status === 'RUNNING' && lastStatus !== 'RUNNING') world.overlay.trailClear();
    lastStatus = status;
    world.overlay.trailAdd(r.x, r.y);
    const a = s.match && s.match.alliance;
    if (a && a !== alliance) { alliance = a; if (cam.mode === 'driver') setCamera('driver'); }
  }

  function onOverlay(m) { if (world) world.overlay.onMessage(m); }
  function clearTrail() { if (world) world.overlay.trailClear(); }

  // ===== camera =====
  // Camera sits at target + dist * (sin pol sin az, cos pol, sin pol cos az).
  // az = 0 puts it on the audience side (+Z): red on the left, blue on the right.
  const cam = { mode: 'orbit', target: new T.Vector3(0, 10, 0), az: 0, pol: rad(50), dist: 235, azOff: 0, head: Math.PI };
  // Distance at which wNeed inches fit between the side panels and hNeed inches fit vertically.
  function fit(wNeed, hNeed, fov) {
    const w = window.innerWidth, h = window.innerHeight;
    const free = Math.max(320, w - 610);
    const k = 2 * Math.tan(rad(fov) / 2);
    return clamp(Math.max((wNeed * h) / (k * free), (hNeed * h) / (k * Math.max(200, h - 70))), 120, 900);
  }
  const PRESET = {
    orbit: () => ({ target: [0, 4, 4], az: 0, pol: rad(47), dist: fit(196, 150, 42), fov: 42 }),
    top: () => ({ target: [0, 0, 0], az: 0, pol: 0.0015, dist: fit(262, 196, 42), fov: 42 }),
    driver: () => ({ target: [0, 10, 0], az: alliance === 'RED' ? -Math.PI / 2 : Math.PI / 2, pol: rad(63), dist: 120, fov: 66 }),
    // Chase camera: close and low enough (about 28 in up) to stay under the HIVE, which hangs at 30 in.
    follow: () => ({ target: [cam.target.x, 8, cam.target.z], az: cam.az, pol: rad(63), dist: 46, fov: 58 }),
  };
  const modeListeners = [];

  function setCamera(mode) {
    if (!PRESET[mode]) return;
    cam.mode = mode;
    const p = PRESET[mode]();
    cam.target.set(...p.target);
    cam.az = p.az; cam.pol = p.pol; cam.dist = p.dist; cam.azOff = 0;
    camera.fov = p.fov;
    camera.updateProjectionMatrix();
    if (mode === 'follow' && state) { cam.head = state.robot.h; cam.target.set(state.robot.y, 8, state.robot.x); }
    for (const f of modeListeners) f(mode);
  }

  function updateCamera(dt) {
    if (cam.mode === 'follow' && state) {
      const r = state.robot;
      const k = 1 - Math.exp(-dt * 6);
      cam.target.x += (r.y - cam.target.x) * k;
      cam.target.z += (r.x - cam.target.z) * k;
      let dh = r.h - cam.head;
      dh = Math.atan2(Math.sin(dh), Math.cos(dh));
      cam.head += dh * (1 - Math.exp(-dt * 3));
      cam.az = cam.head + Math.PI + cam.azOff;   // behind the robot
    }
    const sp = Math.sin(cam.pol), cp = Math.cos(cam.pol);
    camera.position.set(cam.target.x + cam.dist * sp * Math.sin(cam.az),
                        cam.target.y + cam.dist * cp,
                        cam.target.z + cam.dist * sp * Math.cos(cam.az));
    camera.lookAt(cam.target);
  }

  // pointer: drag orbits, right-drag or shift-drag pans, wheel zooms
  let drag = null;
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  canvas.addEventListener('pointerdown', (e) => {
    canvas.setPointerCapture(e.pointerId);
    const pan = e.button === 2 || e.button === 1 || e.shiftKey || cam.mode === 'top';
    drag = { x: e.clientX, y: e.clientY, pan };
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
    drag.x = e.clientX; drag.y = e.clientY;
    if (drag.pan) {
      if (cam.mode === 'follow') return;
      const k = cam.dist * 0.0016;
      const rx = Math.cos(cam.az), rz = -Math.sin(cam.az);    // screen right on the floor
      const fx = -Math.sin(cam.az), fz = -Math.cos(cam.az);   // screen up on the floor
      cam.target.x += (-rx * dx + fx * dy) * k;
      cam.target.z += (-rz * dx + fz * dy) * k;
      cam.target.x = clamp(cam.target.x, -160, 160);
      cam.target.z = clamp(cam.target.z, -160, 160);
    } else {
      if (cam.mode === 'follow') cam.azOff -= dx * 0.006; else cam.az -= dx * 0.006;
      cam.pol = clamp(cam.pol - dy * 0.005, 0.03, 1.54);
    }
  });
  const endDrag = (e) => { drag = null; try { canvas.releasePointerCapture(e.pointerId); } catch (err) { /* not captured */ } };
  canvas.addEventListener('pointerup', endDrag);
  canvas.addEventListener('pointercancel', endDrag);
  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    cam.dist = clamp(cam.dist * Math.exp(e.deltaY * 0.0012), 18, 700);
  }, { passive: false });

  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(1, h);
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', resize);
  resize();

  let lastT = 0;
  function render(tms) {
    const dt = Math.min(0.1, Math.max(0, (tms - lastT) / 1000));
    lastT = tms;
    updateCamera(dt);
    renderer.render(scene, camera);
  }

  build(DEFAULT_FIELD, DEFAULT_ROBOT);
  setCamera('orbit');

  return {
    onHello, onState, onOverlay, setKnobs, clearTrail, setCamera, render,
    onCameraMode: (f) => modeListeners.push(f),
    get cameraMode() { return cam.mode; },
    debug: { scene, camera, cam, get world() { return world; } },
  };
}
