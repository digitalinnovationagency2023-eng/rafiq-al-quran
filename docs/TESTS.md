# Measured test results

Run on 2026-10-20 (Windows, Node 24, JDK 17, Android SDK 34; emulator AVD `rafiqtest`, Android 14, 1080×2340).

## Server suite — 58 passed, 0 failed

`npm test -w server`

| Area | Checks |
|---|---|
| Dataset integrity | 114 chapters, 6236 verses, per-chapter counts, unique well-formed keys, diacritics preserved, no placeholders |
| Translation integrity | 9 translations × 6236 complete rows, no markup, explicit and unverified licence status |
| Provenance | source URL, SHA-256 checksum and import time recorded |
| Retrieval | curates English topics, Arabic with article/diacritics, unrelated query returns no results, reference parsing, language detection for 7 sampled languages, “more” intent |
| Answer safety | every passage matches the dataset exactly, exact English/Arabic notices, exact fallback wording, no “does not mention” claim, unreviewed languages marked pending |
| API | health/meta/chapters/verses/sources, ask + follow-up, validation errors, feedback consent stripping, admin auth, conversation scoping and deletion |
| Donations | amount bounds/currency, disabled state records pending without provider calls, recurring refusal, signature verification (valid/forged/stale), idempotent events, no card columns |
| Premium | disabled state, feature list, entitlement false, checkout refused |
| Admin auth (new) | 5 tests in `server/test/admin.test.js`: 503 when `ADMIN_TOKEN` is unset; 401 for missing, empty, wrong and wrong-length tokens; 200 plus response shape for every read-only endpoint with the correct token; an authorised feedback update lands in the audit log; the token is never echoed in a response body |
| Isolation | no RQM/XINVOX references in sources, own database path, rate limiter behaviour |

## Native app logic — 49 passed, 0 failed

`node mobile/android/scripts/runLogicTests.js` (Java, real bundled assets) — was 25 checks before this release.

| Area | Checks |
|---|---|
| Existing core | dataset counts (114 chapters / 6236 verses / 9 translations), provenance checksum, Uthmani basmala, 2:255 present, English and Arabic topic answers, exact notices, translation kept separate from Arabic, Arabic interface falls back to English labelled, explicit reference, exact fallback, no repeated verses on “more”, donation validation |
| Structural mushaf metadata (new) | `QuranMeta` parses all 6236 rows; 1:1 = Juz 1 / Page 1; 2:255 = Juz 3 / Page 42; 114:6 = Juz 30 / Page 604; exact indicator text “Juz 3  ·  Page 42”; page numbers never regress |
| Study-tools codec (new) | collection names sanitised, empty name → General, case-insensitive de-duplication, length bound; note trim/bound; progress percentages and exact “3 of 38  ·  8%” text; RFC 4180 CSV quoting; Juz 30 preset = surahs 78–114 and full preset = 114; numeric verse-key ordering and validation; next-chapter skips completed chapters and returns 0 when done; plan chapter list round-trips |
| Rights gate (new) | `AudioGate` playback is off in this build; the gate requires both a recorded rights review and the server flag; the pending-reason wording names Quran.com and states that no audio is streamed |

Note: `runLogicTests.js` previously walked the pre-rename package path
`app/src/main/java/com/qurancompanion/app/logic`, which does not exist, so the script could not compile the app
logic. It now walks `com/rqmbooks/rafiqalquran/logic` — fixed in commit `63c332e` — and the 49 checks above are
from the fixed run.

## APK build — 1.0.9

`node mobile/android/scripts/buildApk.js` (aapt2 / javac / d8 / zipalign / apksigner; no Gradle)

- package `com.rqmbooks.rafiqalquran`, versionCode **9**, versionName **1.0.9** (aapt2 badging on
  `dist/rafiq-al-quran-1.0.9.apk`); minSdk 23, targetSdk 34;
- **32 Java sources** compiled; signed (apksigner verify passed) and zipaligned;
- size **3.40 MB** (3,560,379 bytes, SHA-256 `43A6231F05066793425C9DC67146ECCF8E444AD4F47BCCA769209EDA11BEA1AB`);
- bundles `classes.dex`, the dataset assets (including `assets/quran-meta.tsv`) and the launcher icon.

## APK packaging verification — 20 checks passed

`node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.9.apk` (the script was fixed in this round:
package regexes `com.rqmbooks.rafiqalquran`, versionCode/versionName compared against `AndroidManifest.xml`,
`quran-meta.tsv` added to the bundled-asset list, launcher-icon check, and the default APK path resolved from the
manifest versionName).

Checks: APK exists; signature verifies; package name; launchable activity; RECORD_AUDIO and INTERNET permissions;
minSdk 23; targetSdk 34; versionCode and versionName match the manifest; `classes.dex`; all seven bundled assets;
launcher icon packaged; app label “Rafiq Al-Quran”. Output: “APK verification passed.”

