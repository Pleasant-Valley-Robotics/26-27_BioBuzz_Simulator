// Radical 7196 robot, modeled from the team photos with primitives.
// Model frame: forward (intake) = +Z, left = +X, up = +Y. Place with rotation.y = heading.
import { T, box, cylX, beam, lambert, phong, textTexture, perfTexture, rad, COLORS } from './util.js';

const perfBase = { silver: null, dark: null, clear: null };
const perfCache = new Map();

// Perforated material with the hole pattern repeated (ru, rv) times across a face.
function perfMat(kind, ru, rv) {
  if (!perfBase.silver) {
    perfBase.silver = perfTexture('#c2c8cf', '#4d535a', 2);
    perfBase.dark = perfTexture('#2c2f34', '#0b0c0e', 4);
    perfBase.clear = perfTexture('#dfe6f2', '#5a6dbb', 4);
  }
  const key = `${kind}:${ru.toFixed(1)}:${rv.toFixed(1)}`;
  if (perfCache.has(key)) return perfCache.get(key);
  const tex = perfBase[kind].clone();
  tex.needsUpdate = true;
  tex.repeat.set(Math.max(1, Math.round(ru)), Math.max(1, Math.round(rv)));
  const mat = kind === 'clear'
    ? new T.MeshPhongMaterial({ map: tex, transparent: true, opacity: 0.6, side: T.DoubleSide })
    : new T.MeshPhongMaterial({ map: tex, shininess: kind === 'silver' ? 90 : 20, side: T.DoubleSide });
  perfCache.set(key, mat);
  return mat;
}

// goBILDA style U-channel: a box whose faces carry the hole pattern at a 1.9 in pitch.
function channel(w, h, d, x, y, z) {
  const p = 1.9;
  const mx = perfMat('silver', d / p, h / p), my = perfMat('silver', w / p, d / p), mz = perfMat('silver', w / p, h / p);
  const m = new T.Mesh(new T.BoxGeometry(w, h, d), [mx, mx, my, my, mz, mz]);
  m.position.set(x, y, z);
  return m;
}

function gear(r, thick, teeth, mat) {
  const g = new T.Group();
  g.add(cylX(r, thick, mat, 28));
  for (let i = 0; i < teeth; i++) {
    const a = (i / teeth) * Math.PI * 2;
    const t = box(thick, 0.4, 0.32, mat, 0, Math.cos(a) * (r + 0.12), Math.sin(a) * (r + 0.12));
    t.rotation.x = a;
    g.add(t);
  }
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    const hole = cylX(r * 0.2, thick * 1.1, lambert(0x3b4047), 10);
    hole.position.set(0, Math.cos(a) * r * 0.58, Math.sin(a) * r * 0.58);
    g.add(hole);
  }
  return g;
}

// slant: +1 or -1, picks the roller diagonal so the four wheels form an X seen from above.
function mecanumWheel(r, w, slant) {
  const g = new T.Group();
  g.add(cylX(r * 0.6, w * 0.55, lambert(0x17191c), 14));
  const hub = lambert(COLORS.pollen);
  for (const sx of [-1, 1]) {
    const plate = cylX(r * 0.74, 0.14, hub, 7);
    plate.position.x = sx * w * 0.46;
    g.add(plate);
  }
  const rollMat = lambert(0x8a8f95);
  const n = 9, rr = 0.44;
  for (let i = 0; i < n; i++) {
    const holder = new T.Group();
    holder.rotation.x = (i / n) * Math.PI * 2;
    const roller = cylX(rr, w * 1.25, rollMat, 8);
    roller.position.y = r - rr;
    roller.rotation.y = slant * Math.PI / 4;
    holder.add(roller);
    g.add(holder);
  }
  return g;
}

function motor(len, mat, band) {
  const g = new T.Group();
  g.add(cylX(0.72, len, mat, 14));
  const b = cylX(0.75, len * 0.38, band, 14);
  b.position.x = len * 0.12;
  g.add(b);
  return g;
}

