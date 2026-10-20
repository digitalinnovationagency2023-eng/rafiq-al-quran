# Admin API

Operator-only HTTP API used by the in-app admin screen. Every route below is
disabled by default and protected by a shared operator token. The API is meant
for operators only; it is not part of the public app experience.

## Recitation and AI status — read this first

- The publisher's recitation rights review for Quran.com verse audio is **recorded (2026-10-21)**, and the 1.0.10
  app streams recitation **client-side** from Quran.com's public CDN with the attribution "Source: Quran.com". The
  client player does not call this API.
- The server-side recitation settings are independent and unchanged: `RECITATION_ENABLED` defaults to `false`, the
  seeded `audio_reciters` rows are `rights_status = unverified` and `enabled = 0`, and
  `GET /api/recitations/:verseKey` answers **403** in the default deployment. The in-app admin screen can therefore
  legitimately show "Recitation audio DISABLED" and reciter rows as UNVERIFIED while the app itself plays
  Quran.com recitation. Do not "fix" the server flag just to make the admin screen match the player.
- Optional AI assistance is **off by default**. The admin `summary.flags.aiConfigured` value only reports whether
  `AI_PROVIDER` is not `none`; a complete provider configuration is what `GET /api/assistant/status` reports, so
  use that public discovery endpoint to confirm AI is actually available. See `docs/DEPLOYMENT.md`.

## Configuration

| Setting | Value |
|---|---|
| Environment variable | `ADMIN_TOKEN` |
| Where it is read | `.env` at the deployment root (or `server/.env`), loaded once at process start |
| Authentication header | `x-admin-token: <ADMIN_TOKEN>` |
| Token format | Long random hex string, e.g. from `openssl rand -hex 32` |
| Effect when unset | All `/api/admin` routes answer **503** and are unusable |

The token is compared in constant time. Missing, empty or wrong tokens answer
**401**. A query-string fallback (`?adminToken=`) is accepted by the middleware
for local debugging only — do not use it from the app: proxy access logs record
URLs.

Changing `ADMIN_TOKEN` requires a process restart because the `.env` file is
read at startup:

```bash
# as the service user, with Node 22 on PATH (nvm)
pm2 restart rafiq-api --update-env
```

Production deployment (checked 2026-10-20): `ADMIN_TOKEN` is set in
`/home/ubuntu/rafiq-app/.env` (mode `600`, owned by the service user) and the
`rafiq-api` process (Node 22.23.3, port 8790) is online. The value is never
stored in this repository, in documentation, in the app bundle or in logs. Keep
the operator copy in a secure local secret store only.

## Base path

| | |
|---|---|
| Public base URL (production) | `https://rqmbooks.com/rafiq-api` |
| Admin base (production) | `https://rqmbooks.com/rafiq-api/api/admin` |
| Local development | `http://localhost:8787/api/admin` |

nginx serves `/rafiq-api/` from `http://127.0.0.1:8790/`, so a public request to
`/rafiq-api/api/admin/summary` reaches `GET /api/admin/summary` on the app.
Public HTTPS is fronted by CloudFront, which was verified to forward the
`x-admin-token` header to the origin.

Native app clients do not send cookies and do not need CORS. Do not call the
origin port directly; always use the public base URL so TLS and the proxy stay
in the path.

## Authentication outcomes

| Situation | Status | Body |
|---|---|---|
| `ADMIN_TOKEN` not configured on the server | `503` | `{"error":"Administration is not configured (ADMIN_TOKEN is unset)"}` |
| Header missing | `401` | `{"error":"Administrator authentication required"}` |
| Header empty | `401` | same |
| Header wrong (any length) | `401` | same |
| Header correct | route-specific JSON | see below |

Errors from route handlers use the same envelope: `{"error": "...", "code"?: "..."}`.
`400` means invalid input, `404` means the record does not exist, `500` returns a
generic message in production.

## Endpoints

All paths are relative to the admin base. Every response is JSON
(`application/json; charset=utf-8`).

