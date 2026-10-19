// Optional premium convenience tools. They are disabled by default and can only
// ever add convenience: core reading, search and the conversational experience
// stay free, and no premium feature changes Quranic content or its provenance.
import crypto from "node:crypto";
import { config } from "../config.js";

export const DEFAULT_FEATURES = [
  { key: "saved_collections", name: "Saved verse collections", description: "Organise verses into named collections.", priceMinor: 900, billing: "month", sortOrder: 1 },
  { key: "study_notes", name: "Personal study notes", description: "Attach private notes to verses.", priceMinor: 900, billing: "month", sortOrder: 2 },
  { key: "cross_language", name: "Cross-language comparison", description: "Compare verified translations side by side.", priceMinor: 1200, billing: "month", sortOrder: 3 },
  { key: "saved_searches", name: "Saved searches and filters", description: "Store search filters for later.", priceMinor: 900, billing: "month", sortOrder: 4 },
  { key: "reading_plans", name: "Reading plans", description: "Personal reading plans and progress.", priceMinor: 900, billing: "month", sortOrder: 5 },
  { key: "exports", name: "Export notes and references", description: "Export personal notes and verse references.", priceMinor: 1200, billing: "month", sortOrder: 6 },
];

export const seedPremiumFeatures = (db) => {
  const insert = db.prepare(
    `INSERT INTO premium_features (key, name, description, enabled, price_minor, currency, billing, sort_order)
     VALUES (?, ?, ?, 0, ?, ?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET name = excluded.name, description = excluded.description,
       price_minor = excluded.price_minor, currency = excluded.currency, billing = excluded.billing, sort_order = excluded.sort_order`
  );
  for (const feature of DEFAULT_FEATURES) {
    insert.run(
      feature.key,
      feature.name,
      feature.description,
      feature.priceMinor,
      config.premium.currency,
      feature.billing,
      feature.sortOrder
    );
  }
};

export const premiumConfig = () => {
  const reasons = [];
  if (!config.premium.enabled) reasons.push("PREMIUM_ENABLED is false");
  if (!config.premium.stripeSecretKey) reasons.push("premium Stripe secret key is missing");
  if (!config.premium.webhookSecret) reasons.push("premium webhook secret is missing");
  return {
    enabled: config.premium.enabled,
    live: reasons.length === 0,
    currency: config.premium.currency,
    blockedReasons: reasons,
    statement:
      "Premium tools are optional conveniences. The core Quran reading, verse lookup and conversational experience are free, and paying never changes the accuracy or provenance of Quranic content.",
  };
};

export const listFeatures = (db) => {
  const features = db
    .prepare(
      `SELECT key, name, description, enabled, price_minor AS priceMinor, currency, billing
       FROM premium_features ORDER BY sort_order`
    )
    .all();
  return features.map((feature) => ({
    ...feature,
    available: Boolean(config.premium.enabled && feature.enabled),
  }));
};

export const hasEntitlement = (db, sessionId, featureKey) => {
  if (!config.premium.enabled) return false;
  const row = db
    .prepare(
      `SELECT status, expires_at AS expiresAt FROM entitlements WHERE session_id = ? AND feature_key = ?`
    )
    .get(sessionId, featureKey);
  if (!row || row.status !== "active") return false;
  if (row.expiresAt && new Date(row.expiresAt).getTime() < Date.now()) return false;
  return true;
};

export const listEntitlements = (db, sessionId) =>
  config.premium.enabled
    ? db
        .prepare(
          `SELECT feature_key AS featureKey, status, created_at AS createdAt, expires_at AS expiresAt
           FROM entitlements WHERE session_id = ?`
        )
        .all(sessionId)
    : [];

export const createPremiumCheckout = async ({ db, sessionId, featureKey }) => {
  const settings = premiumConfig();
  if (!settings.live) {
    throw Object.assign(new Error("Premium tools are not enabled in this deployment"), {
      status: 400,
      code: "PREMIUM_DISABLED",
    });
  }
  const feature = db
    .prepare("SELECT key, name, price_minor AS priceMinor, currency, billing, enabled FROM premium_features WHERE key = ?")
    .get(featureKey);
  if (!feature || !feature.enabled) {
    throw Object.assign(new Error("This tool is not available"), { status: 404 });
  }

  const now = new Date().toISOString();
  db.prepare(
    `INSERT INTO entitlements (session_id, feature_key, status, created_at)
     VALUES (?, ?, 'pending', ?)
     ON CONFLICT(session_id, feature_key) DO UPDATE SET status = 'pending', created_at = excluded.created_at`
  ).run(sessionId, feature.key, now);

  const body = {
    mode: feature.billing === "year" ? "payment" : "subscription",
    success_url: config.donations.successUrl,
    cancel_url: config.donations.cancelUrl,
    client_reference_id: `${sessionId}:${feature.key}`,
    "metadata[kind]": "premium",
    "metadata[sessionId]": sessionId,
    "metadata[featureKey]": feature.key,
    "line_items[0][quantity]": "1",
    "line_items[0][price_data][currency]": String(feature.currency).toLowerCase(),
    "line_items[0][price_data][unit_amount]": String(feature.priceMinor),
    "line_items[0][price_data][product_data][name]": feature.name,
  };
  if (feature.billing !== "year") {
    body["line_items[0][price_data][recurring][interval]"] = feature.billing === "month" ? "month" : "month";
  }

  const response = await fetch("https://api.stripe.com/v1/checkout/sessions", {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.premium.stripeSecretKey}`,
      "content-type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams(body).toString(),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw Object.assign(new Error(payload?.error?.message || "Payment provider error"), { status: 502 });
  }
  return { checkoutUrl: payload.url, sessionId: payload.id, featureKey: feature.key };
};

export const applyPremiumEvent = ({ db, event }) => {
  const object = event?.data?.object || {};
  if (object?.metadata?.kind !== "premium") return { applied: false, reason: "not a premium event" };
  const sessionId = object.metadata.sessionId;
  const featureKey = object.metadata.featureKey;
  if (!sessionId || !featureKey) return { applied: false, reason: "missing entitlement metadata" };

  const statusByType = {
    "checkout.session.completed": "active",
    "checkout.session.expired": "expired",
    "payment_intent.payment_failed": "failed",
    "customer.subscription.deleted": "cancelled",
  };
  const status = statusByType[event.type];
  if (!status) return { applied: false, reason: `ignored event ${event.type}` };
  const result = db
    .prepare(
      `UPDATE entitlements SET status = ?, provider_ref = ?, created_at = ? WHERE session_id = ? AND feature_key = ?`
    )
    .run(status, object.id || null, new Date().toISOString(), sessionId, featureKey);
  return { applied: result.changes > 0, status };
};

export const newId = () => crypto.randomUUID();
