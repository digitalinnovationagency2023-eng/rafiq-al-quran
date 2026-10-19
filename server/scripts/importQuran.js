#!/usr/bin/env node
/**
 * Import the Quran dataset and the configured translations.
 *
 * Sources
 *  - Arabic (Uthmani) text and chapter metadata: Quran.com API v4
 *    (https://api.quran.com/api/v4, Quran Foundation). Attribution and licence
 *    review status are recorded in docs/PROVENANCE.md and in dataset_versions.
 *  - Translations: Quran.com API v4 translation resources, one per supported
 *    language. Each row keeps the exact provider metadata plus an explicit
 *    licence status; nothing is marked verified automatically.
 *
 * Reproducibility
 *  - Every downloaded payload is written to data/raw/ with a SHA-256 checksum,
 *    and the checksums are stored in dataset_versions.
 *  - `--offline` re-imports from data/raw/ without network access.
 *
 * Nothing in this script touches RQM Books data: it writes only to the local
 * quran-app SQLite file.
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { config, serverRoot } from "../src/config.js";
import { getDb } from "../src/db.js";
import { normalizeArabic, plainArabic } from "../src/services/arabic.js";

const OFFLINE = process.argv.includes("--offline");

/** One translation per supported language. IDs come from the provider catalogue. */
export const TRANSLATIONS = [
  { providerId: "20", languageCode: "en", languageName: "English", direction: "ltr", attribution: "Saheeh International" },
  { providerId: "234", languageCode: "ur", languageName: "Urdu", direction: "rtl", attribution: "Fatah Muhammad Jalandhari" },
  { providerId: "33", languageCode: "id", languageName: "Indonesian", direction: "ltr", attribution: "Indonesian Islamic Affairs Ministry" },
  { providerId: "163", languageCode: "bn", languageName: "Bengali", direction: "ltr", attribution: "Sheikh Mujibur Rahman" },
  { providerId: "77", languageCode: "tr", languageName: "Turkish", direction: "ltr", attribution: "Diyanet Isleri" },
  { providerId: "135", languageCode: "fa", languageName: "Persian", direction: "rtl", attribution: "IslamHouse.com" },
  { providerId: "39", languageCode: "ms", languageName: "Malay", direction: "ltr", attribution: "Abdullah Muhammad Basmeih" },
  { providerId: "49", languageCode: "sw", languageName: "Swahili", direction: "ltr", attribution: "Ali Muhsin Al-Barwani" },
  { providerId: "31", languageCode: "fr", languageName: "French", direction: "ltr", attribution: "Muhammad Hamidullah" },
];

const EXPECTED_CHAPTERS = 114;
const EXPECTED_VERSES = 6236;

export const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");

const rawPath = (...parts) => path.join(config.rawDataDir, ...parts);

const readLocal = (name) => {
  const file = rawPath(name);
  if (!fs.existsSync(file)) return null;
  return fs.readFileSync(file, "utf8");
};

const writeRaw = (name, body) => {
  fs.mkdirSync(config.rawDataDir, { recursive: true });
  fs.writeFileSync(rawPath(name), body);
  return sha256(body);
};

const apiGet = async (url, rawName) => {
  if (OFFLINE) {
    const local = readLocal(rawName);
    if (!local) throw new Error(`Offline import needs data/raw/${rawName}. Run without --offline once.`);
    return { body: local, checksum: sha256(local), url };
  }
  const response = await fetch(url, {
    headers: {
      "user-agent": "quran-app/1.0 (dataset import; contact: operator)",
      accept: "application/json",
    },
  });
  if (!response.ok) throw new Error(`GET ${url} failed with HTTP ${response.status}`);
  const body = await response.text();
  const checksum = writeRaw(rawName, body);
  return { body, checksum, url };
};

/** Strip provider footnote markup; the UI shows plain translation text only. */
export const cleanTranslation = (text) =>
  String(text || "")
    .replace(/<sup[^>]*>.*?<\/sup>/gis, "")
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim();

