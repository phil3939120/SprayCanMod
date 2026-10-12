'use strict';
/*
 * Produktformular – Backend ohne externe Abhaengigkeiten.
 * Node.js >= 22.5 (eingebautes node:sqlite).
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const Schema = require('./public/js/schema');
const { openDatabase } = require('./lib/db');
const sec = require('./lib/security');
const { buildSubmissionPdf } = require('./lib/report');

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '0.0.0.0';
const DATA_DIR = path.resolve(process.env.PF_DATA_DIR || path.join(__dirname, 'data'));
const UPLOAD_DIR = path.join(DATA_DIR, 'uploads');
const PUBLIC_DIR = path.join(__dirname, 'public');
const SECURE_COOKIES = process.env.PF_SECURE_COOKIES === '1';
// Hinter einem Reverse-Proxy (Render, Railway, nginx …) die echte Client-IP aus X-Forwarded-For lesen
const TRUST_PROXY = process.env.PF_TRUST_PROXY === '1';
const DRAFT_TTL_DAYS = Number(process.env.PF_DRAFT_TTL_DAYS || 30);
const ADMIN_SESSION_HOURS = 8;
const MAX_JSON_BYTES = 5 * 1024 * 1024;

fs.mkdirSync(UPLOAD_DIR, { recursive: true, mode: 0o700 });
const db = openDatabase(DATA_DIR);
const ENC_KEY = sec.loadEncryptionKey(DATA_DIR);

const now = () => new Date().toISOString();

/* ---------- Admin-Passwort ---------- */
function initAdminPassword() {
  const envPw = process.env.PF_ADMIN_PASSWORD;
  if (envPw) {
    if (envPw.length < 10) throw new Error('PF_ADMIN_PASSWORD muss mindestens 10 Zeichen lang sein.');
    return sec.hashPassword(envPw);
  }
  const row = db.prepare("SELECT value FROM settings WHERE key = 'admin_password'").get();
  if (row) return row.value;
  const generated = sec.randomToken(12);
  const hash = sec.hashPassword(generated);
  db.prepare("INSERT INTO settings (key, value) VALUES ('admin_password', ?)").run(hash);
  console.log('\n==============================================================');
  console.log(' Admin-Zugang wurde eingerichtet. Passwort (nur jetzt sichtbar):');
  console.log(`   ${generated}`);
  console.log(' Alternativ PF_ADMIN_PASSWORD als Umgebungsvariable setzen.');
  console.log('==============================================================\n');
  return hash;
}
const ADMIN_PASSWORD_HASH = initAdminPassword();

/* ---------- Hilfsfunktionen ---------- */
class HttpError extends Error {
  constructor(status, message, extra) {
    super(message);
    this.status = status;
    this.extra = extra;
  }
}

function audit(action, draftId, detail, req) {
  db.prepare('INSERT INTO audit_log (at, action, draft_id, detail, ip) VALUES (?, ?, ?, ?, ?)').run(now(), action, draftId || null, detail || null, req ? clientIp(req) : null);
}

function clientIp(req) {
  if (TRUST_PROXY && req.headers['x-forwarded-for']) return String(req.headers['x-forwarded-for']).split(',')[0].trim();
  return req.socket.remoteAddress || '';
}

