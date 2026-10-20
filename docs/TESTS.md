# Measured test results

Run on 2026-10-21 (Windows, Node 24, JDK 17, Android SDK 34; emulator AVD `rafiqtest`, Android 14,
1080×2340). Release **1.0.10** (versionCode 10, versionName 1.0.10).

**Artifacts (1.0.10)** — CI run [https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39502295156](https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39502295156) on commit `b40d416b1b3aabb4cd8dadc88f6600b80d5dc772` (success). APK `dist/rafiq-al-quran-1.0.10.apk`: 3,580,859 bytes, SHA-256 `A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8`. Unsigned CI AAB: 3,573,454 bytes, SHA-256 `C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015`. Signed upload AAB `dist/rafiq-al-quran-1.0.10-signed.aab`: 3,576,566 bytes, SHA-256 `C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F` (jarsigner `jar verified`, upload cert 08:DE:24:53…, bundletool validate exit 0)..

## Server suite — 73 passed, 0 failed

`npm test -w server` (the 58-check baseline plus 15 new assistant tests).

| Area | Checks |
|---|---|
| Dataset integrity | 114 chapters, 6236 verses, per-chapter counts, unique well-formed keys, diacritics preserved, no placeholders |
| Translation integrity | 9 translations × 6236 complete rows, no markup, explicit and unverified licence status |
| Provenance | source URL, SHA-256 checksum and import time recorded |
| Retrieval | curates English topics, Arabic with article/diacritics, unrelated query returns no results, reference parsing, language detection for 7 sampled languages, "more" intent |
| Answer safety | every passage matches the dataset exactly, exact English/Arabic notices, exact fallback wording, no "does not mention" claim, unreviewed languages marked pending |
| API | health/meta/chapters/verses/sources, ask + follow-up, validation errors, feedback consent stripping, admin auth, conversation scoping and deletion (recitation endpoints stay disabled while the server flag is off) |
| Donations | amount bounds/currency, disabled state records pending without provider calls, recurring refusal, signature verification (valid/forged/stale), idempotent events, no card columns |
| Premium | disabled state, feature list, entitlement false, checkout refused |
| Admin auth | 5 tests in `server/test/admin.test.js`: 503 when `ADMIN_TOKEN` is unset; 401 for missing, empty, wrong and wrong-length tokens; 200 plus response shape for every read-only endpoint with the correct token; an authorised feedback update lands in the audit log; the token is never echoed in a response body |
| **AI assistance (new)** | 15 tests in `server/test/assistant.test.js`: 503 and status while unconfigured; configured status never exposes key/model/base URL; 400 validation before any provider call; 413 for a > 5 MB decoded attachment and for a request body above the 8 MB parser limit; per-IP 429 with `retry-after`; fixed JSON contract with Markdown fences stripped and the provider called with the Bearer key/model; image request carries an `image_url` part and non-Latin OCR text is preserved; Arabic-script replies, verse references, long quotations and religious wording are dropped (`reply: null`); reply/search-term caps; generic 502 for provider errors and 504 on timeout with no upstream detail or key leaked; optional monthly cap; inherited CORS preflight and Helmet headers |
| Isolation | no RQM/XINVOX references in sources, own database path, rate limiter behaviour |

## Native app logic — 67 passed, 0 failed

`node mobile/android/scripts/runLogicTests.js` (Java, real bundled assets) — was 49 checks in 1.0.9.

