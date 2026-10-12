'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pf-test-'));
process.env.PF_DATA_DIR = dataDir;
process.env.PF_ADMIN_PASSWORD = 'test-passwort-123';
const { server } = require('../server');

let base;
test.before(() => new Promise((resolve) => server.listen(0, '127.0.0.1', () => {
  base = `http://127.0.0.1:${server.address().port}`;
  resolve();
})));
test.after(() => {
  server.close();
  fs.rmSync(dataDir, { recursive: true, force: true });
});

// Minimaler Cookie-Speicher pro "Browser"
function client() {
  const jar = {};
  return async function req(method, url, { json, body, headers = {} } = {}) {
    const h = { ...headers, Cookie: Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ') };
    if (json) h['Content-Type'] = 'application/json';
    const res = await fetch(base + url, { method, headers: h, body: json ? JSON.stringify(json) : body });
    for (const c of res.headers.getSetCookie()) {
      const [kv] = c.split(';');
      const i = kv.indexOf('=');
      jar[kv.slice(0, i)] = kv.slice(i + 1);
    }
    return res;
  };
}

const PNG = Buffer.from('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082', 'hex');

function formData(fileIds) {
  return {
    products: [
      { id: 'pA', name: 'Edelstahl-Topf', articleNumber: 'ART-1' },
      { id: 'pB', name: 'Pfanne „Profi“ 28 cm', articleNumber: '' }
    ],
    answers: {
      pA: {
        vat: { value: 'nein', comment: 'Normaler Steuersatz' },
        delivery: { value: 'ja', days: '7', comment: '' },
        category: { value: 'Küche › Töpfe', comment: '' },
        supplier: { name: 'Muster GmbH', links: ['muster.de/topf', ''], access: 'store', comment: '' },
        price: { value: '49,90', comment: '' },
        media: { comment: 'Hauptbild = erstes Foto', links: ['https://example.com/video'], files: fileIds }
      },
      pB: {
        vat: { value: 'ja', comment: '' },
        delivery: { value: 'nein', comment: '' },
        category: { value: 'Küche › Pfannen', comment: '' },
        supplier: { name: 'Pfannen AG', links: [], access: 'share', shareNote: 'Freigabe per Passwort-Manager', comment: '' },
        price: { value: '1.299,00', comment: 'UVP' },
        media: { comment: '', links: [], files: [] }
      }
    },
    finalComment: 'Bitte bis Freitag einpflegen. Grüße & Dank!'
  };
}

test('kompletter Ablauf: Entwurf, Upload, Validierung, Absenden, PDF, Admin', async () => {
  const user = client();
  const stranger = client();
  const admin = client();

  let r = await user('POST', '/api/drafts');
  assert.equal(r.status, 201);

  // Upload
  r = await user('POST', '/api/files', { body: PNG, headers: { 'X-File-Name': encodeURIComponent('Produktfoto ä.png'), 'X-Product-Id': 'pA', 'Content-Type': 'image/png' } });
  assert.equal(r.status, 201);
  const { file } = await r.json();
  assert.equal(file.name, 'Produktfoto ä.png');
  assert.equal(file.image, true);

  // Falscher Inhalt fuer Endung wird abgelehnt
  r = await user('POST', '/api/files', { body: Buffer.from('kein pdf'), headers: { 'X-File-Name': 'x.pdf', 'X-Product-Id': 'pA' } });
  assert.equal(r.status, 415);
  // Nicht erlaubter Typ
  r = await user('POST', '/api/files', { body: Buffer.from('MZ'), headers: { 'X-File-Name': 'virus.exe', 'X-Product-Id': 'pA' } });
  assert.equal(r.status, 415);
  // Zu gross
  r = await user('POST', '/api/files', { body: Buffer.alloc(26 * 1024 * 1024, 0x20), headers: { 'X-File-Name': 'gross.txt', 'X-Product-Id': 'pA' } });
  assert.equal(r.status, 413);

  // Eigene Datei abrufbar, fremde nicht
  assert.equal((await user('GET', `/api/files/${file.id}`)).status, 200);
  assert.equal((await stranger('GET', `/api/files/${file.id}`)).status, 403);

  // Sitzung kennt die Datei (Wiederherstellung nach Neuladen)
  r = await user('GET', '/api/session');
  const sess = await r.json();
  assert.equal(sess.draft.files.length, 1);

  // Validierung: fehlende Tage / Zugangsdaten
  const bad = formData([file.id]);
  bad.answers.pA.delivery.days = '';
  r = await user('POST', '/api/submit', { json: { data: bad, credentials: {} } });
  assert.equal(r.status, 422);
  const v = await r.json();
  const fields = v.errors.map((e) => `${e.productId}.${e.section}.${e.field}`).sort();
  assert.deepEqual(fields, ['pA.delivery.days', 'pA.supplier.credentials']);

  // Absenden (mit Passwort im Zusatzfeld, das ignoriert werden muss)
  const good = formData([file.id]);
  good.answers.pA.supplier.password = 'SOLLTE-VERWORFEN-WERDEN';
  r = await user('POST', '/api/submit', { json: { data: good, credentials: { pA: { username: 'einkauf@firma.de', password: 'GeHeIm-4711', note: 'Kd-Nr 99' } } } });
  assert.equal(r.status, 200);
  const sub = await r.json();
  assert.match(sub.reference, /^PF-\d{8}-[A-Z0-9]{5}$/);

  // Nach dem Absenden keine Aenderungen mehr
  r = await user('POST', '/api/files', { body: PNG, headers: { 'X-File-Name': 'b.png', 'X-Product-Id': 'pA' } });
  assert.equal(r.status, 409);

  // PDF fuer Einreicher, ohne Zugangsdaten
  r = await user('GET', '/api/submission/pdf');
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-type'), 'application/pdf');
  const pdf = Buffer.from(await r.arrayBuffer());
  assert.equal(pdf.subarray(0, 5).toString(), '%PDF-');
  assert.ok(pdf.toString('latin1').includes('%%EOF'));
  assert.ok(!pdf.includes('GeHeIm-4711'));
  assert.equal((await stranger('GET', '/api/submission/pdf')).status, 401);

  // Datenbank enthaelt das Passwort nicht im Klartext
  const dbFile = fs.readFileSync(path.join(dataDir, 'produktformular.sqlite'));
  const wal = fs.existsSync(path.join(dataDir, 'produktformular.sqlite-wal')) ? fs.readFileSync(path.join(dataDir, 'produktformular.sqlite-wal')) : Buffer.alloc(0);
  assert.ok(!dbFile.includes('GeHeIm-4711') && !wal.includes('GeHeIm-4711'));
  assert.ok(!dbFile.includes('SOLLTE-VERWORFEN') && !wal.includes('SOLLTE-VERWORFEN'));

  // Admin
  assert.equal((await admin('GET', '/api/admin/submissions')).status, 401);
  assert.equal((await admin('POST', '/api/admin/login', { json: { password: 'falsch' } })).status, 401);
  assert.equal((await admin('POST', '/api/admin/login', { json: { password: 'test-passwort-123' } })).status, 200);
  r = await admin('GET', '/api/admin/submissions');
  const list = await r.json();
  assert.equal(list.submissions.length, 1);
  const id = list.submissions[0].id;
  r = await admin('GET', `/api/admin/submissions/${id}`);
  const detail = (await r.json()).submission;
  assert.equal(detail.data.products.length, 2);
  assert.deepEqual(detail.data.answers.pA.supplier.links, ['https://muster.de/topf']);
  assert.equal(detail.data.answers.pA.price.value, '49.90');
  assert.equal(detail.data.answers.pB.price.value, '1299.00');
  assert.ok(!JSON.stringify(detail).includes('GeHeIm'));
  assert.equal((await admin('GET', `/api/files/${file.id}`)).status, 200);
  r = await admin('POST', `/api/admin/submissions/${id}/credentials/pA`);
  assert.deepEqual((await r.json()).credentials, { username: 'einkauf@firma.de', password: 'GeHeIm-4711', note: 'Kd-Nr 99' });
  assert.equal((await stranger('POST', `/api/admin/submissions/${id}/credentials/pA`)).status, 401);
  assert.equal((await admin('GET', `/api/admin/submissions/${id}/pdf`)).status, 200);

  // Cross-Site-Anfragen werden abgelehnt
  r = await admin('POST', `/api/admin/submissions/${id}/credentials/pA`, { headers: { Origin: 'https://evil.example' } });
  assert.equal(r.status, 403);
});

test('Pfad-Traversal bei statischen Dateien wird verhindert', async () => {
  const r = await fetch(base + '/..%2fserver.js');
  assert.equal(r.status, 404);
  const ok = await fetch(base + '/');
  assert.equal(ok.status, 200);
  assert.match(ok.headers.get('content-security-policy'), /default-src 'self'/);
});
