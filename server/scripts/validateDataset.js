#!/usr/bin/env node
/**
 * Re-validates the imported dataset: completeness, chapter counts, translation
 * completeness, provenance and checksum presence. Exit code 1 on any problem.
 */
import path from "node:path";
import { fileURLToPath } from "node:url";

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
process.env.QURAN_DB_PATH = process.env.QURAN_DB_PATH || path.join(serverRoot, "data", "quran-app.db");

const { getDb } = await import("../src/db.js");
const db = getDb();
const problems = [];
const check = (label, condition, detail) => {
  if (condition) console.log(`✔ ${label}`);
  else {
    console.log(`✖ ${label}${detail ? ` — ${detail}` : ""}`);
    problems.push(label);
  }
};

const chapters = db.prepare("SELECT COUNT(*) AS n FROM chapters").get().n;
const verses = db.prepare("SELECT COUNT(*) AS n FROM verses").get().n;
const translations = db.prepare("SELECT COUNT(*) AS n FROM translations").get().n;
check("114 chapters", chapters === 114, String(chapters));
check("6236 verses", verses === 6236, String(verses));
check("9 translations", translations === 9, String(translations));

const mismatched = db
  .prepare(
    `SELECT c.id, c.verses_count AS expected, COUNT(v.id) AS actual
     FROM chapters c LEFT JOIN verses v ON v.chapter_id = c.id
     GROUP BY c.id HAVING expected <> actual`
  )
  .all();
check("chapter verse counts match", mismatched.length === 0, JSON.stringify(mismatched));

for (const translation of db.prepare("SELECT id, language_code AS lang, license_status AS status FROM translations").all()) {
  const count = db.prepare("SELECT COUNT(*) AS n FROM translation_verses WHERE translation_id = ?").get(translation.id).n;
  check(`translation ${translation.lang} complete (${count})`, count === 6236, String(count));
  check(`translation ${translation.lang} licence status explicit`, ["verified", "unverified", "blocked"].includes(translation.status));
}

const provenance = db
  .prepare("SELECT checksum, source_url AS url FROM dataset_versions WHERE dataset = 'quran-uthmani' ORDER BY id DESC LIMIT 1")
  .get();
check("dataset checksum recorded", /^[a-f0-9]{64}$/.test(provenance?.checksum || ""), provenance?.checksum);
check("dataset source recorded", Boolean(provenance?.url), provenance?.url);

const markup = db.prepare("SELECT COUNT(*) AS n FROM translation_verses WHERE text GLOB '*<*'").get().n;
check("translations contain no markup", markup === 0, String(markup));

if (problems.length) {
  console.error(`\n${problems.length} validation problem(s)`);
  process.exit(1);
}
console.log("\nDataset validation passed.");
