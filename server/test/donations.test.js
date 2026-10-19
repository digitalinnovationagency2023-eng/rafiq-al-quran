// Donations: validation, disabled-by-default behaviour and provider callbacks.
import assert from "node:assert/strict";
import crypto from "node:crypto";
import test from "node:test";
import { prepareTestDb } from "./helpers.js";

prepareTestDb();

const { getDb } = await import("../src/db.js");
const {
  validateDonationAmount,
  donationConfig,
  createDonationCheckout,
  verifyStripeSignature,
  applyDonationEvent,
} = await import("../src/services/donations.js");

const db = getDb();

test("donation amounts are validated as whole minor units inside the configured range", () => {
  assert.equal(validateDonationAmount({ amountMinor: 5000, currency: "AED" }), null);
  assert.match(validateDonationAmount({ amountMinor: 1, currency: "AED" }), /at least/);
  assert.match(validateDonationAmount({ amountMinor: 900000000, currency: "AED" }), /at most/);
  assert.match(validateDonationAmount({ amountMinor: 10.5, currency: "AED" }), /whole number/);
  assert.match(validateDonationAmount({ amountMinor: 5000, currency: "XYZ" }), /Unsupported currency/);
});

test("donations are blocked until the operator enables them and records compliance approval", () => {
  const settings = donationConfig();
  assert.equal(settings.enabled, false);
  assert.equal(settings.live, false);
  assert.ok(settings.blockedReasons.some((reason) => reason.includes("DONATIONS_ENABLED")));
  assert.ok(settings.blockedReasons.some((reason) => reason.includes("COMPLIANCE")));
  assert.match(settings.statement, /voluntary/i);
  assert.doesNotMatch(settings.statement, /you will (be rewarded|receive)/i);
});

test("a disabled deployment records a pending donation and never contacts a provider", async () => {
  const result = await createDonationCheckout({
    db,
    amountMinor: 2500,
    currency: "AED",
    recurring: false,
    email: "test@example.com",
    sessionId: "session-1",
  });
  assert.equal(result.live, false);
  assert.ok(result.donationId);
  const row = db.prepare("SELECT status, amount_minor, currency, provider_ref FROM donations WHERE id = ?").get(result.donationId);
  assert.equal(row.status, "pending");
  assert.equal(row.amount_minor, 2500);
  assert.equal(row.provider_ref, null);
});

test("recurring donations are refused when recurring collection is not enabled", async () => {
  await assert.rejects(
    () =>
      createDonationCheckout({
        db,
        amountMinor: 2500,
        currency: "AED",
        recurring: true,
        sessionId: "session-1",
      }),
    /Recurring donations are not enabled/
  );
});

test("out-of-range donations are rejected before any row is created", async () => {
  await assert.rejects(
    () => createDonationCheckout({ db, amountMinor: 10, currency: "AED", sessionId: "session-1" }),
    /at least/
  );
});

test("Stripe signatures are verified and forged or stale signatures are rejected", () => {
  const secret = "whsec_test_secret";
  const payload = JSON.stringify({ id: "evt_1", type: "checkout.session.completed" });
  const timestamp = Math.floor(Date.now() / 1000);
  const signature = crypto.createHmac("sha256", secret).update(`${timestamp}.${payload}`).digest("hex");
  assert.equal(
    verifyStripeSignature({ payload, header: `t=${timestamp},v1=${signature}`, secret }),
    true
  );
  assert.equal(
    verifyStripeSignature({ payload, header: `t=${timestamp},v1=${"0".repeat(64)}`, secret }),
    false
  );
  const stale = timestamp - 10_000;
  const staleSignature = crypto.createHmac("sha256", secret).update(`${stale}.${payload}`).digest("hex");
  assert.equal(
    verifyStripeSignature({ payload, header: `t=${stale},v1=${staleSignature}`, secret }),
    false
  );
});

test("provider events update the donation state idempotently and only for donations", () => {
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  db.prepare(
    `INSERT INTO donations (id, amount_minor, currency, status, provider, provider_ref, recurring, donor_email, created_at, updated_at)
     VALUES (?, 1000, 'AED', 'pending', 'stripe', 'cs_test_1', 0, NULL, ?, ?)`
  ).run(id, now, now);

  const paid = applyDonationEvent({
    db,
    event: {
      type: "checkout.session.completed",
      data: { object: { id: "cs_test_1", client_reference_id: id, metadata: { kind: "donation", donationId: id } } },
    },
  });
  assert.equal(paid.applied, true);
  assert.equal(db.prepare("SELECT status FROM donations WHERE id = ?").get(id).status, "paid");

  const ignored = applyDonationEvent({
    db,
    event: {
      type: "checkout.session.completed",
      data: { object: { id: "cs_test_2", metadata: { kind: "premium", donationId: id } } },
    },
  });
  assert.equal(ignored.applied, false);

  const refunded = applyDonationEvent({
    db,
    event: {
      type: "charge.refunded",
      data: { object: { id: "ch_1", metadata: { kind: "donation", donationId: id } } },
    },
  });
  assert.equal(refunded.applied, true);
  assert.equal(db.prepare("SELECT status FROM donations WHERE id = ?").get(id).status, "refunded");
});

test("no card data is ever stored: the donations table has no card columns", () => {
  const columns = db.prepare("PRAGMA table_info(donations)").all().map((column) => column.name);
  for (const forbidden of ["card", "pan", "cvc", "cvv", "expiry", "card_number"]) {
    assert.equal(columns.includes(forbidden), false, `donations must not store ${forbidden}`);
  }
});
