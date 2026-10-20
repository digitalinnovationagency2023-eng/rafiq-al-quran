# Rafiq Al-Quran — Play Store package (source copy)

Publisher: Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254

---

## Release metadata — shipped build 1.0.11

| Field | Value |
|---|---|
| App name | Rafiq Al-Quran: AI Assistant |
| Category | Education |
| Package name | `com.rqmbooks.rafiqalquran` |
| Version | versionCode **10** · versionName **1.0.11** |
| SDK range | minSdk **24** (Android 7.0) · targetSdk **36** (Android 16 — Play requirement for new apps since 31 Aug 2026) |
| Build toolchain | Android SDK 34 build-tools and platform (`compileSdk` 34, `targetSdk` 36), `aapt2` / `javac` / `d8` / `zipalign` / `apksigner`, **no Gradle**; **34 Java sources**. Edge-to-edge insets are handled in `Ui.init()` for Android 15+ |
| Release APK | `dist/rafiq-al-quran-1.0.11.apk` — signed, installable, **~3.41 MB**; versionCode 11 / versionName 1.0.11 (verified with `aapt2 dump badging`). Exact size and SHA-256 are recorded in the release note |
| AAB (unsigned, CI) | `dist/rafiq-al-quran-1.0.11.aab` — GitHub Actions artifact from `.github/workflows/build-aab.yml` (bundletool, no Gradle); produced by the 1.0.11 CI build, run URL and SHA-256 in the release note |
| AAB (signed, Play upload) | `dist/rafiq-al-quran-1.0.11-signed.aab` — CI AAB signed locally with `jarsigner` using the upload keystore; `jarsigner -verify` must report "jar verified" plus the expected self-signed certificate warning |
| Signing key | Play upload keystore kept **outside the repository** (default `%USERPROFILE%\.secrets\quran-release.keystore`, supplied to `buildApk.js` via `QURAN_KEYSTORE_PATH`); the copy under `mobile/android/build/` is gitignored and **never committed**. Sign the 1.0.11 AAB with the same upload key as 1.0.9 to preserve Play upload-key continuity |
| Artifacts (1.0.11) | CI run **39502295156** on commit `b40d416` (success). APK `dist/rafiq-al-quran-1.0.11.apk` 3,580,859 bytes, SHA-256 `A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8`; unsigned CI AAB 3,573,454 bytes, SHA-256 `C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015`; signed upload AAB `dist/rafiq-al-quran-1.0.11-signed.aab` 3,576,566 bytes, SHA-256 `C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F` (jarsigner verified, upload cert 08:DE:24:53…, bundletool validate exit 0) |
| Version history | 1.0.9 (versionCode 9) was uploaded to **internal testing**. 1.0.11 (versionCode 11) is the next upload candidate; the versionCode is greater than every versionCode previously uploaded. |
| CI verification | GitHub Actions workflow "Build release AAB" (`.github/workflows/build-aab.yml`) builds the AAB with bundletool and validates it with `bundletool validate`; the 1.0.11 run URL and validation output are recorded in the release note. (The previous 1.0.9 run was 39483479573, success.) |

---

## 1. Privacy Policy (public page copy)

**Last updated: 21 October 2026**

Source page: `docs/privacy.md` (published for GitHub Pages). Keep this copy and the public page in sync.

Rafiq Al-Quran is published by Digital Innovative Agency FZ LLC ("we", "us"). This policy explains what the app
does with data.

**Data stored on your device.** Your questions and the answers shown, bookmarks and bookmark collections, reading
history, reading position, saved notes, saved searches, reading plans, and preferences (language, text size,
theme, translation visibility, voice setting, reciter choice) are stored locally on your device. No account is
required to use the app.

**Data that may leave your device.** The core experience — reading the Quran, searching, receiving answers, using
the study tools and the local conversation replies — works offline from a dataset bundled inside the app. Data
leaves the device only in these cases:
- **Recitation**, when you press play: the app requests the verse audio file from Quran.com's public CDN over
  HTTPS (see "Recitation" below).