function parseCookies(req) {
  const out = {};
  for (const part of String(req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

function cookie(name, value, maxAgeSec) {
  return `${name}=${encodeURIComponent(value)}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAgeSec}${SECURE_COOKIES ? '; Secure' : ''}`;
}

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Content-Security-Policy': "default-src 'self'; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
};

function send(res, status, body, headers = {}) {
  res.writeHead(status, { ...SECURITY_HEADERS, ...headers });
  res.end(body);
}

function json(res, status, obj, headers = {}) {
  send(res, status, JSON.stringify(obj), { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
}

async function readJson(req) {
  if (!String(req.headers['content-type'] || '').includes('application/json')) throw new HttpError(415, 'JSON erwartet.');
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_JSON_BYTES) throw new HttpError(413, 'Die Anfrage ist zu groß.');
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
  } catch {
    throw new HttpError(400, 'Ungültige Daten.');
  }
}

function contentDisposition(type, filename) {
  const ascii = filename.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  return `${type}; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(filename)}`;
}

/* ---------- Authentifizierung ---------- */
// Entwurf des aktuellen Nutzers (Cookie "pf_draft" = <id>.<token>)
function currentDraft(req) {
  const raw = parseCookies(req).pf_draft;
  if (!raw) return null;
  const [id, token] = raw.split('.');
  if (!id || !token) return null;
  const row = db.prepare('SELECT * FROM drafts WHERE id = ?').get(id);
  if (!row || !sec.safeEqualHex(sec.sha256(token), row.token_hash)) return null;
  return row;
}

function requireDraft(req, { editable = false } = {}) {
  const d = currentDraft(req);
  if (!d) throw new HttpError(401, 'Ihre Sitzung ist abgelaufen. Bitte laden Sie die Seite neu.');
  if (editable && d.status !== 'draft') throw new HttpError(409, 'Dieses Formular wurde bereits abgesendet.');
  return d;
}

function isAdmin(req) {
  const token = parseCookies(req).pf_admin;
  if (!token) return false;
  const row = db.prepare('SELECT expires_at FROM admin_sessions WHERE token_hash = ?').get(sec.sha256(token));
  return !!row && row.expires_at > Date.now();
}

function requireAdmin(req) {
  if (!isAdmin(req)) throw new HttpError(401, 'Bitte melden Sie sich an.');
}

const loginAttempts = new Map(); // ip -> { count, until }
function checkLoginRate(ip) {
  const e = loginAttempts.get(ip);
  if (e && e.count >= 5 && e.until > Date.now()) {
    throw new HttpError(429, 'Zu viele Anmeldeversuche. Bitte warten Sie 15 Minuten.');
  }
}
function failLogin(ip) {
  const e = loginAttempts.get(ip);
  if (!e || e.until < Date.now()) loginAttempts.set(ip, { count: 1, until: Date.now() + 15 * 60 * 1000 });
  else e.count++;
}

/* ---------- Entwuerfe & Dateien ---------- */
function fileMeta(row) {
  return { id: row.id, productId: row.product_id, name: row.original_name, size: row.size, mime: row.mime, ext: row.ext, image: !!(Schema.FILE_TYPES[row.ext] || {}).image };
}

function draftFiles(draftId) {
  return db.prepare('SELECT * FROM files WHERE draft_id = ? ORDER BY created_at').all(draftId);
}

function deleteStoredFile(id) {
  fs.rm(path.join(UPLOAD_DIR, id), { force: true }, () => {});
}

function deleteDraftCompletely(draftId) {
  for (const f of draftFiles(draftId)) deleteStoredFile(f.id);
  db.prepare('DELETE FROM drafts WHERE id = ?').run(draftId);
}

function createDraft(res) {
  const id = crypto.randomUUID();
  const token = sec.randomToken();
  const t = now();
  db.prepare('INSERT INTO drafts (id, token_hash, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)').run(id, sec.sha256(token), 'draft', t, t);
  res.setHeader('Set-Cookie', cookie('pf_draft', `${id}.${token}`, 60 * 60 * 24 * 365));
  return id;
}

async function handleUpload(req, res) {
  const draft = requireDraft(req, { editable: true });
  let name;
  try {
    name = decodeURIComponent(String(req.headers['x-file-name'] || ''));
  } catch {
    throw new HttpError(400, 'Ungültiger Dateiname.');
  }
  name = path.basename(name).replace(/[\u0000-\u001f]/g, '').slice(0, 200);
  const productId = String(req.headers['x-product-id'] || '');
  if (!name) throw new HttpError(400, 'Dateiname fehlt.');
  if (!/^[a-zA-Z0-9_-]{1,40}$/.test(productId)) throw new HttpError(400, 'Produktzuordnung fehlt.');
  const ext = Schema.extOf(name);
  const type = Schema.FILE_TYPES[ext];
  if (!type) throw new HttpError(415, `Dateityp „.${ext || '?'}“ wird nicht unterstützt.`);
  const declared = Number(req.headers['content-length'] || 0);
  if (declared > Schema.LIMITS.maxFileBytes) throw new HttpError(413, `Die Datei ist größer als ${Schema.formatBytes(Schema.LIMITS.maxFileBytes)}.`);
  const count = db.prepare('SELECT COUNT(*) AS n FROM files WHERE draft_id = ?').get(draft.id).n;
  if (count >= Schema.LIMITS.maxFilesPerDraft) throw new HttpError(413, 'Maximale Anzahl an Dateien erreicht.');

  const id = crypto.randomBytes(16).toString('hex');
  const tmp = path.join(UPLOAD_DIR, `.tmp-${id}`);
  const hash = crypto.createHash('sha256');
  let size = 0;
  let head = Buffer.alloc(0);
  const tooLarge = () => new HttpError(413, `Die Datei ist größer als ${Schema.formatBytes(Schema.LIMITS.maxFileBytes)}.`);
  try {
    await new Promise((resolve, reject) => {
      const out = fs.createWriteStream(tmp, { mode: 0o600 });
      let failed = false;
      const fail = (e) => {
        if (failed) return;
        failed = true;
        out.destroy();
        reject(e);
      };
      req.on('data', (chunk) => {
        if (failed) return;
        size += chunk.length;
        if (size > Schema.LIMITS.maxFileBytes) return fail(tooLarge());
        if (head.length < 16) head = Buffer.concat([head, chunk.subarray(0, 16 - head.length)]);
        hash.update(chunk);
        if (!out.write(chunk)) {
          req.pause();
          out.once('drain', () => req.resume());
        }
      });
      req.on('end', () => !failed && out.end());
      req.on('error', () => fail(new HttpError(400, 'Upload abgebrochen.')));
      req.on('close', () => !req.complete && fail(new HttpError(400, 'Upload abgebrochen.')));
      out.on('error', fail);
      out.on('finish', () => !failed && resolve());
    });
    if (size === 0) throw new HttpError(400, 'Die Datei ist leer.');
    if (!sec.matchesSignature(ext, head)) throw new HttpError(415, `Der Inhalt passt nicht zur Dateiendung „.${ext}“.`);
    fs.renameSync(tmp, path.join(UPLOAD_DIR, id));
  } catch (e) {
    fs.rm(tmp, { force: true }, () => {});
    if (e instanceof HttpError) {
      res.setHeader('Connection', 'close');
      throw e;
    }
    throw new HttpError(400, 'Upload abgebrochen.');
  }
  db.prepare('INSERT INTO files (id, draft_id, product_id, original_name, ext, mime, size, sha256, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)')
    .run(id, draft.id, productId, name, ext, type.mime, size, hash.digest('hex'), now());
  db.prepare('UPDATE drafts SET updated_at = ? WHERE id = ?').run(now(), draft.id);
  json(res, 201, { file: fileMeta(db.prepare('SELECT * FROM files WHERE id = ?').get(id)) });
}

function serveFile(req, res, fileId, url) {
  if (!/^[a-f0-9]{32}$/.test(fileId)) throw new HttpError(404, 'Datei nicht gefunden.');
  const row = db.prepare('SELECT * FROM files WHERE id = ?').get(fileId);
  if (!row) throw new HttpError(404, 'Datei nicht gefunden.');
  const draft = currentDraft(req);
  const allowed = isAdmin(req) || (draft && draft.id === row.draft_id);
  if (!allowed) throw new HttpError(403, 'Kein Zugriff auf diese Datei.');
  const p = path.join(UPLOAD_DIR, row.id);
  if (!fs.existsSync(p)) throw new HttpError(404, 'Datei nicht gefunden.');
  const inline = (Schema.FILE_TYPES[row.ext] || {}).inline && url.searchParams.get('download') !== '1';
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    'Content-Security-Policy': "default-src 'none'; sandbox",
    'Content-Type': row.mime,
    'Content-Length': row.size,
    'Content-Disposition': contentDisposition(inline ? 'inline' : 'attachment', row.original_name),
    'Cache-Control': 'private, no-store'
  });
  fs.createReadStream(p).pipe(res);
}