| Method | Path | Response top-level keys | Notes |
|---|---|---|---|
| GET | `/summary` | `generatedAt`, `dataset`, `counts`, `flags` | `dataset` = `{dataset,version,source_url,checksum,imported_at}`; `counts` = `{chapters,verses,translations,conversations,messages,feedbackOpen,donationsPaid,donationsTotalMinor}`; `flags` = `{donationsLive,premiumEnabled,recitationEnabled,aiConfigured}` — `recitationEnabled` is the server `RECITATION_ENABLED` switch (not the client player), `aiConfigured` only shows `AI_PROVIDER != none`; see the status note above |
| GET | `/datasets` | `datasets` | Dataset versions with checksums and validation state |
| GET | `/feedback` | `feedback` | Newest first, max 200; optional `?status=open\|triaged\|in_review\|resolved\|rejected` |
| PATCH | `/feedback/:id` | `ok` | Body: `status?`, `severity?` (`low\|normal\|high\|critical`), `resolutionNote?` (max 2000 chars); writes an audit entry |
| GET | `/translations` | `translations` | Licence status and enabled flag per translation |
| PATCH | `/translations/:id` | `ok` | Body: `licenseStatus?` (`verified\|unverified\|blocked`), `enabled?`, `licenseNote?`; audited |
| GET | `/reciters` | `reciters` | Recitation sources with rights status |
| POST | `/reciters` | `ok` | **201**; requires `key` and `name`; other fields optional; created disabled/unverified |
| PATCH | `/reciters/:id` | `ok` | Body: `rightsStatus?`, `enabled?`, `template?`, `notes?`; audited |
| GET | `/premium/features` | `enabled`, `features` | Each feature: `{key,name,description,enabled,priceMinor,currency,billing}` |
| PATCH | `/premium/features/:key` | `ok` | Body: `enabled`; audited |
| POST | `/premium/grant` | `ok` | Body: `sessionId`, `featureKey`; support-only entitlement repair, recorded in the audit log (does not imply a payment) |
| GET | `/donations` | `donations` | Newest first, max 200; provider references only, never card data |
| POST | `/donations/:id/refund` | `ok` | Marks a donation refunded and audits it; irreversible in the UI |
| GET | `/audit` | `entries` | Newest first; `?limit=` 1–500 (default 100); each entry `{id,at,actor,action,detail}` |
| POST | `/purge-conversations` | `ok`, `purged` | Body: `olderThanDays?` (default = retention window); irreversible |
| GET | `/settings/:key` | `key`, `value`, `updatedAt` | 404 for an unknown key |
| PUT | `/settings/:key` | `ok` | Body: `value`; key must match `^[a-z0-9._-]{2,80}$`; audited |
| POST | `/premium/replay-event` | provider-event result | Body: `{event}`; replays a missed premium provider event, returns `{applied, reason?}` / `{applied, status?}` |

Mutations (`PATCH`, `POST`, `PUT`) are written to `audit_log` and appear in
`GET /audit`.

## Rate limiting and caching

* `/api/admin` routes are **not** wrapped by the in-process limiter. That limiter
  applies to public chat/feedback/checkout routes (`RATE_LIMIT_MAX` default
  60/min; `/api/ask` default 20/min; `/api/feedback` default 10/min), keyed by
  IP + anonymous session + path. If an admin route is ever given a limiter, the
  response is `429` with `{"error":"Too many requests. Please wait a moment and try again."}`
  and a `retry-after` header.
* nginx has no `limit_req`/`limit_conn` on `/rafiq-api/`.
* No CDN rate limiting was observed: 20 rapid authenticated `GET /summary`
  calls all returned `200` in ~6.7 s; no `429` was produced.
* CloudFront does not cache `/rafiq-api` responses today (all responses report
  `X-Cache: Miss`), and authenticated responses are never served to
  unauthenticated callers (verified repeatedly). **Do not enable caching for
  `/api/admin`**: `x-admin-token` is not part of a default CDN cache key, so
  caching could serve operator data to other callers.

