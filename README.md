# Rafiq Al-Quran: AI Assistant

**Status: 1.0.10** — native Android release, versionCode 10, versionName 1.0.10 (Android 7.0+, minSdk 24, targetSdk 36).

A free, Quran-only **native Android app** with an offline Quran reader, study tools, a conversational
verse-finding assistant and enabled Quran.com recitation, plus an optional API. The complete Quran — all 114
surahs — is bundled with the Arabic text and nine identified translations, and every passage shows its
surah/verse reference and the source provenance separately. It is not a website wrapper: the client is a Java
Android application with no WebView and no Gradle, the Quran dataset is bundled in the APK, and reading, search,
answers, conversation replies and study tools work fully offline. Recitation streams from Quran.com and the
optional AI assistance needs the operator to configure a free key; see below.

## What's in the 1.0.10 build

- **Offline reader for all 114 surahs**, with a Juz and Madani page indicator on every verse, per-verse actions
  (copy, share, save, note, listen), reading progress and a continue-reading card.
- **Nine translations**, each shown with its source, checksum provenance and licence status, with text-size controls
  and a side-by-side Arabic/translation layout.
- **Light and dark Mushaf-inspired themes** across every screen.
- **Study tools**: bookmark collections, personal notes on any verse, saved searches, and reading plans with
  progress — stored on the device only, with CSV/plain-text export through the Android share sheet.
- **Quran.com recitation is enabled** (publisher rights review recorded 2026-10-21): verse audio streams from
  Quran.com's public CDN over HTTPS with play/pause, replay, seek, previous/next verse and automatic advance to the
  next verse, showing the reciter name and **"Source: Quran.com"**. Reciters: Mishary Rashid Alafasy, Abdul Basit
  Abdus Samad (Murattal), Muhammad Siddiq Al-Minshawi (Murattal). Nothing is downloaded, cached or stored on the
  device; an internet connection is required.
- **Local conversation layer**: deterministic, neutral replies for greetings, thanks, identity/capability questions
  and simple off-topic messages, with no Quran text or interpretation of its own. Advice/topic questions always go
  through the local Quran retrieval; when it cannot find a confident verse the app shows the exact safe fallback
  plus a **"Where to look next"** card (qualified scholar/local imam, Hadith and Fiqh study with a qualified
  teacher, general reference for non-Quran questions, explicit no-responsibility statement). Ruling, fatwa and
  tafsir requests are refused and pointed to scholars.
- **In-app feature help**: ask how to use the app — “how do I bookmark a verse?”, “how do I listen to recitation?”, “change the language”, “attach a file”, “privacy”, “is there a paid plan?” — and the local conversation layer answers with step-by-step instructions. It needs no AI service and writes no religious content, so feature questions never become fatwas.
- **Listen in Browse surahs**: every surah row now has a “▷ Listen” action that opens the Quran.com recitation player at that surah's first verse.
- **Optional AI assistance** (server `/api/assistant`): **off by default**. The operator can enable a free-tier
  Google AI Studio / Gemini key; it is used only for short neutral small-talk replies, search-word suggestions and
  OCR of an attached screenshot — never for Quran text or interpretation. There is a one-time consent notice, a
  30-calls-per-day device cap, and the app falls back to local behaviour when AI is off, unavailable or limited.
- **Attachments**: the composer's 📎 Attach action reads `.txt`/`.md` on the device, extracts `.docx` text on the
  device, and uses the optional AI service (vision/OCR) for PNG/JPEG/WebP images only. **Reading images requires the
  operator-configured free AI key; without it the app works locally and images are not readable.** PDFs are not
  supported yet (clear message).
- **Accessibility and page fixes**: the language chooser is now a full-width dialog with 10 large rows (English
  name, native name, code) instead of a tiny spinner; the "Listen" action initialises TTS lazily, falls back to
  English and now speaks; mojibake in the in-app pages is fixed; the Support page no longer shows raw config keys or
  a dead checkout form; the former "Optional tools" page now presents the free study tools; the operator admin
  screen explains its purpose before use.
- **Operator-only admin screen**: token-protected and read-only, reached only by tapping the app title five
  times quickly (hidden from customers, session-only unlock); connected to the app's own live API
  (`https://rqmbooks.com/rafiq-api`); no operator token is bundled with the app.