/* ---------- Absenden ---------- */
function makeReference() {
  const d = new Date();
  const date = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let r = '';
  for (const b of crypto.randomBytes(5)) r += alphabet[b % alphabet.length];
  return `PF-${date}-${r}`;
}

async function handleSubmit(req, res) {
  const draft = requireDraft(req, { editable: true });
  const body = await readJson(req);
  const data = Schema.normalize(body.data);
  const rawCreds = body.credentials && typeof body.credentials === 'object' ? body.credentials : {};
  const errors = Schema.validate(data, rawCreds);
  if (errors.length) throw new HttpError(422, 'Bitte prüfen Sie die markierten Angaben.', { errors });

  // Dateien: nur eigene Dateien zulassen, Zuordnung aus der Datenbank uebernehmen
  const files = draftFiles(draft.id);
  const owned = new Map(files.map((f) => [f.id, f]));
  const used = new Set();
  for (const p of data.products) {
    const media = data.answers[p.id].media;
    media.files = media.files.filter((id) => owned.has(id) && !used.has(id) && used.add(id));
    for (const id of media.files) {
      if (owned.get(id).product_id !== p.id) db.prepare('UPDATE files SET product_id = ? WHERE id = ?').run(p.id, id);
    }
  }
  // Links normalisieren, leere entfernen
  for (const p of data.products) {
    const a = data.answers[p.id];
    a.supplier.links = a.supplier.links.map(Schema.normalizeUrl).filter(Boolean);
    a.media.links = a.media.links.map(Schema.normalizeUrl).filter(Boolean);
    const price = Schema.parsePrice(a.price.value);
    a.price.value = price.toFixed(2);
    if (a.delivery.value === 'ja') a.delivery.days = String(Number(a.delivery.days));
  }

  const t = now();
  let reference;
  db.exec('BEGIN');
  try {
    db.prepare('DELETE FROM credentials WHERE draft_id = ?').run(draft.id);
    for (const p of data.products) {
      if (data.answers[p.id].supplier.access !== 'store') continue;
      const c = rawCreds[p.id] || {};
      const plain = {
        username: String(c.username || '').slice(0, 500),
        password: String(c.password || '').slice(0, 500),
        note: String(c.note || '').slice(0, 2000)
      };
      const enc = sec.encryptJson(ENC_KEY, plain, `${draft.id}:${p.id}`);
      db.prepare('INSERT INTO credentials (draft_id, product_id, iv, tag, ciphertext, created_at) VALUES (?, ?, ?, ?, ?, ?)').run(draft.id, p.id, enc.iv, enc.tag, enc.ciphertext, t);
    }
    for (let i = 0; i < 5 && !reference; i++) {
      const candidate = makeReference();
      if (!db.prepare('SELECT 1 FROM drafts WHERE reference = ?').get(candidate)) reference = candidate;
    }
    db.prepare("UPDATE drafts SET status = 'submitted', reference = ?, data_json = ?, updated_at = ?, submitted_at = ? WHERE id = ?").run(reference, JSON.stringify(data), t, t, draft.id);
    db.exec('COMMIT');
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
  // Nicht zugeordnete Dateien (z. B. von entfernten Produkten) loeschen
  for (const f of files) {
    if (!used.has(f.id)) {
      db.prepare('DELETE FROM files WHERE id = ?').run(f.id);
      deleteStoredFile(f.id);
    }
  }
  audit('submit', draft.id, reference, req);
  json(res, 200, { reference, submittedAt: t });
}

function loadSubmission(id) {
  const row = db.prepare("SELECT * FROM drafts WHERE id = ? AND status = 'submitted'").get(id);
  if (!row) throw new HttpError(404, 'Einreichung nicht gefunden.');
  const files = draftFiles(id).map((f) => ({ id: f.id, productId: f.product_id, originalName: f.original_name, size: f.size, mime: f.mime, ext: f.ext }));
  const credentialProducts = new Set(db.prepare('SELECT product_id FROM credentials WHERE draft_id = ?').all(id).map((r) => r.product_id));
  return { id: row.id, reference: row.reference, submittedAt: row.submitted_at, data: JSON.parse(row.data_json), files, credentialProducts };
}

function sendPdf(res, submission) {
  const pdf = buildSubmissionPdf(submission);
  send(res, 200, pdf, {
    'Content-Type': 'application/pdf',
    'Content-Length': pdf.length,
    'Content-Disposition': contentDisposition('attachment', `Produkterfassung-${submission.reference}.pdf`),
    'Cache-Control': 'private, no-store'
  });
}

/* ---------- Statische Dateien ---------- */
const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.png': 'image/png' };

function serveStatic(req, res, pathname) {
  if (pathname === '/') pathname = '/index.html';
  if (pathname === '/admin') pathname = '/admin.html';
  const file = path.normalize(path.join(PUBLIC_DIR, pathname));
  if (!file.startsWith(PUBLIC_DIR + path.sep)) throw new HttpError(404, 'Nicht gefunden.');
  let stat;
  try {
    stat = fs.statSync(file);
  } catch {
    throw new HttpError(404, 'Nicht gefunden.');
  }
  if (!stat.isFile()) throw new HttpError(404, 'Nicht gefunden.');
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
    'Content-Length': stat.size,
    'Cache-Control': 'no-cache'
  });
  if (req.method === 'HEAD') return res.end();
  fs.createReadStream(file).pipe(res);
}

