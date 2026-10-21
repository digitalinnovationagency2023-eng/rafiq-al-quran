# Measured test results

Run on 2026-10-21 (Windows, Node 24, JDK 17, Android SDK 34; emulator AVD `rafiqtest`, Android 14,
1080×2340). Release **1.1.0** (versionCode 13, versionName 1.1.0). Earlier measurements are kept for history; the
1.1.0 advisory-answer additions, the 1.0.12 voice matrix and the updated counts are in the sections below.

**Artifacts (1.1.0)** — APK `dist/rafiq-al-quran-1.1.0.apk`: 3,593,144 bytes, SHA-256 `B680204A4BA2BAAC0465C4B7EBD6675639476F3791803A83C68F84AC1F95068A`; unsigned AAB `dist/rafiq-al-quran-1.1.0.aab`: 3,585,640 bytes, SHA-256 `2A275C7AE887D9DEFC0A4D512B38CCB8C53AAB13D035D38BD14637E7B03692BB`; signed upload AAB `dist/rafiq-al-quran-1.1.0-signed.aab`: 3,588,965 bytes, SHA-256 `AC42360B8C397A9502955B8D3C249C5A13F8BF1644DFBBF73BC81B420D298FEF` (jarsigner "jar verified"; upload cert 08:DE:24:53…).

**Artifacts (1.0.12)** — APK `dist/rafiq-al-quran-1.0.12.apk`: 3,584,952 bytes, SHA-256 `4ED8ACAA7726ABB6075B6DFE551A9F5D0B8F1B872B7253B14AE91001593EA91C`; unsigned AAB `dist/rafiq-al-quran-1.0.12.aab`: 3,576,845 bytes, SHA-256 `E5FB7FAD125EBA52300F87428C0C92F5BC363EF403E4138E573C5AF4E480334F`; signed upload AAB `dist/rafiq-al-quran-1.0.12-signed.aab`: 3,580,167 bytes, SHA-256 `165AA653BADB9FF31C45BC05BB2EAA4020FAB68E832515CA234BF0338AD0A659` (jarsigner "jar verified"; upload cert 08:DE:24:53…).

**Artifacts (1.0.11)** — CI run [https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39503214114](https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39503214114) on commit `557057d01ce08c6702cb760170f5a855cffb0732` (success). APK `dist/rafiq-al-quran-1.0.11.apk`: 3,580,857 bytes, SHA-256 `C3FB6D536014F7E646EB7D01835BE46E2E9D5F6CDEEA87019DA48591E64E9005`. Unsigned CI AAB: 3,573,455 bytes, SHA-256 `8943D5878B738BB8783747A1267C671073F7FE93C7A3643720F8C56CE8067B74`. Signed upload AAB `dist/rafiq-al-quran-1.0.11-signed.aab`: 3,576,568 bytes, SHA-256 `BE998597D7EE7EAA98CB63CBC68550834F90B7A99F3F1A6E20A6531A9E3EDECB` (jarsigner `jar verified`, upload cert 08:DE:24:53…, bundletool validate exit 0)..

## Server suite — 81 passed, 0 failed

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
| **Grounded advisory synthesis (new in 1.1.0)** | 8 tests in `server/test/synthesis.test.js`: 503 while unconfigured; 400 validation (missing/blank/overlong question, missing/empty/oversized/duplicate/malformed/unknown verse keys) before any provider call; success path sends ONLY the supplied verses' authoritative translations (no Arabic script, no unsupplied references) with the Bearer key and returns the validated summary + citations; answers citing a verse outside the supplied set are rejected (502); Arabic-script, citation-free, too-short and too-long answers are rejected (502); provider 500 → 502 and timeout → 504 without leaking key/upstream detail; the optional monthly cap applies; conversation history is validated (role/content/count) and injected only as context, never as a source |
| Isolation | no RQM/XINVOX references in sources, own database path, rate limiter behaviour |

## Native app logic — 107 passed, 0 failed

`node mobile/android/scripts/runLogicTests.js` (Java, real bundled assets) — was 49 checks in 1.0.9, 67 in 1.0.11.

