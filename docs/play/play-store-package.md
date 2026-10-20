# Rafiq Al-Quran — Play Store package (source copy)

Publisher: Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254

---

## Release metadata — shipped build 1.0.9

| Field | Value |
|---|---|
| App name | Rafiq Al-Quran: AI Assistant |
| Category | Education |
| Package name | `com.rqmbooks.rafiqalquran` |
| Version | versionCode **9** · versionName **1.0.9** |
| SDK range | minSdk **24** (Android 7.0) · targetSdk **36** (Android 16 — Play requirement for new apps since 31 Aug 2026) |
| Build toolchain | Android SDK 34 build-tools and platform (`compileSdk` 34, `targetSdk` 36), `aapt2` / `javac` / `d8` / `zipalign` / `apksigner`, **no Gradle**; **32 Java sources**. Edge-to-edge insets are handled in `Ui.init()` for Android 15+ |
| Release APK | `dist/rafiq-al-quran-1.0.9.apk` — 3.40 MB, signed, installable |
| AAB (unsigned, CI) | `dist/rafiq-al-quran-1.0.9.aab` — GitHub Actions artifact from `.github/workflows/build-aab.yml` (bundletool); 3,553,062 bytes |
| AAB (signed, Play upload) | `dist/rafiq-al-quran-1.0.9-signed.aab` — CI AAB (3,556,176 bytes) signed locally with `jarsigner` using the upload keystore; `jarsigner -verify` → "jar verified" |
| Signing key | Local upload keystore `mobile/android/build/quran-release.keystore` — gitignored, **never committed** |
| SHA-256 | APK `845E133AFF68D8D30EA49B2A2E0AA9BFA74F0895B0988A50D86FFDE6830F2104` · signed AAB `0D100D4A826BDB9E8B1AB7FB9F431BAA8F466EA197A2D04E7765B8E6AE6780D3` |
| Version history | versionCode was **1** on the earlier local build. No build has ever been uploaded to Google Play; this is the first upload candidate (versionCode 9). |
| CI verification | GitHub Actions "Build release AAB" run **39483479573** on commit `9350aee1d238a1bd9d81ee382987712689d60f64` — success; AAB validated with `bundletool validate` (exit 0). `jarsigner -verify` reports the expected self-signed certificate warning. |

---

## 1. Privacy Policy (public page copy)

**Last updated: 20 October 2026**

Rafiq Al-Quran is published by Digital Innovative Agency FZ LLC ("we", "us"). This policy explains what the app does with data.

**What stays on your device.** Your questions and the answers shown, bookmarks and bookmark collections, reading history, reading position, saved notes, saved searches, reading plans, and preferences (language, text size, theme, translation visibility, voice setting) are stored locally on your device. If you use the operator screen, the operator token is stored on the device only. No account is required to use the app.

**What may leave your device.** The core experience — reading the Quran, searching, receiving answers and using all study tools — works entirely offline using a dataset bundled inside the app. Data leaves the device only in these cases:
- **Feedback reports**, when you choose to send one. Only the category, your description, the interface language, the optional verse reference and optional contact detail are sent. Conversation text and diagnostics are included only if you explicitly tick the consent box.
- **The optional operator admin screen**, which is operator-only: it sends the operator token to the app's own API to read service status. It does not send your bookmarks, notes, reading history or other study data.
- **Optional online service checks** (anonymous availability requests when a data API is configured). No personal data is included in these requests.

Billing, subscriptions and donations are **not enabled in this release**, so no purchase or payment verification data is sent.

**Recitation.** The recitation player screen is present but rights-gated. No recitation audio is streamed, downloaded, stored or played in this release; the screen states that playback is disabled until the Quran.com recitation rights review is recorded.

**Voice features.** Speech input uses your device's speech recognition, which may be processed by your device platform provider. Spoken answers use your device's text-to-speech. The app does not record or upload audio. Voice features can be disabled in the app menu.

**Payments.** Billing, subscriptions and in-app purchases are not implemented or enabled in this release. The app does not collect or store card details. If paid tools are enabled in a future release, payment would be processed by Google Play and a purchase token sent to our server only to confirm entitlement.

