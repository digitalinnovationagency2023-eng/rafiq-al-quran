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
3. **Cross-language retrieval is limited.** The curated lexicon covers common topics in all ten languages; an
   unusual query in a non-English language may fail to map to Arabic terms. Falling back to the safe message is
   by design.
4. **The lexicon is editorial and finite.** It is datasheet-driven (`assets/lexicon.tsv`, from
   `server/src/services/lexicon.js`) and reviewable; extending it is a dataset release, not a code change.
5. **Recitation is disabled.** Candidate reciters are seeded with unverified rights and playback off; enabling
   requires rights verification recorded in the admin API. No audio is bundled.
6. **Android only.** iOS was not built (no macOS toolchain here). The client is Java/Android SDK; there is no
   React Native/Flutter parity build.
7. **No on-device instrumentation test run.** The APK builds, signs, verifies and its logic is unit-tested on the
   JVM against the real assets, but this environment has no emulator/physical device, so taps, permission dialogs
   and speech recognition were not exercised at runtime. Device smoke testing is an explicit remaining step.
8. **Notifications/offline sync** are not included: there is no push, no background sync and no multi-device
   conversation sync. Conversations live on the device.
9. **Donations and premium are disabled** in this build; the server integration is implemented and tested for the
   disabled and signature-verification paths, but no live provider transaction was executed (no keys, no
   compliance approval).
10. **Admin is API + web preview only**, not part of the mobile app; `/api/admin` is disabled unless
    `ADMIN_TOKEN` is set.
11. **Single-instance rate limiting** (in-memory). Multi-instance deployments need a shared store.
12. **Interface chrome** is fully translated for English and Arabic only; the other eight languages show English
    chrome with a visible "pending review" note, mirroring the notice policy.