// goBILDA FTC StarterBot with Mecanum Wheels (3200-2627-0004), converted from goBILDA's STEP file and
// simplified. models/starterbot.json lists color groups; models/starterbot.bin holds float32 positions
// (inches, model frame above) followed by uint32 indices, each group's indices local to its own vertices.
let cadData = null;
function loadCad() {
  if (!cadData) {
    cadData = Promise.all([
      fetch('models/starterbot.json').then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }),
      fetch('models/starterbot.bin').then((r) => { if (!r.ok) throw new Error(r.status); return r.arrayBuffer(); }),
    ]);
    cadData.catch(() => { cadData = null; });
  }
  return cadData.then(([meta, buf]) => {
    const pos = new Float32Array(buf, 0, meta.vertices * 3);
    const idx = new Uint32Array(buf, meta.vertices * 12, meta.indices);
    const cad = new T.Group();
    for (const g of meta.groups) {
      const geo = new T.BufferGeometry();
      geo.setAttribute('position', new T.BufferAttribute(pos.subarray(g.vOff * 3, (g.vOff + g.vCount) * 3), 3));
      geo.setIndex(new T.BufferAttribute(idx.subarray(g.iOff, g.iOff + g.iCount), 1));
      geo.computeVertexNormals();
      const [r, gr, b] = g.color;
      const mat = new T.MeshPhongMaterial({ color: new T.Color(r / 255, gr / 255, b / 255), flatShading: true,
        side: T.DoubleSide, shininess: r > 200 && gr > 200 ? 60 : 15 });
      cad.add(new T.Mesh(geo, mat));
    }
    return cad;
  });
}