| Area | Checks |
|---|---|
| Existing core | dataset counts (114 chapters / 6236 verses / 9 translations), provenance checksum, Uthmani basmala, 2:255 present, English and Arabic topic answers, exact notices, translation kept separate from Arabic, Arabic interface falls back to English labelled, explicit reference, exact fallback, no repeated verses on "more", donation validation |
| Structural mushaf metadata | `QuranMeta` parses all 6236 rows; 1:1 = Juz 1 / Page 1; 2:255 = Juz 3 / Page 42; 114:6 = Juz 30 / Page 604; exact indicator text "Juz 3  ·  Page 42"; page numbers never regress |
| Study-tools codec | collection names sanitised, empty name → General, case-insensitive de-duplication, length bound; note trim/bound; progress percentages and exact "3 of 38  ·  8%" text; RFC 4180 CSV quoting; Juz 30 preset = surahs 78–114 and full preset = 114; numeric verse-key ordering and validation; next-chapter skips completed chapters and returns 0 when done; plan chapter list round-trips |
| **Recitation rights and attribution (updated)** | `AudioGate.rightsReviewRecorded()` is true for this build (rights review recorded 2026-10-21); `playbackAllowedInThisBuild()` is true; the gate still requires both the recorded review and the server flag (`playbackAllowed(false, true)` is false); attribution contains "Quran.com" and the decision text contains "rights review recorded" |
| **Conversation and feature help (new)** | greeting gets a reply that names the Quran; Islamic greeting recognised; thanks gets a short reply; a fatwa/ruling request is refused and points to scholars; off-topic questions are handled gracefully; advice questions are routed to Quran retrieval (not answered conversationally); overlong messages are not treated as small talk; guidance names scholars, Hadith and Fiqh study and disclaims responsibility; the attachment fallback names the AI service; the AI consent text states that the AI never supplies Quran text; feature-help questions about bookmarks, recitation, language and AI return app instructions without religious content; a bare “help” returns the overview; normal Quran questions still bypass feature help and go to retrieval |

The 67 checks include the per-passage dataset-existence assertions emitted in a loop over the returned passages.

## APK build — 1.0.10

`node mobile/android/scripts/buildApk.js` (aapt2 / javac / d8 / zipalign / apksigner; no Gradle)

- package `com.rqmbooks.rafiqalquran`, versionCode **10**, versionName **1.0.10** (`aapt2 dump badging` on
  `mobile/android/build/quran-companion.apk`); minSdk **24**, targetSdk **36**, compileSdk 34;
- **34 Java sources** compiled; signed (apksigner verify passed) and zipaligned;
- size **~3.41 MB**; the exact size and SHA-256 of the release artifact are recorded in the release note;
- bundles `classes.dex`, the seven dataset assets (including `assets/quran-meta.tsv`) and the launcher icon;
- **Artifacts (1.0.10): CI run 39502295156 on commit b40d416 (success); APK SHA-256 A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8; signed upload AAB SHA-256 C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015**.

## APK packaging verification — 20 checks passed

`node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.10.apk` → **20 checks passed**
("APK verification passed."), including the minSdk **24** and targetSdk **36** checks.

Checks: APK exists; signature verifies; package name; launchable activity; RECORD_AUDIO and INTERNET permissions;
minSdk 24; targetSdk 36; versionCode and versionName match the manifest; `classes.dex`; all seven bundled assets;
launcher icon packaged; app label "Rafiq Al-Quran". The SHA-256 of the released APK is recorded in the release
note (**Artifacts (1.0.10): CI run 39502295156 on commit b40d416 (success); APK SHA-256 A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8; signed upload AAB SHA-256 C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015**).

## AAB — CI build for 1.0.10