**Donations.** Donation collection is not enabled in this release: the donation screen is shown disabled and no payment is requested. If enabled in a future release, donations would be voluntary, processed by the payment provider, and never required to read or search the Quran.

**What we never do.** We do not sell data, we do not run advertising, we do not use analytics identifiers, and the app never reads RQM Books accounting, customer, invoice or payroll data.

**Children.** The app is suitable for general audiences and does not knowingly collect personal information from children.

**Retention and deletion.** Locally stored data is removed when you clear the app's data or uninstall it. Feedback reports are kept for review and can be deleted on request. In-app conversations can be deleted with "New conversation", and the operator token can be removed with "Forget token".

**Your choices.** Use the app's Privacy page to review this information, disable voice, and delete conversations. Contact info@rqmbooks.com for data requests.

**Changes.** We will update this page when the app changes; the date above shows the latest revision.

---

## 2. Terms of Use (public page copy)

1. **Free core experience.** Reading, searching, study tools and the conversational verse-finding experience are free. Premium tools are not enabled in this release; if enabled in a future release they are conveniences and never change Quranic text, its sources, or the accuracy of an answer.
2. **Nature of the service.** The app is an AI-assisted search tool for Quranic verses and identified translations. It is **not** a religious authority, does not issue fatwas or rulings, and does not provide authoritative interpretation. Always consult qualified scholars where interpretation is required.
3. **Sources.** Quranic text and translations are served from the dataset bundled with the app; each passage shows its source, version and checksum. Translation licence status is shown explicitly. Juz, Madani page and hizb data is structural layout metadata imported from the public Quran.com API v4; it is not scripture.
4. **Acceptable use.** Do not use the app to misrepresent its output as divine communication or as a religious ruling.
5. **Donations.** Donation collection is not enabled in this release. If enabled in a future release, donations are voluntary, are not a purchase, and confer no additional religious authority or access to Quranic content.
6. **Premium tools.** Not enabled in this release. If enabled in a future release, billing, renewal and cancellation would be handled by Google Play under its terms, with prices and renewal periods shown before purchase.
7. **Recitation.** The recitation player screen is gated and disabled until the Quran.com recitation rights review is recorded; no recitation audio is provided in this release.
8. **Study data.** Bookmarks, collections, personal notes, saved searches and reading plans are stored on your device and are not synced to an account or another device.
9. **Liability.** The app is provided "as is". To the extent permitted by law, we are not liable for decisions made in reliance on search results.
10. **Contact.** Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254.

---

## 3. Google Play Data Safety answers

| Question | Answer |
|---|---|
| Does your app collect or share any required user data types? | No required collection and no account. Optional feedback is the only user data that can leave the device in this release; purchases and donations are not implemented. |
| Data types | App activity: "Other user-generated content" — feedback description, optional verse reference, optional contact detail. Collected only when the user submits a report, not shared with third parties, optional and user-initiated. Consented question text/diagnostics are included only when the user ticks the consent box. |
| Stays on the device (not collected) | Bookmarks and bookmark collections, personal notes, saved searches, reading plans, reading history, reading position, conversation history, language/theme/text-size/voice preferences, and the operator token (operator screen only). Never transmitted to us. |
| Is data encrypted in transit? | Yes — HTTPS for the app's own API (public API base `https://rqmbooks.com/rafiq-api`). |
| Can users request deletion? | Yes — clear app data or uninstall; "New conversation" clears conversations; "Forget token" removes the operator token; feedback can be deleted by email request. |
| Data collected for advertising? | No ads, no advertising identifiers. |
| Data sold? | No. |
| Account creation? | No account required; all functionality available without login. |
| Location / contacts / files / photos / audio? | Not collected. If voice input is used, microphone audio is processed by the device's speech recogniser; the app does not record, store or upload it. Recitation audio: none streamed or stored in this release. |
| Purchase history | None — billing, subscriptions and donations are not implemented or enabled in this release. |
| Operator admin screen | Operator-only and read-only: sends only the operator token to the app's own API to read status data; it does not send user study data and stores nothing from the responses in the app's study data. |

Declarations: **No ads**, **No news app**, **No government app**, **All functionality available without login**, **Content rating**: Everyone / 3+ (religious reference content, no violence, no user-generated public content).

---

## 4. Store listing copy

