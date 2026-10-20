# Rafiq Al-Quran: AI Assistant

**Status: 1.0.9** — native Android release, versionCode 9, versionName 1.0.9 (Android 7.0+, minSdk 24, targetSdk 36).

A free, Quran-only **native Android app** with an offline Quran reader, study tools and a conversational
verse-finding assistant, plus an optional API. The complete Quran — all 114 surahs — is bundled with the Arabic text
and nine identified translations, and every passage shows its surah/verse reference and the source provenance
separately. It is not a website wrapper: the client is a Java Android application with no WebView and no Gradle,
the Quran dataset is bundled in the APK, and reading, search and answers work fully offline.

## What's in the 1.0.9 build

- **Offline reader for all 114 surahs**, with a Juz and Madani page indicator on every verse, per-verse actions
  (copy, share, save, note, listen), reading progress and a continue-reading card.
- **Nine translations**, each shown with its source, checksum provenance and licence status, with text-size controls
  and a side-by-side Arabic/translation layout.
- **Light and dark Mushaf-inspired themes** across every screen.
- **Study tools**: bookmark collections, personal notes on any verse, saved searches, and reading plans with
  progress — stored on the device only, with CSV/plain-text export through the Android share sheet.
- **Rights-gated recitation player UI**: the verse-audio screen is present but gated off in this build
  (Quran.com audio disabled until the recitation rights review is recorded); it shows "rights pending" and no
  audio is streamed, cached or stored.
- **Operator-only admin screen**: token-protected and read-only, connected to the app's own live API
  (`https://rqmbooks.com/rafiq-api`); no operator token is bundled with the app.
- **No billing, subscriptions or donations** in this release — the premium and donation screens are present but
  disabled. **No account, no ads, no analytics**; study data never leaves the device unless the user exports it.

## Deliverables in this folder

| Path | What it is |
|---|---|
| `dist/rafiq-al-quran-1.0.9.apk` | Signed, installable release APK (versionCode 9 / 1.0.9, Android 7.0+, 3.40 MB) |
| `dist/rafiq-al-quran-1.0.9-signed.aab` | Signed release AAB for Play upload (CI-built, `jarsigner`-signed locally) |
| `dist/rafiq-al-quran-1.0.9.aab` | Unsigned AAB produced by the GitHub Actions workflow (bundletool) |
| `dist/screenshot-*.png` | 21 emulator QA screenshots (AVD `rafiqtest`, Android 14, 1080×2340) |
| `mobile/android/` | Native Android client (Java, Android SDK; no Gradle, no WebView) |
| `server/` | Optional API: feedback, donation configuration/checkout, premium entitlements, admin |
| `web/` | Optional browser client used for preview and administration (not the product app) |
| `server/data/quran-app.db` | Imported dataset (SQLite) used to generate bundled assets |
| `docs/` | Provenance, limitations, deployment, admin, privacy/security, tests |

## The app, in one paragraph

Open the app → ask a question (typed or with the device microphone) → the app retrieves verses from the bundled
dataset → it shows the exact Arabic text, the identified translation, the reference, and a source panel with the
dataset checksum and the translation licence status. Every substantive answer carries the required notice. When
retrieval is not confident, the app shows the exact fallback wording and never claims that the Quran does not
address the topic. Conversations, bookmarks, notes, reading plans and reading history are stored on the device;
conversations can be deleted with **New conversation**.

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

# 3. Build, align and sign the APK
node mobile/android/scripts/buildApk.js
# -> mobile/android/build/quran-companion.apk
```

Install on a device with `adb install -r dist/rafiq-al-quran-1.0.9.apk` or by opening the APK on the phone.
The APK signing key is generated under `mobile/android/build/` and is **not** a Play Store upload key: generate and
store a production keystore before publishing (documented in `docs/DEPLOYMENT.md`). Uninstall an earlier install
before installing a locally rebuilt APK — a clean build regenerates the local signing key, so the signature changes.

The release AAB is built by `.github/workflows/build-aab.yml` (aapt2/javac/d8 plus bundletool, no Gradle) and
signed locally with `jarsigner`; the upload keystore is stored outside version control
(`mobile/android/build/quran-release.keystore` is gitignored and must never be committed).

## Run the optional API

```bash
cp .env.example server/.env     # optional
npm run dev -w server           # http://localhost:8787
```

Then set `mobile/android/res/values/strings.xml` → `api_base_url` to that origin and rebuild the APK if you want
feedback and donation checkout online. Leaving it empty produces a fully offline build.

## Tests

| Suite | Command | Result |
|---|---|---|
| Server data, retrieval, API, donations, safety, admin auth | `npm run test -w server` | 58 passed |
| Native app core logic against real bundled assets | `node mobile/android/scripts/runLogicTests.js` | 49 passed |
| APK packaging check | `node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.9.apk` | 20/20 checks passed (signature, manifest, versionCode/Name, bundled assets incl. `quran-meta.tsv`, icon) |
| Release artifacts | CI AAB run 39447188748 → `jarsigner`/`bundletool validate`; AAB-derived APK installed on AVD `rafiqtest` | versionCode 9 / 1.0.9 confirmed via `dumpsys` |
| Emulator QA | 21 screenshots in `dist/` (home, reader, study tools, player gate, admin, share sheets) | flows verified; see `docs/TESTS.md` |

## Guarantees encoded in the code and tests

- Only the text and translations in the imported dataset are ever displayed; every passage is re-verified against
  the dataset before it is shown, and answers with unknown references cannot be rendered.
- The exact English and Arabic notices and the exact fallback wording are asserted by tests.
- No Hadith, Tafsir, external commentary or model-written religious text is used. The optional AI interpreter may
  only return search terms; it never supplies religious content.
- Recitation is a separate rights-gated feature and is disabled until recording rights are recorded; the player
  screen shows only the pending-rights state and never streams or stores audio.
- Study data (bookmarks, notes, collections, saved searches, reading plans, reading position) is device-local and
  leaves the app only when the user chooses a share target for an export.
- Billing, subscriptions and donation processing are not enabled in this release; premium and donation screens show
  their disabled state.
- The app and API use their own database and never read RQM Books accounting or customer data.

## Documentation

- `docs/PROVENANCE.md` — sources, licences, checksums, how to re-import and re-verify.
- `docs/LIMITATIONS.md` — what the app cannot do and the known open items.
- `docs/DEPLOYMENT.md` — API deployment, APK signing/publishing, configuration.
- `docs/ADMIN.md` — operator-only admin API (token header, endpoints, live deployment notes).
- `docs/PRIVACY-SECURITY.md` — data handling, voice providers, secret management.
- `docs/DONATIONS-COMPLIANCE.md` — donation and premium compliance gates.
- `docs/TESTS.md` — measured test results.
