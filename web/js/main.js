// Entry point: wires the engine link (or the mock), the 3D scene, the panels and input.
//   http://localhost:8790/          live engine (WebSocket on port + 1)
//   .../?mock=1                     no engine needed, fabricated data
//   .../?mock=1&broken=1            mock a compile failure
//   .../?ws=host:port               talk to an engine somewhere else
import { $ } from './util.js';
import { createNet } from './net.js';
import { createMock } from './mock.js';
import { createScene } from './scene.js';
import { createUI } from './ui.js';
import { createInput } from './input.js';

const params = new URLSearchParams(location.search);
const isMock = params.has('mock') && params.get('mock') !== '0';
const link = isMock ? createMock({ broken: params.has('broken') }) : createNet(params.get('ws'));

const scene = createScene($('view'));
const ui = createUI({ send: link.send, scene, isMock });
const input = createInput({ send: link.send, scene });

link.onStatus((open) => { ui.setConn(open, link.url); if (open) input.resync(); });
link.onMessage((m) => {
  switch (m.type) {
    case 'hello': scene.onHello(m); ui.onHello(m); break;
    case 'state': scene.onState(m); ui.onState(m); break;
    case 'telemetry': ui.onTelemetry(m); break;
    case 'overlay': scene.onOverlay(m); break;
    case 'event': ui.onEvent(m); break;
    case 'report': ui.onReport(m); break;
    default: break;
  }
});

function frame(t) {
  input.poll(t);
  scene.render(t);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
link.start();

window.sim = { scene, ui, input, link };   // handy in the browser console
