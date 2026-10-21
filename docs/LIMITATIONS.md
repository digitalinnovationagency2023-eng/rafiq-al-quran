# Known limitations

Reported honestly; nothing here is claimed as solved.

1. **Translation licences are unverified.** The provider catalogue does not publish per-translation terms. Every
   translation is marked unverified in the dataset, the app's source panel and the Sources page. Public release
   requires the operator to complete the licence review and record it (`PATCH /api/admin/translations/:id`).
   The canonical Arabic text provider's terms likewise need the operator's review before store publication;
   Tanzil.net was investigated but unreachable from the build environment.
2. **Retrieval is keyword/concept based, not semantic embeddings.** It may miss paraphrases and rank a less
   relevant verse highly. Curated test questions pass, but this is measured on a small set, not a claim of
   completeness. There is a confidence floor and a safe fallback.
3. **Cross-language retrieval is limited.** The curated lexicon covers common topics in all ten interface
   languages; an unusual query in a non-English language may fail to map to Arabic terms. Falling back to the safe
   message is by design.
4. **The lexicon is editorial and finite.** It is datasheet-driven (`assets/lexicon.tsv`, from
   `server/src/services/lexicon.js`) and reviewable; extending it is a dataset release, not a code change.
5. **Recitation is enabled but online-only and depends on a third party.** The publisher's recitation rights review
   for this source is recorded (2026-10-21). The app streams per-verse audio from **Quran.com's public CDN**
   (`https://verses.quran.com/`) over HTTPS with reciters Mishary Rashid Alafasy, Abdul Basit Abdus Samad
   (Murattal) and Muhammad Siddiq Al-Minshawi (Murattal), showing "Source: Quran.com". Nothing is downloaded,
   cached or stored on the device, so listening needs an internet connection and depends on Quran.com's service:
   a verse may fail to load, buffer or be unavailable when the CDN or the network is down. There is no offline or
   downloadable recitation, and playback advances verse by verse rather than gapless. The app's player is a
   client-side feature: it does not use the server's `RECITATION_ENABLED` switch or the seeded `audio_reciters`
   rows, which remain unverified/disabled, and `/api/recitations` still answers 403 in the default deployment.
   The recorded rights decision is the publisher's review record, not a legal opinion.
6. **Optional AI assistance needs an operator-configured key and has free-tier limits.** AI assistance is **off by
   default**; without a free Google AI Studio / Gemini key (or another OpenAI-compatible endpoint) in the server
   `.env`, `/api/assistant` answers 503 and the app uses its local rules. When enabled it can be slow, rate-limited
   (20 requests per 10 minutes per client IP by default, with an optional monthly cap) or unavailable; the app also
   caps AI-assisted requests at 30 per day and falls back to local behaviour. The AI is used only for short,
   neutral small talk, search-word suggestions and OCR of an attached image; it never supplies Quran text,
   translation or interpretation and its intent guesses can be wrong. The free tier's data-use terms differ from
   paid tiers; the operator must review the provider's terms before enabling it (`docs/AI-ASSISTANT.md`).
7. **Image reading requires the optional AI service; PDFs are unsupported.** Images (PNG/JPEG/WebP, decoded
   ≤ 5 MB) are not readable on the device; they can only be OCR'd through the optional AI service, so with AI
   assistance off the app says images cannot be read. PDFs and other document formats are not supported yet and
   get a clear message. `.txt`, `.md` and `.docx` text is extracted on the device, but extracted text is truncated
   (8,000 characters) and OCR quality depends on the image.
8. **The conversation layer is deterministic and limited — not a general chatbot.** It answers greetings, thanks,
   identity and capability questions and simple off-topic messages with fixed neutral wording; questions about
   what the Quran says always go through the local keyword retrieval; ruling/tafsir requests are refused and
   pointed to qualified scholars. When retrieval cannot find a confident verse, the exact safe fallback and a
   "Where to look next" guidance card are shown (consult a qualified scholar or local imam; study Hadith and Fiqh
   with a qualified teacher; use a general reference for non-Quran questions; explicit no-responsibility
   statement). No AI ever writes Quran text or interpretation.
