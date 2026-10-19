# Quran Companion

A free, Quran-only conversational **native Android app** with an optional API. It helps users locate and read
verses, with the Quranic Arabic text, an identified translation, the surah/verse reference and the source
provenance shown separately. It is not a website wrapper: the client is a Java Android application with no
WebView, the Quran dataset is bundled in the APK, and reading/search/answers work fully offline.

## Deliverables in this folder

| Path | What it is |
|---|---|
| `dist/quran-companion-1.0.0.apk` | Signed, installable release APK (Android 6.0+, ~3.4 MB) |
| `mobile/android/` | Native Android client (Java, Android SDK; no Gradle, no WebView) |
| `server/` | Optional API: feedback, donation configuration/checkout, premium entitlements, admin |
| `web/` | Optional browser client used for preview and administration (not the product app) |
| `server/data/quran-app.db` | Imported dataset (SQLite) used to generate bundled assets |
| `docs/` | Provenance, limitations, deployment, privacy/security, tests |

## The app, in one paragraph

Open the app → ask a question (typed or with the device microphone) → the app retrieves verses from the bundled
dataset → it shows the exact Arabic text, the identified translation, the reference, and a source panel with the
dataset checksum and the translation licence status. Every substantive answer carries the required notice. When
retrieval is not confident, the app shows the exact fallback wording and never claims that the Quran does not
address the topic. Conversations are stored on the device and can be deleted with **New conversation**.

## Build the APK

Requirements: JDK 17+, Android SDK with `platforms;android-34` and `build-tools;34.0.0`
(set `ANDROID_HOME`, default `%USERPROFILE%\android-sdk`).

```bash
# 1. Import the dataset and translations (once; needs network)
npm install
npm run import:quran -w server

# 2. Export the compact assets the app bundles
node mobile/android/scripts/exportAssets.js

# 3. Build, align and sign the APK
node mobile/android/scripts/buildApk.js
# -> mobile/android/build/quran-companion.apk
```

Install on a device with `adb install -r dist/quran-companion-1.0.0.apk` or by opening the APK on the phone.
The signing key is generated under `mobile/android/build/` and is **not** a Play Store upload key: generate and
store a production keystore before publishing (documented in `docs/DEPLOYMENT.md`).

## Run the optional API

```bash
cp .env.example server/.env     # optional
npm run dev -w server           # http://localhost:8787
```

Then set `res/values/strings.xml` → `api_base_url` to that origin and rebuild the APK if you want feedback and
donation checkout online. Leaving it empty produces a fully offline build.

## Tests

| Suite | Command | Result |
|---|---|---|
| Server data, retrieval, API, donations, safety | `npm run test -w server` | 53 passed |
| Native app core logic against real bundled assets | `node mobile/android/scripts/runLogicTests.js` | 25 passed |
| APK packaging check | `node mobile/android/scripts/verifyApk.js` | 15/15 checks passed (signature, manifest, bundled assets, dex) |
| Live API smoke | health, ask (EN/AR), fallback, sources, donations config, reciters, feedback, admin auth | all expected status codes |

## Guarantees encoded in the code and tests

- Only the text and translations in the imported dataset are ever displayed; every passage is re-verified against
  the dataset before it is shown, and answers with unknown references cannot be rendered.
- The exact English and Arabic notices and the exact fallback wording are asserted by tests.
- No Hadith, Tafsir, external commentary or model-written religious text is used. The optional AI interpreter may
  only return search terms; it never supplies religious content.
- Recitation is a separate rights-gated feature and is disabled until recording rights are recorded. Text-to-speech
  reads translations and summaries only and is labelled as not being a Quranic recitation.
- Donations are voluntary, off by default, and gated on the operator recording the compliance review. Premium tools
  are optional conveniences, disabled by default, and never affect Quranic content or core access.
- The app and API use their own database and never read RQM Books accounting or customer data.

## Documentation

- `docs/PROVENANCE.md` — sources, licences, checksums, how to re-import and re-verify.
- `docs/LIMITATIONS.md` — what the app cannot do and the known open items.
- `docs/DEPLOYMENT.md` — API deployment, APK signing/publishing, configuration.
- `docs/PRIVACY-SECURITY.md` — data handling, voice providers, secret management.
- `docs/DONATIONS-COMPLIANCE.md` — donation and premium compliance gates.
- `docs/TESTS.md` — measured test results.
