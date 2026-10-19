// End-to-end API behaviour over HTTP with a real (throw-away) dataset copy.
import assert from "node:assert/strict";
import test from "node:test";
import { prepareTestDb, startTestServer } from "./helpers.js";

prepareTestDb();
const { request, close, index, db } = await startTestServer();

test.after(() => close());

test("health reports the imported dataset, not a placeholder", async () => {
  const response = await request("GET", "/api/health");
  assert.equal(response.status, 200);
  assert.equal(response.json.dataset.verses, 6236);
  assert.equal(response.json.dataset.chapters, 114);
  assert.match(response.json.dataset.checksum, /^[a-f0-9]{64}$/);
});

test("meta exposes the ten interface languages and the licence status of every translation", async () => {
  const response = await request("GET", "/api/meta");
  assert.equal(response.status, 200);
  assert.equal(response.json.languages.length, 10);
  assert.deepEqual(
    response.json.languages.map((language) => language.code).sort(),
    ["ar", "bn", "en", "fa", "fr", "id", "ms", "sw", "tr", "ur"].sort()
  );
  assert.equal(response.json.translations.length, 9);
  for (const translation of response.json.translations) {
    assert.equal(translation.licenseStatus, "unverified");
  }
  // Admin secrets are never exposed.
  assert.equal(JSON.stringify(response.json).includes("test-admin-token"), false);
});

test("ask returns verified passages with the required notice", async () => {
  const response = await request("POST", "/api/ask", { question: "What does the Quran say about patience?", language: "en" });
  assert.equal(response.status, 200);
  const { answer, conversationId } = response.json;
  assert.ok(conversationId);
  assert.equal(answer.kind, "answer");
  assert.ok(answer.passages.length > 0);
  assert.match(answer.notice.text, /^Notice: I am an AI system designed to help locate Quranic verses/);
  for (const passage of answer.passages) {
    const verse = index.verseByKey.get(passage.verseKey);
    assert.ok(verse, `fabricated reference ${passage.verseKey}`);
    assert.equal(passage.arabic, verse.text_uthmani);
    assert.ok(passage.translation.text.length > 10);
    assert.equal(passage.source.checksum.length, 64);
  }
});

test("a follow-up 'more' request returns different passages from the same conversation", async () => {
  const first = await request("POST", "/api/ask", { question: "patience", language: "en" });
  const conversationId = first.json.conversationId;
  const seen = first.json.answer.passages.map((passage) => passage.verseKey);
  const cookie = (first.headers.getSetCookie?.()[0] || first.headers.get("set-cookie") || "").split(";")[0];
  const second = await request("POST", "/api/ask", { question: "more", language: "en", conversationId }, { cookie });
  assert.equal(second.status, 200);
  const next = second.json.answer.passages.map((passage) => passage.verseKey);
  assert.ok(next.length > 0);
  for (const key of next) assert.equal(seen.includes(key), false, `${key} was repeated`);
});