- **Optional AI assistance**, only when the app's operator has configured an AI service, you have switched it on
  and you have accepted the one-time consent notice (see "Optional AI assistance" below).
- **Feedback reports**, when you choose to send one: category, description, interface language, optional verse
  reference and optional contact detail. Conversation text and diagnostics are sent only if you explicitly tick
  the consent box.
- **The operator admin screen**, which is operator-only and read-only: it sends the operator token to the app's
  own API to read service status. It is not part of normal app use, it is hidden from customers (it only appears
  after five quick taps on the app title, on an operator device), and it does not send your bookmarks, notes,
  reading history or other study data.

**Optional AI assistance.** AI assistance is **optional and off by default**. A fresh install works fully
locally: Quran reading, search, answers, conversation replies and the study tools need no account and no network.
The app's operator can enable a free-tier AI service (for example a free Google AI Studio / Gemini API key) on the
app's own server; without that configuration the app does not contact an AI provider at all.

When you switch AI assistance on (menu → **AI conversation**) and accept the one-time consent notice, the
following may be sent over HTTPS to the app's own server, which forwards it to the configured AI provider for that
single request:
- the question text you typed;
- if you attached an image, that image.

The AI service is used only to (a) write a short, neutral small-talk or off-topic reply, (b) suggest search words
for the app's own Quran search, and (c) read text from an attached screenshot (OCR). It **never** supplies Quran
text, translations, verse references or interpretation: every verse and translation you see is taken from the
validated dataset bundled with the app and rendered by the app itself.

The request is processed transiently. The app's server does not store your question text, the attachment or the
AI provider's answer, and it logs metadata only (such as whether a request succeeded). No account, name or
advertising identifier is attached. The configured AI provider is a third party and processes the request under
its own terms; free-tier services may have different data-use terms from paid tiers.

Free-tier limits: the app allows up to 30 AI-assisted requests per day on the device, and the service also applies
rate limits (currently about 20 requests per 10 minutes per client, with an optional monthly cap). When AI
assistance is unavailable, switched off or rate-limited, the app falls back to its local behaviour and tells you.

You can turn AI assistance off at any time in the menu. With it off, conversation replies are produced locally and
attached images cannot be read; typed questions, text and .docx attachments, Quran reading and search are
unaffected.

**Attachments.** The composer's attach button lets you add a file to a question.
- **Text files** (`.txt`, `.md` and other plain-text files) are read on your device and used to search the
  bundled Quran dataset. They are not uploaded.
- **`.docx` documents** have their text extracted on your device (no upload) and are used in the same way.
- **Images** (PNG, JPEG, WebP) cannot be read on the device. If the optional AI service is enabled and you send a
  message with an image attached, the image is sent to the app's server (see "Optional AI assistance") so that
  visible text can be extracted from it (OCR). Without the AI service, images are not readable and the app says
  so. OCR returns only text from your own image; it never supplies Quran text.
- **PDFs and other file types** are not supported yet; the app shows a clear message.

Attachments are used for the current question only and are not stored by the app's server. Please do not attach
documents or screenshots containing sensitive personal information.

**Recitation.** Verse recitation is now enabled. When you press play, the app streams that verse's audio from
**Quran.com's public CDN** over HTTPS. The reciters offered are Mishary Rashid Alafasy, Abdul Basit Abdus Samad
(Murattal) and Muhammad Siddiq Al-Minshawi (Murattal). The player shows the reciter name and the attribution
**"Source: Quran.com"**. The recitation rights review for this source is recorded (21 October 2026).

No audio is downloaded, cached or stored on your device, and the app does not modify or generate recitation. As
with any internet request, Quran.com's CDN receives the device's IP address and the requested verse file; no
account or study data is sent. Recitation needs an internet connection, and availability depends on Quran.com's
service. The Arabic text and translations shown alongside the player still come from the app's bundled dataset,
not from the audio service.

**Voice features.** Speech input uses your device's speech recognition, which may process audio on your device or
with your platform provider. Spoken answers use your device's text-to-speech; only the translation and summary
are spoken, and the Arabic verse is not presented as recitation. The app does not record or upload audio. Voice
can be disabled in the app menu.

