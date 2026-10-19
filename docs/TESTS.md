# Measured test results

Run on 2026-10-19 (Windows, Node 24, JDK 17, Android SDK 34).

## Server suite — 53 passed, 0 failed

`npm run test -w server`

| Area | Checks |
|---|---|
| Dataset integrity | 114 chapters, 6236 verses, per-chapter counts, unique well-formed keys, diacritics preserved, no placeholders |
| Translation integrity | 9 translations × 6236 complete rows, no markup, explicit and unverified licence status |
| Provenance | source URL, SHA-256 checksum and import time recorded |
| Retrieval | curates English topics, Arabic with article/diacritics, unrelated query returns no results, reference parsing, language detection for 7 sampled languages, “more” intent |
| Answer safety | every passage matches the dataset exactly, exact English/Arabic notices, exact fallback wording, no “does not mention” claim, unreviewed languages marked pending |
| API | health/meta/chapters/verses/sources, ask + follow-up, validation errors, feedback consent stripping, admin auth, conversation scoping and deletion |
| Donations | amount bounds/currency, disabled state records pending without provider calls, recurring refusal, signature verification (valid/forged/stale), idempotent events, no card columns |
| Premium | disabled state, feature list, entitlement false, checkout refused |
| Isolation | no RQM/XINVOX references in sources, own database path, rate limiter behaviour |

## Native app logic — 25 passed, 0 failed

`node mobile/android/scripts/runLogicTests.js` (Java, real bundled assets)

Counts, provenance checksum, basmala text, 2:255 presence, English and Arabic topic answers, exact notices,
translation separated from Arabic, passages re-verified against the dataset, Arabic interface falls back to the
English translation with a label, explicit reference, exact fallback, no repeated verses on “more”, donation
validation.

## APK — built, signed and verified

`node mobile/android/scripts/buildApk.js`

- package `com.qurancompanion.app`, versionName 1.0.0, minSdk 23, targetSdk 34;
- signed (apksigner verify passed) and zipaligned;
- bundles the dataset assets (`quran.tsv`, `translations.tsv`, …) and `classes.dex`; no WebView class in the APK;
- size 3.29 MB;
- **not** device-tested: no emulator/physical device was available (see `docs/LIMITATIONS.md`).

## Server dataset validation

`npm run validate:dataset -w server` — completeness, chapter counts, translation completeness, checksum and
markup checks all pass.
