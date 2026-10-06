// Yakın sunucusu: numarayı kaydeder, çevrimiçi olanı bulur, mesaj ve ses kareleri iletir.
// Mesajları SAKLAMAZ; sadece o an bağlı olan kişiye iletir.
const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const server = http.createServer((req, res) => { res.writeHead(200); res.end('Yakin sunucusu calisiyor'); });
const wss = new WebSocketServer({ server, maxPayload: 1 << 20 });

const users = new Map();     // numara -> { ws, key, number, name, mood, color, watch:Set }
const watchers = new Map();  // numara -> Set(izleyen numaralar)
const NUM = /^\d{3} \d{3} \d{3}$/;

const send = (ws, o) => { if (ws && ws.readyState === 1) ws.send(JSON.stringify(o)); };
const pub = (u) => ({ number: u.number, name: u.name, mood: u.mood, color: u.color });

function notify(u, on) {
  const set = watchers.get(u.number); if (!set) return;
  for (const w of set) { const t = users.get(w); if (t) send(t.ws, on ? { t: 'online', ...pub(u) } : { t: 'offline', number: u.number }); }
}
function setWatch(me, numbers) {
  for (const n of me.watch) { const s = watchers.get(n); if (s) { s.delete(me.number); if (!s.size) watchers.delete(n); } }
  me.watch = new Set(numbers.filter((n) => typeof n === 'string' && NUM.test(n)).slice(0, 500));
  for (const n of me.watch) {
    if (!watchers.has(n)) watchers.set(n, new Set());
    watchers.get(n).add(me.number);
    const t = users.get(n); if (t) send(me.ws, { t: 'online', ...pub(t) });
  }
}

wss.on('connection', (ws) => {
  let me = null;
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data, isBinary) => {
    if (isBinary) { // ses: [uzunluk][hedef numara][pcm]  ->  [uzunluk][gönderen][pcm]
      if (!me || data.length < 2) return;
      const n = data[0]; const to = data.slice(1, 1 + n).toString();
      const t = users.get(to); if (!t || t.ws.readyState !== 1) return;
      const from = Buffer.from(me.number);
      t.ws.send(Buffer.concat([Buffer.from([from.length]), from, data.slice(1 + n)]), { binary: true });
      return;
    }
    let m; try { m = JSON.parse(data.toString()); } catch { return; }

    if (m.t === 'hello') {
      if (!NUM.test(m.number || '') || typeof m.key !== 'string' || m.key.length < 8) return;
      const old = users.get(m.number);
      if (old && old.key !== m.key && old.ws.readyState === 1) { send(ws, { t: 'taken' }); return; }
      if (old && old.ws !== ws) { try { old.ws.close(); } catch {} }
      me = { ws, key: m.key, number: m.number, name: String(m.name || '').slice(0, 24),
             mood: Number(m.mood) || 0, color: Number.isInteger(m.color) ? m.color : -1, watch: new Set() };
      users.set(me.number, me);
      send(ws, { t: 'ok' });
      notify(me, true);
      return;
    }
    if (!me) return;
    if (m.t === 'watch' && Array.isArray(m.numbers)) setWatch(me, m.numbers);
    else if (m.t === 'find' && typeof m.number === 'string') {
      const t = users.get(m.number);
      if (t) send(ws, { t: 'online', ...pub(t) }); else send(ws, { t: 'absent', number: m.number });
    } else if (m.t === 'msg' && typeof m.to === 'string') {
      const t = users.get(m.to);
      if (t) send(t.ws, { t: 'msg', from: me.number, body: String(m.body || '').slice(0, 20000) });
      else send(ws, { t: 'offline', number: m.to });
    }
  });

  ws.on('close', () => {
    if (!me) return;
    setWatch(me, []);
    if (users.get(me.number) && users.get(me.number).ws === ws) { users.delete(me.number); notify(me, false); }
  });
});

setInterval(() => {
  for (const ws of wss.clients) { if (!ws.isAlive) { ws.terminate(); continue; } ws.isAlive = false; ws.ping(); }
}, 30000);

server.listen(PORT, () => console.log('Yakin sunucusu ' + PORT + ' portunda'));