**Payments and donations.** Billing, subscriptions, in-app purchases and donation collection are **not
implemented or enabled in this release**. No payment is requested, and the app collects no card details. If paid
tools or donations are enabled in a future release, this page will be updated first.

**What we never do.** We do not sell data, we do not run advertising, we do not use advertising identifiers, and
we never ask an AI service to supply Quran text or interpretation. The app never reads RQM Books accounting,
customer, invoice or payroll data.

**Children.** The app is suitable for general audiences and does not knowingly collect personal information from
children.

**Retention and deletion.** Locally stored data is removed when you clear the app's data or uninstall it.
Feedback reports are kept for review and can be deleted on request. In-app conversations can be deleted from the
app. AI-assisted questions and attachments are not stored by the app's server. Recitation audio is not stored on
the device.

**Your choices.** Use the app's menu to turn AI assistance off, disable voice, remove a saved operator token, and
delete conversations with "New conversation". Use the Privacy page to review this information. Contact
info@rqmbooks.com for data requests.

**Changes.** We will update this page when the app changes; the date above shows the latest revision.

---

## 2. Terms of Use (public page copy)

**Last updated: 21 October 2026**

Source page: `docs/terms.md` (published for GitHub Pages). Keep this copy and the public page in sync.

1. **Free core experience.** Reading, searching, the conversational verse-finding experience and the study tools
   are free. No premium tools, subscriptions or add-ons are sold in this release, and nothing in the app requires
   a payment.
2. **Nature of the service.** The app is a Quran-focused reading and search assistant for Quranic verses and
   identified translations. It is **not** a religious authority, does not issue fatwas or rulings, and does not
   provide authoritative interpretation. Please consult qualified scholars where interpretation is required.
3. **Sources.** Quranic text and translations are served from the dataset bundled with the app; each passage
   shows its source, version and checksum. Translation licence status is shown explicitly. The optional AI
   service is never a source of Quran text, translation, verse reference or interpretation. Recitation audio
   comes from Quran.com (see item 5) and is separate from the text dataset.
4. **Optional AI assistance.** AI assistance is off by default and must be configured by the app's operator with
   a third-party AI service (the current free configuration uses a Google AI Studio / Gemini key held on the
   server). Where it is enabled and switched on by the user, it is used only to write a short neutral small-talk
   reply, suggest search words for the app's own search, and read text from an attached image (OCR). It may be
   limited, rate-limited or unavailable, in which case the app falls back to its local behaviour. AI requests are
   processed transiently and the app's server stores no user text or attachments. AI output is not religious
   content and must not be treated as guidance or interpretation; all Quranic text shown comes from the bundled
   dataset.
5. **Recitation.** Verse recitation is enabled: audio streams from Quran.com's public CDN (reciters Mishary
   Rashid Alafasy, Abdul Basit Abdus Samad — Murattal, and Muhammad Siddiq Al-Minshawi — Murattal), with the
   attribution "Source: Quran.com" shown. The publisher's recitation rights review for this source is recorded
   (21 October 2026). Nothing is stored on your device by the app. Availability depends on your internet
   connection and on Quran.com's service and may change without notice. Do not download, copy, re-upload or
   redistribute any recording through or from the app; the recitations remain the property of their owners and
   providers.
6. **Attachments.** You may attach text files, .docx documents or (when AI assistance is enabled) images. Text
   and .docx files are processed on your device; images are sent to the app's server and forwarded to the AI
   provider only for the single request. You must have the legal right to use anything you attach, and you must
   not attach unlawful, harmful or sensitive personal data. PDFs are not supported yet.
7. **Acceptable use.** Do not misrepresent the app's output as divine communication or as a religious ruling, and
   do not use the recitation or AI features in a way that infringes rights or breaks the law.
8. **Study data.** Bookmarks, collections, personal notes, saved searches, reading plans and reading position are
   stored on your device and are not synced to an account or another device.
