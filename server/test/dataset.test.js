// Dataset integrity: completeness, exact text and provenance metadata.
import assert from "node:assert/strict";
import test from "node:test";
import { prepareTestDb } from "./helpers.js";

prepareTestDb();

const { getDb } = await import("../src/db.js");
const { buildIndex, getVerseByKey } = await import("../src/services/retrieval.js");
const { normalizeArabic } = await import("../src/services/arabic.js");

const db = getDb();
const index = buildIndex(db);

test("the dataset contains all 114 chapters and 6236 verses", () => {
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM chapters").get().n, 114);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM verses").get().n, 6236);
  assert.equal(index.size, 6236);
  assert.equal(index.chapters.size, 114);
});

test("every chapter verse count matches the canonical metadata", () => {
  const mismatched = db
    .prepare(
      `SELECT c.id, c.verses_count AS expected, COUNT(v.id) AS actual
       FROM chapters c LEFT JOIN verses v ON v.chapter_id = c.id
       GROUP BY c.id HAVING expected <> actual`
    )
    .all();
  assert.deepEqual(mismatched, []);
});

test("verse keys are unique, ordered and well formed", () => {
  const duplicates = db
    .prepare("SELECT verse_key, COUNT(*) AS n FROM verses GROUP BY verse_key HAVING n > 1")
    .all();
  assert.deepEqual(duplicates, []);
  const first = getVerseByKey(index, "1:1");
  const last = getVerseByKey(index, "114:6");
  assert.ok(first && last);
  assert.equal(first.text_plain.split(" ").length, 4);
  assert.ok(first.text_plain.startsWith("بسم"));
  assert.equal(first.text_plain.split(" ")[1], "ٱلله");
  const chapterTwo255 = getVerseByKey(index, "2:255");
  assert.ok(chapterTwo255.text_uthmani.includes("ٱللَّهُ"));
});

test("the canonical Arabic text is stored verbatim and never empty", () => {
  const empty = db.prepare("SELECT COUNT(*) AS n FROM verses WHERE text_uthmani IS NULL OR TRIM(text_uthmani) = ''").get().n;
  assert.equal(empty, 0);
  // Diacritics are preserved in the canonical column (the exact source text).
  const withDiacritics = db
    .prepare("SELECT COUNT(*) AS n FROM verses WHERE text_uthmani <> text_plain")
    .get().n;
  assert.ok(withDiacritics > 6000, "expected the Uthmani text to keep its diacritics");
});

test("search normalisation never changes the stored text", () => {
  const verse = getVerseByKey(index, "1:1");
  assert.ok(verse.text_uthmani.startsWith("بِسْمِ"));
  assert.notEqual(verse.text_uthmani, verse.text_plain);
  assert.equal(verse.text_uthmani.split(" ").length, 4);
  const normalized = normalizeArabic(verse.text_uthmani);
  assert.equal(normalized.includes("بسم"), true);
  assert.notEqual(normalized, verse.text_uthmani);
  // Stored canonical text is untouched by building the index.
  const again = getVerseByKey(index, "1:1");
  assert.equal(again.text_uthmani, verse.text_uthmani);
});

test("every translation row is complete and free of markup", () => {
  const translations = db.prepare("SELECT id, language_code, license_status FROM translations ORDER BY id").all();
  assert.equal(translations.length, 9);
  for (const translation of translations) {
    const count = db
      .prepare("SELECT COUNT(*) AS n FROM translation_verses WHERE translation_id = ?")
      .get(translation.id).n;
    assert.equal(count, 6236, `translation ${translation.id} is incomplete`);
    const empty = db
      .prepare("SELECT COUNT(*) AS n FROM translation_verses WHERE translation_id = ? AND TRIM(text) = ''")
      .get(translation.id).n;
    assert.equal(empty, 0, `translation ${translation.id} has empty verses`);
    const markup = db
      .prepare("SELECT COUNT(*) AS n FROM translation_verses WHERE translation_id = ? AND text GLOB '*<*'")
      .get(translation.id).n;
    assert.equal(markup, 0, `translation ${translation.id} still contains markup`);
    // Licence status is explicit (never silently "approved").
    assert.ok(["verified", "unverified", "blocked"].includes(translation.license_status));
    assert.equal(translation.license_status, "unverified");
  }
});

test("the dataset version records source, checksum and import time", () => {
  const row = db
    .prepare("SELECT dataset, version, source_url, checksum, imported_at FROM dataset_versions WHERE dataset = 'quran-uthmani' ORDER BY id DESC LIMIT 1")
    .get();
  assert.ok(row);
  assert.match(row.source_url, /^https:\/\//);
  assert.match(row.checksum, /^[a-f0-9]{64}$/);
  assert.ok(new Date(row.imported_at).getTime() > 0);
});

test("all supported languages have a translation and the text differs from Arabic", () => {
  const languages = db.prepare("SELECT DISTINCT language_code FROM translations ORDER BY language_code").all().map((row) => row.language_code);
  for (const code of ["en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"]) {
    assert.ok(languages.includes(code), `missing translation for ${code}`);
  }
  const verse = getVerseByKey(index, "2:255");
  const row = db
    .prepare(
      `SELECT tv.text FROM translation_verses tv JOIN translations t ON t.id = tv.translation_id
       WHERE t.language_code = 'en' AND tv.verse_id = ?`
    )
    .get(verse.id);
  assert.ok(row.text.length > 20);
  assert.notEqual(row.text, verse.text_uthmani);
});

test("no placeholder or demo verses were inserted", () => {
  const suspicious = db
    .prepare("SELECT COUNT(*) AS n FROM verses WHERE text_uthmani LIKE '%lorem%' OR text_uthmani LIKE '%test verse%'")
    .get().n;
  assert.equal(suspicious, 0);
});
