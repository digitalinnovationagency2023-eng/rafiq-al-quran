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

Optional: `AI_PROVIDER`/`AI_BASE_URL`/`AI_API_KEY`/`AI_MODEL` (query interpretation only), `STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET`, `DONATIONS_ENABLED`, `DONATIONS_COMPLIANCE_APPROVED`, `PREMIUM_ENABLED`,
`RECITATION_ENABLED`, `WHATSAPP_BUSINESS_NUMBER`.

## Android app

1. `node mobile/android/scripts/exportAssets.js`
2. `node mobile/android/scripts/buildApk.js` → `mobile/android/build/quran-companion.apk`
3. Set `res/values/strings.xml` → `api_base_url` to the API origin and rebuild if online feedback/donations are
   wanted; leave empty for a fully offline build.
4. Replace the generated development keystore with a production upload keystore, stored outside the repository:
   `keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000` and pass
   `QURAN_KEYSTORE_PASSWORD`; then build an AAB/APK with your store tooling or sign the aligned APK.
5. Install for testing: `adb install -r dist/quran-companion-1.0.0.apk`.

## Publishing checklist

- Complete the translation licence review and record it per translation; then set
  `TRANSLATION_LICENSE_MODE=verified-only`.
- Complete the donor/fundraising and consumer-protection review before enabling donations
  (`DONATIONS_COMPLIANCE_APPROVED=true`), and confirm the payment provider account permits donations.
- Verify recitation recording rights before enabling a reciter.
- Keep `ADMIN_TOKEN`, signing keys and provider secrets out of source control.
- Device-test microphone permission, speech recognition, TTS and the source dialogs on the target Android
  versions.