9. **Liability.** The app is provided "as is". To the extent permitted by law, we are not liable for decisions
   made in reliance on search results or AI-assisted output, or for third-party services (including the
   recitation service) being unavailable or incorrect.
10. **Privacy.** The handling of data is described in the Privacy Policy. Contact info@rqmbooks.com for data
    requests.
11. **Contact.** Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254.

---

## 3. Google Play Data Safety answers

| Question | Answer |
|---|---|
| Does your app collect or share any required user data types? | Yes — but only **optional, user-initiated** data: feedback reports, and (only when the user turns on optional AI assistance and accepts the consent notice) question text and an attached image. There is no account, no required data collection, no advertising and no data selling. |
| App activity → "Other user-generated content" | **Optional.** Feedback content (description, optional verse reference, optional contact detail) when the user submits a report, and question text only when the user has switched on optional AI assistance and accepted the one-time consent. AI data is sent to the app's own server and forwarded to the configured AI provider for transient processing (small-talk reply, search words, OCR). Not stored by the app, not used for advertising, not sold. |
| Photos and videos → "Photos" | **Optional.** An attached screenshot image is sent only when the user attaches an image **and** optional AI assistance is enabled and used, so text in the image can be extracted (OCR). Sent to the app's own server and forwarded to the AI provider for transient processing; not stored by the app, not used for advertising, not sold. No camera or photo-library access beyond the user's explicit file pick. |
| Stays on the device (not collected) | Bookmarks and bookmark collections, personal notes, saved searches, reading plans, reading history, reading position, conversation history, language/theme/text-size/voice/reciter preferences, the local AI-assistance switch and daily counter, and the operator token (operator screen only). Never transmitted. |
| Is data encrypted in transit? | Yes — HTTPS for the app's own API (public API base `https://rqmbooks.com/rafiq-api`), for the AI request it forwards, and for Quran.com's recitation CDN (`https://verses.quran.com/`). |
| Can users request deletion? | Yes — clear app data or uninstall; "New conversation" clears conversations; the AI switch can be turned off and AI requests/attachments are not retained server-side; "Forget token" removes the operator token; feedback can be deleted by email request. |
| Data collected for advertising? | No ads, no advertising identifiers, no analytics. |
| Data sold? | No. |
| Data shared with third parties? | Only the optional AI request when the user uses AI assistance: forwarded to the configured AI provider (a service provider) for the single transient request — not sold, not used for advertising. Feedback is not forwarded automatically. |
| Account creation? | No account required; all functionality available without login. |
| Location / contacts / files / audio? | Location and contacts: not collected. Files: only the file the user explicitly picks; text/.docx files are read on the device, images are sent only when optional AI is used. Audio: if voice input is used, microphone audio is processed by the device's speech recogniser and the app never records or uploads it. Recitation: the app streams verse audio from Quran.com and stores none of it. |
| Purchase history | None — billing, subscriptions and donations are not implemented or enabled in this release. |
| Operator admin screen | Operator-only and read-only, hidden from customers behind a five-tap gesture on the app title and shown for the operator session only: sends only the operator token to the app's own API to read status data; it does not send user study data and stores nothing from the responses in the app's study data. |

Declarations: **No ads**, **No news app**, **No government app**, **All functionality available without login**, **Content rating**: Everyone / 3+ (religious reference content, no violence, no user-generated public content).

---

## 4. Store listing copy

**App name:** Rafiq Al-Quran: AI Assistant

**Short description (≤80 chars):** Find Quran verses with a respectful, source-transparent assistant.

**Full description:**
Rafiq Al-Quran is a free, Quran-focused Android app for reading and studying the Quran, online or offline. The complete Quran — all 114 surahs — is bundled with the Arabic text and nine identified translations; every passage shows its source, checksum and translation licence status. The app is Quran-only: no Hadith, Tafsir or external commentary, and it never writes or invents verses. The verse-finding assistant retrieves passages from the bundled dataset only, and when it cannot find sufficiently relevant verses it says so instead of guessing.

