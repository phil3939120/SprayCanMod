'use strict';
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

function openDatabase(dataDir) {
  const db = new DatabaseSync(path.join(dataDir, 'produktformular.sqlite'));
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS drafts (
      id TEXT PRIMARY KEY,
      token_hash TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'draft',
      reference TEXT UNIQUE,
      data_json TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      submitted_at TEXT
    );

    CREATE TABLE IF NOT EXISTS files (
      id TEXT PRIMARY KEY,
      draft_id TEXT NOT NULL REFERENCES drafts(id) ON DELETE CASCADE,
      product_id TEXT NOT NULL,
      original_name TEXT NOT NULL,
      ext TEXT NOT NULL,
      mime TEXT NOT NULL,
      size INTEGER NOT NULL,
      sha256 TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS files_draft ON files(draft_id);

    -- Zugangsdaten: AES-256-GCM verschluesselt, getrennt von den Formulardaten
    CREATE TABLE IF NOT EXISTS credentials (
      draft_id TEXT NOT NULL REFERENCES drafts(id) ON DELETE CASCADE,
      product_id TEXT NOT NULL,
      iv TEXT NOT NULL,
      tag TEXT NOT NULL,
      ciphertext TEXT NOT NULL,
      created_at TEXT NOT NULL,
      PRIMARY KEY (draft_id, product_id)
    );

    CREATE TABLE IF NOT EXISTS admin_sessions (
      token_hash TEXT PRIMARY KEY,
      expires_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS audit_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      at TEXT NOT NULL,
      action TEXT NOT NULL,
      draft_id TEXT,
      detail TEXT,
      ip TEXT
    );
  `);
  return db;
}

module.exports = { openDatabase };
