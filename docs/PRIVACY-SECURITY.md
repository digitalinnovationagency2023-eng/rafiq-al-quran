# Privacy and security

## Data handling

- **No account required.** An anonymous signed session cookie scopes conversations; conversations are stored on
  the device (app) and in the app's own database (API) for the configured retention window, then deleted
  automatically (`CONVERSATION_RETENTION_DAYS`, default 90).
- **Feedback** is stored with only what the user submits. Conversation text and diagnostics are included **only**
  when the user ticks consent; the server strips diagnostics when consent is false (asserted by tests). Nothing is
  forwarded to WhatsApp or another third party automatically.
- **Donations** store amount, currency, status and the provider reference. No card data is stored or proxied;
  checkout is hosted by the provider and webhooks are signature-verified with a timestamp tolerance and
  constant-time comparison.
- **Recitation** is enabled: the app streams verse audio from Quran.com's public CDN over HTTPS with the reciter
  name and "Source: Quran.com" attribution. No audio is stored on the device. The publisher recorded the recitation
  rights decision on 2026-10-21.
- **Optional AI assistance** is off by default. When the operator configures a free-tier AI key and the user consents,
  the question text, (only if attached) an image, and — for summary answers — the references/translations of the
  verses the app already retrieved are sent to the app's own server and forwarded to the AI provider for transient
  intent/OCR/summary processing; nothing is stored or logged by the app, and the AI never supplies Quran text
  (summaries are grounded by the server: references outside the supplied set and Arabic script are rejected).
  The user can turn it off in the menu; limits are enforced client- and server-side.
- **Attachments**: .txt/.md and .docx are read on the device and never uploaded; images are sent only through the
  optional AI service; PDFs are unsupported.
- **Voice** input uses the platform recogniser and may be processed by the platform provider; TTS is local. Both
  can be disabled, and the app states that spoken answers are not Quranic recitation.
- **No RQM Books data**: the app/API use their own SQLite database and never read RQM Books accounting, customer,
  invoice or payroll data. A test asserts the sources contain no RQM/XINVOX references.

## Security controls

- Server input validation (question length, feedback category/length/consent, donation amount bounds and currency),
  per-route rate limiting, Helmet headers, restricted CORS, JSON body size limit, centralised error handler that
  does not leak stack traces or secrets.
- Admin endpoints require `x-admin-token` compared in constant time; the endpoint family is disabled entirely when
  the token is unset. Admin mutations are written to an audit log that is readable through the API.
- The canonical Quran text cannot be edited through any admin screen: dataset changes only happen through the
  validated import script.
- Client: no WebView, no third-party analytics, no ad identifiers; preferences live in `SharedPreferences`.

## Secret management

All secrets are read from environment configuration and are never bundled into the app or committed, including the
optional `AI_API_KEY` used only by the server's `/api/assistant` route. The repository ignores `.env*` (except
`.env.example`), databases and keystores; the Android upload keystore is kept outside the repo and referenced through
`QURAN_KEYSTORE_PATH`.
