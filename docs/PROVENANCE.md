# Source provenance

## Quranic Arabic text

| Field | Value |
|---|---|
| Source organisation | Quran.com API v4 (`https://api.quran.com/api/v4`), operated by Quran Foundation |
| Dataset | Uthmani script verses (`/quran/verses/uthmani`) and chapter metadata (`/chapters`) |
| Version | API v4, imported 2026-10-19 (recorded in `dataset_versions` and `assets/provenance.tsv`) |
| Script / reading | Uthmani orthography as published by the provider; the app does not change the text |
| Completeness | 114 chapters, 6236 verses, chapter verse counts validated against the provider metadata |
| Checksum | SHA-256 of the downloaded verses payload, stored in `dataset_versions.checksum` and shown in the app's source panel |
| Attribution | The app shows the dataset name, version, source URL and checksum with every passage |

The Arabic text is stored exactly as imported and is never edited through the app or an admin screen. Search
normalisation is computed into separate columns and never replaces the canonical text. Re-import is the only way to
change the dataset, and it re-runs the same validation.

Tanzil.net was investigated as an alternative canonical source but is not reachable from the build environment used
for this delivery; the licence review for the provider used here must be completed by the operator before public
release (see `docs/LIMITATIONS.md`).

## Translations

One translation per supported interface language is imported, with provider metadata kept verbatim:

| Language | Translation | Author | Licence status |
|---|---|---|---|
| en | Saheeh International | Saheeh International | unverified |
| ur | Fatah Muhammad Jalandhari | Fatah Muhammad Jalandhari | unverified |
| id | Indonesian Islamic Affairs Ministry | Indonesian Islamic Affairs Ministry | unverified |
| bn | Sheikh Mujibur Rahman | Sheikh Mujibur Rahman | unverified |
| tr | Diyanet Isleri | Diyanet Isleri | unverified |
| fa | IslamHouse.com | IslamHouse.com | unverified |
| ms | Abdullah Muhammad Basmeih | Abdullah Muhammad Basmeih | unverified |
| sw | Ali Muhsin Al-Barwani | Ali Muhsin Al-Barwani | unverified |
| fr | Muhammad Hamidullah | Muhammad Hamidullah | unverified |

- The provider catalogue does not publish per-translation licence terms, so every translation is explicitly marked
  **unverified** in the dataset, in the app's source panel and on the Sources page. Nothing is presented as approved.
- Before public release the operator must record the licence review per translation. The API supports this:
  `PATCH /api/admin/translations/:id { "licenseStatus": "verified", "licenseNote": "..." }`, and the change is
  written to the audit log. Setting `TRANSLATION_LICENSE_MODE=verified-only` restricts the app to verified rows.
- Translation text is displayed separately from the Arabic and labelled with its title/author. Footnote markup is
  stripped at import; the Arabic text is never mixed into a translation field.

## Structural mushaf metadata (Juz / Madani page / hizb)

| Field | Value |
|---|---|
| Asset | `mobile/android/scripts/data/quran-meta.tsv` (committed, reproducible) |
| Columns | `verse_key`, `juz`, Madani `page`, `hizb` — one row per verse |
| Generator | `mobile/android/scripts/fetchQuranMeta.js` (network needed only to regenerate the table) |
| Source | Public Quran.com API v4 — `/verses/by_chapter/{n}?fields=page_number,juz_number,hizb_number` |
| Coverage | 6236 verse rows; juz 1–30; Madani pages 1–604; hizb 1–60 (Quran.com API v4 `hizb_number`) |
| Captured | 2026-10-20 (timestamp in the TSV header line) |
| Bundled copy | `node mobile/android/scripts/exportAssets.js` → `assets/quran-meta.tsv`, included in the APK/AAB |

- The generator validates completeness before writing: exactly 6236 rows, unique well-formed verse keys, page
  1–604, juz 1–30, hizb 1–60, and non-decreasing page/juz/hizb order. It fails instead of writing a partial table.
- The app parses the same file at runtime (`logic/QuranMeta.java`) and rejects a missing, malformed or incomplete
  table; the reader and player show "Juz N · Page N" from it.