export function buildRobot(R) {
  const group = new T.Group();
  const L = R.length, W = R.width, H = R.height, wr = R.wheelRadius;
  const hw = W / 2, hl = L / 2;
  const wheelW = 1.5;
  const wheelX = hw - wheelW / 2, wheelZ = hl - wr - 0.2;
  const railX = hw - wheelW - 0.35 - 0.95;
  const railY = wr + 0.25;
  const plateY = railY + 1.0;

  const black = lambert(0x121315);
  const dark = lambert(0x24272b);
  const yellow = lambert(COLORS.pollen);
  const steel = phong(0xcfd4da, { shininess: 100 });

  // drive base: side rails, back cross channel, perforated base plate
  for (const sx of [-1, 1]) group.add(channel(1.9, 1.9, L - 0.6, sx * railX, railY, 0));
  group.add(channel(2 * railX - 1.9, 1.9, 1.9, 0, railY, -(hl - 1.25)));
  const plateW = 2 * railX - 1.9;
  const plate = new T.Mesh(new T.BoxGeometry(plateW, 0.15, 9.6),
    [dark, dark, perfMat('dark', plateW / 1.2, 8), perfMat('dark', plateW / 1.2, 8), dark, dark]);
  plate.position.set(0, plateY, -1.2);
  group.add(plate);

  // mecanum wheels, order [frontLeft, frontRight, backLeft, backRight]
  const wheels = [];
  for (const [sx, sz, slant] of [[1, 1, -1], [-1, 1, 1], [1, -1, 1], [-1, -1, -1]]) {
    const wgrp = mecanumWheel(wr, wheelW, slant);
    wgrp.position.set(sx * wheelX, wr, sz * wheelZ);
    group.add(wgrp);
    wheels.push(wgrp);
    // drive motor (Yellow Jacket) lying under the plate, pointing inboard
    const m = motor(4.0, black, yellow);
    m.rotation.y = sx > 0 ? Math.PI : 0;
    m.position.set(sx * (railX - 3.0), 1.55, sz > 0 ? 3.0 : -5.0);
    group.add(m);
  }

  // front: raised cross channel on two posts, intake roller under it, big gear on the left end
  const frontZ = hl - 1.6, frontY = 5.6;
  group.add(channel(2 * railX + 1.9, 1.9, 2.6, 0, frontY, frontZ));
  for (const sx of [-1, 1]) group.add(channel(1.9, frontY - railY - 1.9, 1.9, sx * railX, (frontY + railY) / 2, frontZ));
  const intake = new T.Group();
  intake.position.set(0, 3.35, hl - 0.5);
  const rollerLen = 2 * railX - 2.4;
  intake.add(cylX(0.8, rollerLen, black, 12));
  const flapMat = lambert(0x3a3d42);
  for (let i = 0; i < 4; i++) {
    const holder = new T.Group();
    holder.rotation.x = i * Math.PI / 2;
    holder.add(box(rollerLen - 0.4, 0.14, 1.0, flapMat, 0, 0, 1.1));
    intake.add(holder);
  }
  group.add(intake);
  const bigGear = gear(2.0, 0.3, 22, steel);
  bigGear.position.set(railX + 1.25, frontY + 0.2, frontZ);
  group.add(bigGear);

  // feeder wheels on servos at the front corners (leftFeeder, rightFeeder), vertical axles
  const feeders = [];
  for (const sx of [1, -1]) {
    const f = new T.Group();
    f.position.set(sx * 4.3, 2.2, hl - 2.5);
    f.add(new T.Mesh(new T.CylinderGeometry(1.1, 1.1, 0.9, 16), black));
    f.add(box(1.9, 0.08, 0.3, flapMat, 0, 0.47, 0));
    f.add(box(0.3, 0.08, 1.9, flapMat, 0, 0.47, 0));
    group.add(f);
    feeders.push(f);
    group.add(box(0.9, 1.5, 1.7, black, sx * 4.3, 3.5, hl - 2.5));
  }

  // hopper: perforated clear ramp from the intake up to the launcher
  const rampLo = new T.Vector3(0, 0.75, hl - 2.5), rampHi = new T.Vector3(0, 6.6, 0.3);
  const rampLen = rampLo.distanceTo(rampHi);
  const ramp = beam(rampLo, rampHi, 7.0, 0.08, perfMat('clear', 9, 11));
  group.add(ramp);
  const dir = new T.Vector3().subVectors(rampHi, rampLo).normalize();
  const nrm = new T.Vector3(0, dir.z * -1, dir.y).normalize();
  if (nrm.y < 0) nrm.negate();
  const slot = (s, x) => new T.Vector3(x, 0, 0).addScaledVector(dir, s).add(rampLo).addScaledVector(nrm, 1.5);
  const slots = [slot(rampLen - 1.5, 0), slot(rampLen - 4.4, 0), slot(1.5, -1.5), slot(1.5, 1.5)];
  const heldMat = phong(COLORS.pollen);
  const held = slots.map((p) => {
    const b = new T.Mesh(new T.SphereGeometry(1.4, 14, 10), heldMat);
    b.position.copy(p);
    b.visible = false;
    group.add(b);
    return b;
  });

  // launcher tower in the middle: two upright channels, double flywheel, motor, feed wheel
  const flyR = 2.0;
  const flyY = R.launchExitZ - 1.4 - flyR, flyZ = R.launchExitX + 0.6;
  const towerH = H - 0.4 - plateY;
  for (const sx of [-1, 1]) group.add(channel(1.9, towerH, 1.9, sx * 3.1, plateY + towerH / 2, flyZ));
  const fly = new T.Group();
  fly.position.set(0, flyY, flyZ);
  for (const sx of [-1, 1]) {
    const wheel = cylX(flyR, 1.5, black, 24);
    wheel.position.x = sx * 0.95;
    fly.add(wheel);
  }
  for (let i = 0; i < 6; i++) {
    const holder = new T.Group();
    holder.rotation.x = (i / 6) * Math.PI * 2;
    holder.add(box(3.5, 0.16, 0.34, flapMat, 0, flyR, 0));
    fly.add(holder);
  }
  fly.add(cylX(0.22, 9.5, steel, 8));
  group.add(fly);
  const launchMotor = motor(3.4, black, yellow);
  launchMotor.position.set(-(3.1 + 0.95 + 1.7), flyY, flyZ);
  group.add(launchMotor);
  const hood = box(5.2, 0.12, 4.2, steel, 0, H - 0.25, flyZ + 0.9);
  hood.rotation.x = 0.25;
  group.add(hood);
  const servo = box(0.9, 1.5, 1.7, phong(0x2a6fdb), 3.1 + 1.4, flyY + 2.6, flyZ);
  group.add(servo);
  group.add(box(0.2, 1.3, 0.5, lambert(0xff7a1a), 3.1 + 1.95, flyY + 2.6, flyZ));

  const feedLaunch = new T.Group();
  feedLaunch.position.set(0, 8.7, 3.5);
  feedLaunch.add(cylX(1.1, 1.3, black, 16));
  for (let i = 0; i < 4; i++) {
    const holder = new T.Group();
    holder.rotation.x = i * Math.PI / 2;
    holder.add(box(1.34, 0.14, 0.3, flapMat, 0, 1.1, 0));
    feedLaunch.add(holder);
  }
  group.add(feedLaunch);
  for (const sx of [-1, 1]) {
    group.add(beam(new T.Vector3(sx * 1.6, 10.2, flyZ + 0.9), new T.Vector3(sx * 1.6, 8.7, 3.5), 0.3, 0.9, steel));
  }

  // electronics: Control Hub behind the tower, Expansion Hub on the right side, battery low at the back
  const orange = lambert(0xff6a13);
  group.add(box(4.1, 5.6, 1.2, black, 1.6, plateY + 2.9, flyZ - 1.75));
  group.add(box(1.0, 5.6, 1.24, orange, 3.2, plateY + 2.9, flyZ - 1.75));
  group.add(box(1.2, 4.1, 5.6, black, -(hw - 1.5), 7.7, -3.6));
  group.add(box(1.24, 0.5, 5.6, orange, -(hw - 1.5), 5.9, -3.6));
  const batZ = -(hl - 3.2);
  group.add(box(5.4, 1.9, 2.4, black, 0, plateY + 1.05, batZ));
  const label = new T.Mesh(new T.PlaneGeometry(3.2, 1.1),
    new T.MeshBasicMaterial({ map: textTexture('Radical', { w: 256, h: 88, bg: '#f4f4f0', fg: '#111', font: 'italic 60px sans-serif' }) }));
  label.rotation.y = Math.PI;
  label.position.set(0, plateY + 1.05, batZ - 1.22);
  group.add(label);

  // odometry pod bracket near the back right corner
  group.add(box(1.0, 1.3, 2.4, black, -(railX - 1.9), 1.15, -2.2));
  const pod = cylX(0.65, 0.3, dark, 12);
  pod.position.set(-(railX - 1.9), 0.65, -2.2);
  group.add(pod);

  // pink polycarbonate side panels with a blue top stripe, and the red 7196 plates
  const pink = phong(0xff5fa2, { transparent: true, opacity: 0.5, side: T.DoubleSide, depthWrite: false });
  const stripe = phong(0x1f5fd0);
  const plateTex = textTexture('7196', { w: 512, h: 230, bg: '#d6281f', fg: '#ffffff', font: 'bold 190px sans-serif' });
  const plateMat = new T.MeshBasicMaterial({ map: plateTex });
  const panelLo = 2 * wr + 0.8, panelHi = 11.2, panelLen = L - 2.4;
  for (const sx of [-1, 1]) {
    const px = sx * (hw - 0.2);
    group.add(box(0.12, panelHi - panelLo, panelLen, pink, px, (panelHi + panelLo) / 2, 0));
    group.add(box(0.16, 1.0, panelLen, stripe, px, panelHi + 0.5, 0));
    for (const sz of [-1, 1]) group.add(box(0.6, panelHi + 1 - railY, 0.6, steel, sx * (hw - 0.7), (panelHi + 1 + railY) / 2, sz * (panelLen / 2 - 0.3)));
    const np = new T.Mesh(new T.PlaneGeometry(8.2, 3.7), plateMat);
    np.rotation.y = sx * Math.PI / 2;
    np.position.set(sx * (hw - 0.1), (panelHi + panelLo) / 2 - 0.3, 1.2);
    group.add(np);
  }

  // FRONT markers: lime strip on the front channel and an arrow on top of it
  const lime = new T.MeshBasicMaterial({ color: 0x7dff4d, side: T.DoubleSide });
  group.add(box(2 * railX + 1.9, 0.6, 0.12, lime, 0, frontY, frontZ + 1.37));
  const tri = new T.Shape();
  tri.moveTo(-1.9, 0); tri.lineTo(1.9, 0); tri.lineTo(0, 2.4); tri.closePath();
  const arrow = new T.Mesh(new T.ShapeGeometry(tri), lime);
  arrow.rotation.x = Math.PI / 2;
  arrow.position.set(0, frontY + 1.0, frontZ - 1.2);
  group.add(arrow);

  // launcher throw direction (shown while the flywheel has a target)
  const throwGeo = new T.BufferGeometry();
  throwGeo.setAttribute('position', new T.Float32BufferAttribute([0, 0, 0, 0, 1, 0], 3));
  const throwLine = new T.Line(throwGeo, new T.LineBasicMaterial({ color: 0xffe066 }));
  throwLine.frustumCulled = false;
  throwLine.visible = false;
  group.add(throwLine);
  let throwDeg = null;
  function setLaunchAngle(angleDeg) {
    if (angleDeg === throwDeg) return;
    throwDeg = angleDeg;
    const a = rad(angleDeg), len = 40;
    const p = throwGeo.attributes.position;
    p.setXYZ(0, 0, R.launchExitZ, R.launchExitX);
    p.setXYZ(1, 0, R.launchExitZ + Math.sin(a) * len, R.launchExitX - Math.cos(a) * len);
    p.needsUpdate = true;
  }
  setLaunchAngle(60);

  // goBILDA StarterBot CAD: replaces the primitive model once it has loaded (add ?cad=0 to the URL to keep
  // the primitive one). The held POLLEN and the throw line stay visible on top of it.
  // flowerStick: a hook reaching up to 10 in out of the front, 1.5 in off the floor (drawn only while out)
  const stickMesh = box(0.5, 0.5, 1, lambert(0xff3b6b), 0, 1.5, hl);
  stickMesh.visible = false;
  group.add(stickMesh);

  const primitives = group.children.filter((c) => c !== throwLine && c !== stickMesh && !held.includes(c));
  if (!/[?&]cad=0/.test(location.search)) {
    loadCad().then((cad) => {
      group.add(cad);
      for (const c of primitives) c.visible = false;
    }).catch((e) => console.warn('StarterBot CAD not loaded, keeping the simple robot:', e));
  }

  function update(r) {
    group.position.set(r.y, 0, r.x);
    group.rotation.y = r.h;
    const w = r.wheels || [];
    for (let i = 0; i < 4; i++) wheels[i].rotation.x = w[i] || 0;
    intake.rotation.x = r.intake || 0;
    bigGear.rotation.x = -(r.intake || 0) * 0.4;
    feeders[0].rotation.y = r.feedL || 0;
    feeders[1].rotation.y = r.feedR || 0;
    feedLaunch.rotation.x = r.feedLaunch || 0;
    fly.rotation.x = -(r.flywheel || 0);
    for (let i = 0; i < held.length; i++) held[i].visible = i < (r.held || 0);
    throwLine.visible = (r.flyTarget || 0) > 0;
    const out = r.stick || 0;
    stickMesh.visible = out > 0.02;
    if (stickMesh.visible) {
      const len = 2 + 10 * out;           // starts 2 in inside the front face
      stickMesh.scale.z = len;
      stickMesh.position.z = hl - 2 + len / 2;
    }
  }

  return { group, update, setLaunchAngle };
}
