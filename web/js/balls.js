// Every ball outside the robot, drawn from state.balls as instanced spheres.
import { T, phong, perfTexture, COLORS } from './util.js';

const KINDS = [
  { r: 1.4, color: COLORS.pollen },   // 0 POLLEN
  { r: 1.8, color: COLORS.red },      // 1 red NECTAR
  { r: 1.8, color: COLORS.blue },     // 2 blue NECTAR
];

export function buildBalls(max = 512) {
  const group = new T.Group();
  const geo = new T.SphereGeometry(1, 16, 12);
  const tex = perfTexture('#ffffff', '#8a8a8a', 4);   // wiffle ball holes
  tex.repeat.set(3, 2);
  const meshes = KINDS.map((k) => {
    const m = new T.InstancedMesh(geo, phong(k.color, { map: tex, shininess: 30 }), max);
    m.count = 0;
    m.frustumCulled = false;
    group.add(m);
    return m;
  });
  const mtx = new T.Matrix4();

  function update(balls) {
    const n = [0, 0, 0];
    for (const b of balls || []) {
      const k = b[1] | 0;
      if (k < 0 || k > 2 || n[k] >= max) continue;
      const r = KINDS[k].r;
      mtx.makeScale(r, r, r);
      mtx.setPosition(b[3], b[4], b[2]);   // field (x, y, z) to three (y, z, x)
      meshes[k].setMatrixAt(n[k]++, mtx);
    }
    for (let k = 0; k < 3; k++) {
      meshes[k].count = n[k];
      meshes[k].instanceMatrix.needsUpdate = true;
    }
  }
  return { group, update };
}