- **This is layout metadata only** — which page of the standard Madani mushaf a verse is printed on. It is not
  scripture, tafsir or translation, and it never supplies or alters Quranic text. The Arabic text and translations
  still come exclusively from the validated dataset above.
- The hizb value is stored in the table but is not surfaced in the app UI. Committed output means every build
  reproduces the same structural table; re-fetch only when intentionally updating it, then commit the new TSV.

## Recitation

| Field | Value |
|---|---|
| Source | **Quran.com public CDN** — `https://verses.quran.com/`, streamed per verse over HTTPS |
| Reciters | Mishary Rashid Alafasy (`Alafasy/mp3/%03d%03d.mp3`) · Abdul Basit Abdus Samad, Murattal (`AbdulBaset/Murattal/mp3/%03d%03d.mp3`) · Muhammad Siddiq Al-Minshawi, Murattal (`Minshawi/Murattal/mp3/%03d%03d.mp3`) |
| Rights review | Recorded by the publisher **2026-10-21**; the decision record and attribution live in `logic/AudioGate.java` |
| Recorded decision | "Recitation rights review recorded 2026-10-21 — Quran.com verse audio streaming approved by the publisher." |
| Attribution | "Source: Quran.com" shown in the player and the Source & rights card, next to the reciter name |
| Storage | None — no download, cache or copy on the device; playback uses the Android `MediaPlayer` MP3 decoder |
| Player controls | play/pause, replay, seek, previous/next verse, automatic advance to the next verse |
| Relationship to the text dataset | Separate: the Arabic text and translations beside the player still come from the validated bundled dataset, never from the audio service |

- This is a **client-side** feature: the app streams directly from Quran.com's CDN and does not call the server's
  `/api/recitations` route. The server's `RECITATION_ENABLED` switch and the seeded `audio_reciters` rows (Abdul
  Basit Abdus Samad, Maher Al-Muaiqly, Abdul Rahman Al-Sudais) remain `rights_status = unverified` and
  `enabled = 0`, and the server endpoint answers 403 in the default deployment; they are not what enables the app
  player.
- No audio is downloaded, cloned, synthesised, cached or redistributed by this project. The app only plays the
  verse files for the selected reciter and shows their source; the recorded publisher decision covers this
  streaming use, and is recorded as a review record rather than a legal opinion.
- Emulator evidence: Al-Fatihah 1:6 played (~5 s) and auto-advanced to 1:7 (13 s), with the MP3 decoder active and
  playback running to completion (`dist/screenshot-qa-r2-player.png`, `docs/TESTS.md`).

## Optional AI assistance (never a source of Quran text)

The optional AI layer (`server/src/services/assistant.js`, `POST /api/assistant`) is **not a source of Quran text,
translation, verse reference or interpretation**. It is used only for (a) a short neutral small-talk reply,
(b) search-word/intent suggestions for the app's local search, and (c) OCR of an image the user attached. The app
always renders verses from the validated dataset itself; server-side sanitisation drops any reply containing
Arabic script, verse references, long quotations or religious wording, and the raw provider response is never
returned to a client. The AI is off by default (`AI_PROVIDER=none`, 503) and needs an operator-configured free-tier
key; full configuration and privacy notes are in `docs/AI-ASSISTANT.md` and `docs/DEPLOYMENT.md`.

## Voice

Voice input uses the Android speech recogniser; spoken answers use Android text-to-speech (initialised lazily,
falling back to English when the selected language has no voice). Spoken text is the translation and summary only,
never the Arabic verse, and neither feature is used to imitate a Quranic recitation. The app states this in the
interface and in the privacy page.

## Reproducibility

```
npm run import:quran -w server        # downloads, checksums, validates, writes SQLite
node mobile/android/scripts/fetchQuranMeta.js   # optional: regenerate the committed structural TSV (network)
node mobile/android/scripts/exportAssets.js
node mobile/android/scripts/buildApk.js
```

The AAB is built by `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool) and signed locally with
`jarsigner`; locally, `node mobile/android/scripts/buildAab.js` does the same when `BUNDLETOOL_JAR` points to the
bundletool jar. Raw provider payloads and their SHA-256 checksums are written under `server/data/raw/` on import
(not committed). `server/scripts/validateDataset.js` re-checks completeness and reports the recorded checksum.