## Verified live behaviour (2026-10-20)

Checked from an operator workstation over public HTTPS
(`https://rqmbooks.com/rafiq-api`) against EC2 `i-09e2608bfa43e70bf`, pm2 app
`rafiq-api`, after `pm2 restart rafiq-api --update-env`:

* `GET /api/health` → `200`; dataset `6236` verses / `114` chapters /
  `9` translations.
* `GET /summary`, `/datasets`, `/feedback`, `/audit?limit=5`, `/reciters`,
  `/premium/features`, `/translations`, `/donations` → all `200` with the
  top-level keys listed above.
* `PATCH /feedback/:id` → `200 {"ok":true}`; the row changed and a
  `feedback.updated` audit entry was written.
* Same requests without a token, with an empty token, or with a wrong token →
  `401`; no operator data is returned.
* A request outside the `/rafiq-api` prefix → `404`, so the app must include the
  `/rafiq-api` base path.

`503` cannot occur while a token is configured; it is covered by automated
tests (`server/test/admin.test.js`).

**1.0.10 note (rights decision recorded 2026-10-21).** The live check above predates the 1.0.10 release. On that
release the app enables client-side Quran.com recitation (see the status note at the top), while this server-side
summary and the reciter rows keep their seeded values: `flags.recitationEnabled` is `false`, reciters stay
UNVERIFIED/disabled, and `GET /api/recitations/:verseKey` answers 403. That is expected, not a regression. The
optional AI endpoints were not part of the recorded live check; after configuring a key, verify
`GET https://rqmbooks.com/rafiq-api/api/assistant/status` returns `{"enabled":true,"providerConfigured":true}`
(`docs/DEPLOYMENT.md`).

## App / admin-screen integration notes

* Base URL: `https://rqmbooks.com/rafiq-api` → admin calls go to
  `https://rqmbooks.com/rafiq-api/api/admin/<endpoint>`.
* Send the header `x-admin-token` on every request. Use header auth, not the
  query parameter.
* Handle `401` by prompting the operator to re-enter the token (do not retry
  blindly); handle `503` as "administration is not enabled on the server";
  surface `400`/`404`/`500` from the `error` field.
* Responses are plain JSON; no cookies or CSRF tokens are involved for a native
  client.

## Privacy and security

* The admin screen is operator-only. Do not ship the token (or the admin screen
  enabled by default) in a public store build; the operator enters the token at
  runtime and it is kept in local/encrypted storage only.
* Admin responses can contain personal data (feedback contact details,
  descriptions, consented diagnostics, donation metadata) and security-relevant
  audit history. Handle them under the app privacy policy and do not copy them
  into tickets, analytics or logs.
* The token is never returned in a response body and is never logged by the
  server. Never place it in source, docs, CI variables that are visible to
  forks, build artifacts, screenshots or chat.
* Rotation: set a new `ADMIN_TOKEN` in the deployment `.env`, restart the
  process, update the operator secret store. The previous token stops working
  immediately after the restart.
* The optional AI assistant has **no admin view**: prompts, attached images, OCR text and the provider's answer are
  transient, are not stored and are not logged. `GET /api/assistant/status` is the only public signal
  (`enabled`, `providerConfigured`) and never exposes the provider, model, base URL or key. The AI provider key is a
  server secret like `ADMIN_TOKEN`: never in the app, docs, CI variables, screenshots or chat.

## Automated tests

`server/test/admin.test.js` covers, against a throw-away copy of the dataset:
`503` when `ADMIN_TOKEN` is unset; `401` for missing, empty, wrong and
wrong-length tokens; `200` plus response shape for every read-only endpoint with
the correct token; an authorised feedback update landing in the audit log; and
the token never appearing in a response body.

```bash
npm test -w server   # full server suite: 73 passing, 0 failing (58 baseline + 15 assistant tests)
```
