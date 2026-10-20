# Deployment

## Optional API

```bash
npm install
npm run import:quran -w server      # dataset + translations, with checksums
npm run validate:dataset -w server  # integrity report
npm run dev -w server               # http://localhost:8787
```

Production: Node 22+, a persistent disk for `server/data/quran-app.db`, and a reverse proxy with TLS. Required
environment:

| Variable | Purpose |
|---|---|
| `SESSION_SECRET` | signs the anonymous session cookie |
| `ADMIN_TOKEN` | enables `/api/admin` (unset = disabled) |
| `CORS_ORIGINS` | allowed browser origins for the optional web preview |
| `QURAN_DB_PATH` | database location (defaults under `server/data/`) |
| `TRUST_PROXY` | set `true` behind the reverse proxy/TLS terminator so Express reads the forwarded client address. Needed for correct per-client rate limiting (AI and the other public routes); enable it only when the origin is reachable solely through the proxy |

Every external capability is off unless configured. Feature switches for this release:

| Variable | Default | Purpose |
|---|---|---|
| `RECITATION_ENABLED` | `false` | server-side `/api/recitations` route only. The 1.0.10 app streams Quran.com audio **client-side** and does not need this flag; keep it off unless the server recitation route is separately reviewed and a verified reciter row exists |
| `AI_PROVIDER` … `RATE_LIMIT_ASSISTANT_MAX` | see below | optional AI assistance (off by default) |
| `DONATIONS_*`, `STRIPE_*`, `PREMIUM_*` | disabled | billing, subscriptions and donations are **not** enabled in this release |
| `WHATSAPP_*` | configured number only | feedback channel; never forwards reports automatically |

## Optional AI assistance (`/api/assistant`)

`POST /api/assistant` and `GET /api/assistant/status` add an **optional** free-tier AI layer used only for (a) a
short neutral small-talk reply, (b) search-word/intent suggestions for the app's local Quran search, and (c) OCR of
an attached screenshot. It never supplies Quran text, translations, verse references or interpretation; the app
renders every verse from its validated dataset. It is **off by default**: with no provider configured the endpoint
answers `503` and the app silently uses its local rules. Full endpoint, sanitisation and client-fallback notes are
in `docs/AI-ASSISTANT.md`.

| Variable | Default | Purpose |
|---|---|---|
| `AI_PROVIDER` | `none` | `none` (disabled) or `openai-compatible` |
| `AI_BASE_URL` | empty | provider base URL; `/chat/completions` is appended (e.g. `https://generativelanguage.googleapis.com/v1beta/openai`) |
| `AI_MODEL` | `gemini-2.5-flash` | model name sent to the provider |
| `AI_API_KEY` | empty | provider key; **server-side only**, never in the app, never committed, never echoed |
| `AI_TIMEOUT_MS` | `8000` | per-call timeout; an aborted call returns `504` |
| `AI_MAX_MONTHLY_CALLS` | `0` | optional hard monthly cap on provider calls (`0` = unlimited). Counted per UTC calendar month in the local database; no prompt or image data is stored |
| `RATE_LIMIT_ASSISTANT_MAX` | `20` | allowed `POST /api/assistant` requests per client IP per 10-minute window |

### Free Google AI Studio / Gemini setup

The free Google AI Studio key works through Gemini's OpenAI-compatible endpoint, so no paid billing account is
needed:

1. Sign in at <https://aistudio.google.com/> and create a free API key at
   <https://aistudio.google.com/app/apikey>.
2. On the server (not in this repository, not in `.env.example`), add to the deployment `.env` (for example
   `/home/ubuntu/rafiq-app/.env`, mode `600`, owned by the service user):

   ```dotenv
   AI_PROVIDER=openai-compatible
   AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
   AI_MODEL=gemini-2.5-flash
   AI_API_KEY=<free key from AI Studio - never commit it>
   AI_TIMEOUT_MS=8000
   AI_MAX_MONTHLY_CALLS=0
   RATE_LIMIT_ASSISTANT_MAX=20
   TRUST_PROXY=true
   ```

3. Restart the API process so the environment is re-read:

   ```bash
   pm2 restart rafiq-api --update-env
   ```

4. Verify without sending user data:

   ```bash
   curl -s https://rqmbooks.com/rafiq-api/api/assistant/status
   # {"enabled":true,"providerConfigured":true}
   ```

   If it returns `{"enabled":false,"providerConfigured":false}`, one of the four required values (`AI_PROVIDER`,
   `AI_BASE_URL`, `AI_API_KEY`, model) is missing; the app then uses its local rules.

