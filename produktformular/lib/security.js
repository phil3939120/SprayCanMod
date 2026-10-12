'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function randomToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString('base64url');
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function safeEqualHex(a, b) {
  const ba = Buffer.from(String(a), 'hex');
  const bb = Buffer.from(String(b), 'hex');
  return ba.length === bb.length && ba.length > 0 && crypto.timingSafeEqual(ba, bb);
}

/*
 * Schluessel fuer die Verschluesselung der Zugangsdaten.
 * Bevorzugt aus PF_ENCRYPTION_KEY (32 Byte, hex oder base64), sonst wird
 * einmalig eine Schluesseldatei mit Dateirechten 0600 angelegt.
 */
function loadEncryptionKey(dataDir) {
  const env = process.env.PF_ENCRYPTION_KEY;
  if (env) {
    if (/^[0-9a-f]{64}$/i.test(env)) return Buffer.from(env, 'hex');
    const b64 = Buffer.from(env, 'base64');
    if (b64.length === 32) return b64;
    // Anderes Format (z. B. vom Hoster generierter Zufallswert): Schluessel ableiten
    if (env.length < 32) throw new Error('PF_ENCRYPTION_KEY ist zu kurz (mind. 32 Zeichen).');
    return crypto.createHash('sha256').update(env).digest();
  }
  const file = path.join(dataDir, 'secret.key');
  if (!fs.existsSync(file)) {
    fs.writeFileSync(file, crypto.randomBytes(32).toString('hex'), { mode: 0o600 });
    console.warn(`[sicherheit] Neuer Verschlüsselungsschlüssel erzeugt: ${file} – sicher aufbewahren bzw. PF_ENCRYPTION_KEY setzen.`);
  }
  return Buffer.from(fs.readFileSync(file, 'utf8').trim(), 'hex');
}

function encryptJson(key, obj, aad) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(aad));
  const ct = Buffer.concat([cipher.update(JSON.stringify(obj), 'utf8'), cipher.final()]);
  return { iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), ciphertext: ct.toString('base64') };
}

function decryptJson(key, rec, aad) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(rec.iv, 'base64'));
  decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(Buffer.from(rec.tag, 'base64'));
  const pt = Buffer.concat([decipher.update(Buffer.from(rec.ciphertext, 'base64')), decipher.final()]);
  return JSON.parse(pt.toString('utf8'));
}

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${hash}`;
}

function verifyPassword(password, stored) {
  const [salt, hash] = String(stored).split(':');
  if (!salt || !hash) return false;
  return safeEqualHex(crypto.scryptSync(String(password), salt, 64).toString('hex'), hash);
}

/* Prueft die ersten Bytes einer Datei gegen die erwartete Dateiart. */
function matchesSignature(ext, head) {
  const startsWith = (...bytes) => bytes.every((b, i) => head[i] === b);
  switch (ext) {
    case 'jpg':
    case 'jpeg':
      return startsWith(0xff, 0xd8, 0xff);
    case 'png':
      return startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
    case 'gif':
      return head.slice(0, 4).toString('latin1') === 'GIF8';
    case 'webp':
      return head.slice(0, 4).toString('latin1') === 'RIFF' && head.slice(8, 12).toString('latin1') === 'WEBP';
    case 'pdf':
      return head.slice(0, 5).toString('latin1') === '%PDF-';
    case 'docx':
    case 'xlsx':
    case 'pptx':
    case 'odt':
    case 'ods':
    case 'zip':
      return startsWith(0x50, 0x4b, 0x03, 0x04);
    case 'doc':
    case 'xls':
      return startsWith(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
    case 'mp4':
    case 'mov':
      return head.slice(4, 8).toString('latin1') === 'ftyp' || head.slice(4, 8).toString('latin1') === 'moov' || head.slice(4, 8).toString('latin1') === 'wide';
    case 'txt':
    case 'csv':
      return !head.includes(0);
    default:
      return false;
  }
}

module.exports = { randomToken, sha256, safeEqualHex, loadEncryptionKey, encryptJson, decryptJson, hashPassword, verifyPassword, matchesSignature };
