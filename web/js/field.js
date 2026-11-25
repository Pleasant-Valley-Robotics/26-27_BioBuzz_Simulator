// Field: tiles, perimeter walls, tape zones, alliance mats, FLOWERS and wall labels.
// Everything is built in three.js coordinates through fv(fieldX, fieldY, fieldZ).
import { T, fv, COLORS, beam, rod, box, lambert, phong, floorLabel, textTexture } from './util.js';

function tileTexture(n) {
  const c = document.createElement('canvas');
  const px = 128;
  c.width = c.height = n * px;
  const g = c.getContext('2d');
  let seed = 7;
  const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
    const v = 104 + Math.floor(rnd() * 10);
    g.fillStyle = `rgb(${v},${v + 3},${v + 7})`;
    g.fillRect(i * px, j * px, px, px);
  }
  // speckle so the foam does not look like flat plastic
  for (let k = 0; k < 9000; k++) {
    g.fillStyle = rnd() > 0.5 ? 'rgba(255,255,255,0.035)' : 'rgba(0,0,0,0.05)';
    g.fillRect(rnd() * c.width, rnd() * c.height, 2, 2);
  }
  g.strokeStyle = 'rgba(30,33,38,0.9)'; g.lineWidth = 2;
  for (let i = 0; i <= n; i++) {
    g.beginPath(); g.moveTo(i * px, 0); g.lineTo(i * px, c.height); g.stroke();
    g.beginPath(); g.moveTo(0, i * px); g.lineTo(c.width, i * px); g.stroke();
  }
  const tex = new T.CanvasTexture(c);
  tex.anisotropy = 8;
  return tex;
}

// Flat rectangle on the floor between field corners (x0, y0) and (x1, y1).
function floorRect(x0, x1, y0, y1, mat, lift = 0.06) {
  const m = new T.Mesh(new T.PlaneGeometry(Math.abs(y1 - y0), Math.abs(x1 - x0)), mat);
  m.rotation.x = -Math.PI / 2;
  m.position.copy(fv((x0 + x1) / 2, (y0 + y1) / 2, lift));
  return m;
}

function tapeBox(g, r, color, w = 1) {
  const mat = new T.MeshBasicMaterial({ color });
  g.add(floorRect(r.x0, r.x1, r.y0, r.y0 + w, mat));
  g.add(floorRect(r.x0, r.x1, r.y1 - w, r.y1, mat));
  g.add(floorRect(r.x0, r.x0 + w, r.y0, r.y1, mat));
  g.add(floorRect(r.x1 - w, r.x1, r.y0, r.y1, mat));
  g.add(floorRect(r.x0, r.x1, r.y0, r.y1,
    new T.MeshBasicMaterial({ color, transparent: true, opacity: 0.13, depthWrite: false }), 0.04));
}

function buildFlower(f, F) {
  const g = new T.Group();
  g.position.copy(fv(f.x, f.y, 0));
  const green = lambert(0x35c13c);
  const dark = lambert(0x1c1f23);
  const ringR = 2.35, topZ = 21.5;
  for (let k = 0; k < 4; k++) {
    const a = Math.PI / 4 + k * Math.PI / 2;
    const px = Math.cos(a) * ringR, pz = Math.sin(a) * ringR;
    g.add(rod(new T.Vector3(px, 0, pz), new T.Vector3(px, topZ + 5, pz), 0.42, green, 8));
  }
  for (const z of [0.45, topZ]) {
    const ring = new T.Mesh(new T.TorusGeometry(ringR, 0.5, 8, 24), dark);
    ring.rotation.x = Math.PI / 2;
    ring.position.y = z;
    g.add(ring);
  }
  // bracket from the top ring to the wall it hangs on
  const toWall = { back: [-1, 0], audience: [1, 0], blue: [0, 1], red: [0, -1] }[f.wall] || [0, 0];
  const reach = F.half - Math.max(Math.abs(f.x), Math.abs(f.y)) + 0.6;
  const tip = fv(toWall[0] * reach, toWall[1] * reach, 0);
  for (const z of [topZ, F.wallHeight - 1]) {
    g.add(beam(new T.Vector3(0, z, 0), new T.Vector3(tip.x, z, tip.z), 1.2, 0.8, dark));
  }
  return g;
}