/* ---------- Routing ---------- */
async function route(req, res) {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;
  const m = req.method;

  // Schutz vor Cross-Site-Anfragen (zusaetzlich zu SameSite=Strict)
  if (m !== 'GET' && m !== 'HEAD' && req.headers.origin) {
    let originHost = '';
    try {
      originHost = new URL(req.headers.origin).host;
    } catch {}
    if (originHost !== req.headers.host) throw new HttpError(403, 'Anfrage nicht erlaubt.');
  }

  if (!p.startsWith('/api/')) {
    if (m !== 'GET' && m !== 'HEAD') throw new HttpError(405, 'Methode nicht erlaubt.');
    return serveStatic(req, res, p);
  }

  let mm;
  // --- Nutzer ---
  if (p === '/api/session' && m === 'GET') {
    const d = currentDraft(req);
    if (!d) return json(res, 200, { draft: null });
    return json(res, 200, {
      draft: { id: d.id, status: d.status, reference: d.reference, submittedAt: d.submitted_at, files: d.status === 'draft' ? draftFiles(d.id).map(fileMeta) : [] }
    });
  }
  if (p === '/api/drafts' && m === 'POST') {
    const id = createDraft(res);
    return json(res, 201, { draft: { id, status: 'draft', files: [] } });
  }
  if (p === '/api/files' && m === 'POST') return handleUpload(req, res);
  if ((mm = /^\/api\/files\/([^/]+)$/.exec(p))) {
    if (m === 'GET') return serveFile(req, res, mm[1], url);
    if (m === 'DELETE') {
      const draft = requireDraft(req, { editable: true });
      const row = db.prepare('SELECT id FROM files WHERE id = ? AND draft_id = ?').get(mm[1], draft.id);
      if (!row) throw new HttpError(404, 'Datei nicht gefunden.');
      db.prepare('DELETE FROM files WHERE id = ?').run(row.id);
      deleteStoredFile(row.id);
      return json(res, 200, { ok: true });
    }
  }
  if (p === '/api/submit' && m === 'POST') return handleSubmit(req, res);
  if (p === '/api/submission/pdf' && m === 'GET') {
    const d = requireDraft(req);
    if (d.status !== 'submitted') throw new HttpError(409, 'Das Formular wurde noch nicht abgesendet.');
    return sendPdf(res, loadSubmission(d.id));
  }

  // --- Admin ---
  if (p === '/api/admin/login' && m === 'POST') {
    const ip = clientIp(req);
    checkLoginRate(ip);
    const body = await readJson(req);
    if (!sec.verifyPassword(String(body.password || ''), ADMIN_PASSWORD_HASH)) {
      failLogin(ip);
      audit('admin_login_failed', null, null, req);
      throw new HttpError(401, 'Das Passwort ist nicht korrekt.');
    }
    loginAttempts.delete(ip);
    const token = sec.randomToken();
    db.prepare('DELETE FROM admin_sessions WHERE expires_at < ?').run(Date.now());
    db.prepare('INSERT INTO admin_sessions (token_hash, expires_at) VALUES (?, ?)').run(sec.sha256(token), Date.now() + ADMIN_SESSION_HOURS * 3600 * 1000);
    audit('admin_login', null, null, req);
    return json(res, 200, { ok: true }, { 'Set-Cookie': cookie('pf_admin', token, ADMIN_SESSION_HOURS * 3600) });
  }
  if (p === '/api/admin/logout' && m === 'POST') {
    const token = parseCookies(req).pf_admin;
    if (token) db.prepare('DELETE FROM admin_sessions WHERE token_hash = ?').run(sec.sha256(token));
    return json(res, 200, { ok: true }, { 'Set-Cookie': cookie('pf_admin', '', 0) });
  }
  if (p === '/api/admin/me' && m === 'GET') return json(res, 200, { admin: isAdmin(req) });

  if (p.startsWith('/api/admin/')) requireAdmin(req);

  if (p === '/api/admin/submissions' && m === 'GET') {
    const rows = db.prepare("SELECT id, reference, submitted_at, data_json FROM drafts WHERE status = 'submitted' ORDER BY submitted_at DESC").all();
    const counts = new Map(db.prepare('SELECT draft_id, COUNT(*) AS n FROM files GROUP BY draft_id').all().map((r) => [r.draft_id, r.n]));
    return json(res, 200, {
      submissions: rows.map((r) => {
        const data = JSON.parse(r.data_json);
        return {
          id: r.id,
          reference: r.reference,
          submittedAt: r.submitted_at,
          productCount: data.products.length,
          productNames: data.products.map((x) => x.name).filter(Boolean).slice(0, 5),
          fileCount: counts.get(r.id) || 0
        };
      })
    });
  }
  if ((mm = /^\/api\/admin\/submissions\/([0-9a-f-]{36})$/.exec(p))) {
    if (m === 'GET') {
      const s = loadSubmission(mm[1]);
      audit('admin_view', s.id, null, req);
      return json(res, 200, { submission: { ...s, credentialProducts: [...s.credentialProducts] } });
    }
    if (m === 'DELETE') {
      const s = loadSubmission(mm[1]);
      deleteDraftCompletely(s.id);
      audit('admin_delete', s.id, s.reference, req);
      return json(res, 200, { ok: true });
    }
  }
  if ((mm = /^\/api\/admin\/submissions\/([0-9a-f-]{36})\/pdf$/.exec(p)) && m === 'GET') {
    return sendPdf(res, loadSubmission(mm[1]));
  }
  if ((mm = /^\/api\/admin\/submissions\/([0-9a-f-]{36})\/credentials\/([a-zA-Z0-9_-]{1,40})$/.exec(p)) && m === 'POST') {
    const rec = db.prepare('SELECT * FROM credentials WHERE draft_id = ? AND product_id = ?').get(mm[1], mm[2]);
    if (!rec) throw new HttpError(404, 'Keine Zugangsdaten hinterlegt.');
    audit('credentials_reveal', mm[1], mm[2], req);
    return json(res, 200, { credentials: sec.decryptJson(ENC_KEY, rec, `${mm[1]}:${mm[2]}`) });
  }

  throw new HttpError(404, 'Nicht gefunden.');
}

