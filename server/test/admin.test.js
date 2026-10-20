// Admin API: operator-only endpoints protected by the x-admin-token header.
// These tests pin the three documented auth outcomes (503 when ADMIN_TOKEN is
// unset, 401 for a missing/wrong token, 200 for the correct token) and the
// top-level shape of every read-only operator response.
import assert from "node:assert/strict";
import test from "node:test";
import { prepareTestDb, startTestServer } from "./helpers.js";

const ADMIN_TOKEN = "test-admin-token";

prepareTestDb();
// The shared helper configures ADMIN_TOKEN for the common case. This file starts
// the app with administration genuinely unconfigured so the 503 branch runs
// against the real config, then restores the token for the authentication tests
// below (adminAuth reads config.admin.token on every request).
process.env.ADMIN_TOKEN = "";
const { request, close, db } = await startTestServer();
const { config } = await import("../src/config.js");

const auth = (token = ADMIN_TOKEN) => ({ "x-admin-token": token });

test.after(() => close());

test("administration is disabled (503) when ADMIN_TOKEN is unset", async () => {
  try {
    for (const path of ["/api/admin/summary", "/api/admin/audit", "/api/admin/datasets", "/api/admin/feedback"]) {
      const response = await request("GET", path, null, auth());
      assert.equal(response.status, 503, path);
      assert.match(response.json.error, /ADMIN_TOKEN is unset/);
    }
  } finally {
    config.admin.token = ADMIN_TOKEN;
  }
});

test("a missing or incorrect token is rejected with 401 and returns no operator data", async () => {
  const missing = await request("GET", "/api/admin/summary");
  assert.equal(missing.status, 401);
  assert.equal(missing.json.error, "Administrator authentication required");
  assert.equal(missing.json.counts, undefined);

  const wrong = await request("GET", "/api/admin/summary", null, auth("wrong-token"));
  assert.equal(wrong.status, 401);
  assert.equal(wrong.json.counts, undefined);

  // A token of a different length must fail the constant-time length guard.
  const wrongLength = await request("GET", "/api/admin/datasets", null, auth("test-admin-token-"));
  assert.equal(wrongLength.status, 401);

  const empty = await request("GET", "/api/admin/audit", null, { "x-admin-token": "" });
  assert.equal(empty.status, 401);
});

test("the correct token authorises every read-only operator view", async () => {
  const summary = await request("GET", "/api/admin/summary", null, auth());
  assert.equal(summary.status, 200);
  assert.equal(summary.json.counts.verses, 6236);
  assert.equal(summary.json.counts.chapters, 114);
  assert.ok(summary.json.counts.translations >= 9);
  assert.match(summary.json.dataset.checksum, /^[a-f0-9]{64}$/);
  assert.equal(summary.json.flags.donationsLive, false);
  assert.equal(typeof summary.json.flags.premiumEnabled, "boolean");

  const datasets = await request("GET", "/api/admin/datasets", null, auth());
  assert.equal(datasets.status, 200);
  assert.ok(
    datasets.json.datasets.some((row) => row.dataset === "quran-uthmani" && /^[a-f0-9]{64}$/.test(row.checksum))
  );

  const feedback = await request("GET", "/api/admin/feedback", null, auth());
  assert.equal(feedback.status, 200);
  assert.ok(Array.isArray(feedback.json.feedback));

  const audit = await request("GET", "/api/admin/audit?limit=5", null, auth());
  assert.equal(audit.status, 200);
  assert.ok(Array.isArray(audit.json.entries));
  assert.ok(audit.json.entries.length <= 5);

  const translations = await request("GET", "/api/admin/translations", null, auth());
  assert.equal(translations.status, 200);
  assert.ok(translations.json.translations.length >= 9);

  const reciters = await request("GET", "/api/admin/reciters", null, auth());
  assert.equal(reciters.status, 200);
  assert.ok(Array.isArray(reciters.json.reciters));

  const premium = await request("GET", "/api/admin/premium/features", null, auth());
  assert.equal(premium.status, 200);
  assert.equal(premium.json.enabled, false);
  assert.ok(Array.isArray(premium.json.features));

  const donations = await request("GET", "/api/admin/donations", null, auth());
  assert.equal(donations.status, 200);
  assert.ok(Array.isArray(donations.json.donations));
});

test("an authorised update is applied and recorded in the audit log", async () => {
  const created = await request("POST", "/api/feedback", {
    category: "bug",
    description: "Synthetic report used to verify the operator audit trail.",
    language: "en",
  });
  assert.equal(created.status, 201);

  const patched = await request(
    "PATCH",
    `/api/admin/feedback/${created.json.id}`,
    { status: "triaged", severity: "high" },
    auth()
  );
  assert.equal(patched.status, 200);
  assert.deepEqual(patched.json, { ok: true });
  const row = db.prepare("SELECT status, severity FROM feedback WHERE id = ?").get(created.json.id);
  assert.equal(row.status, "triaged");
  assert.equal(row.severity, "high");

  const audit = await request("GET", "/api/admin/audit", null, auth());
  const entry = audit.json.entries.find(
    (item) => item.action === "feedback.updated" && item.detail?.id === created.json.id
  );
  assert.ok(entry, "admin mutation was not written to the audit log");
});

test("the admin token is never echoed back in a response body", async () => {
  const response = await request("GET", "/api/admin/summary", null, auth());
  assert.equal(response.status, 200);
  assert.equal(response.text.includes(ADMIN_TOKEN), false);
});