**App name:** Rafiq Al-Quran: AI Assistant

**Short description (≤80 chars):** Find Quran verses with a respectful, source-transparent AI assistant.

**Full description:**
Rafiq Al-Quran is a free, Quran-focused Android app for reading and studying the Quran, online or offline. The complete Quran — all 114 surahs — is bundled with the Arabic text and nine identified translations, and every passage shows its source, checksum and translation licence status.

The app is Quran-only: no Hadith, Tafsir or external commentary, and it never writes or invents verses. The verse-finding assistant retrieves passages from the bundled dataset only. When it cannot find sufficiently relevant verses, it says so with an exact fallback notice instead of guessing.

Reading and study
- Offline reader for all 114 surahs, with a Juz and Madani page indicator on every verse.
- Nine translations, each labelled with its source, author and licence status.
- Light and dark Mushaf-inspired themes, text-size controls and a side-by-side Arabic/translation layout.
- Bookmark collections, personal notes on any verse, saved searches and reading plans with progress. This study data stays on your device.
- Export bookmarks, notes and references as CSV or plain text through the Android share sheet — nothing is uploaded automatically.
- Verse actions: copy, share, save, note, and a verse-by-verse view with previous/next navigation.
- Optional voice input and read-aloud through the device's own speech services; no audio is recorded or uploaded.

Recitation
- The recitation player screen ships in a rights-pending state: Quran.com audio is disabled until the recitation rights review is recorded for that source. No audio is streamed, stored or played in this build, and the screen says so.

For operators
- An optional operator-only admin screen (token-protected, read-only) connects to the app's own API for service status. It is not part of the public experience, and no operator token is bundled with the app.

Free and private
- No account, no ads, no analytics, no data selling. Reading and study work offline.

Important: this app is an AI-assisted search tool. It is not a religious authority, does not issue fatwas or rulings, and does not provide authoritative interpretation. Please consult qualified scholars where interpretation is required.

Publisher: Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254

**Category:** Education · **Contact email:** info@rqmbooks.com · **Website:** the policy pages are hosted from this repo (`docs/privacy.md`, `docs/terms.md`) for GitHub Pages; confirm the live URL before submitting.

---

## 5. Play asset checklist

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | DONE — `play-assets/play-icon-512.png` (512×512, 32-bit, fully opaque) |
| Icon master | 1024×1024 PNG | DONE — `play-assets/play-icon-1024-master.png` (source for the icon exports) |
| Feature graphic | 1024×500 PNG | DONE — `play-assets/feature-graphic-1024x500.png` (1024×500, fully opaque) |
| Launcher icon | in-app icon | DONE — `mobile/android/res/drawable-xxhdpi/ic_launcher.png` (432×432 raster, referenced by `@drawable/ic_launcher`; no adaptive-icon XML in this build) |
| Phone screenshots | ≥2 (Play accepts 2–8), phone aspect | DONE — 21 emulator captures in `dist/` (1080×2340, AVD `rafiqtest`, Android 14); see §6 |
| Tablet screenshots | optional, recommended | NOT CAPTURED — phone only |
| Privacy policy URL | public HTTPS | repo `docs/privacy.md` (GitHub Pages); confirm the live URL before submitting |
| Terms URL | public HTTPS | repo `docs/terms.md` (GitHub Pages); confirm the live URL before submitting |
| Signed AAB | bundletool, upload key | DONE — `dist/rafiq-al-quran-1.0.9-signed.aab` (CI-built, `jarsigner`-signed locally with the upload keystore) |
| Installable APK | signed, sideload | DONE — `dist/rafiq-al-quran-1.0.9.apk` (3.40 MB, versionCode 9) |
| Data Safety form | answers above | paste into Play Console |
| Content rating | questionnaire | answers above |

---

## 6. Screenshots shipped

Emulator QA on AVD `rafiqtest` (Android 14, 1080×2340). All files are in `dist/` and are 1080×2340 PNGs:

