// Voluntary donations. Two hard rules are encoded here:
//  1. nothing is charged unless the operator has explicitly enabled donations
//     AND recorded that the compliance review for the operating entity is done;
//  2. donations are never mixed with premium purchases or RQM Books accounting.
// Payment card data never reaches this service: the provider hosts checkout.
import crypto from "node:crypto";
import { config } from "../config.js";

export const ALLOWED_CURRENCIES = ["AED", "USD", "EUR", "GBP"];

export const donationConfig = () => {
  const reasons = [];
  if (!config.donations.enabled) reasons.push("DONATIONS_ENABLED is false");
  if (!config.donations.complianceApproved) {
    reasons.push("DONATIONS_COMPLIANCE_APPROVED is false (fundraising/consumer-protection review not recorded)");
  }
  if (!config.donations.stripeSecretKey) reasons.push("STRIPE_SECRET_KEY is missing");
  if (!config.donations.stripeWebhookSecret) reasons.push("STRIPE_WEBHOOK_SECRET is missing");
  if (!config.donations.successUrl || !config.donations.cancelUrl) {
    reasons.push("DONATIONS_SUCCESS_URL / DONATIONS_CANCEL_URL are missing");
  }
  return {
    enabled: config.donations.enabled,
    live: reasons.length === 0,
    currency: config.donations.currency,
    currencies: ALLOWED_CURRENCIES,
    minAmountMinor: config.donations.minAmountMinor,
    maxAmountMinor: config.donations.maxAmountMinor,
    recurringEnabled: config.donations.recurringEnabled && reasons.length === 0,
    provider: config.donations.provider,
    complianceApproved: config.donations.complianceApproved,
    blockedReasons: reasons,
    statement:
      "Donations are voluntary and never required to read, search or discuss Quranic verses. " +
      "This application does not promise religious reward and does not claim charitable or tax-deductible status.",
  };
};

export const validateDonationAmount = ({ amountMinor, currency }) => {
  const amount = Number(amountMinor);
  if (!Number.isInteger(amount)) return "Amount must be a whole number of minor units (fils/cents)";
  if (amount < config.donations.minAmountMinor) return `Amount must be at least ${config.donations.minAmountMinor}`;
  if (amount > config.donations.maxAmountMinor) return `Amount must be at most ${config.donations.maxAmountMinor}`;
  if (!ALLOWED_CURRENCIES.includes(String(currency || "").toUpperCase())) return "Unsupported currency";
  return null;
};

export const verifyStripeSignature = ({ payload, header, secret, toleranceSeconds = 300 }) => {
  if (!header || !secret) return false;
  const parts = Object.fromEntries(
    String(header)
      .split(",")
      .map((entry) => entry.split("="))
      .filter((pair) => pair.length === 2)
  );
  const timestamp = Number(parts.t);
  if (!Number.isFinite(timestamp)) return false;
  if (Math.abs(Date.now() / 1000 - timestamp) > toleranceSeconds) return false;
  const expected = crypto.createHmac("sha256", secret).update(`${parts.t}.${payload}`).digest("hex");
  const provided = Buffer.from(String(parts.v1 || ""));
  const expectedBuffer = Buffer.from(expected);
  if (provided.length !== expectedBuffer.length) return false;
  return crypto.timingSafeEqual(provided, expectedBuffer);
};

const stripeRequest = async ({ path, body, secretKey }) => {
  const response = await fetch(`https://api.stripe.com/v1/${path}`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${secretKey}`,
      "content-type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams(body).toString(),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = payload?.error?.message || `Stripe request failed with HTTP ${response.status}`;
    throw Object.assign(new Error(message), { status: 502 });
  }
  return payload;
};

/**
 * Creates a donation row and, only when live configuration is complete, a
 * provider checkout session. Returns a disabled response otherwise.
 */
export const createDonationCheckout = async ({ db, amountMinor, currency, recurring = false, email = null, sessionId }) => {
  const settings = donationConfig();
  const invalid = validateDonationAmount({ amountMinor, currency });
  if (invalid) throw Object.assign(new Error(invalid), { status: 400 });
  if (recurring && !settings.recurringEnabled) {
    throw Object.assign(new Error("Recurring donations are not enabled"), { status: 400 });
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  db.prepare(
    `INSERT INTO donations (id, amount_minor, currency, status, provider, provider_ref, recurring, donor_email, created_at, updated_at)
     VALUES (?, ?, ?, 'pending', ?, NULL, ?, ?, ?, ?)`
  ).run(
    id,
    Number(amountMinor),
    String(currency).toUpperCase(),
    settings.provider,
    recurring ? 1 : 0,
    email ? String(email).slice(0, 200) : null,
    now,
    now
  );

  if (!settings.live) {
    return {
      live: false,
      donationId: id,
      amountMinor: Number(amountMinor),
      currency: String(currency).toUpperCase(),
      blockedReasons: settings.blockedReasons,
      message:
        "Donation collection is not enabled in this deployment. The amount was validated and recorded as pending; no payment was requested.",
    };
  }

  const body = {
    mode: recurring ? "subscription" : "payment",
    success_url: config.donations.successUrl,
    cancel_url: config.donations.cancelUrl,
    client_reference_id: id,
    "metadata[kind]": "donation",
    "metadata[donationId]": id,
    "metadata[sessionId]": sessionId || "",
    "line_items[0][quantity]": "1",
    "line_items[0][price_data][currency]": String(currency).toLowerCase(),
    "line_items[0][price_data][unit_amount]": String(amountMinor),
    "line_items[0][price_data][product_data][name]": "Voluntary donation to support the app",
  };
  if (recurring) {
    body["line_items[0][price_data][recurring][interval]"] = "month";
  }
  if (email) body.customer_email = email;

  const checkout = await stripeRequest({
    path: "checkout/sessions",
    body,
    secretKey: config.donations.stripeSecretKey,
  });

  db.prepare("UPDATE donations SET provider_ref = ?, updated_at = ? WHERE id = ?").run(
    checkout.id,
    new Date().toISOString(),
    id
  );

  return {
    live: true,
    donationId: id,
    checkoutUrl: checkout.url,
    sessionId: checkout.id,
    amountMinor: Number(amountMinor),
    currency: String(currency).toUpperCase(),
  };
};

/** Applies a verified provider event to the donation record. Idempotent. */
export const applyDonationEvent = ({ db, event }) => {
  const type = event?.type;
  const object = event?.data?.object || {};
  const donationId = object?.metadata?.donationId || object?.client_reference_id;
  if (!donationId) return { applied: false, reason: "no donation reference" };
  if (object?.metadata?.kind && object.metadata.kind !== "donation") {
    return { applied: false, reason: "not a donation event" };
  }
  const statusByType = {
    "checkout.session.completed": "paid",
    "checkout.session.expired": "expired",
    "charge.refunded": "refunded",
    "payment_intent.payment_failed": "failed",
  };
  const status = statusByType[type];
  if (!status) return { applied: false, reason: `ignored event ${type}` };
  const result = db
    .prepare("UPDATE donations SET status = ?, updated_at = ? WHERE id = ?")
    .run(status, new Date().toISOString(), donationId);
  return { applied: result.changes > 0, status };
};

export const listDonations = ({ db, limit = 50 }) =>
  db
    .prepare(
      `SELECT id, amount_minor AS amountMinor, currency, status, provider, provider_ref AS providerRef,
              recurring, created_at AS createdAt, updated_at AS updatedAt
       FROM donations ORDER BY created_at DESC LIMIT ?`
    )
    .all(Math.min(200, Math.max(1, limit)));
