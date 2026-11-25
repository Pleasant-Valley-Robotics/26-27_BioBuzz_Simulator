// HIVE Structure: two A-frames, crossbar with banner, and two seesaws with pentagon CELLs.
// Geometry follows docs/PROTOCOL.md "Geometry notes" exactly.
import { T, COLORS, beam, rod, box, lambert, phong, textTexture, rad } from './util.js';

// Seesaw-local frame used below: local X = field y (v), local Y = cell "up" (w),
// local Z = along the arm toward the AUDIENCE end. The seesaw group sits on the pivot
// and is rotated about X by phi, which maps local Z to (cos phi, 0, -sin phi) and
// local Y to (sin phi, 0, cos phi) in field (x, y, z): the protocol's directions.

function tagTexture(seed) {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const g = c.getContext('2d');
  g.fillStyle = '#fff'; g.fillRect(0, 0, 64, 64);
  g.fillStyle = '#000'; g.fillRect(6, 6, 52, 52);
  g.fillStyle = '#fff';
  let s = seed;
  for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    if ((s >> 8) & 1) g.fillRect(14 + i * 9, 14 + j * 9, 9, 9);
  }
  const tex = new T.CanvasTexture(c);
  tex.magFilter = T.NearestFilter;
  return tex;
}

function buildCell(h, dir, frameMat, panelMat, tagSeed) {
  const g = new T.Group();
  const hw = h.cellHalfW, f = h.cellFloorW;
  const pent = [[-hw, f], [hw, f], [hw, f + h.cellSide], [0, f + h.cellPeak], [-hw, f + h.cellSide]];
  const zin = dir * h.cellInner, zout = dir * h.cellOuter;

  // clear panels: floor, sides, roof and the inner end. The outer end stays open.
  const pos = [];
  const idx = [];
  for (let i = 0; i < 5; i++) {
    const [v0, w0] = pent[i], [v1, w1] = pent[(i + 1) % 5];
    const b = pos.length / 3;
    pos.push(v0, w0, zin, v1, w1, zin, v1, w1, zout, v0, w0, zout);
    idx.push(b, b + 1, b + 2, b, b + 2, b + 3);
  }
  const c0 = pos.length / 3;
  for (const [v, w] of pent) pos.push(v, w, zin);
  idx.push(c0, c0 + 1, c0 + 2, c0, c0 + 2, c0 + 3, c0, c0 + 3, c0 + 4);
  const geo = new T.BufferGeometry();
  geo.setAttribute('position', new T.Float32BufferAttribute(pos, 3));
  geo.setIndex(idx);
  geo.computeVertexNormals();
  const panels = new T.Mesh(geo, panelMat);
  panels.renderOrder = 2;
  g.add(panels);

  // colored frame along every edge
  const t = 0.7;
  for (let i = 0; i < 5; i++) {
    const [v0, w0] = pent[i], [v1, w1] = pent[(i + 1) % 5];
    for (const z of [zin, zout]) g.add(beam(new T.Vector3(v0, w0, z), new T.Vector3(v1, w1, z), t, t, frameMat));
    g.add(beam(new T.Vector3(v0, w0, zin), new T.Vector3(v0, w0, zout), t, t, frameMat));
  }

  // AprilTag sticker under the floor panel
  const tag = new T.Mesh(new T.PlaneGeometry(6, 6), new T.MeshBasicMaterial({ map: tagTexture(tagSeed) }));
  tag.rotation.x = Math.PI / 2;
  tag.position.set(0, f - 0.42, (zin + zout) / 2);
  g.add(tag);
  return g;
}

function buildSeesaw(h, color, tagSeed) {
  const g = new T.Group();
  const frameMat = phong(color);
  const panelMat = phong(0xdff1ff, { transparent: true, opacity: 0.17, side: T.DoubleSide, depthWrite: false });
  const armMat = phong(new T.Color(color).multiplyScalar(0.55).getHex());
  const armY = h.cellFloorW - 1.15;
  for (const sx of [-1, 1]) {
    g.add(box(1.5, 1.5, 2 * h.cellOuter, armMat, sx * (h.cellHalfW - 2.5), armY, 0));
  }
  g.add(box(2 * h.cellHalfW, 1.2, 1.5, armMat, 0, armY, 0));
  const hub = new T.Mesh(new T.CylinderGeometry(1.5, 1.5, 2 * h.cellHalfW - 2, 14), lambert(0x30343a));
  hub.rotation.z = Math.PI / 2;
  g.add(hub);
  g.add(buildCell(h, 1, frameMat, panelMat, tagSeed));
  g.add(buildCell(h, -1, frameMat, panelMat, tagSeed + 3));
  return g;
}

export function buildHive(F) {
  const h = F.hive;
  const group = new T.Group();
  const frame = phong(0x8a9199);
  const foot = lambert(0x25292e);

  for (const sx of [-1, 1]) {
    const X = sx * h.frameHalfW;
    const apex = new T.Vector3(X, h.pivotZ, 0);
    for (const sz of [-1, 1]) {
      group.add(beam(new T.Vector3(X, 0.6, sz * h.frameHalfD), apex, 1.6, 1.6, frame));
      group.add(box(4, 0.5, 5, foot, X, 0.25, sz * h.frameHalfD));
    }
    group.add(beam(new T.Vector3(X, 0.9, -h.frameHalfD), new T.Vector3(X, 0.9, h.frameHalfD), 1.6, 1.6, frame));
  }
  group.add(rod(new T.Vector3(-h.frameHalfW - 1, h.pivotZ, 0), new T.Vector3(h.frameHalfW + 1, h.pivotZ, 0), 0.9, frame, 12));

  // yellow banner on the crossbar, readable from both sides
  const tex = textTexture('BIOBUZZ', { w: 1024, h: 160, bg: '#f2c81e', fg: '#1a1a1a', font: 'bold 120px sans-serif' });
  const face = new T.MeshBasicMaterial({ map: tex });
  const edge = lambert(0xc9a516);
  const banner = new T.Mesh(new T.BoxGeometry(34, 5.2, 0.4), [edge, edge, edge, edge, face, face]);
  banner.position.set(0, h.pivotZ + 4.4, 0);
  group.add(banner);

  const blue = buildSeesaw(h, COLORS.blue, 11);
  blue.position.set(h.blueY, h.pivotZ, 0);
  const red = buildSeesaw(h, COLORS.red, 29);
  red.position.set(h.redY, h.pivotZ, 0);
  group.add(blue, red);

  function setPhi(blueDeg, redDeg) {
    blue.rotation.x = rad(blueDeg);
    red.rotation.x = rad(redDeg);
  }
  setPhi(h.tiltDeg, -h.tiltDeg);
  return { group, setPhi };
}

// Field position (x, y, z) of a point given in seesaw-local (u signed toward audience, v, w).
// Used by the mock to place balls that rest inside a CELL.
export function cellPoint(h, hiveY, phiDeg, uSigned, v, w) {
  const p = rad(phiDeg), c = Math.cos(p), s = Math.sin(p);
  return [uSigned * c + w * s, hiveY + v, h.pivotZ - uSigned * s + w * c];
}
