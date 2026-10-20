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
5. **Recitation audio is intentionally gated and disabled pending the Quran.com rights review.** The recitation
   player screen exists, but `logic/AudioGate.java` allows playback only when the rights review is recorded **and**
   the configured server reports recitation enabled; both are false in this build, so the screen shows
   "rights pending" and the controls are disabled. No audio is streamed, downloaded, cached or stored. Candidate
   reciters are seeded with unverified rights and `enabled = 0`. Enabling playback requires the operator to record
   the rights review and enable the server flag first.
6. **Android only.** iOS was not built (no macOS toolchain here). The client is Java/Android SDK; there is no
   React Native/Flutter parity build.
7. **Device testing is emulator-only.** The 1.0.9 build was installed and exercised on one emulator (AVD
   `rafiqtest`, Android 14, 1080×2340) and captured as screenshots in `dist/`; there was no physical-device run and
   no automated instrumentation/UI suite. Microphone/speech-recognition and text-to-speech paths were not part of
   the recorded screenshot QA and still need a real-device pass.
8. **Study data is device-local with no sync.** Bookmarks and collections, personal notes, saved searches, reading
   plans and reading position are stored only on the device (SharedPreferences storage). They are lost when the app's
   data is cleared or the app is uninstalled; they are not synced to an account or another device. Exports leave
   the app only when the user picks a share target.
9. **Billing, subscriptions and donations are not implemented in this release.** All premium and donation UI is
   disabled: the app reports that paid tools are not enabled and donation collection is off. The server contains
   endpoints for a future release, but no live provider transaction was executed and no entitlement or purchase
   path is active in the shipped app.
10. **The in-app admin screen is operator-only and read-only.** It requires the operator to enter `ADMIN_TOKEN` at
    runtime (stored on the operator's device only) and only reads status endpoints; it cannot mutate data. The
    `/api/admin` API itself is disabled (503) unless `ADMIN_TOKEN` is set on the server. See `docs/ADMIN.md`.
11. **Page numbering follows the Madani mushaf layout from Quran.com metadata.** The Juz/page/hizb values are
    layout metadata imported from the public Quran.com API v4 (`juz` 1–30, Madani pages 1–604, hizb 1–60). Other
    mushaf layouts, page counts or verse-numbering conventions are not supported. The hizb value is stored but not
    displayed.
12. **Single-instance rate limiting** (in-memory). Multi-instance deployments need a shared store.
13. **App chrome is English-only in this release.** The ten-language selector changes the translation and the
    answer notice/fallback language, but the interface labels themselves are not localised. Only the English and
    Arabic notices are approved wording; other languages show the English notice and are marked "pending review".