## AAB — built by CI, signed locally, installed on the emulator

- `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool; no Gradle) — GitHub Actions run
  [39480456879](https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39480456879),
  commit `88d2629bd474ab77574fc48be2a47f17cb83c204`: success.
  Unsigned artifact: `dist/rafiq-al-quran-1.0.9.aab`.
- Signed locally with `jarsigner` using the upload keystore → `dist/rafiq-al-quran-1.0.9-signed.aab`
  (3,555,625 bytes, SHA-256 `300C0CD3C9A017B323A40C00476CF3BBEC45C558FA71770C1E8DA65EB2478D1D`).
  `jarsigner -verify` → “jar verified” (exit 0; self-signed certificate warning expected, no timestamp);
  `bundletool validate` → exit 0.
- The AAB signer certificate SHA-256 matches the APK signer certificate
  (`08DE2453EE306688430E6714D0090B2890EB2C197444882DA8945960D5219D42`); both are the local
  `CN=Quran Companion` upload key.
- The AAB-derived universal APK was installed on the emulator: `dumpsys package com.rqmbooks.rafiqalquran` reports
  `versionCode=9 minSdk=23 targetSdk=34`, `versionName=1.0.9`; screenshot `dist/screenshot-from-aab.png`.

## Emulator QA — AVD `rafiqtest` (Android 14, 1080×2340)

1.0.9 installed from the build (versionCode 9 / versionName 1.0.9). Screenshots in `dist/`:

| Screenshot | Verified |
|---|---|
| `screenshot-home.png` | Home (light) loads: composer, example questions, study-tools entry |
| `screenshot-home-dark.png` | Dark theme applied across the home screen |
| `screenshot-answer.png` | “patience” question → Al-Baqarah 2:45 with the required notice |
| `screenshot-surahs.png` | Surah list — all 114 surahs, offline |
| `screenshot-reader.png` | Al-Baqarah reader with the Juz/Page indicator |
| `screenshot-reader-actions.png` | Per-verse actions (copy, share, save, note, listen) |
| `screenshot-reader-note.png` | Personal note stored/shown in the reader |
| `screenshot-reader-side-by-side.png` | Side-by-side Arabic/translation layout |
| `screenshot-reader-dark.png` | Reader in the dark theme |
| `screenshot-bookmarks.png` | Bookmarks screen with collection and saved note |
| `screenshot-history.png` | Reading history screen |
| `screenshot-study-tools.png` | Study tools hub (bookmarks, notes, saved searches, plans, export) |
| `screenshot-study-tools-plans.png` | Reading plan progress persisted: “1 of 114 · 1%” after marking Al-Fatihah read |
| `screenshot-saved-search-run.png` | Saved search launches its query from the study-tools hub |
| `screenshot-player-gated.png` | Recitation player: “QURAN.COM · RIGHTS PENDING”, “RECITATION RIGHTS PENDING”, controls disabled, 0:00 / 0:00 |
| `screenshot-player-controls.png` | Player layout with play/pause/replay disabled; Previous/Next verse navigation changed the shown verse (6 → 7 → 6 of 7) |
| `screenshot-admin.png` | Operator admin token-entry screen |
| `screenshot-admin-data.png` | Live admin data: Verses 6236, Chapters 114, Translations 9, latest feedback RESOLVED, audit entries, reciters UNVERIFIED/disabled, premium DISABLED |
| `screenshot-share-sheet.png` | Android share sheet with bookmarks CSV — header `Reference,Collection,Note` |
| `screenshot-share-sheet-notes.png` | Share sheet with notes CSV — e.g. `Al-Baqarah 2:1,General,Guidance begins with sincere intention` |
| `screenshot-from-aab.png` | App installed from the signed AAB: home screen with the continue-reading card |

Also exercised in the same QA pass: new bookmark-collection creation (dialog → collection row), note editing and
removal, bookmarks and notes CSV share sheets, and the operator screen connecting to the live API (header/token
entered at runtime, never stored in the repository, docs, logs or screenshots).

## Live production API — verified from the app

The operator screen connected to `https://rqmbooks.com/rafiq-api` with `x-admin-token` and rendered
**6236 verses / 114 chapters / 9 translations**, the latest feedback row (**RESOLVED**), recent audit entries and
reciter rows with rights status **UNVERIFIED** and playback disabled on the server. Endpoint and authentication
details are documented in `docs/ADMIN.md`; `server/test/admin.test.js` covers the automated auth paths.

## Dataset validation

`npm run validate:dataset -w server` — completeness, chapter counts, translation completeness, checksum, source,
markup checks and explicit translation licence status all pass.