Reading and study
- Offline reader for all 114 surahs with a Juz and Madani page indicator on every verse.
- Nine translations, each labelled with its source, author and licence status; light and dark themes, text-size controls and a side-by-side layout.
- Bookmark collections, personal notes, saved searches and reading plans with progress — stored on your device only, with CSV/plain-text export through the Android share sheet.
- Voice input and read-aloud through the device's own speech services; the spoken text is the translation/summary, never recitation, and no audio is recorded or uploaded.

Conversation
- A local conversational layer answers greetings, thanks, questions about the app and simple small talk without writing religious content of its own.
- Questions about the Quran go to the app's own verse search; when no verse is confident, the app shows its exact fallback message and a "Where to look next" card (consult a qualified scholar or local imam; study Hadith and Fiqh with a qualified teacher; use a general reference for non-Quran topics).
- Requests for rulings, fatwas or tafsir are politely refused and pointed to qualified scholars. The app never presents AI output as Quranic text or interpretation.
- Ask how to use the app: “how do I bookmark a verse?”, “how do I listen to recitation?”, “where do I change the language?”, “how do I attach a file?”, “is there a paid plan?” — answered locally with steps, with no AI service needed.

Recitation
- Quran.com verse recitation is available: play, pause, replay, seek, previous/next verse and automatic advance to the next verse.
- Audio streams verse by verse from Quran.com's public CDN over HTTPS, with the reciter name and the attribution "Source: Quran.com". Nothing is downloaded, cached or stored on the device; an internet connection is required.
- In Browse surahs, tap “Listen” on any surah row to open the recitation player at that surah's first verse.
- Reciters: Mishary Rashid Alafasy, Abdul Basit Abdus Samad (Murattal) and Muhammad Siddiq Al-Minshawi (Murattal). The recitation rights review for this source is recorded (21 October 2026).

Attachments and optional AI assistance
- Attach a .txt, .md or .docx file: the text is read on your device and used for the local Quran search — no upload.
- Images (PNG, JPEG, WebP) can be used only when the optional AI assistance service is available, because reading an image is an OCR step performed by that service.
- Optional AI assistance is off by default and needs the app's operator to enable a free-tier key (for example a Google AI Studio / Gemini key held only on the server). When enabled, you are asked for consent once; it only helps with short neutral small talk, search words and reading text from an attached image. It never supplies Quran text, translation or interpretation, and the app falls back to local behaviour when the service is unavailable or rate-limited.
- PDFs are not supported yet. The free core experience never depends on AI assistance: reading, search, answers, conversation replies and study tools work locally.

Free and private
- No account, no ads, no analytics, no data selling. Reading, search and study work offline; recitation and optional AI need a connection.
- Billing, subscriptions and donations are not enabled in this release.

Important: this app is a Quran-focused reading and search assistant. It is not a religious authority, does not issue fatwas or rulings, and does not provide authoritative interpretation. Please consult qualified scholars where interpretation is required.

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
| Phone screenshots | ≥2 (Play accepts 2–8), phone aspect | DONE — 1.0.11 round-2 emulator captures in `dist/` (1080×2340, AVD `rafiqtest`, Android 14) plus earlier screens with no visual change; recommended Play set in §6 |
| Tablet screenshots | optional, recommended | NOT CAPTURED — phone only |
| Privacy policy URL | public HTTPS | repo `docs/privacy.md` (GitHub Pages); confirm the live URL before submitting |
| Terms URL | public HTTPS | repo `docs/terms.md` (GitHub Pages); confirm the live URL before submitting |
| Signed AAB | bundletool, upload key | PENDING CI — `dist/rafiq-al-quran-1.0.11-signed.aab` (CI-built, `jarsigner`-signed locally with the upload keystore; hash/run in the release note) |
| Installable APK | signed, sideload | DONE locally — `mobile/android/build/quran-companion.apk` (~3.41 MB, versionCode 11); release copy `dist/rafiq-al-quran-1.0.11.apk` with exact size and SHA-256 in the release note |
| Data Safety form | answers in §3 | paste into Play Console |
| Content rating | questionnaire | answers in §3 |

---

## 6. Screenshots shipped

