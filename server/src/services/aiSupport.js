// Persistence helpers for the AI cost guards and answer cache.
//
// Tables are created lazily so older databases keep working without a manual
// migration. Only validated answers are cached; nothing contains user identity.
import crypto from "node:crypto";

export const ANSWER_CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

const CREATE_TABLES = [
  "CREATE TABLE IF NOT EXISTS ai_answer_cache (",
  "  key TEXT PRIMARY KEY,",
  "  question TEXT NOT NULL,",
  "  language TEXT NOT NULL,",
  "  answer_json TEXT NOT NULL,",
  "  created_at TEXT NOT NULL,",
  "  expires_at TEXT NOT NULL",
  ");",
  "CREATE TABLE IF NOT EXISTS ai_usage (",
  "  day TEXT PRIMARY KEY,",
  "  calls INTEGER NOT NULL DEFAULT 0,",
  "  cost_micros INTEGER NOT NULL DEFAULT 0,",
  "  updated_at TEXT NOT NULL",
  ");",
].join("\n");

export const ensureAiTables = (db) => {
  db.exec(CREATE_TABLES);
};

const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");
const today = () => new Date().toISOString().slice(0, 10);
const monthPrefix = () => new Date().toISOString().slice(0, 7);

export const cacheKey = ({ question, language, verseKeys, version = "answer-v1" }) =>
  sha256([version, language, String(question || "").trim().toLowerCase(),
    [...verseKeys].sort().join(",")].join("|"));

export const getCachedAnswer = (db, key) => {
  try {
    ensureAiTables(db);
    const row = db.prepare("SELECT answer_json AS json, expires_at AS expiresAt FROM ai_answer_cache WHERE key = ?").get(key);
    if (!row) return null;
    if (Date.parse(row.expiresAt) < Date.now()) {
      db.prepare("DELETE FROM ai_answer_cache WHERE key = ?").run(key);
      return null;
    }
    return JSON.parse(row.json);
  } catch {
    return null;
  }
};

export const putCachedAnswer = (db, key, { question, language, payload }) => {
  try {
    ensureAiTables(db);
    const now = new Date();
    // The cache stores no user text: the key is a one-way hash and question is empty.
    db.prepare("INSERT OR REPLACE INTO ai_answer_cache (key, question, language, answer_json, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)")
      .run(key, "", language, JSON.stringify(payload),
        now.toISOString(), new Date(now.getTime() + ANSWER_CACHE_TTL_MS).toISOString());
  } catch {
    // cache is best-effort only
  }
};

const usageRow = (db) => {
  ensureAiTables(db);
  return db.prepare("SELECT calls, cost_micros AS costMicros FROM ai_usage WHERE day = ?").get(today()) || { calls: 0, costMicros: 0 };
};

const monthCostMicros = (db) => {
  ensureAiTables(db);
  const row = db.prepare("SELECT COALESCE(SUM(cost_micros), 0) AS total FROM ai_usage WHERE day LIKE ?").get(monthPrefix() + "-%");
  return Number(row && row.total) || 0;
};

/** Hard guards: daily answer count and monthly estimated spend. */
export const budgetAvailable = (db, dailyCap, monthlyBudgetUsd) => {
  try {
    const row = usageRow(db);
    const cap = Number.isFinite(dailyCap) ? dailyCap : 300;
    const budget = Number.isFinite(monthlyBudgetUsd) ? monthlyBudgetUsd : 25;
    if (Number(row.calls) >= cap) return { ok: false, reason: "daily", calls: Number(row.calls), cap };
    const spentUsd = monthCostMicros(db) / 1e6;
    if (budget > 0 && spentUsd >= budget) return { ok: false, reason: "budget", spentUsd, budget };
    return { ok: true, calls: Number(row.calls), cap, spentUsd, budget };
  } catch {
    return { ok: true };
  }
};

export const recordUsage = (db, costMicros) => {
  try {
    ensureAiTables(db);
    db.prepare("INSERT INTO ai_usage (day, calls, cost_micros, updated_at) VALUES (?, 1, ?, ?) ON CONFLICT(day) DO UPDATE SET calls = calls + 1, cost_micros = cost_micros + ?, updated_at = excluded.updated_at")
      .run(today(), Math.max(0, Math.round(costMicros)), new Date().toISOString(), Math.max(0, Math.round(costMicros)));
  } catch {
    // never block an answer on analytics
  }
};

export const usageSummary = (db) => {
  try {
    const row = usageRow(db);
    return { calls: Number(row.calls), costUsd: Number(monthCostMicros(db)) / 1e6 };
  } catch {
    return { calls: 0, costUsd: 0 };
  }
};