- `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool; no Gradle). The same workflow produced the
  validated 1.0.9 AAB (GitHub Actions run
  [39483479573](https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39483479573),
  success; `bundletool validate` exit 0; local `jarsigner` signing with the upload keystore).
- For 1.0.10: the CI run URL, signed AAB SHA-256 and validation output are recorded in the release note
  (**Artifacts (1.0.10): CI run 39502295156 on commit b40d416 (success); APK SHA-256 A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8; signed upload AAB SHA-256 C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015**). The signed AAB must be `jarsigner`
  verified ("jar verified" plus the expected self-signed warning) and `bundletool validate` must exit 0 before
  upload, and it must be signed with the same Play upload key as 1.0.9 so Play upload-key continuity is preserved
  (`QURAN_KEYSTORE_PATH`; a generated throwaway key would be rejected).

## Emulator QA — AVD `rafiqtest` (Android 14, 1080×2340)

1.0.10 installed from the local build (versionCode 10 / versionName 1.0.10). Round-2 captures in `dist/`:

| Screenshot | Verified |
|---|---|
| `qa-r2-home.png` | Home: composer with Voice and 📎 ATTACH actions, example questions, continue-reading card (Al-Fatihah 1:6, Juz 1 · Page 1) |
| `screenshot-qa-r2-language.png` | Full-width "Interface language" dialog with 10 large rows (English name — native name — code), no tiny spinner |
| `screenshot-qa-r2-conversation.png` | "Hello" → local neutral greeting reply, with the notice that conversation replies contain no Quranic text or interpretation |
| `screenshot-qa-r2-player.png` | Recitation playing: Al-Fatihah 1:6, "Playing — Source: Quran.com", 0:04 / 0:05, Pause/Replay/Stop, reciter Mishary Rashid Alafasy, Source & rights card "Review recorded 2026-10-21" |
| `screenshot-qa-r2-attached.png` | Composer attachment chip "Attached: rafiq-test.txt (144 characters read)" with a Remove action (text read on the device) |
| `screenshot-qa-r2-attachment-answer.png` | Attachment-driven retrieval returned Al-Baqarah 2:177, with translation and "Recitation — Quran.com" action |
| `screenshot-qa-r2-tts.png` | Answer screen with the Listen action after TTS was exercised (Google TTS synthesis en-US verified on the emulator; audio cannot be shown in a screenshot) |
| `screenshot-qa-r2-support.png` | Support page: voluntary-only copy, "Donations are not enabled in this release", no raw config keys and no dead checkout form |
| `screenshot-qa-r2-admin.png` | Operator screen explains its purpose before token entry; token field masked; Connect / Forget token; no token shown |
| `screenshot-qa-r2-attach-picker.png` | Blank capture — the system file picker window is excluded from screencap; not used as evidence |

Verified in the same pass:

- **Recitation streaming and auto-next.** Al-Fatihah 1:6 played (~5 s) and automatically advanced to 1:7
  (13 s run), with the MP3 decoder active; playback ran to completion. The player shows the reciter name and
  "Source: Quran.com"; nothing is stored on the device.
- **TTS synthesis.** Tapping "Listen" initialises the TTS engine lazily, falls back to English when the selected
  language has no voice, and speaks the translation/summary; Google TTS en-US synthesis was verified on the
  emulator.
- **Language chooser.** The chooser is a full-width dialog with 10 large rows replacing the tiny spinner; the
  screenshot shows Arabic, English, Urdu, Indonesian, Bengali, Turkish, Persian, Malay, Swahili and French with
  native names and codes.
- **Conversation.** Greeting/off-topic/ruling replies are asserted by the JVM logic tests (ruling requests are
  refused and pointed to scholars; conversation replies carry no Quran text or interpretation). The emulator
  capture shows the greeting path, and the safe fallback now includes the "Where to look next" guidance card
  (scholars, Hadith/Fiqh study with a qualified teacher, general reference for non-Quran questions, explicit
  no-responsibility statement).
- **Attachments.** The `.txt` attachment was read entirely on the device (144 characters) and used for Quran
  retrieval; images need the optional AI service, which is off by default and cannot read images without the
  operator's key; PDFs show the unsupported-type message.
- **Page fixes.** The Support page no longer shows raw configuration keys or a dead checkout form; the former
  "Optional tools" page now presents the free study tools and states that no plans, subscriptions or add-ons are
  sold; the operator admin screen explains what it is for; the Operator admin menu entry is absent in the normal menu and appears after five quick taps on the app title, and the screen then connected with the operator token and rendered live diagnostics (flags, dataset counts); the captured pages render without mojibake
  (source files are UTF-8).

## Live production API — checked 2026-10-20

The operator screen connected to `https://rqmbooks.com/rafiq-api` with `x-admin-token` and rendered
**6236 verses / 114 chapters / 9 translations**, the latest feedback row (**RESOLVED**), recent audit entries and
reciter rows with rights status **UNVERIFIED** and playback disabled on the server. Endpoint and authentication
details are documented in `docs/ADMIN.md`; `server/test/admin.test.js` covers the automated auth paths.

The server's `recitationEnabled` flag and the seeded reciter rows are server-side only: in 1.0.10 the app streams
Quran.com audio as a **client-side** feature (rights decision recorded 2026-10-21), so the server flag may still
read `false` while the app plays recitation. The optional AI endpoints were not part of this round's recorded live
verification; check `/rafiq-api/api/assistant/status` after the operator configures a key (see
`docs/DEPLOYMENT.md`).

## Dataset validation

`npm run validate:dataset -w server` — completeness, chapter counts, translation completeness, checksum, source,
markup checks and explicit translation licence status all pass.