All files are in `dist/` and are 1080×2340 PNGs captured on emulator AVD `rafiqtest` (Android 14).

### 1.0.11 QA captures (this round)

| File | Shows |
|---|---|
| `qa-r2-home.png` | Home screen: composer with Voice and 📎 ATTACH actions, example questions, continue-reading card (Al-Fatihah 1:6, Juz 1 · Page 1) |
| `screenshot-qa-r2-language.png` | Full-width "Interface language" dialog with 10 large rows (English name — native name — code), English selected |
| `screenshot-qa-r2-conversation.png` | Local conversation: "Hello" → neutral greeting reply, with the notice that conversation replies contain no Quranic text or interpretation |
| `screenshot-qa-r2-help-reply.png` | In-app feature help: “How do I bookmark a verse?” → step-by-step instructions, no religious content |
| `screenshot-qa-r2-surah-listen.png` | Browse surahs with a “▷ Listen” action on every surah row |
| `screenshot-qa-r2-surah-listen-player.png` | Tapping Listen opens the Quran.com recitation player at the surah's first verse |
| `screenshot-qa-r2-player.png` | Verse audio, Al-Fatihah 1:6: "Playing — Source: Quran.com", 0:04 / 0:05, Pause/Replay/Stop, reciter Mishary Rashid Alafasy, and a Source & rights card ("Review recorded 2026-10-21") |
| `screenshot-qa-r2-attached.png` | Attachment chip in the composer: "Attached: rafiq-test.txt (144 characters read)" with a Remove action; the text is read on the device before the question is sent |
| `screenshot-qa-r2-attachment-answer.png` | Answer retrieved from the attached text: Al-Baqarah 2:177 with translation and "Recitation — Quran.com" action |
| `screenshot-qa-r2-tts.png` | Answer screen with the "Listen" action after TTS was exercised (Google TTS synthesis en-US verified on the emulator; the screenshot cannot show audio) |
| `screenshot-qa-r2-support.png` | Support page: voluntary-only copy, "Donations are not enabled in this release", no config keys and no checkout form |
| `screenshot-qa-r2-admin.png` | Operator screen: purpose explanation before token entry, masked token field, Connect / Forget token (no token shown) |
| `screenshot-qa-r2-attach-picker.png` | Blank capture — the system file picker window is excluded from screencap; not used as evidence |

### Recommended Play listing set (2–8 phone screenshots)

`qa-r2-home.png`, `screenshot-qa-r2-language.png`, `screenshot-qa-r2-conversation.png`,
`screenshot-qa-r2-player.png`, `screenshot-qa-r2-attached.png`,
`screenshot-qa-r2-attachment-answer.png`, `screenshot-qa-r2-tts.png` — 7 captures. If an 8th is wanted, use
`screenshot-surahs.png` (surah list; the screen is unchanged in 1.0.11) and re-capture on the 1.0.11 build if
convenient. **Do not upload `screenshot-player-gated.png` or `screenshot-player-controls.png`** — they show the
previous rights-pending player from 1.0.9 and are superseded by `screenshot-qa-r2-player.png`.

### Earlier captures retained in `dist/` (1.0.9 QA — re-capture before using in the 1.0.11 listing)

These were captured on the 1.0.9 build and kept for reference. Most show screens that are unchanged or only lightly
changed in 1.0.11, but the answer/passage cards gained a "Recitation — Quran.com" action and the reader's "Listen"
action now opens the recitation player, so re-capture anything you intend to submit.

`screenshot-home.png`, `screenshot-home-dark.png`, `screenshot-answer.png`, `screenshot-surahs.png`,
`screenshot-reader.png`, `screenshot-reader-actions.png`, `screenshot-reader-note.png`,
`screenshot-reader-side-by-side.png`, `screenshot-reader-dark.png`, `screenshot-bookmarks.png`,
`screenshot-history.png`, `screenshot-study-tools.png`, `screenshot-study-tools-plans.png`,
`screenshot-saved-search-run.png`, `screenshot-admin-data.png`, `screenshot-share-sheet.png`,
`screenshot-share-sheet-notes.png`, `screenshot-from-aab.png`. Also retained: `qa-e2e-home.png`,
`screenshot-qa-e2e-keyboard.png`. The two 1.0.9 player captures (`screenshot-player-gated.png`,
`screenshot-player-controls.png`) are kept for history only; they do not reflect the shipped 1.0.11 behaviour.