- **No billing, subscriptions or donations** in this release — the premium and donation screens show their disabled
  state. **No account, no ads, no analytics**; study data never leaves the device unless the user exports it.

## Deliverables in this folder

| Path | What it is |
|---|---|
| `dist/rafiq-al-quran-1.0.10.apk` | Signed, installable release APK (versionCode 10 / 1.0.10, Android 7.0+, ~3.41 MB); built locally from `mobile/android/build/quran-companion.apk` — SHA-256 recorded in the release note |
| `dist/rafiq-al-quran-1.0.10-signed.aab` | Signed release AAB for Play upload (CI-built, `jarsigner`-signed locally) — artifact SHA-256/CI run recorded in the release note |
| `dist/rafiq-al-quran-1.0.10.aab` | Unsigned AAB produced by the GitHub Actions workflow (bundletool) |
| `dist/` screenshots | 31 `screenshot-*.png` captures plus 2 `qa-*` home captures (33 PNGs total, 1080×2340, AVD `rafiqtest`, Android 14); the 10 new 1.0.10 captures are `qa-r2-home.png` and the `screenshot-qa-r2-*.png` files |
| `mobile/android/` | Native Android client (Java, Android SDK; no Gradle, no WebView; 34 Java sources) |
| `server/` | Optional API: feedback, donations configuration/checkout, premium entitlements, admin, optional AI assistance |
| `web/` | Optional browser client used for preview and administration (not the product app) |
| `server/data/quran-app.db` | Imported dataset (SQLite) used to generate bundled assets |
| `docs/` | Provenance, limitations, deployment, admin, AI assistance, privacy/security, tests |

## The app, in one paragraph

Open the app → ask a question (typed, with the device microphone, or by attaching a file) → the app retrieves
verses from the bundled dataset → it shows the exact Arabic text, the identified translation, the reference, and a
source panel with the dataset checksum and the translation licence status. Every substantive answer carries the
required notice. When retrieval is not confident, the app shows the exact fallback wording, a "Where to look next"
card, and never claims that the Quran does not address the topic. Recitation opens from any verse and streams
Quran.com audio with attribution; conversation replies stay neutral and local. Conversations, bookmarks, notes,
reading plans and reading history are stored on the device; conversations can be deleted with **New conversation**.

## Contact

Digital Innovative Agency FZ LLC
- Email: info@rqmbooks.com
- Phone / WhatsApp: +971 9 501 0254
- WhatsApp chat: https://wa.me/97195010254

## Build the APK

Requirements: JDK 17+, Android SDK with `platforms;android-34` and `build-tools;34.0.0`
(set `ANDROID_HOME`, default `%USERPROFILE%\android-sdk`).

```bash
# 1. Import the dataset and translations (once; needs network)
npm install
npm run import:quran -w server

# 2. Export the compact assets the app bundles (includes quran-meta.tsv)
node mobile/android/scripts/exportAssets.js

# 3. Build, align and sign the APK (34 Java sources; versionCode 10 / 1.0.10)
node mobile/android/scripts/buildApk.js
# -> mobile/android/build/quran-companion.apk
```

Copy the APK to `dist/rafiq-al-quran-1.0.10.apk` and verify it:
`node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.10.apk` → **20 checks passed**. Install on a
device with `adb install -r dist/rafiq-al-quran-1.0.10.apk` or by opening the APK on the phone. `buildApk.js`
signs with the Play upload keystore supplied via `QURAN_KEYSTORE_PATH` (default
`%USERPROFILE%\.secrets\quran-release.keystore`), so rebuilding does not change the signature and it matches the
signing key of the Play upload AAB; keep that keystore outside the repository and never commit it. If no keystore
is found the build warns loudly and generates a throwaway key — such an APK must not be uploaded as an update.
Uninstall an earlier install before installing a locally rebuilt APK when the signature changes.

