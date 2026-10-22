# AI assistance (optional)

`POST /api/assistant` and `GET /api/assistant/status` add an **optional** AI layer
to the Rafiq Al-Quran API for two jobs only:

1. a short, neutral small-talk / off-topic reply, and
2. intent + search keywords for a question, and OCR text from an attached
   screenshot (vision).

It is **off by default**. With no provider configured the endpoint answers
`503` and the app uses its own local, deterministic rules — so a fresh checkout
never needs an AI key and never calls a paid service.

## What the AI is not allowed to do

The model is never a source of religious content. The app always retrieves the
Arabic text, translations and references from its validated local dataset and
renders them itself.

* It **never** quotes, paraphrases or supplies Quran or Hadith text.
* It **never** gives rulings, fatwas, tafsir or interpretation.
* It **never** supplies the verse text shown to the user; `searchTerms` only
  feed the app's local search index.
* Its output is parsed as JSON and sanitised server-side; the raw upstream
  response is never returned to a client.

Server-side enforcement (see `server/src/services/assistant.js`):

| Rule | Applied |
|---|---|
| Strict system prompt (JSON-only, no scripture, no commentary) | every call |
| Markdown / code fences stripped | every response |
| `reply` dropped if it contains Arabic script | every response |
| `reply` dropped if it contains a verse reference (`2:255`, "Surah 2", "verse 155") | every response |
| `reply` dropped if it contains a long quotation or religious wording | every response |
| `reply` only returned for `smalltalk` / `offtopic`; otherwise forced `null` | every response |
| `reply` capped at 300 characters | every response |
| `searchTerms` ASCII-only, ≤ 6 terms, ≤ 200 characters total | every response |
| `imageText` capped at 4000 characters; non-Latin script allowed (it is OCR of the user's own image) | every response |
| `intent` restricted to `smalltalk \| question \| offtopic \| attachment` | every response |
| Length/type/mime/base64/image-signature validation of the request | every call |

A dropped reply is returned as `"reply": null`. The app then shows its own local
wording; the server never fills the gap with AI-generated religious content.

## Endpoints

Public base URL (production): `https://rqmbooks.com/rafiq-api` — nginx proxies
`/rafiq-api/` to the API origin, so the public endpoints are:

* `POST https://rqmbooks.com/rafiq-api/api/assistant`
* `GET  https://rqmbooks.com/rafiq-api/api/assistant/status`

Local development: `http://localhost:8787/api/assistant`.

Both are mounted on the normal public router (`server/src/http/publicRoutes.js`),
so they inherit the same Helmet headers, CORS policy and cookie/session
middleware as `/api/health` and `/api/feedback`. The assistant route has its own
8 MB JSON parser (an image is a base64 string); every other route keeps 128 KB.

### `GET /api/assistant/status`

Cheap discovery call. Never exposes the provider, model, base URL or key.

```json
{ "enabled": true, "providerConfigured": true }
```

`enabled` and `providerConfigured` are both `false` until the server has a
complete provider configuration. The app may cache this briefly (e.g. 10
minutes) and must not call it on every message.

### `POST /api/assistant`

Request body (`application/json`, maximum 8 MB):

```json
{
  "text": "How are you today?",
  "language": "en",
  "attachment": {
    "name": "screenshot.png",
    "mime": "image/png",
    "dataBase64": "iVBORw0KGgoAAAANSUhEUg..."
  },
  "mode": "chat"
}
```

| Field | Required | Rules |
|---|---|---|
| `text` | one of `text` / `attachment` | string, ≤ 1000 characters after trimming |
| `language` | no | one of `ar,en,ur,id,bn,tr,fa,ms,sw,fr`; otherwise detected from `text` |
| `attachment` | one of `text` / `attachment` | object; see below |
| `attachment.mime` | yes (with attachment) | `image/png`, `image/jpeg`, `image/webp` (`image/jpg` accepted as an alias) |
| `attachment.dataBase64` | yes (with attachment) | base64 of the image; a `data:image/png;base64,` prefix is tolerated; decoded size ≤ 5 MB and must match the declared image type |
| `attachment.name` | no | display-only, sanitised, ≤ 120 characters |
| `mode` | no | may only be the string `"chat"` |

Success (`200`):

```json
{
  "enabled": true,
  "intent": "smalltalk",
  "reply": "Hello! I'm doing well, thanks for asking.",
  "searchTerms": [],
  "imageText": null
}
```

Example for an on-topic question (`searchTerms` are then used by the app's local
search; the app renders the matched verses itself):

```json
{
  "enabled": true,
  "intent": "question",
  "reply": null,
  "searchTerms": ["patience", "hardship"],
  "imageText": null
}
```

Example for an attached screenshot (Arabic OCR is allowed in `imageText`
because it is the user's own text; it is not AI religious content):

```json
{
  "enabled": true,
  "intent": "attachment",
  "reply": null,
  "searchTerms": ["patience"],
  "imageText": "ما معنى الصبر في هذا السؤال؟"
}
```

Response fields:

| Field | Type | Meaning |
|---|---|---|
| `enabled` | `true` | always true on a `200` |
| `intent` | `"smalltalk" \| "question" \| "offtopic" \| "attachment"` | classifier result (server-validated) |
| `reply` | `string \| null` | short neutral chat reply; only for smalltalk/offtopic; `null` = use local fallback |
| `searchTerms` | `string[]` | ASCII keywords for the app's local dataset search (may be empty) |
| `imageText` | `string \| null` | OCR text visible in the attached image (≤ 4000 chars, may be non-Latin); `null` without an image |

Errors use the normal public error shape `{ "error": "..." }` and never include
a key, an upstream response or a stack trace:

| Status | When | Client behaviour |
|---|---|---|
| `400` | empty request, oversized text, bad `mode`, unsupported mime, invalid/empty base64, image bytes do not match mime, too many characters | fix request / ignore feature |
| `413` | decoded attachment > 5 MB, or request body > 8 MB | downscale the image |
| `429` | client over the per-IP allowance, or monthly provider budget exhausted | back off (honour `retry-after`) / local fallback |
| `502` | provider error, timeout-adjacent failure, or unparseable provider output | local fallback |
| `503` | AI assistance is not configured on the server | local fallback (silent) |
| `504` | provider did not answer within `AI_TIMEOUT_MS` | local fallback |

The exact `503` body is:

```json
{ "error": "AI assistance is not enabled. Configure AI_PROVIDER and AI_API_KEY on the server." }
```

## Configuration

All settings live in `.env` at the deployment root (or `server/.env`) and are
read once at process start. Nothing here has a default that costs money:
`AI_PROVIDER` defaults to `none`.

| Variable | Default | Purpose |
|---|---|---|
| `AI_PROVIDER` | `none` | `none` (disabled) or `openai-compatible` |
| `AI_BASE_URL` | empty | provider base URL; `/chat/completions` is appended (e.g. `https://generativelanguage.googleapis.com/v1beta/openai`) |
| `AI_MODEL` | `gemini-flash-lite-latest` | model name sent to the provider |
| `AI_API_KEY` | empty | provider key; **server-side only**, never in the app, never committed, never echoed |
| `AI_TIMEOUT_MS` | `8000` | per-call timeout; aborting returns `504` |
| `AI_MAX_MONTHLY_CALLS` | `0` | optional hard monthly cap (`0` = unlimited). Counted per UTC calendar month in the local database; no prompt/image data is stored |
| `RATE_LIMIT_ASSISTANT_MAX` | `20` | allowed `POST /api/assistant` requests per client IP per 10-minute window |

`providerConfigured`/`enabled` require `AI_PROVIDER=openai-compatible`,
`AI_BASE_URL`, `AI_API_KEY` and a model (the default model counts).

## Free-tier Gemini setup

The free Google AI Studio key works through Gemini's OpenAI-compatible
endpoint, so no paid billing account is needed:

1. Sign in at <https://aistudio.google.com/> and create a free API key at
   <https://aistudio.google.com/app/apikey>.
2. On the server (not in this repository, not in `.env.example`), add to
   `/home/ubuntu/rafiq-app/.env` with mode `600`:

   ```dotenv
   AI_PROVIDER=openai-compatible
   AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
   AI_MODEL=gemini-flash-lite-latest
   AI_API_KEY=<free key from AI Studio>
   AI_TIMEOUT_MS=8000
   AI_MAX_MONTHLY_CALLS=0
   RATE_LIMIT_ASSISTANT_MAX=20
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

Any other service that exposes an OpenAI-style
`POST {AI_BASE_URL}/chat/completions` with a Bearer key can be used the same
way. Other provider types are deliberately not implemented; they stay `503`.

**Never commit a real key.** This repository does not contain one, and the AI
tests use a local mock provider with a fake key.

## Rate limits and budget

* `POST /api/assistant` is limited to **20 requests per client IP per 10
  minutes** by default (`RATE_LIMIT_ASSISTANT_MAX`). The native app does not
  send cookies, so the assistant bucket is keyed by IP, not by the anonymous
  session. It returns `429` with a `retry-after` header.
* Behind nginx/CloudFront, `req.ip` is the proxy address unless the deployment
  sets `TRUST_PROXY=true`; in that case every client shares one 20-requests /
  10-minutes bucket. Set `TRUST_PROXY=true` (only when the origin is reachable
  solely through the proxy) so per-IP limiting sees clients separately. This is
  a deployment setting, not a code change; requests still degrade to the app's
  local fallback if they hit the limit.
* The limiter is in-process, like the rest of the API's limiter: a multi-instance
  deployment would need a shared store.
* If `AI_MAX_MONTHLY_CALLS` is set (`>= 1`), calls beyond the cap return `429`
  until the next UTC month. Only an aggregate counter
  (`ai_assistant_calls:YYYY-MM` in the `settings` table) is written.
* `GET /api/assistant/status` is deliberately not rate limited (it is a tiny
  discovery call).

## Privacy

* **Transient only.** The user's text, the image and the provider's answer are
  held in memory for the single request. They are not persisted, not written to
  the database and not cached.
* **Not logged.** The server logs metadata only (provider HTTP status, timeout,
  "unparseable output"). Prompt text, image bytes, OCR text and the raw provider
  response are never logged. The existing central error handler never leaks
  details to clients.
* **No secrets returned.** The key is only used in the `Authorization` header of
  the server-to-provider request; it is never sent to the app and never echoed
  in an error.
* **Provider-side processing.** The text and (if sent) the image are processed
  by the configured provider. Review that provider's data-use terms before
  enabling — the free Gemini tier has different data-use terms from paid tiers.
  Do not enable AI assistance for deployments that must not send any content to
  a third party.
* **No user data in the rate-limit/budget stores.** The limiter keeps counters
  in memory; the monthly cap stores only an integer count per month.

## Failure behaviour and the app fallback

The app must treat AI assistance as an optional enhancement:

* `503` (not configured) → hide/disable the AI layer and use local rules.
* Any other non-2xx (`400`, `413`, `429`, `502`, `504`) → use local rules.
  Optionally retry once, later, on `502`/`504`/`429` (respect `retry-after`);
  never retry in a loop.
* `200` with `reply: null` → show the local wording for that kind of turn.
* `200` with `intent: "question"` → run the app's local dataset search with
  `searchTerms` (never ask the AI for verses).
* `200` with `imageText` → show it as "text from your image" (OCR), clearly
  separate from any Quran text; the app still retrieves verses locally.

## Android client notes

* Endpoint: `{baseUrl}/api/assistant` and `{baseUrl}/api/assistant/status`
  where `baseUrl` is the existing API base including `/rafiq-api`
  (`https://rqmbooks.com/rafiq-api`). No extra nginx rule is needed: the
  `/rafiq-api/` proxy already forwards all `/api/*` paths.
* Native clients do not send cookies or an `Origin` header; CORS is irrelevant
  for the app, but the same route is also callable from the browser client.
* Send `Content-Type: application/json`. Keep the request under 8 MB; downscale
  screenshots and send PNG/JPEG/WebP base64 so the decoded image stays ≤ 5 MB.
* Use a client timeout slightly above `AI_TIMEOUT_MS` (e.g. 10–12 s) so the
  server's documented `504` can arrive first.
* `reply` should never be rendered as a religious answer. It is a neutral chat
  bubble at most, and only for small talk / off-topic. For questions the app
  shows verses from its own dataset with the standard notice.
* Do not embed, request or transmit an API key in the app. The key exists only
  in the server environment.

## Grounded advisory summaries (`POST /api/assistant/summary`)

This is the optional "answer like a study companion" layer requested for advisory
questions. The app first retrieves the relevant verses **locally** (deterministic
retrieval), then sends only the question and those verse references. The server loads
the authoritative translations itself and asks the configured model for a short answer
that uses **nothing except those passages**.

```json
{
  "question": "What does the Quran say about theft?",
  "language": "en",
  "verseKeys": ["5:38", "5:39"],
  "history": [
    { "role": "user", "content": "Who is Hazrat Essa?" },
    { "role": "assistant", "content": "The Quran mentions Isa in several passages." }
  ]
}
```

`history` is optional (max 6 items, each ≤ 400 chars, roles `user`/`assistant`). It is included in the prompt only
as conversational context so follow-up questions such as "and what about his mother?" work; it is never treated as
a source of Quran text and is validated/rejected like every other field.

Success (`200`):

```json
{
  "enabled": true,
  "summary": "The Quran addresses theft with a measured legal response [5:38]. It also states that repentance after wrongdoing is accepted [5:39].",
  "citations": ["5:38", "5:39"],
  "verseLanguage": "en"
}
```

Server-side grounding rules (enforced in `server/src/services/synthesis.js`, not only
in the prompt):

| Rule | Enforcement |
|---|---|
| Only the supplied verses are sent to the provider | The server loads those translations from its own database; the client cannot inject text |
| Every citation must be one of the supplied references | Any `a:b` reference outside that set rejects the answer (`502`) |
| No Arabic script in the answer | Rejected (`502`) — the model may paraphrase translations, never write Quran text |
| Minimum one citation, 40–1500 characters | Rejected (`502`) |
| No fatwa/tafsir/hadith/fiqh content | Strict system prompt; the app labels the card as a summary, not a ruling |
| Unknown verse reference in the request | `400` |
| Provider error/timeout | `502` / `504`; the app keeps the deterministic verse answer |

The app shows the summary in a card labelled **"Quran-only summary"** with the notice
*"Synthesised only from the verses below — not tafsir or a fatwa. Consult a qualified
scholar for rulings."* The verse cards with Arabic text, translation, reference and
provenance are always displayed below it. If AI assistance is off, unavailable, over
its limits or returns an ungrounded answer, the app simply shows the deterministic
retrieved verses (no error state).

Privacy is the same as the rest of the optional AI layer: the question and the
translations of the retrieved verses are processed transiently by the configured
provider; they are not stored or logged by this server. The one-time AI consent text
now mentions the summary use. The same `AI_*` settings, rate limit and optional
monthly cap apply (a summary is an additional AI request).

## Tests

`server/test/assistant.test.js` (15 tests) starts a local mock
OpenAI-compatible HTTP server and covers:

* `503` + status while unconfigured, and configured status without secrets;
* `400` validation (empty request, bad mode, bad text, bad mime, bad base64,
  non-image bytes) with no provider call;
* `413` for a > 5 MB decoded attachment and for a request body above the 8 MB
  parser limit;
* `429` after the per-IP allowance, with `retry-after`;
* success path asserting the provider receives `POST {base}/chat/completions`,
  the Bearer key and model, the strict system prompt and the Markdown-fenced
  reply is stripped to the fixed response contract;
* image path: the request carries a `content` array with an
  `{ "type": "image_url", "image_url": { "url": "data:image/png;base64,..." } }`
  part, and non-Latin OCR text is preserved in `imageText`;
* sanitisation: Arabic-script replies, long quotations, verse references and
  religious commentary are dropped (`reply: null`), and over-long replies /
  search-term lists are capped;
* `502` for provider errors/unreadable output and `504` for a timeout, with no
  upstream detail or key leaked;
* the optional monthly cap;
* inherited CORS preflight and Helmet headers.

Run with `npm test -w server` from the repository root. No real provider and no
real key are used by the suite.


## Deep reasoning answer (`POST /api/assistant/answer`, 1.2.0)

The app sends the question, up to **18** verse keys it retrieved (including reviewed core verses for
major figures), a short history and—for count/list questions—one reviewed index fact. The server loads
the authoritative translations for exactly those references and asks the provider for a direct answer
followed by step-by-step reasoning. The same grounding rules apply: citations outside the supplied set
or Arabic script are rejected, the answer must be 80–2200 characters with at least one citation, and
nothing is persisted.

```json
{
  "question": "How many prophets are named in the Quran?",
  "language": "en",
  "verseKeys": ["4:164", "40:78"],
  "facts": ["App index: 25 named prophets (...); 4:164/40:78 say others were not mentioned."],
  "history": []
}
```

`facts` is optional (max 6 items, each ≤ 300 ASCII characters, no Arabic script) and is used only for
counts/names, never as scripture. The deterministic fact answer is always available offline, so a
provider failure leaves the count/list and its qualifying verses on screen.
