// Shared helpers and default constants (same numbers as docs/PROTOCOL.md).
// three.js is loaded as a classic script (vendor/three.min.js), so it is a global.
export const T = window.THREE;

export const DEFAULT_FIELD = {
  half: 72, tile: 24, wallHeight: 12,
  lz: { blue: { x0: 24.5, x1: 47.5, y0: 61, y1: 72 }, red: { x0: -47.5, x1: -24.5, y0: -72, y1: -61 } },
  garden: { blue: { x0: -72, x1: -70, y0: 49, y1: 72 }, red: { x0: 70, x1: 72, y0: -72, y1: -49 } },
  flowers: [{ x: -68.6, y: -24, wall: 'back' }, { x: -24, y: 68.6, wall: 'blue' },
            { x: 24, y: -68.6, wall: 'red' }, { x: 68.6, y: 24, wall: 'audience' }],
  hive: { pivotZ: 43.95, blueY: 12.75, redY: -12.75, frameHalfW: 24.73, frameHalfD: 19.48,
          cellInner: 9.42, cellOuter: 21.46, cellHalfW: 10.0, cellFloorW: -1.36, cellSide: 7.61,
          cellPeak: 14.0, tiltDeg: 30 },
};

export const DEFAULT_ROBOT = { length: 17.6, width: 17.6, height: 17.0, wheelRadius: 2.05,
                               launchExitX: -2.0, launchExitZ: 15.0 };

export const COLORS = { blue: 0x2f6fe0, red: 0xd93a2b, pollen: 0xf2c81e, tapeBlue: 0x2a62d8, tapeRed: 0xd8362a };

// Field (x toward audience, y toward blue, z up) to three.js (X = y, Y = z, Z = x).
export function fv(x, y, z = 0) { return new T.Vector3(y, z, x); }

export const rad = (deg) => deg * Math.PI / 180;
export const deg = (r) => r * 180 / Math.PI;
export const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

const Z_AXIS = new T.Vector3(0, 0, 1);
const Y_AXIS = new T.Vector3(0, 1, 0);

// Square bar from a to b (three.js vectors in the parent's frame).
export function beam(a, b, w, h, mat) {
  const d = new T.Vector3().subVectors(b, a);
  const len = d.length();
  const m = new T.Mesh(new T.BoxGeometry(w, h, len), mat);
  m.position.copy(a).addScaledVector(d, 0.5);
  m.quaternion.setFromUnitVectors(Z_AXIS, d.normalize());
  return m;
}

// Round bar from a to b.
export function rod(a, b, r, mat, seg = 10) {
  const d = new T.Vector3().subVectors(b, a);
  const len = d.length();
  const m = new T.Mesh(new T.CylinderGeometry(r, r, len, seg), mat);
  m.position.copy(a).addScaledVector(d, 0.5);
  m.quaternion.setFromUnitVectors(Y_AXIS, d.normalize());
  return m;
}

export function box(w, h, d, mat, x = 0, y = 0, z = 0) {
  const m = new T.Mesh(new T.BoxGeometry(w, h, d), mat);
  m.position.set(x, y, z);
  return m;
}

// Cylinder whose axis runs along three.js X (robot left/right, field y).
export function cylX(r, len, mat, seg = 16) {
  const g = new T.CylinderGeometry(r, r, len, seg);
  g.rotateZ(Math.PI / 2);
  return new T.Mesh(g, mat);
}

export function lambert(color, extra = {}) { return new T.MeshLambertMaterial({ color, ...extra }); }
export function phong(color, extra = {}) { return new T.MeshPhongMaterial({ color, shininess: 60, ...extra }); }

// Canvas texture with one line of text.
export function textTexture(text, { w = 512, h = 128, bg = null, fg = '#ffffff', font = 'bold 96px sans-serif', border = null } = {}) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d');
  if (bg) { g.fillStyle = bg; g.fillRect(0, 0, w, h); }
  if (border) { g.strokeStyle = border; g.lineWidth = 10; g.strokeRect(5, 5, w - 10, h - 10); }
  g.fillStyle = fg; g.font = font; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(text, w / 2, h / 2 + 4);
  const tex = new T.CanvasTexture(c);
  tex.anisotropy = 4;
  return tex;
}

// Flat label lying on the floor, readable from the audience side.
export function floorLabel(text, widthIn, heightIn, color = '#c8d2de') {
  const tex = textTexture(text, { fg: color });
  const m = new T.Mesh(new T.PlaneGeometry(widthIn, heightIn),
    new T.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }));
  m.rotation.x = -Math.PI / 2;
  return m;
}

// Dot grid texture that reads as perforated plate or mesh.
export function perfTexture(base = '#2b2e33', hole = '#0c0d0f', cells = 8) {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  g.fillStyle = base; g.fillRect(0, 0, 128, 128);
  g.fillStyle = hole;
  const s = 128 / cells;
  for (let i = 0; i < cells; i++) for (let j = 0; j < cells; j++) {
    g.beginPath(); g.arc((i + 0.5) * s, (j + 0.5) * s, s * 0.28, 0, 6.3); g.fill();
  }
  const tex = new T.CanvasTexture(c);
  tex.wrapS = tex.wrapT = T.RepeatWrapping;
  return tex;
}

// "#RRGGBB", "#RRGGBBAA", "#RGB" or a CSS color name to { color, alpha }.
export function parseColor(str, fallback = '#ffffff') {
  let s = (typeof str === 'string' && str) ? str.trim() : fallback;
  let alpha = 1;
  if (/^#[0-9a-f]{8}$/i.test(s)) { alpha = parseInt(s.slice(7, 9), 16) / 255; s = s.slice(0, 7); }
  else if (/^#[0-9a-f]{4}$/i.test(s)) { alpha = parseInt(s[4] + s[4], 16) / 255; s = s.slice(0, 4); }
  let color;
  try { color = new T.Color(s); } catch (e) { color = new T.Color(fallback); }
  return { color, alpha };
}

export function disposeTree(obj) {
  obj.traverse((o) => {
    if (o.geometry) o.geometry.dispose();
    const mats = Array.isArray(o.material) ? o.material : (o.material ? [o.material] : []);
    for (const m of mats) { if (m.map) m.map.dispose(); m.dispose(); }
  });
  if (obj.parent) obj.parent.remove(obj);
}

export const $ = (id) => document.getElementById(id);