5. Keep the limits on. The app also caps AI-assisted requests at 30 per day on the device and asks for one-time
   consent before the first AI request, so the free tier cannot be exhausted by ordinary use.

Notes:

- Any service that exposes an OpenAI-style `POST {AI_BASE_URL}/chat/completions` with a Bearer key can be used the
  same way; other provider types are deliberately not implemented and stay `503`. `TRUST_PROXY=true` is required
  for the per-IP assistant limiter to see clients separately; without it every client shares one bucket behind
  nginx/CloudFront.
- The request body limit for `/api/assistant` is 8 MB (an image is base64); decoded images must be ≤ 5 MB and
  PNG/JPEG/WebP. User text, attachments and the provider's answer are processed transiently and are not stored or
  logged server-side (metadata only).
- The free Gemini tier has different data-use terms from paid tiers. Review the provider's terms before enabling
  and do not enable AI assistance where content must not be sent to a third party.
- **Never commit a real key.** This repository does not contain one; the tests use a local mock provider with a
  fake key. Keep the operator copy in a secure local secret store.

## Android app

1. `node mobile/android/scripts/exportAssets.js`
2. `node mobile/android/scripts/buildApk.js` → `mobile/android/build/quran-companion.apk`
3. Set `res/values/strings.xml` → `api_base_url` to the API origin (`https://rqmbooks.com/rafiq-api` in the
   release) and rebuild if online feedback, optional AI assistance and the operator screen are wanted; leaving it
   empty produces a fully offline build. Recitation still streams directly from Quran.com's CDN because the player
   is client-side.
4. Sign release builds with the Play upload keystore. `buildApk.js` copies the keystore at
   `QURAN_KEYSTORE_PATH` (default `%USERPROFILE%\.secrets\quran-release.keystore`; password from
   `QURAN_KEYSTORE_PASSWORD`) over `mobile/android/build/quran-release.keystore` and signs with it, so rebuilding
   the APK does not change the signature and it matches the key that signs the Play upload AAB. Keep that keystore
   outside the repository and **never commit it**. If no keystore is found the build warns loudly and generates a
   throwaway key in `mobile/android/build/`: that APK will not match the Play upload key and must not be uploaded
   as an update. Generate a fresh keystore with
   `keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000` only when
   starting a new app identity.
5. Copy the built APK to `dist/rafiq-al-quran-1.0.10.apk`, verify it with
   `node mobile/android/scripts/verifyApk.js dist/rafiq-al-quran-1.0.10.apk` (**20 checks passed**), and install for
   testing: `adb install -r dist/rafiq-al-quran-1.0.10.apk`. If a build used a throwaway key (no persistent upload
   keystore found), uninstall the earlier install first (`adb uninstall com.rqmbooks.rafiqalquran`) because the
   throwaway signature will not match.

Keep `minSdk` at **24** and `targetSdk` at **36**: Play requires minSdk 24 (Automatic protection) and target API
36 for new apps since 31 Aug 2026. The app handles Android 15+ edge-to-edge insets in `Ui.init()`.

## Publishing checklist

- Complete the translation licence review and record it per translation; then set
  `TRANSLATION_LICENSE_MODE=verified-only`.
- Complete the donor/fundraising and consumer-protection review before enabling donations
  (`DONATIONS_COMPLIANCE_APPROVED=true`), and confirm the payment provider account permits donations. Billing,
  subscriptions and donations stay **disabled** in this release.
- **Recitation:** the publisher's recitation rights review for Quran.com verse audio is recorded (2026-10-21) and
  the app shows "Source: Quran.com". Keep that attribution visible in the player and source card; keep audio
  unstored (no download/cache) and do not redistribute the recordings. The server's `RECITATION_ENABLED` flag and
  seeded reciter rows are separate from the client player and may stay off/unverified.
- **AI assistance:** keep it optional and off unless a key is configured; the key lives only in the server `.env`
  (never in the app, repo, CI variables, docs or screenshots); keep `RATE_LIMIT_ASSISTANT_MAX` and (optionally)
  `AI_MAX_MONTHLY_CALLS` limits on; set `TRUST_PROXY=true` on the live server; verify
  `/rafiq-api/api/assistant/status` after each restart; review the provider's data-use terms. The AI is only ever
  used for conversation/OCR, never for Quran text.
- Keep `ADMIN_TOKEN`, signing keys and provider secrets out of source control.
- Publish the public policy pages (`docs/privacy.md`, `docs/terms.md`) from this repo with GitHub Pages and confirm
  the live URLs match the Play listing and the in-app Privacy page.
- Device-test microphone permission, speech recognition, TTS and recitation playback on the target Android
  versions.