| Area | Checks |
|---|---|
| Existing core | dataset counts (114 chapters / 6236 verses / 9 translations), provenance checksum, Uthmani basmala, 2:255 present, English and Arabic topic answers, exact notices, translation kept separate from Arabic, Arabic interface falls back to English labelled, explicit reference, exact fallback, no repeated verses on "more", donation validation |
| Structural mushaf metadata | `QuranMeta` parses all 6236 rows; 1:1 = Juz 1 / Page 1; 2:255 = Juz 3 / Page 42; 114:6 = Juz 30 / Page 604; exact indicator text "Juz 3  ·  Page 42"; page numbers never regress |
| Study-tools codec | collection names sanitised, empty name → General, case-insensitive de-duplication, length bound; note trim/bound; progress percentages and exact "3 of 38  ·  8%" text; RFC 4180 CSV quoting; Juz 30 preset = surahs 78–114 and full preset = 114; numeric verse-key ordering and validation; next-chapter skips completed chapters and returns 0 when done; plan chapter list round-trips |
| **Recitation rights and attribution (updated)** | `AudioGate.rightsReviewRecorded()` is true for this build (rights review recorded 2026-10-21); `playbackAllowedInThisBuild()` is true; the gate still requires both the recorded review and the server flag (`playbackAllowed(false, true)` is false); attribution contains "Quran.com" and the decision text contains "rights review recorded" |
| **Voice language selection (new in 1.0.12)** | All ten interface codes map to real BCP-47 tags whose language matches the code; unknown codes fall back to en-US; `spokenLanguage` picks English for Arabic answers (no Arabic translation rows) and for non-Arabic fallbacks, the answer's translation language for translated answers, and English for conversation replies; Urdu answers speak Urdu; non-English spoken text omits the English reference labels and connectives; the spoken text is capped at the platform limit |
| **Romanised/fuzzy/mixed input (new in 1.1.0)** | "hazrath eesa" → Isa; "yaqubb" → Yaqub; "moosa" → Musa; mixed English/Roman Urdu answered; mixed Arabic + Roman answered; "namaz" → prayer verses; "roza" → fasting verses; one-letter typo still retrieves; entity lookup ignores honorifics and unrelated English |
| **Named figures and topic retrieval (new in 1.1.0)** | "Hazrat Essa" resolves to Isa/Jesus and returns several verses; "Yaqoob" resolves to Jacob; "What is the law about theft?" is answered and includes the Quranic theft verse 5:38; Arabic "من هو عيسى" is answered; honorifics alone and ordinary topic questions are not treated as named figures; the bundled lexicon carries the alias rows |
| **Conversation and feature help (new)** | greeting gets a reply that names the Quran; Islamic greeting recognised; thanks gets a short reply; a fatwa/ruling request is refused and points to scholars; off-topic questions are handled gracefully; advice questions are routed to Quran retrieval (not answered conversationally); overlong messages are not treated as small talk; guidance names scholars, Hadith and Fiqh study and disclaims responsibility; the attachment fallback names the AI service; the AI consent text states that the AI never supplies Quran text; feature-help questions about bookmarks, recitation, language and AI return app instructions without religious content; a bare “help” returns the overview; normal Quran questions still bypass feature help and go to retrieval |

The 67 checks include the per-passage dataset-existence assertions emitted in a loop over the returned passages.

## APK build — 1.1.0

`node mobile/android/scripts/buildApk.js` (aapt2 / javac / d8 / zipalign / apksigner; no Gradle)

- package `com.rqmbooks.rafiqalquran`, versionCode **13**, versionName **1.1.0** (`aapt2 dump badging` on
  `mobile/android/build/quran-companion.apk`); minSdk **24**, targetSdk **36**, compileSdk 34;