test("a question with no relevant evidence returns the exact fallback, never a fabricated verse", async () => {
  const response = await request("POST", "/api/ask", {
    question: "What does the Quran say about superconductors and quantum tunnelling?",
    language: "en",
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.answer.kind, "fallback");
  assert.equal(response.json.answer.passages.length, 0);
  assert.match(response.json.answer.message, /it does not establish that no relevant verses exist/);
});

test("invalid questions are rejected", async () => {
  const empty = await request("POST", "/api/ask", { question: "" });
  assert.equal(empty.status, 400);
  const huge = await request("POST", "/api/ask", { question: "x".repeat(1200) });
  assert.equal(huge.status, 400);
});

test("chapter and verse endpoints serve the canonical text", async () => {
  const chapters = await request("GET", "/api/chapters");
  assert.equal(chapters.json.chapters.length, 114);
  const verse = await request("GET", "/api/verses/2:255?language=en");
  assert.equal(verse.status, 200);
  assert.equal(verse.json.verseKey, "2:255");
  assert.ok(verse.json.arabic.includes("ٱللَّهُ"));
  assert.ok(verse.json.translation.text.length > 20);
  const missing = await request("GET", "/api/verses/999:1");
  assert.equal(missing.status, 404);
  const chapter = await request("GET", "/api/chapters/1?language=en");
  assert.equal(chapter.json.chapter.versesCount, 7);
  assert.equal(chapter.json.verses.length, 7);
});

test("sources expose dataset provenance and each translation's licence status", async () => {
  const response = await request("GET", "/api/sources");
  assert.equal(response.status, 200);
  assert.ok(response.json.datasets.some((dataset) => dataset.dataset === "quran-uthmani" && dataset.checksum));
  assert.equal(response.json.translations.length, 9);
  assert.ok(response.json.translations.every((translation) => translation.licenseStatus === "unverified"));
  assert.ok(response.json.reciters.every((reciter) => reciter.rightsStatus === "unverified" && reciter.enabled === 0));
});

test("feedback requires a known category and explicit consent for diagnostics", async () => {
  const invalid = await request("POST", "/api/feedback", { category: "made-up", description: "hello there" });
  assert.equal(invalid.status, 400);

  const created = await request("POST", "/api/feedback", {
    category: "missing_verse",
    description: "I expected a verse about a specific topic.",
    language: "en",
    verseReference: "2:255",
    diagnostics: { question: "secret question text" },
    consent: false,
  });
  assert.equal(created.status, 201);
  assert.equal(created.json.status, "open");
  assert.match(created.json.message, /not treated as a verified correction/i);
  const row = db.prepare("SELECT diagnostics_json FROM feedback WHERE id = ?").get(created.json.id);
  assert.equal(row.diagnostics_json, null, "diagnostics must not be stored without consent");

  const withConsent = await request("POST", "/api/feedback", {
    category: "translation_display",
    description: "The translation looked cut off in the card.",
    diagnostics: { question: "sample" },
    consent: true,
  });
  assert.equal(withConsent.status, 201);
  const consentRow = db
    .prepare("SELECT diagnostics_json FROM feedback WHERE id = ?")
    .get(withConsent.json.id);
  assert.ok(consentRow.diagnostics_json);
});

test("donations are disabled by default and never requested silently", async () => {
  const config = await request("GET", "/api/donations/config");
  assert.equal(config.json.live, false);
  assert.ok(config.json.blockedReasons.length > 0);
  const checkout = await request("POST", "/api/donations/checkout", { amountMinor: 5000, currency: "AED" });
  assert.equal(checkout.status, 200);
  assert.equal(checkout.json.live, false);
  assert.ok(checkout.json.donationId);
});

test("premium tools are disabled and cannot be purchased from a disabled deployment", async () => {
  const features = await request("GET", "/api/premium/features");
  assert.equal(features.json.enabled, false);
  assert.equal(features.json.features.length, 6);
  assert.ok(features.json.features.every((feature) => feature.available === false));
  const checkout = await request("POST", "/api/premium/checkout", { featureKey: "saved_collections" });
  assert.equal(checkout.status, 400);
});

test("recitation is disabled until recording rights are verified", async () => {
  const response = await request("GET", "/api/reciters");
  assert.equal(response.json.enabled, false);
  const audio = await request("GET", "/api/recitations/2:255");
  assert.equal(audio.status, 403);
});

test("admin endpoints require the operator token", async () => {
  const unauthorised = await request("GET", "/api/admin/summary");
  assert.equal(unauthorised.status, 401);
  const authorised = await request("GET", "/api/admin/summary", null, { "x-admin-token": "test-admin-token" });
  assert.equal(authorised.status, 200);
  assert.equal(authorised.json.counts.verses, 6236);
  assert.equal(authorised.json.flags.donationsLive, false);
});

test("a conversation can only be read by the session that created it", async () => {
  const created = await request("POST", "/api/ask", { question: "mercy", language: "en" });
  const conversationId = created.json.conversationId;
  const cookie = created.headers.getSetCookie?.()[0] || created.headers.get("set-cookie");
  assert.ok(cookie);
  const ownerRead = await request("GET", `/api/conversations/${conversationId}`, null, { cookie: cookie.split(";")[0] });
  assert.equal(ownerRead.status, 200);
  assert.ok(ownerRead.json.messages.length >= 2);
  const otherSession = await request("GET", `/api/conversations/${conversationId}`);
  assert.equal(otherSession.status, 404);
});

test("deleting a conversation removes its messages", async () => {
  const created = await request("POST", "/api/ask", { question: "guidance", language: "en" });
  const conversationId = created.json.conversationId;
  const cookie = created.headers.getSetCookie?.()[0] || created.headers.get("set-cookie");
  const deleted = await request("DELETE", `/api/conversations/${conversationId}`, null, { cookie: cookie.split(";")[0] });
  assert.equal(deleted.status, 200);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM messages WHERE conversation_id = ?").get(conversationId).n, 0);
});
