# Donations and optional tools — compliance gates

The interfaces and server integration exist, but **live collection is disabled by default** and no payment was
executed in this delivery. Both capabilities are gated by configuration and by an explicitly recorded compliance
review.

## Donations

Enable only when all of the following are done:

1. The operating entity is legally able to collect donations in its jurisdiction (fundraising / consumer-protection
   review completed and recorded).
2. A payment provider account suitable for donations is active, with webhook signing configured.
3. `DONATIONS_ENABLED=true`, `DONATIONS_COMPLIANCE_APPROVED=true`, `STRIPE_SECRET_KEY`,
   `STRIPE_WEBHOOK_SECRET`, `DONATIONS_SUCCESS_URL`, `DONATIONS_CANCEL_URL` are set.
4. The interface copy still states: voluntary, not required for core features, no religious reward promised, no
   charitable/tax-deductible claim unless independently verified.

Behaviour encoded in code and tests:

- amounts are whole minor units inside `DONATIONS_MIN_MINOR`…`DONATIONS_MAX_MINOR`; currency restricted;
- recurring is refused unless `DONATIONS_RECURRING_ENABLED=true` and live configuration is complete;
- donations are recorded as `pending` and no provider call happens while disabled;
- provider events update status idempotently and only for `kind=donation`; forged/stale signatures are rejected;
- donations never create a subscription and are never described as a purchase; no card data is stored.

## Optional premium tools

- `PREMIUM_ENABLED=true` plus provider keys are required before any tool can be purchased; otherwise checkout
  returns 400 and the app shows "not available".
- Entitlements are checked server-side (`hasEntitlement`) and admin grants are audited (`admin-grant`).
- Core reading, search and the conversational experience never depend on payment, and no premium feature changes
  Quranic text, sources or answer quality claims.

## Not done here

No live Stripe transaction, no refund, and no tax/charity verification was performed. These remain operator
actions, documented as outstanding before enabling collection.