export function buildField(F) {
  const g = new T.Group();
  const H = F.half;
  const n = Math.round((2 * H) / F.tile);

  // surroundings
  const ground = new T.Mesh(new T.PlaneGeometry(900, 900), lambert(0x171b21));
  ground.rotation.x = -Math.PI / 2; ground.position.y = -0.3;
  g.add(ground);

  // foam tiles
  const floor = new T.Mesh(new T.PlaneGeometry(2 * H, 2 * H), lambert(0xffffff, { map: tileTexture(n) }));
  floor.rotation.x = -Math.PI / 2;
  g.add(floor);

  // perimeter: clear panels, dark rails and posts
  const rail = lambert(0x22262b);
  const glass = phong(0xcfe6ff, { transparent: true, opacity: 0.13, depthWrite: false, side: T.DoubleSide });
  const wh = F.wallHeight, o = H + 0.6;
  const corners = [[-o, -o], [-o, o], [o, o], [o, -o]];
  for (let i = 0; i < 4; i++) {
    const [ax, ay] = corners[i], [bx, by] = corners[(i + 1) % 4];
    g.add(beam(fv(ax, ay, wh - 0.6), fv(bx, by, wh - 0.6), 1.2, 1.2, rail));
    g.add(beam(fv(ax, ay, 0.6), fv(bx, by, 0.6), 1.2, 1.2, rail));
    g.add(beam(fv(ax, ay, wh / 2), fv(bx, by, wh / 2), 0.2, wh - 2.2, glass));
    g.add(box(1.6, wh, 1.6, rail, ay, wh / 2, ax));
    for (const t of [1 / 3, 2 / 3]) {
      const mx = ax + (bx - ax) * t, my = ay + (by - ay) * t;
      g.add(box(0.9, wh, 0.9, rail, my, wh / 2, mx));
    }
  }

  // alliance area mats outside the red and blue walls
  for (const [sign, color, name, text] of [[1, 0x12203f, 'BLUE', '#7fa6f0'], [-1, 0x3c1512, 'RED', '#f08a7f']]) {
    const y0 = sign * (H + 2), y1 = sign * (H + 46);
    g.add(floorRect(-60, 60, Math.min(y0, y1), Math.max(y0, y1), lambert(color), -0.1));
    const lab = floorLabel(name, 44, 11, text);
    lab.position.copy(fv(0, sign * (H + 24), 0.05));
    g.add(lab);
    const tape = new T.MeshBasicMaterial({ color: sign > 0 ? COLORS.tapeBlue : COLORS.tapeRed });
    const lo = Math.min(y0, y1), hi = Math.max(y0, y1);
    g.add(floorRect(-60, -59, lo, hi, tape, -0.05));
    g.add(floorRect(59, 60, lo, hi, tape, -0.05));
    g.add(floorRect(-60, 60, sign > 0 ? hi - 1 : lo, sign > 0 ? hi : lo + 1, tape, -0.05));
  }
  const aud = floorLabel('AUDIENCE', 44, 8);
  aud.position.copy(fv(H + 9, 0, 0.05)); g.add(aud);
  const back = floorLabel('BACK', 26, 8);
  back.position.copy(fv(-(H + 9), 0, 0.05)); g.add(back);

  // tape: LOADING ZONE boxes and GARDEN strips
  tapeBox(g, F.lz.blue, COLORS.tapeBlue);
  tapeBox(g, F.lz.red, COLORS.tapeRed);
  for (const [r, color] of [[F.garden.blue, COLORS.tapeBlue], [F.garden.red, COLORS.tapeRed]]) {
    g.add(floorRect(r.x0, r.x1, r.y0, r.y1, new T.MeshBasicMaterial({ color })));
  }
  // small zone captions on the floor
  const cap = (text, x, y, color) => {
    const m = new T.Mesh(new T.PlaneGeometry(16, 3.2),
      new T.MeshBasicMaterial({ map: textTexture(text, { fg: color, font: 'bold 70px sans-serif' }), transparent: true, opacity: 0.75, depthWrite: false }));
    m.rotation.x = -Math.PI / 2;
    m.position.copy(fv(x, y, 0.08));
    g.add(m);
  };
  cap('LOADING ZONE', (F.lz.blue.x0 + F.lz.blue.x1) / 2, F.lz.blue.y0 + 5.5, '#9dbcf5');
  cap('LOADING ZONE', (F.lz.red.x0 + F.lz.red.x1) / 2, F.lz.red.y1 - 5.5, '#f5a79d');
  cap('GARDEN', F.garden.blue.x1 + 4, (F.garden.blue.y0 + F.garden.blue.y1) / 2, '#9dbcf5');
  cap('GARDEN', F.garden.red.x0 - 4, (F.garden.red.y0 + F.garden.red.y1) / 2, '#f5a79d');

  for (const f of F.flowers) g.add(buildFlower(f, F));
  return g;
}