The release AAB is built by `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool, no Gradle) and
signed locally with `jarsigner`; sign it with the same Play upload keystore as the APK (kept outside version
control, by default `%USERPROFILE%\.secrets\quran-release.keystore`; `mobile/android/build/quran-release.keystore`
is only a gitignored copy). The upload keystore must never be committed.

## Run the optional API

```bash
cp .env.example server/.env     # optional
npm run dev -w server           # http://localhost:8787
```

Then set `mobile/android/res/values/strings.xml` → `api_base_url` to that origin and rebuild the APK if online
feedback, optional AI assistance and the operator screen are wanted. Leaving it empty produces a fully offline
build (recitation still streams directly from Quran.com because the player is client-side).

The optional AI assistance is off by default. It needs an operator-configured free-tier key on the server (for
example Google AI Studio / Gemini) and the app's one-time consent; full configuration, free-tier setup, limits and
verification are in `docs/AI-ASSISTANT.md` and `docs/DEPLOYMENT.md`. Never put a provider key in the app or the
repository.

## Tests

| Suite | Command | Result |
|---|---|---|
| Server data, retrieval, API, donations, safety, admin auth, AI assistance | `npm run test -w server` | 73 passed (58 baseline + 15 assistant) |
| Native app core logic against real bundled assets (incl. conversation and recitation rights) | `node mobile/android/scripts/runLogicTests.js` | 67 passed (includes feature-help checks) |
| APK packaging check | `node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.10.apk` | 20/20 checks passed (signature, manifest, versionCode/Name, minSdk 24/targetSdk 36, bundled assets incl. `quran-meta.tsv`, icon) |
| Release artifacts | CI AAB workflow + local `jarsigner`/`bundletool validate` | **Artifacts (1.0.10): CI run 39498315274 on commit c2deeba (success); APK SHA-256 C2FF8DE972E821F56B0D135B6229E5276668739521A565DA8EFC360C6FEC7EF4; signed upload AAB SHA-256 FF814F30D8954D5752D048F0CE3B4A5754909D64A20F323F2348AEEF8475CCC7 (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 368920A490AD1CF958C5580D2705D3A0ACFE0FF8B7F0C8FE452AB708D8A2A5C8** |
| Emulator QA | 1.0.10 round-2 captures in `dist/` (home + attach, language dialog, conversation, recitation player, text attachment, TTS/answer, support, admin) | flows verified; see `docs/TESTS.md` |

## Guarantees encoded in the code and tests

- Only the text and translations in the imported dataset are ever displayed; every passage is re-verified against
  the dataset before it is shown, and answers with unknown references cannot be rendered.
- The exact English and Arabic notices and the exact fallback wording are asserted by tests.
- No Hadith, Tafsir, external commentary or model-written religious text is used. The optional AI assistant is
  limited to short neutral small talk, search-word suggestions and OCR of the user's own image; it never supplies
  Quran text, translations, verse references or interpretation, and server-side sanitisation drops any attempt.
- Recitation is enabled with a recorded publisher rights review (2026-10-21) and always shows "Source: Quran.com";
  the app streams Quran.com's public CDN and never downloads, caches, stores, clones or redistributes the audio.
- Conversation replies are deterministic and leave Quran content entirely to the bundled dataset; ruling/fatwa/
  tafsir requests are refused and pointed to qualified scholars.
- Attachments: `.txt`/`.md`/`.docx` are read on the device; images can only be read through the optional AI service
  (so without the operator's key the app works locally and images are not readable); PDFs are unsupported.
- Study data (bookmarks, notes, collections, saved searches, reading plans, reading position) is device-local and
  leaves the app only when the user chooses a share target for an export.
- Billing, subscriptions and donation processing are not enabled in this release; premium and donation screens show
  their disabled state.
- The app and API use their own database and never read RQM Books accounting or customer data.

## Documentation

- `docs/PROVENANCE.md` — sources, licences, checksums, recitation source/rights record, AI scope, how to re-import.
- `docs/LIMITATIONS.md` — what the app cannot do and the known open items.
- `docs/DEPLOYMENT.md` — API deployment, optional AI assistance setup and limits, APK signing/publishing.
- `docs/ADMIN.md` — operator-only admin API (token header, endpoints, recitation/AI flag semantics, live notes).
- `docs/AI-ASSISTANT.md` — the optional AI layer: endpoints, sanitisation, fallbacks, privacy and tests.
- `docs/PRIVACY-SECURITY.md` — data handling, voice providers, secret management.
- `docs/DONATIONS-COMPLIANCE.md` — donation and premium compliance gates.
- `docs/TESTS.md` — measured test results.
