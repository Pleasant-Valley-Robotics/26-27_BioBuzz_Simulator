// Floor drawings: the team's RoadRunner overlay ops (preview and live), the TRUE pose
// trail, and a ghost outline at the localizer estimate. All flat, a hair above the tiles.
import { T, parseColor, disposeTree, clamp } from './util.js';

const LIFT = 0.3;

// Flat ribbon along field points (xs, ys). Returns a BufferGeometry in three.js coords.
function ribbon(xs, ys, width, closed) {
  const n = Math.min(xs.length, ys.length);
  const pos = [];
  const hw = width / 2;
  const segs = closed ? n : n - 1;
  for (let i = 0; i < segs; i++) {
    const j = (i + 1) % n;
    const dx = xs[j] - xs[i], dy = ys[j] - ys[i];
    const len = Math.hypot(dx, dy);
    if (len < 1e-6) continue;
    const nx = (-dy / len) * hw, ny = (dx / len) * hw;
    // extend each piece by half a width so joints do not gap
    const ex = (dx / len) * hw * 0.5, ey = (dy / len) * hw * 0.5;
    const ax = xs[i] - ex, ay = ys[i] - ey, bx = xs[j] + ex, by = ys[j] + ey;
    pos.push(ay + ny, 0, ax + nx, ay - ny, 0, ax - nx, by - ny, 0, bx - nx,
             ay + ny, 0, ax + nx, by - ny, 0, bx - nx, by + ny, 0, bx + nx);
  }
  const g = new T.BufferGeometry();
  g.setAttribute('position', new T.Float32BufferAttribute(pos, 3));
  return g;
}

function filled(xs, ys) {
  const n = Math.min(xs.length, ys.length);
  const pts = [];
  const pos = [];
  for (let i = 0; i < n; i++) { pts.push(new T.Vector2(ys[i], xs[i])); pos.push(ys[i], 0, xs[i]); }
  const g = new T.BufferGeometry();
  g.setAttribute('position', new T.Float32BufferAttribute(pos, 3));
  g.setIndex(T.ShapeUtils.triangulateShape(pts, []).flat());
  return g;
}

function flatMat(colorStr) {
  const { color, alpha } = parseColor(colorStr, '#3F51B5');
  return new T.MeshBasicMaterial({ color, transparent: true, opacity: alpha, depthWrite: false, side: T.DoubleSide });
}

function circlePts(cx, cy, r, n = 40) {
  const xs = [], ys = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    xs.push(cx + Math.cos(a) * r); ys.push(cy + Math.sin(a) * r);
  }
  return [xs, ys];
}

export function buildOverlay(R) {
  const group = new T.Group();
  const sets = { preview: new T.Group(), live: new T.Group() };
  sets.preview.position.y = LIFT;
  sets.live.position.y = LIFT + 0.12;
  group.add(sets.preview, sets.live);

  function wipe(g) {
    for (const c of [...g.children]) disposeTree(c);
  }

  function setOps(kind, ops) {
    const g = sets[kind];
    if (!g) return;
    wipe(g);
    let stroke = '#3F51B5', fill = '#3F51B5', w = 1, order = 0;
    const add = (geo, colorStr) => {
      const m = new T.Mesh(geo, flatMat(colorStr));
      m.position.y = order * 0.004;
      m.renderOrder = 5 + order;
      order++;
      g.add(m);
    };
    const lineW = () => clamp(0.55 * w, 0.45, 3);
    for (const op of ops || []) {
      if (!op) continue;
      switch (op.op) {
        case 'stroke': stroke = op.color; break;
        case 'fill': fill = op.color; break;
        case 'width': w = Number(op.w) || 1; break;
        case 'polyline':
          if (op.x && op.x.length > 1) add(ribbon(op.x, op.y, lineW(), false), stroke);
          break;
        case 'polygon':
          if (!op.x || op.x.length < 3) break;
          if (op.fill) add(filled(op.x, op.y), fill);
          else add(ribbon(op.x, op.y, lineW(), true), stroke);
          break;
        case 'circle': {
          const [xs, ys] = circlePts(op.x, op.y, op.r);
          if (op.fill) add(filled(xs, ys), fill);
          else add(ribbon(xs, ys, lineW(), true), stroke);
          break;
        }
        default: break;
      }
    }
  }

  function onMessage(m) {
    if (m.kind === 'clear') { wipe(sets.preview); wipe(sets.live); return; }
    setOps(m.kind, m.ops);
  }

  // TRUE pose trail: ring buffer of flat quads
  const MAXSEG = 6000, TW = 0.3;
  const tpos = new Float32Array(MAXSEG * 18);
  const tgeo = new T.BufferGeometry();
  tgeo.setAttribute('position', new T.BufferAttribute(tpos, 3));
  tgeo.setDrawRange(0, 0);
  const trail = new T.Mesh(tgeo, new T.MeshBasicMaterial({ color: 0xffffff, side: T.DoubleSide, depthWrite: false, transparent: true, opacity: 0.92 }));
  trail.frustumCulled = false;
  trail.position.y = LIFT + 0.25;
  trail.renderOrder = 40;
  group.add(trail);
  let tcount = 0, last = null;

  function trailClear() {
    tcount = 0; last = null;
    tgeo.setDrawRange(0, 0);
  }
  function trailAdd(x, y) {
    if (!last) { last = [x, y]; return; }
    const dx = x - last[0], dy = y - last[1];
    const d = Math.hypot(dx, dy);
    if (d < 0.6) return;
    if (d > 24) { last = [x, y]; return; }   // teleport (place or reset): do not connect
    const nx = (-dy / d) * TW, ny = (dx / d) * TW;
    const o = (tcount % MAXSEG) * 18;
    const [ax, ay] = last;
    const q = [ay + ny, 0, ax + nx, ay - ny, 0, ax - nx, y - ny, 0, x - nx,
               ay + ny, 0, ax + nx, y - ny, 0, x - nx, y + ny, 0, x + nx];
    tpos.set(q, o);
    tcount++;
    tgeo.attributes.position.needsUpdate = true;
    tgeo.setDrawRange(0, Math.min(tcount, MAXSEG) * 6);
    last = [x, y];
  }

  // ghost outline where the team's localizer thinks the robot is
  const ghost = new T.Group();
  const gmat = new T.MeshBasicMaterial({ color: 0x35e0ff, transparent: true, opacity: 0.9, depthWrite: false, side: T.DoubleSide });
  const hl = R.length / 2, hw = R.width / 2;
  // local model frame: forward +Z, left +X; as field-style (x forward, y left) lists
  ghost.add(new T.Mesh(ribbon([hl, hl, -hl, -hl], [hw, -hw, -hw, hw], 0.5, true), gmat));
  ghost.add(new T.Mesh(ribbon([0, hl + 4], [0, 0], 0.5, false), gmat));
  ghost.add(new T.Mesh(filled([hl + 6.5, hl + 3, hl + 3], [0, 1.8, -1.8]), gmat));
  ghost.position.y = LIFT + 0.4;
  ghost.renderOrder = 45;
  ghost.visible = false;
  group.add(ghost);

  function setGhost(est) {
    if (!est) { ghost.visible = false; return; }
    ghost.visible = true;
    ghost.position.x = est[1];
    ghost.position.z = est[0];
    ghost.rotation.y = est[2];
  }

  return { group, onMessage, trailAdd, trailClear, setGhost };
}