---

## 7. Release checklist

- [ ] Confirm `AndroidManifest.xml` versionCode is greater than every versionCode previously uploaded — current **10** (versionCode 9 was uploaded to internal testing) — and versionName matches the release: **1.0.11**.
- [ ] Rebuild assets with `node mobile/android/scripts/exportAssets.js` (this copies `quran-meta.tsv` into the app assets).
- [ ] Build the APK with `node mobile/android/scripts/buildApk.js`, then copy/rename it to `dist/rafiq-al-quran-1.0.11.apk` and record its SHA-256 in the release note.
- [ ] Verify the installable APK: `node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.11.apk` → **20 checks passed**.
- [ ] Build the AAB from a clean committed tree: GitHub Actions `.github/workflows/build-aab.yml` → download the unsigned artifact → sign locally with `jarsigner` using the upload keystore → `jarsigner -verify` (expect "jar verified" plus the self-signed warning) → `bundletool validate`. Record the CI run URL and artifact SHA-256 values in the release note (**Artifacts (1.0.11): CI run 39502295156 on commit b40d416 (success); APK SHA-256 A5F92DB1C7205D4EF3881620CEC5FE972FFA45F9A7E0AB444FF21B27F28C5FE8; signed upload AAB SHA-256 C64A39DB17869E82FAA62EDB5B60973C88DF981A7F5C1CD914AD9BC196B0DB6F (jarsigner verified, upload cert 08:DE…); unsigned CI AAB SHA-256 C09956149C193A7CB5DCECA2CA3B23E5AA70EE6B5BF923AAA90480696D5DB015**).
- [ ] **AI assistance is optional and only for conversation/OCR; never for Quran text; the key lives only in the server `.env`; keep limits on** (`RATE_LIMIT_ASSISTANT_MAX`, optional `AI_MAX_MONTHLY_CALLS`, app 30 calls/day cap). Verify `/api/assistant/status` after `pm2 restart` (see `docs/DEPLOYMENT.md`), and confirm `TRUST_PROXY=true` on the live server so per-client limiting is correct.
- [ ] **Recitation streams from Quran.com with attribution; rights decision recorded 2026-10-21.** Keep the "Source: Quran.com" attribution visible in the player, keep audio unstored (no download/cache), and keep the server's `RECITATION_ENABLED` switch and unverified reciter rows independent of the client player (the app streams directly from the CDN; see `docs/ADMIN.md`).
- [ ] Keep billing, subscriptions and donation processing disabled in this release (server feature flags off; the app shows disabled states).
- [ ] Keep `targetSdk` at **36** (Android 16) and `minSdk` at **24**: Play rejects new apps below these levels (Automatic protection requires minSdk 24; target API 36 is required for new apps since 31 Aug 2026). The app handles Android 15+ edge-to-edge insets in `Ui.init()`. Ensure the emulator/device QA covers a modern Android version after changing SDK levels.
- [ ] Never commit the Play upload keystore (kept outside the repository, default `%USERPROFILE%\.secrets\quran-release.keystore`), the throwaway `mobile/android/build/quran-release.keystore`, keystore passwords, `.env`, `ADMIN_TOKEN`, AI provider keys or other secrets. Never include the operator token in screenshots, docs or commits.
- [ ] Confirm the Data Safety answers and the privacy/terms URLs match this sheet before submitting.
- [ ] Installable-testing note: with the persistent upload keystore (`QURAN_KEYSTORE_PATH`) the signature stays stable across rebuilds, so an update installs over an earlier build. If the build used a throwaway key, uninstall the earlier install first (`adb uninstall com.rqmbooks.rafiqalquran`), then `adb install`.
- [ ] Upload the signed AAB to Play Console (internal testing first), then promote.