const insertDatasetVersion = (db, row) => {
  db.prepare(
    `INSERT INTO dataset_versions (dataset, version, source_url, checksum, imported_at, notes)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(row.dataset, row.version, row.sourceUrl, row.checksum, new Date().toISOString(), row.notes || null);
};

export const importQuran = async ({ db = getDb(), log = console.log } = {}) => {
  const base = config.sources.quranApiBase;
  log(`Importing Quran dataset from ${base}${OFFLINE ? " (offline mode)" : ""}`);

  // 1. Chapters -------------------------------------------------------------
  const chaptersPayload = await apiGet(`${base}/chapters?language=en`, "chapters.json");
  const chapters = JSON.parse(chaptersPayload.body).chapters;
  if (!Array.isArray(chapters) || chapters.length !== EXPECTED_CHAPTERS) {
    throw new Error(`Expected ${EXPECTED_CHAPTERS} chapters, received ${chapters?.length}`);
  }

  // 2. Arabic Uthmani text --------------------------------------------------
  const versesPayload = await apiGet(`${base}/quran/verses/uthmani`, "verses-uthmani.json");
  const verses = JSON.parse(versesPayload.body).verses;
  if (!Array.isArray(verses) || verses.length !== EXPECTED_VERSES) {
    throw new Error(`Expected ${EXPECTED_VERSES} verses, received ${verses?.length}`);
  }

  // 3. Provider translation catalogue (metadata for provenance) -------------
  const cataloguePayload = await apiGet(`${base}/resources/translations`, "translations-catalogue.json");
  const catalogue = new Map(
    JSON.parse(cataloguePayload.body).translations.map((entry) => [String(entry.id), entry])
  );

  // 4. Download each selected translation -----------------------------------
  // The full-translation endpoint returns verses in mushaf order without a
  // verse_key, so alignment is verified against a per-chapter fetch before the
  // positional mapping is trusted.
  const orderedVerseKeys = [...verses]
    .sort((a, b) => a.id - b.id)
    .map((verse) => verse.verse_key);
  const chapterOffsets = new Map();
  {
    let offset = 0;
    for (const chapter of chapters) {
      chapterOffsets.set(chapter.id, offset);
      offset += chapter.verses_count;
    }
  }

  const translationBodies = [];
  for (const spec of TRANSLATIONS) {
    const meta = catalogue.get(spec.providerId);
    if (!meta) throw new Error(`Translation ${spec.providerId} is not in the provider catalogue`);
    const rawName = `translation-${spec.providerId}.json`;
    const payload = await apiGet(`${base}/quran/translations/${spec.providerId}`, rawName);
    const rows = JSON.parse(payload.body).translations;
    if (!Array.isArray(rows) || rows.length !== EXPECTED_VERSES) {
      throw new Error(
        `Translation ${spec.providerId} returned ${rows?.length} verses, expected ${EXPECTED_VERSES}`
      );
    }

    // Alignment check: compare the full-response slice for Al-Baqarah with a
    // per-chapter fetch of the same chapter.
    const chapterTwo = chapters.find((chapter) => chapter.id === 2);
    const offset = chapterOffsets.get(2);
    const spot = await apiGet(
      `${base}/quran/translations/${spec.providerId}?chapter_number=2`,
      `translation-${spec.providerId}-chapter-2.json`
    );
    const spotRows = JSON.parse(spot.body).translations;
    const aligned = spotRows.every(
      (row, index) => cleanTranslation(row.text) === cleanTranslation(rows[offset + index]?.text)
    );
    if (!aligned || spotRows.length !== chapterTwo.verses_count) {
      throw new Error(
        `Translation ${spec.providerId} is not aligned with the canonical verse order; refusing to import it.`
      );
    }

    const mapped = rows.map((row, index) => ({ verse_key: orderedVerseKeys[index], text: row.text }));
    const mappedBody = JSON.stringify({ provider_id: spec.providerId, translations: mapped });
    const mappedChecksum = writeRaw(`translation-${spec.providerId}.mapped.json`, mappedBody);

    translationBodies.push({
      spec,
      meta,
      rows,
      mapped,
      checksum: mappedChecksum,
      sourceChecksum: payload.checksum,
      alignmentChecked: "al-baqarah",
      sourceUrl: payload.url,
    });
    log(
      `  fetched translation ${spec.providerId} (${spec.languageCode}) ${rows.length} verses, alignment verified`
    );
  }

  // 5. Write to SQLite in one transaction -----------------------------------
  db.exec("BEGIN");
  try {
    db.exec("DELETE FROM translation_verses");
    db.exec("DELETE FROM translations");
    db.exec("DELETE FROM verses");
    db.exec("DELETE FROM chapters");

    const insertChapter = db.prepare(
      `INSERT INTO chapters (id, name_arabic, name_simple, name_english, verses_count, revelation_place, revelation_order)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    );
    for (const chapter of chapters) {
      insertChapter.run(
        chapter.id,
        chapter.name_arabic,
        chapter.name_simple,
        chapter.translated_name?.name || chapter.name_complex || chapter.name_simple,
        chapter.verses_count,
        chapter.revelation_place || null,
        chapter.revelation_order || null
      );
    }

    const insertVerse = db.prepare(
      `INSERT INTO verses (id, chapter_id, verse_number, verse_key, text_uthmani, text_plain, text_normalized, word_count)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    );
    const verseIdByKey = new Map();
    for (const verse of verses) {
      const [chapterId, verseNumber] = verse.verse_key.split(":").map(Number);
      const text = verse.text_uthmani;
      const plain = plainArabic(text);
      const normalized = normalizeArabic(text);
      insertVerse.run(
        verse.id,
        chapterId,
        verseNumber,
        verse.verse_key,
        text,
        plain,
        normalized,
        plain.split(/\s+/).filter(Boolean).length
      );
      verseIdByKey.set(verse.verse_key, verse.id);
    }

    const insertTranslation = db.prepare(
      `INSERT INTO translations
        (id, provider, provider_id, slug, title, author, publisher, language_code, language_name, direction,
         license_status, license_note, source_url, imported_at, checksum, enabled)
       VALUES (?, 'quran.com', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`
    );
    const insertTranslationVerse = db.prepare(
      "INSERT INTO translation_verses (translation_id, verse_id, text) VALUES (?, ?, ?)"
    );

    for (const translation of translationBodies) {
      const { spec, meta, rows, checksum, sourceUrl } = translation;
      const licenseNote =
        "Provider catalogue does not publish per-translation licence terms. " +
        "Licence review is required before this translation is treated as approved. See docs/PROVENANCE.md.";
      const inserted = insertTranslation.run(
        Number(spec.providerId),
        spec.providerId,
        meta.slug || null,
        meta.name || spec.attribution,
        meta.author_name || spec.attribution,
        meta.publisher || "Quran.com",
        spec.languageCode,
        spec.languageName,
        spec.direction,
        "unverified",
        licenseNote,
        sourceUrl,
        new Date().toISOString(),
        checksum
      );
      void inserted;
      const translationId = Number(spec.providerId);
      for (const row of translation.mapped) {
        const verseKey = row.verse_key;
        if (!verseKey) throw new Error(`Translation ${spec.providerId} row without verse_key`);
        const verseId = verseIdByKey.get(verseKey);
        if (!verseId) throw new Error(`Translation ${spec.providerId} references unknown verse ${verseKey}`);
        insertTranslationVerse.run(translationId, verseId, cleanTranslation(row.text));
      }
    }

    insertDatasetVersion(db, {
      dataset: "quran-uthmani",
      version: "quran.com-api-v4",
      sourceUrl: versesPayload.url,
      checksum: versesPayload.checksum,
      notes: `Chapters checksum ${chaptersPayload.checksum}. Text served by Quran.com (Quran Foundation).`,
    });
    for (const translation of translationBodies) {
      insertDatasetVersion(db, {
        dataset: `translation-${translation.spec.providerId}`,
        version: "quran.com-api-v4",
        sourceUrl: translation.sourceUrl,
        checksum: translation.checksum,
        notes: `Language ${translation.spec.languageCode}; licence status unverified.`,
      });
    }

    db.exec("COMMIT");
  } catch (error) {
    db.exec("ROLLBACK");
    throw error;
  }

  // 6. Validate ------------------------------------------------------------
  const chapterCount = db.prepare("SELECT COUNT(*) AS n FROM chapters").get().n;
  const verseCount = db.prepare("SELECT COUNT(*) AS n FROM verses").get().n;
  const translationCount = db.prepare("SELECT COUNT(*) AS n FROM translations").get().n;
  const mismatched = db
    .prepare(
      `SELECT c.id, c.verses_count, COUNT(v.id) AS actual
       FROM chapters c LEFT JOIN verses v ON v.chapter_id = c.id
       GROUP BY c.id HAVING c.verses_count <> actual`
    )
    .all();
  if (chapterCount !== EXPECTED_CHAPTERS) throw new Error(`chapter count ${chapterCount}`);
  if (verseCount !== EXPECTED_VERSES) throw new Error(`verse count ${verseCount}`);
  if (mismatched.length) throw new Error(`chapter verse counts mismatch: ${JSON.stringify(mismatched)}`);

  log(`Imported ${chapterCount} chapters, ${verseCount} verses, ${translationCount} translations.`);
  return { chapterCount, verseCount, translationCount };
};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(serverRoot, "scripts", "importQuran.js");
if (isMain) {
  importQuran()
    .then(() => process.exit(0))
    .catch((error) => {
      console.error("Import failed:", error.message);
      process.exit(1);
    });
}
