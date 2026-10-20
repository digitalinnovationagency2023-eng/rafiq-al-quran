#!/usr/bin/env node
/**
 * Fetches structural verse metadata (juz / mushaf page / hizb) from the public
 * Quran.com API v4 and stores it as a small TSV that the Android app bundles.
 *
 * This is *structural* metadata only (which page a verse is printed on). The
 * Arabic text and translations still come exclusively from the validated
 * dataset imported by server/scripts/importQuran.js.
 *
 * Output: mobile/android/scripts/data/quran-meta.tsv (committed, reproducible)
 * Source: https://api.quran.com/api/v4 (Quran.com)
 *
 * Usage: node mobile/android/scripts/fetchQuranMeta.js
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(here, "data");
const outFile = path.join(outDir, "quran-meta.tsv");
const API = "https://api.quran.com/api/v4";
const CHAPTERS = 114;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function fetchJson(url, attempts = 4) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      const response = await fetch(url, { headers: { accept: "application/json" } });
      if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
      return await response.json();
    } catch (error) {
      lastError = error;
      await sleep(400 * attempt * attempt);
    }
  }
  throw lastError;
}

const rows = [];
const seen = new Set();
for (let chapter = 1; chapter <= CHAPTERS; chapter++) {
  const url = `${API}/verses/by_chapter/${chapter}?fields=page_number,juz_number,hizb_number&per_page=300&page=1`;
  const payload = await fetchJson(url);
  const verses = payload?.verses;
  if (!Array.isArray(verses) || verses.length === 0) throw new Error(`No verses for chapter ${chapter}`);
  for (const verse of verses) {
    const key = verse.verse_key;
    if (!/^\d+:\d+$/.test(key || "")) throw new Error(`Bad verse key ${key} in chapter ${chapter}`);
    if (seen.has(key)) throw new Error(`Duplicate verse key ${key}`);
    seen.add(key);
    const page = Number(verse.page_number);
    const juz = Number(verse.juz_number);
    const hizb = Number(verse.hizb_number);
    if (!(page >= 1 && page <= 604)) throw new Error(`Bad page ${page} for ${key}`);
    if (!(juz >= 1 && juz <= 30)) throw new Error(`Bad juz ${juz} for ${key}`);
    if (!(hizb >= 1 && hizb <= 240)) throw new Error(`Bad hizb ${hizb} for ${key}`);
    rows.push([key, juz, page, hizb].join("\t"));
  }
  process.stdout.write(`\rFetched chapter ${chapter}/${CHAPTERS}`);
}
process.stdout.write("\n");

if (rows.length !== 6236) throw new Error(`Expected 6236 verses, fetched ${rows.length}`);

// Structural sanity: page and juz numbers are continuous and never decrease.
let lastPage = 0;
let lastJuz = 0;
const pages = new Set();
const juzs = new Set();
for (const row of rows) {
  const [, juz, page] = row.split("\t").map(Number);
  if (page < lastPage) throw new Error(`Page number regressed at ${row}`);
  if (juz < lastJuz) throw new Error(`Juz number regressed at ${row}`);
  lastPage = page;
  lastJuz = juz;
  pages.add(page);
  juzs.add(juz);
}
if (lastPage !== 604) throw new Error(`Expected last page 604, got ${lastPage}`);
if (lastJuz !== 30) throw new Error(`Expected last juz 30, got ${lastJuz}`);
if (pages.size !== 604) throw new Error(`Expected 604 distinct pages, got ${pages.size}`);
if (juzs.size !== 30) throw new Error(`Expected 30 distinct juz, got ${juzs.size}`);

fs.mkdirSync(outDir, { recursive: true });
const header = [
  "# quran-meta structural metadata: verse_key, juz, madani page, hizb",
  "# source: Quran.com API v4 (https://api.quran.com/api/v4/verses/by_chapter/{n}?fields=page_number,juz_number,hizb_number)",
  `# fetched: ${new Date().toISOString()}`,
  `# verses: 6236, pages: 604, juz: 30`,
];
fs.writeFileSync(outFile, `${[...header, ...rows].join("\n")}\n`, "utf8");
console.log(`Wrote ${outFile} (${rows.length} verses, ${(fs.statSync(outFile).size / 1024).toFixed(1)} KB)`);
