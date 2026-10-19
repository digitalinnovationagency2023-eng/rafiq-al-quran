// SQLite storage using Node's built-in driver. One file, no external service,
// and completely separate from any RQM Books database.
import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { config } from "./config.js";

const MIGRATIONS = [
  {
    id: 1,
    sql: `
      CREATE TABLE IF NOT EXISTS schema_migrations (
        id INTEGER PRIMARY KEY,
        applied_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS chapters (
        id INTEGER PRIMARY KEY,
        name_arabic TEXT NOT NULL,
        name_simple TEXT NOT NULL,
        name_english TEXT,
        verses_count INTEGER NOT NULL,
        revelation_place TEXT,
        revelation_order INTEGER
      );

      CREATE TABLE IF NOT EXISTS verses (
        id INTEGER PRIMARY KEY,
        chapter_id INTEGER NOT NULL REFERENCES chapters(id),
        verse_number INTEGER NOT NULL,
        verse_key TEXT NOT NULL UNIQUE,
        text_uthmani TEXT NOT NULL,
        text_plain TEXT NOT NULL,
        text_normalized TEXT NOT NULL,
        word_count INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_verses_chapter ON verses(chapter_id, verse_number);

      CREATE TABLE IF NOT EXISTS translations (
        id INTEGER PRIMARY KEY,
        provider TEXT NOT NULL,
        provider_id TEXT NOT NULL,
        slug TEXT,
        title TEXT NOT NULL,
        author TEXT,
        publisher TEXT,
        language_code TEXT NOT NULL,
        language_name TEXT NOT NULL,
        direction TEXT NOT NULL DEFAULT 'ltr',
        license_status TEXT NOT NULL DEFAULT 'unverified',
        license_note TEXT,
        source_url TEXT,
        imported_at TEXT NOT NULL,
        checksum TEXT,
        enabled INTEGER NOT NULL DEFAULT 1,
        UNIQUE(provider, provider_id)
      );

      CREATE TABLE IF NOT EXISTS translation_verses (
        translation_id INTEGER NOT NULL REFERENCES translations(id),
        verse_id INTEGER NOT NULL REFERENCES verses(id),
        text TEXT NOT NULL,
        PRIMARY KEY (translation_id, verse_id)
      );

      CREATE TABLE IF NOT EXISTS dataset_versions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        dataset TEXT NOT NULL,
        version TEXT NOT NULL,
        source_url TEXT,
        checksum TEXT,
        imported_at TEXT NOT NULL,
        validated_at TEXT,
        validation_ok INTEGER,
        verse_count INTEGER,
        chapter_count INTEGER,
        notes TEXT
      );

      CREATE TABLE IF NOT EXISTS audio_reciters (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL,
        style TEXT,
        provider TEXT,
        audio_url_template TEXT,
        rights_status TEXT NOT NULL DEFAULT 'unverified',
        enabled INTEGER NOT NULL DEFAULT 0,
        notes TEXT
      );

      CREATE TABLE IF NOT EXISTS conversations (
        id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL,
        language TEXT NOT NULL DEFAULT 'en',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_conversations_session ON conversations(session_id, updated_at);

      CREATE TABLE IF NOT EXISTS messages (
        id TEXT PRIMARY KEY,
        conversation_id TEXT NOT NULL REFERENCES conversations(id),
        role TEXT NOT NULL,
        text TEXT NOT NULL,
        language TEXT,
        payload_json TEXT,
        created_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);

      CREATE TABLE IF NOT EXISTS feedback (
        id TEXT PRIMARY KEY,
        category TEXT NOT NULL,
        description TEXT NOT NULL,
        language TEXT,
        verse_reference TEXT,
        contact TEXT,
        consent INTEGER NOT NULL DEFAULT 0,
        diagnostics_json TEXT,
        status TEXT NOT NULL DEFAULT 'open',
        severity TEXT NOT NULL DEFAULT 'normal',
        created_at TEXT NOT NULL,
        resolved_at TEXT,
        resolution_note TEXT
      );
      CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback(status, created_at);

      CREATE TABLE IF NOT EXISTS donations (
        id TEXT PRIMARY KEY,
        amount_minor INTEGER NOT NULL,
        currency TEXT NOT NULL,
        status TEXT NOT NULL,
        provider TEXT,
        provider_ref TEXT UNIQUE,
        recurring INTEGER NOT NULL DEFAULT 0,
        donor_email TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_donations_status ON donations(status, created_at);

      CREATE TABLE IF NOT EXISTS premium_features (
        key TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT,
        enabled INTEGER NOT NULL DEFAULT 0,
        price_minor INTEGER,
        currency TEXT,
        billing TEXT,
        sort_order INTEGER NOT NULL DEFAULT 0
      );

      CREATE TABLE IF NOT EXISTS entitlements (
        session_id TEXT NOT NULL,
        feature_key TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'active',
        provider_ref TEXT,
        created_at TEXT NOT NULL,
        expires_at TEXT,
        PRIMARY KEY (session_id, feature_key)
      );

      CREATE TABLE IF NOT EXISTS audit_log (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        at TEXT NOT NULL,
        actor TEXT,
        action TEXT NOT NULL,
        detail_json TEXT
      );

      CREATE TABLE IF NOT EXISTS settings (
        key TEXT PRIMARY KEY,
        value_json TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
    `,
  },
];

let database = null;

export const getDb = () => {
  if (database) return database;
  fs.mkdirSync(path.dirname(config.dbPath), { recursive: true });
  database = new DatabaseSync(config.dbPath);
  database.exec("PRAGMA journal_mode = WAL;");
  database.exec("PRAGMA foreign_keys = ON;");
  migrate(database);
  return database;
};

export const migrate = (db) => {
  db.exec(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id INTEGER PRIMARY KEY,
      applied_at TEXT NOT NULL
    );
  `);
  const applied = new Set(
    db.prepare("SELECT id FROM schema_migrations").all().map((row) => row.id)
  );
  for (const migration of MIGRATIONS) {
    if (applied.has(migration.id)) continue;
    db.exec("BEGIN");
    try {
      db.exec(migration.sql);
      db.prepare("INSERT INTO schema_migrations (id, applied_at) VALUES (?, ?)").run(
        migration.id,
        new Date().toISOString()
      );
      db.exec("COMMIT");
    } catch (error) {
      db.exec("ROLLBACK");
      throw error;
    }
  }
};

/** Test helper: a fresh in-memory database with the same schema. */
export const createMemoryDb = () => {
  const db = new DatabaseSync(":memory:");
  db.exec("PRAGMA foreign_keys = ON;");
  migrate(db);
  return db;
};

export const closeDb = () => {
  if (database) {
    database.close();
    database = null;
  }
};
