// WebSocket link to the Java engine. Reconnects every second while the socket is down.
export function createNet(override) {
  const handlers = [];
  const statusCbs = [];
  let ws = null;
  let open = false;
  const host = location.hostname || 'localhost';
  const port = (parseInt(location.port, 10) || 8790) + 1;
  const url = override ? `ws://${override}` : `ws://${host}:${port}`;

  function setOpen(v) {
    if (open === v) return;
    open = v;
    for (const cb of statusCbs) cb(open);
  }

  function connect() {
    try {
      ws = new WebSocket(url);
    } catch (e) {
      setTimeout(connect, 1000);
      return;
    }
    ws.onopen = () => setOpen(true);
    ws.onclose = () => { setOpen(false); setTimeout(connect, 1000); };
    ws.onerror = () => { try { ws.close(); } catch (e) { /* already closed */ } };
    ws.onmessage = (ev) => {
      let m;
      try { m = JSON.parse(ev.data); } catch (e) { return; }
      if (!m || typeof m.type !== 'string') return;
      for (const h of handlers) {
        try { h(m); } catch (e) { console.error('handler failed for', m.type, e); }
      }
    };
  }

  return {
    url,
    isMock: false,
    start: connect,
    send(obj) { if (open && ws && ws.readyState === 1) ws.send(JSON.stringify(obj)); },
    onMessage(h) { handlers.push(h); },
    onStatus(cb) { statusCbs.push(cb); cb(open); },
  };
}