| File | Shows |
|---|---|
| `screenshot-home.png` | Home screen (light theme): question composer, example questions, study-tools entry |
| `screenshot-home-dark.png` | Home screen in the dark theme |
| `screenshot-answer.png` | Answer screen for "What does the Quran say about patience?" — Al-Baqarah 2:45 with the required notice |
| `screenshot-surahs.png` | Surah list — all 114 surahs, offline |
| `screenshot-reader.png` | Al-Baqarah reader with the Juz/Page indicator |
| `screenshot-reader-actions.png` | Per-verse actions (copy, share, save, note, listen) |
| `screenshot-reader-note.png` | Personal note visible/editable in the reader |
| `screenshot-reader-side-by-side.png` | Side-by-side Arabic/translation layout |
| `screenshot-reader-dark.png` | Reader in the dark theme |
| `screenshot-bookmarks.png` | Bookmarks screen (collections, notes, export via share sheet) |
| `screenshot-history.png` | Reading history screen |
| `screenshot-study-tools.png` | Study tools hub |
| `screenshot-study-tools-plans.png` | Reading plans — "1 of 114 · 1%" after marking Al-Fatihah read |
| `screenshot-saved-search-run.png` | Saved search launches its query from the study-tools hub |
| `screenshot-player-gated.png` | Verse-audio screen: rights-pending gate, playback controls disabled |
| `screenshot-player-controls.png` | Verse-audio screen layout with play/pause/replay disabled; Previous/Next verse navigation verified (6 → 7 → 6 of 7) |
| `screenshot-admin.png` | Operator admin screen (token-entry state; no token is shown or stored in the repo) |
| `screenshot-admin-data.png` | Operator admin screen with live API data: Verses 6236, Chapters 114, Translations 9, feedback RESOLVED, audit entries, reciters UNVERIFIED/disabled |
| `screenshot-share-sheet.png` | Android share sheet with the bookmarks CSV export — header `Reference,Collection,Note` |
| `screenshot-share-sheet-notes.png` | Share sheet with the notes CSV — e.g. `Al-Baqarah 2:1,General,Guidance begins with sincere intention` |
| `screenshot-from-aab.png` | App installed from the signed AAB (universal APK): home screen with the continue-reading card |

---

## 7. Release checklist

- [ ] Confirm `AndroidManifest.xml` versionCode is greater than every versionCode previously uploaded — current **9** (the earlier local build used 1 and no build has been uploaded) — and versionName matches the release: **1.0.9**.
- [ ] Rebuild assets with `node mobile/android/scripts/exportAssets.js` (this copies `quran-meta.tsv` into the app assets).
- [ ] Build the AAB from a clean committed tree: GitHub Actions `.github/workflows/build-aab.yml` → download the unsigned artifact → sign locally with `jarsigner` using the upload keystore → `jarsigner -verify` (expect "jar verified" plus the self-signed warning) → `bundletool validate`.
- [ ] Verify the installable APK: `node mobile/android/scripts/buildApk.js`, then `node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.9.apk` → **20 checks passed**.
- [ ] Keep billing, subscriptions and donation processing disabled in this release (server feature flags off; the app shows disabled states).
- [ ] Keep the recitation rights gate closed until the Quran.com recitation rights review is recorded; no recitation audio may be streamed or stored before then (`AudioGate.rightsReviewRecorded()` must stay false and the server recitation flag must be off).
- [ ] Uninstall an earlier install before installing a locally rebuilt APK: each `buildApk.js` run recreates `mobile/android/build/` and its local signing key, so the signature changes. Run `adb uninstall com.rqmbooks.rafiqalquran`, then `adb install`.
- [ ] Never commit `mobile/android/build/quran-release.keystore`, keystore passwords, `.env`, `ADMIN_TOKEN` or other secrets. Never include the operator token in screenshots, docs or commits.
- [ ] Keep `targetSdk` at **36** (Android 16) and `minSdk` at **24**: Play rejects new apps below these levels (Automatic protection requires minSdk 24; target API 36 is required for new apps since 31 Aug 2026). The app handles Android 15+ edge-to-edge insets in `Ui.init()`. Ensure the emulator/device QA covers a modern Android version after changing SDK levels.
- [ ] Confirm the Data Safety answers and the privacy/terms URLs match this sheet before submitting.
- [ ] Record the artifact SHA-256 values (APK and signed AAB) and keep the uploaded AAB/APK pair together.
- [ ] Upload the signed AAB to Play Console (internal testing first), then promote.