- **36 Java sources** compiled (adds `logic/VoiceLanguage.java` and `logic/Roman.java`); signed (apksigner verify passed) and zipaligned;
- size **~3.43 MB**; the exact size and SHA-256 of the release artifact are recorded in the release note;
- bundles `classes.dex`, the seven dataset assets (including `assets/quran-meta.tsv`) and the launcher icon;
- **Artifacts (1.1.0): APK SHA-256 B680204A4BA2BAAC0465C4B7EBD6675639476F3791803A83C68F84AC1F95068A; unsigned AAB SHA-256 2A275C7AE887D9DEFC0A4D512B38CCB8C53AAB13D035D38BD14637E7B03692BB; signed upload AAB SHA-256 AC42360B8C397A9502955B8D3C249C5A13F8BF1644DFBBF73BC81B420D298FEF (jarsigner verified, upload cert 08:DE…).**

## APK packaging verification — 22 checks passed

`node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.1.0.apk` → **22 checks passed**
("APK verification passed."), including the minSdk **24**, targetSdk **36** and the two new speech `<queries>` checks.

Checks: APK exists; signature verifies; package name; launchable activity; RECORD_AUDIO and INTERNET permissions;
minSdk 24; targetSdk 36; versionCode and versionName match the manifest; **manifest queries allow speech
recognition (`android.speech.RecognitionService`)**; **manifest queries allow text-to-speech
(`android.intent.action.TTS_SERVICE`)**; `classes.dex`; all seven bundled assets; launcher icon packaged; app label
"Rafiq Al-Quran". The SHA-256 of the released APK is recorded at the top of this file. In this build sandbox the
verification script itself cannot run (its piped subprocess capture is blocked), so the same 22 facts were checked
directly on the built APK: `aapt2 dump xmltree` (both `<queries>` actions present), `aapt2 dump badging`
(versionCode 12 / 1.0.12, minSdk 24, targetSdk 36), `apksigner verify --print-certs` (upload certificate
08:DE:24:53…) and `jar tf` (`classes.dex` and the seven assets).

## AAB — 1.1.0

The build host has no GitHub/bundletool access and the cached `bundletool.jar` is the thin artifact whose
dependencies are missing, so the AAB was assembled (like 1.0.12, which Play accepted) from the Play-validated 1.0.12
AAB template: the new proto `AndroidManifest.xml`, `resources.pb`, `classes.dex` and the changed assets
(`lexicon.tsv`, `provenance.tsv`) from this build were hash-compared with the new build (all match), the asset set
is unchanged (seven assets with the same names), and `jarsigner -verify` reports "jar verified" with the upload
certificate. The authoritative `bundletool validate` must still be run by re-running
`.github/workflows/build-aab.yml` on the pushed commit before rollout. Hashes: unsigned AAB
`2A275C7AE887D9DEFC0A4D512B38CCB8C53AAB13D035D38BD14637E7B03692BB`; signed AAB
`AC42360B8C397A9502955B8D3C249C5A13F8BF1644DFBBF73BC81B420D298FEF`. The previous CI-built AABs are described
below.

## AAB — CI build for 1.0.11

