#!/usr/bin/env node
/**
 * Exports the validated dataset from the server database into compact TSV assets
 * that the native Android app reads at first run (fully offline).
 *
 * The app never parses JSON at runtime and never needs a network connection to
 * read or search the Quran.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..", "..");
const serverRoot = path.join(repoRoot, "server");
process.env.QURAN_DB_PATH = process.env.QURAN_DB_PATH || path.join(serverRoot, "data", "quran-app.db");

const { getDb } = await import(pathToFileURL(path.join(serverRoot, "src", "db.js")).href);
const { CONCEPTS } = await import(pathToFileURL(path.join(serverRoot, "src", "services", "lexicon.js")).href);

const db = getDb();
const assetsDir = path.join(here, "..", "app", "src", "main", "assets");
fs.mkdirSync(assetsDir, { recursive: true });

const write = (name, lines) => {
  const file = path.join(assetsDir, name);
  fs.writeFileSync(file, `${lines.join("\n")}\n`, "utf8");
  return { file, bytes: fs.statSync(file).size };
};

const chapters = db
  .prepare("SELECT id, name_arabic, name_simple, verses_count, revelation_place FROM chapters ORDER BY id")
  .all();

const verses = db
  .prepare("SELECT verse_key, text_uthmani FROM verses ORDER BY id")
  .all();

const translations = db
  .prepare("SELECT id, language_code, title, author, direction, license_status FROM translations ORDER BY language_code")
  .all();

const stats = [];
stats.push(write("chapters.tsv", chapters.map((row) => [row.id, row.name_arabic, row.name_simple, row.verses_count, row.revelation_place || ""].join("\t"))));
stats.push(write("quran.tsv", verses.map((row) => `${row.verse_key}\t${row.text_uthmani}`)));

const translationLines = [];
for (const translation of translations) {
  const rows = db
    .prepare(
      "SELECT v.verse_key, tv.text FROM translation_verses tv JOIN verses v ON v.id = tv.verse_id WHERE tv.translation_id = ? ORDER BY v.id"
    )
    .all(translation.id);
  for (const row of rows) {
    // Tabs and newlines cannot appear inside a translation row; normalise defensively.
    translationLines.push(`${translation.language_code}\t${row.verse_key}\t${String(row.text).replace(/[\t\r\n]+/g, " ")}`);
  }
}
stats.push(write("translations.tsv", translationLines));
stats.push(
  write(
    "translation-meta.tsv",
    translations.map((row) => [row.language_code, row.title, row.author || "", row.direction, row.license_status].join("\t"))
  )
);

// Curated search lexicon: key + one column per language (comma separated terms).
const languageColumns = ["ar", "en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"];
stats.push(
  write(
    "lexicon.tsv",
    CONCEPTS.map((concept) =>
      [concept.key, concept.arabic, concept.en, concept.ur, concept.id, concept.bn, concept.tr, concept.fa, concept.ms, concept.sw, concept.fr]
        .map((value) => (Array.isArray(value) ? value.join(",") : String(value || "")))
        .join("\t")
    )
  )
);

const provenance = db
  .prepare("SELECT dataset, version, source_url, checksum, imported_at FROM dataset_versions WHERE dataset = 'quran-uthmani' ORDER BY id DESC LIMIT 1")
  .get();
stats.push(
  write(
    "provenance.tsv",
    [
      `quran\t${provenance.dataset}\t${provenance.version}\t${provenance.source_url}\t${provenance.checksum}\t${provenance.imported_at}`,
      `imported-at\t${new Date().toISOString()}`,
      `verse-count\t${verses.length}`,
      `chapter-count\t${chapters.length}`,
      `translation-count\t${translations.length}`,
    ]
  )
);

// Structural mushaf metadata (juz / page / hizb). Generated from the public
// Quran.com API by mobile/android/scripts/fetchQuranMeta.js and committed under
// mobile/android/scripts/data/ so every build reproduces the same table.
const metaSource = path.join(here, "data", "quran-meta.tsv");
if (!fs.existsSync(metaSource)) {
  console.error("Missing structural metadata: " + metaSource);
  console.error("Run: node mobile/android/scripts/fetchQuranMeta.js");
  process.exit(1);
}
const metaLines = fs.readFileSync(metaSource, "utf8").trimEnd().split("\n");
stats.push(write("quran-meta.tsv", metaLines));

console.log("Exported assets:", stats.map((entry) => `${path.basename(entry.file)} ${Math.round(entry.bytes / 1024)}KB`).join(", "));
console.log(`Verses: ${verses.length}, chapters: ${chapters.length}, translations: ${translations.length}`);