9. **Attachments are processed transiently and are not stored.** Text/.docx extraction happens in memory; an image
   sent with AI assistance on is forwarded for one request only. Nothing is kept by the app or its server, so
   there is no saved copy if a message fails. Do not attach sensitive personal data. Unknown file types are
   rejected with a message.
10. **Android only.** iOS was not built (no macOS toolchain here). The client is Java/Android SDK; there is no
    React Native/Flutter parity build.
11. **Device testing is emulator-only.** Builds are installed and exercised on one emulator (AVD `rafiqtest`,
    Android 14, 1080×2340) and captured as screenshots in `dist/`; there was no physical-device run and no automated
    instrumentation/UI suite. Recitation streaming/auto-next and TTS synthesis were verified on the emulator. The
    1.0.12 voice matrix requested the correct TTS locale and recognizer locale for all ten interface languages and
    exercised the missing-voice/missing-speech-pack messages, but the emulator had only English speech/voice data
    and no internet, so audible synthesis and real dictation in the other nine languages still need a real-device
    pass with the language packs installed (see item 18).
12. **Study data is device-local with no sync.** Bookmarks and collections, personal notes, saved searches, reading
    plans and reading position are stored only on the device (SharedPreferences storage). They are lost when the
    app's data is cleared or the app is uninstalled; they are not synced to an account or another device. Exports
    leave the app only when the user picks a share target.
13. **Billing, subscriptions and donations are not implemented in this release.** All premium and donation UI is
    disabled: the app reports that paid tools are not enabled and donation collection is off. The server contains
    endpoints for a future release, but no live provider transaction was executed and no entitlement or purchase
    path is active in the shipped app.
14. **The in-app admin screen is operator-only and read-only.** It requires the operator to enter `ADMIN_TOKEN`
    at runtime (stored on the operator's device only) and only reads status endpoints; it cannot mutate data. The
    `/api/admin` API itself is disabled (503) unless `ADMIN_TOKEN` is set on the server. Its summary flags are
    server-side: `recitationEnabled` reflects `RECITATION_ENABLED` (still false by default) and reciter rows are
    still unverified/disabled even though the app streams Quran.com audio client-side; `aiConfigured` only shows
    that `AI_PROVIDER` is not `none`, so use `GET /api/assistant/status` to check a complete AI configuration.
    See `docs/ADMIN.md`.
15. **Page numbering follows the Madani mushaf layout from Quran.com metadata.** The Juz/page/hizb values are
    layout metadata imported from the public Quran.com API v4 (`juz` 1–30, Madani pages 1–604, hizb 1–60). Other
    mushaf layouts, page counts or verse-numbering conventions are not supported. The hizb value is stored but not
    displayed.
16. **Single-instance rate limiting** (in-memory, including the per-IP assistant limiter). Multi-instance
    deployments need a shared store.
17. **App chrome is English-only in this release.** The ten-language selector changes the translation and the
    answer notice/fallback language and now opens as a full-width dialog with native names, but the interface
    labels themselves are not localised. Only the English and Arabic notices are approved wording; other languages
    show the English notice and are marked "pending review".
18. **Voice output and input depend on the device's installed voices and speech packs.** "Listen" chooses the
    text-to-speech voice from the language of the text actually being spoken, and never reads non-English text with
    an English voice silently; when the voice is missing or unsupported the app says so (install/update voice data
    in Android Settings) and reports a synthesis failure instead of failing silently. "Voice" (dictation) needs the
    device recognizer (normally the Google app) to support the selected language and, for offline recognition, its
    speech pack; when it is missing the app now names the language and explains how to download the pack. The voice
    quality, availability and Android Settings paths vary by device, OEM, region and Google app version, and the
    free Google speech/voice services have their own limits. English and Urdu text-to-speech and the locale requests
    for all ten languages were verified on the QA emulator, but the emulator lacked the other languages' voice and
    speech packs, so audible output and real recognition for those languages must be checked on a physical device.