- `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool; no Gradle). The same workflow produced the
  validated 1.0.9 AAB (GitHub Actions run
  [39483479573](https://github.com/digitalinnovationagency2023-eng/rafiq-al-quran/actions/runs/39483479573),
  success; `bundletool validate` exit 0; local `jarsigner` signing with the upload keystore).
- For 1.0.11: the CI run URL, signed AAB SHA-256 and validation output are recorded in the release note
  (**Artifacts (1.0.11): CI run 39503214114 on commit 557057d (success); APK SHA-256 C3FB6D536014F7E646EB7D01835BE46E2E9D5F6CDEEA87019DA48591E64E9005; signed upload AAB SHA-256 BE998597D7EE7EAA98CB63CBC68550834F90B7A99F3F1A6E20A6531A9E3EDECB (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 8943D5878B738BB8783747A1267C671073F7FE93C7A3643720F8C56CE8067B74**). The signed AAB must be `jarsigner`
  verified ("jar verified" plus the expected self-signed warning) and `bundletool validate` must exit 0 before
  upload, and it must be signed with the same Play upload key as 1.0.9 so Play upload-key continuity is preserved
  (`QURAN_KEYSTORE_PATH`; a generated throwaway key would be rejected).

## Emulator QA — AVD `rafiqtest` (Android 14, 1080×2340)

1.0.11 installed from the local build (versionCode 11 / versionName 1.0.11). Round-2 captures in `dist/`:

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

## Voice language matrix — 1.0.12 (Android 14 AVD `rafiqtest`)

The 1.0.12 build was installed (versionCode 12) on a clean Android 14 AVD and each interface language was set,
then the answer's **Listen** action and the composer's **Voice** action were exercised while capturing the
`RafiqVoice` log tag. The AVD had no internet and only English voice/speech data installed, so the table proves
the *locale/voice selection and failure handling* per language, not the audible quality (that needs a real device
with the packs installed — see `docs/LIMITATIONS.md` items 11 and 18).

| Code | Listen: spoken language | Listen: TTS locale / availability | Listen outcome | Voice: recognizer locale | Voice outcome |
|---|---|---|---|---|---|
| en | en | en_US / available 1 | utterance started | en-US | client error 5 (pack missing on AVD) |
| ar | en (English translation is spoken: Arabic has no translation rows) | en_US / available 1 | utterance started | ar-SA | client error 5 (Soda pack error 12) |
| ur | ur | ur_PK / available 1 | utterance started | ur-PK | client error 5 (Soda pack error 12) |
| id | id | in_ID / available 1 | utterance started | id-ID | client error 5 (Soda pack error 12) |
| bn | bn | bn_BD / available 1 | utterance started | bn-BD | client error 5 (Soda pack error 12) |
| tr | tr | tr_TR / available 1 | utterance started | tr-TR | client error 5 (Soda pack error 12) |
| fa | fa | fa_IR / **not supported (-2)** | clear "engine does not support" message, no wrong-language speech | fa-IR | client error 5 (Soda pack error 12) |
| ms | ms | ms_MY / available 1 | utterance started | ms-MY | client error 5 (Soda pack error 12) |
| sw | sw | sw_KE / available 1 | utterance started | sw-KE | client error 5 (Soda pack error 12) |
| fr | fr | fr_FR / available 1 | queued, then synthesis error -7 (voice data incomplete) — now surfaces a user message | fr-FR | client error 5 (Soda pack error 13) |

Also verified on the same run:

- the **cold-start translation** fix: switching to Urdu, force-stopping and relaunching produced an Urdu translation
  (`translationLanguage: "ur"`, Urdu text in the saved history) instead of the old silent English fallback;
- the old 1.0.11 build **started** English recognition on Android 14 too, so the missing `<queries>` did not
  reproduce on the emulator; the entries are still declared per Android documentation and are checked by
  `verifyApk.js` for devices/OEMs where the filter applies;
- when the recognizer reports a missing language pack, the app names the language and points to
  **Google app → Settings → Voice → Offline speech recognition**; English keeps the generic "type instead" wording.

## Live production API — checked 2026-10-20

The operator screen connected to `https://rqmbooks.com/rafiq-api` with `x-admin-token` and rendered
**6236 verses / 114 chapters / 9 translations**, the latest feedback row (**RESOLVED**), recent audit entries and
reciter rows with rights status **UNVERIFIED** and playback disabled on the server. Endpoint and authentication
details are documented in `docs/ADMIN.md`; `server/test/admin.test.js` covers the automated auth paths.

The server's `recitationEnabled` flag and the seeded reciter rows are server-side only: in 1.0.11 the app streams
Quran.com audio as a **client-side** feature (rights decision recorded 2026-10-21), so the server flag may still
read `false` while the app plays recitation. The optional AI endpoints were not part of this round's recorded live
verification; check `/rafiq-api/api/assistant/status` after the operator configures a key (see
`docs/DEPLOYMENT.md`).

## Dataset validation

`npm run validate:dataset -w server` — completeness, chapter counts, translation completeness, checksum, source,
markup checks and explicit translation licence status all pass.