const server = http.createServer(async (req, res) => {
  try {
    await route(req, res);
  } catch (e) {
    if (!(e instanceof HttpError)) console.error(e);
    const status = e instanceof HttpError ? e.status : 500;
    const message = e instanceof HttpError ? e.message : 'Interner Fehler. Bitte versuchen Sie es erneut.';
    if (res.headersSent) return res.destroy();
    json(res, status, { error: message, ...(e.extra || {}) });
    // Restliche Upload-Daten verwerfen
    req.resume();
  }
});

/* ---------- Aufraeumen alter, nie abgesendeter Entwuerfe ---------- */
function cleanup() {
  const cutoff = new Date(Date.now() - DRAFT_TTL_DAYS * 86400 * 1000).toISOString();
  for (const d of db.prepare("SELECT id FROM drafts WHERE status = 'draft' AND updated_at < ?").all(cutoff)) deleteDraftCompletely(d.id);
  db.prepare('DELETE FROM admin_sessions WHERE expires_at < ?').run(Date.now());
  for (const name of fs.readdirSync(UPLOAD_DIR)) {
    if (name.startsWith('.tmp-')) {
      const full = path.join(UPLOAD_DIR, name);
      if (Date.now() - fs.statSync(full).mtimeMs > 3600 * 1000) fs.rmSync(full, { force: true });
    }
  }
}
cleanup();
setInterval(cleanup, 6 * 3600 * 1000).unref();

if (require.main === module) {
  server.listen(PORT, HOST, () => console.log(`Produktformular läuft auf http://localhost:${PORT}  (Admin: /admin)`));
}

module.exports = { server };
