// AI assistance endpoint: optional provider, sanitisation, limits and privacy.
// A local OpenAI-compatible HTTP server stands in for Gemini so the tests never
// call a real provider, never need a real key, and can assert exactly what is
// sent over the wire and what is returned to the client.
import assert from "node:assert/strict";
import http from "node:http";
import test from "node:test";
import { prepareTestDb, startTestServer } from "./helpers.js";

prepareTestDb();
const { request, close, db } = await startTestServer();
const { config } = await import("../src/config.js");
const { resetRateLimits } = await import("../src/middleware/rateLimit.js");

const TEST_API_KEY = "test-assistant-key-never-echoed";
const MOCK_BASE_PATH = "/v1";
const PNG_BYTES = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  Buffer.from("stand-in png bytes for the mock provider"),
]);
const PNG_BASE64 = PNG_BYTES.toString("base64");

// --- mock OpenAI-compatible provider ---------------------------------------
const mock = {
  calls: [],
  status: 200,
  delayMs: 0,
  content: "",
};
const validOutput = (overrides = {}) =>
  JSON.stringify({
    intent: "smalltalk",
    reply: "Hello! I'm doing well, thanks for asking.",
    searchTerms: [],
    imageText: "",
    ...overrides,
  });

mock.content = validOutput();

const mockServer = http.createServer((req, res) => {
  let raw = "";
  req.on("data", (chunk) => {
    raw += chunk;
  });
  req.on("end", async () => {
    let body = null;
    try {
      body = raw ? JSON.parse(raw) : null;
    } catch {
      body = null;
    }
    mock.calls.push({ method: req.method, url: req.url, headers: req.headers, body });
    if (mock.delayMs) await new Promise((resolve) => setTimeout(resolve, mock.delayMs));
    if (res.writableEnded || res.destroyed) return; // client timed out
    try {
      if (mock.status !== 200) {
        res.writeHead(mock.status, { "content-type": "application/json" });
        res.end(JSON.stringify({ error: "upstream-secret-error-detail" }));
        return;
      }
      res.writeHead(200, { "content-type": "application/json" });
      res.end(
        JSON.stringify({
          id: "raw-upstream-completion-id",
          object: "chat.completion",
          choices: [{ index: 0, message: { role: "assistant", content: mock.content } }],
          usage: { total_tokens: 7 },
          secret: "upstream-secret-field",
        })
      );
    } catch {
      // The test client may have aborted the request; nothing to do.
    }
  });
});
await new Promise((resolve) => mockServer.listen(0, "127.0.0.1", resolve));
const mockBaseUrl = `http://127.0.0.1:${mockServer.address().port}${MOCK_BASE_PATH}`;

// --- helpers ---------------------------------------------------------------
const configureProvider = () => {
  config.ai.provider = "openai-compatible";
  config.ai.baseUrl = mockBaseUrl;
  config.ai.apiKey = TEST_API_KEY;
  config.ai.model = "test-model";
  config.ai.timeoutMs = 8000;
  config.ai.maxMonthlyCalls = 0;
};
const unconfigureProvider = () => {
  config.ai.provider = "none";
  config.ai.baseUrl = "";
  config.ai.apiKey = "";
  config.ai.model = "gemini-2.5-flash";
  config.ai.maxMonthlyCalls = 0;
};
const resetMock = () => {
  mock.calls.length = 0;
  mock.status = 200;
  mock.delayMs = 0;
  mock.content = validOutput();
};
const resetBudget = () => db.prepare("DELETE FROM settings WHERE key LIKE 'ai_assistant_calls:%'").run();
const cleanSlate = () => {
  resetRateLimits();
  resetMock();
  resetBudget();
};
const post = (body) => request("POST", "/api/assistant", body);
const noSecrets = (response, label = "") => {
  assert.equal(response.text.includes(TEST_API_KEY), false, `${label}: API key leaked in response body`);
  assert.equal(response.text.includes("upstream-secret"), false, `${label}: upstream detail leaked`);
  assert.equal(response.text.toLowerCase().includes("authorization"), false, `${label}: auth header echoed`);
};

test.after(async () => {
  await close();
  await new Promise((resolve) => mockServer.close(resolve));
});

// --- disabled state --------------------------------------------------------
test("assistant is disabled (503) until a provider is configured and reports status without secrets", async () => {
  unconfigureProvider();
  cleanSlate();
  const response = await post({ text: "Hello" });
  assert.equal(response.status, 503);
  assert.equal(response.json.error, "AI assistance is not enabled. Configure AI_PROVIDER and AI_API_KEY on the server.");
  noSecrets(response, "503");

  const status = await request("GET", "/api/assistant/status");
  assert.equal(status.status, 200);
  assert.deepEqual(status.json, { enabled: false, providerConfigured: false });
  noSecrets(status, "status");
});

test("status reports a configured provider without exposing the key, model or base URL", async () => {
  configureProvider();
  try {
    const status = await request("GET", "/api/assistant/status");
    assert.equal(status.status, 200);
    assert.deepEqual(status.json, { enabled: true, providerConfigured: true });
    noSecrets(status, "configured status");
    assert.equal(status.text.includes("test-model"), false);
    assert.equal(status.text.includes(mockBaseUrl), false);

    // A provider name without a key is not a usable configuration.
    config.ai.apiKey = "";
    const missingKey = await request("GET", "/api/assistant/status");
    assert.deepEqual(missingKey.json, { enabled: false, providerConfigured: false });
    config.ai.apiKey = TEST_API_KEY;

    config.ai.provider = "gemini";
    const unsupported = await request("GET", "/api/assistant/status");
    assert.deepEqual(unsupported.json, { enabled: false, providerConfigured: false });
  } finally {
    unconfigureProvider();
  }
});

// --- request validation ----------------------------------------------------
test("invalid requests are rejected with 400 before any provider call", async () => {
  configureProvider();
  cleanSlate();
  const cases = [
    [{}, /Provide text or an attachment/],
    [{ text: "   " }, /Provide text or an attachment/],
    [{ text: "hi", mode: "vision" }, /mode must be "chat"/],
    [{ text: 42 }, /text must be a string/],
    [{ text: "x".repeat(1001) }, /text must be at most 1000 characters/],
    [{ text: "hi", attachment: "not-an-object" }, /attachment must be an object/],
    [{ text: "hi", attachment: { mime: "text/plain", dataBase64: "AAAA" } }, /attachment\.mime must be one of/],
    [{ text: "hi", attachment: { mime: "image/png" } }, /dataBase64 is required/],
    [{ text: "hi", attachment: { mime: "image/png", dataBase64: "not-base64!!" } }, /not valid base64/],
    [
      { text: "hi", attachment: { mime: "image/png", dataBase64: Buffer.from("definitely not a png").toString("base64") } },
      /does not appear to be a valid image\/png/,
    ],
  ];
  for (const [body, expected] of cases) {
    resetRateLimits();
    const response = await post(body);
    assert.equal(response.status, 400, JSON.stringify(body).slice(0, 120));
    assert.match(response.json.error, expected);
    noSecrets(response, "validation");
  }
  assert.equal(mock.calls.length, 0, "invalid requests must never reach the provider");
});

test("an oversized attachment is rejected with 413 before the provider is called", async () => {
  configureProvider();
  cleanSlate();
  // One byte over the 5 MB decoded limit; ~6.7 MB as base64, which proves the
  // assistant route (not the global 128 KB parser) reads the body.
  const tooBig = Buffer.alloc(5 * 1024 * 1024 + 1, 0x41).toString("base64");
  const response = await post({ text: "What is this?", attachment: { name: "screen.png", mime: "image/png", dataBase64: tooBig } });
  assert.equal(response.status, 413);
  assert.equal(response.json.error, "Attachment is too large (maximum 5 MB decoded)");
  assert.equal(mock.calls.length, 0);
  noSecrets(response, "413");
  unconfigureProvider();
});

test("a request body above the 8 MB parser limit is rejected with 413", async () => {
  cleanSlate();
  const response = await post({ text: "x".repeat(9 * 1024 * 1024) });
  assert.equal(response.status, 413);
});

test("the assistant allowance is rate limited per IP and returns retry-after", async () => {
  unconfigureProvider(); // keep the allowed calls cheap (503) — the limiter runs first
  cleanSlate();
  const max = config.rateLimit.assistantMax;
  assert.ok(max > 0);
  for (let index = 0; index < max; index += 1) {
    const response = await post({ text: "hello" });
    assert.notEqual(response.status, 429, `request ${index + 1} should be within the allowance`);
  }
  const blocked = await post({ text: "hello" });
  assert.equal(blocked.status, 429);
  assert.match(blocked.json.error, /Too many requests/);
  assert.ok(Number(blocked.headers.get("retry-after")) > 0);
  noSecrets(blocked, "429");
  resetRateLimits();
});

// --- success paths ---------------------------------------------------------
test("small talk returns the fixed contract, strips Markdown fences and calls the configured provider correctly", async () => {
  configureProvider();
  cleanSlate();
  mock.content = "```json\n" + validOutput({ searchTerms: ["  Patience  ", "صبر", "**patience**"] }) + "\n```";

  const response = await post({ text: "How are you doing today?", language: "en" });
  assert.equal(response.status, 200);
  assert.deepEqual(Object.keys(response.json).sort(), ["enabled", "imageText", "intent", "reply", "searchTerms"]);
  assert.equal(response.json.enabled, true);
  assert.equal(response.json.intent, "smalltalk");
  assert.equal(response.json.reply, "Hello! I'm doing well, thanks for asking.");
  assert.deepEqual(response.json.searchTerms, ["Patience"]);
  assert.equal(response.json.imageText, null);
  noSecrets(response, "success");

  assert.equal(mock.calls.length, 1);
  const call = mock.calls[0];
  assert.equal(call.method, "POST");
  assert.equal(call.url, `${MOCK_BASE_PATH}/chat/completions`);
  assert.equal(call.headers.authorization, `Bearer ${TEST_API_KEY}`);
  assert.equal(call.headers["content-type"], "application/json");
  assert.equal(call.body.model, "test-model");
  assert.equal(call.body.temperature, 0);
  assert.equal(call.body.messages.length, 2);
  assert.equal(call.body.messages[0].role, "system");
  assert.match(call.body.messages[0].content, /never quote/i);
  assert.match(call.body.messages[0].content, /never supply religious content/i);
  assert.match(call.body.messages[0].content, /smalltalk\|question\|offtopic\|attachment/);
  assert.equal(call.body.messages[1].role, "user");
  assert.equal(typeof call.body.messages[1].content, "string");
  assert.match(call.body.messages[1].content, /How are you doing today\?/);
  assert.equal(call.body.messages[1].content.includes("data:image"), false);
  unconfigureProvider();
});

test("an attached screenshot is sent as an image_url part and OCR text may be non-Latin", async () => {
  configureProvider();
  cleanSlate();
  mock.content =
    "```json\n" +
    JSON.stringify({
      intent: "attachment",
      reply: "",
      searchTerms: ["patience", "sabr"],
      imageText: "ما معنى الصبر في هذا السؤال؟\n**second line**",
    }) +
    "\n```";

  const response = await post({
    text: "What is written in this screenshot?",
    language: "ar",
    attachment: { name: "screenshot.png", mime: "image/png", dataBase64: PNG_BASE64 },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.intent, "attachment");
  assert.equal(response.json.reply, null, "reply is never shown for attachment intents");
  assert.equal(response.json.imageText, "ما معنى الصبر في هذا السؤال؟\nsecond line");
  assert.deepEqual(response.json.searchTerms, ["patience", "sabr"]);
  noSecrets(response, "attachment");

  assert.equal(mock.calls.length, 1);
  const content = mock.calls[0].body.messages[1].content;
  assert.ok(Array.isArray(content));
  assert.equal(content.length, 2);
  assert.equal(content[0].type, "text");
  assert.match(content[0].text, /What is written in this screenshot\?/);
  assert.equal(content[1].type, "image_url");
  assert.equal(content[1].image_url.url, `data:image/png;base64,${PNG_BASE64}`);
  unconfigureProvider();
});

// --- sanitisation ----------------------------------------------------------
test("a reply containing Arabic script is dropped instead of being shown", async () => {
  configureProvider();
  cleanSlate();
  mock.content = validOutput({ reply: "الصبر مفتاح الفرج", searchTerms: [] });

  const response = await post({ text: "hello" });
  assert.equal(response.status, 200);
  assert.equal(response.json.intent, "smalltalk");
  assert.equal(response.json.reply, null);
  assert.deepEqual(response.json.searchTerms, []);
  noSecrets(response, "arabic reply");
  unconfigureProvider();
});

test("a reply that looks like a quotation or a verse reference is dropped", async () => {
  configureProvider();
  cleanSlate();
  const quoted =
    'They said, "Indeed the patient ones will be rewarded without measure for what they endured," and that is settled.';
  mock.content = validOutput({ reply: quoted });
  let response = await post({ text: "hello" });
  assert.equal(response.status, 200);
  assert.equal(response.json.reply, null, "long quotation must be dropped");

  resetRateLimits();
  mock.content = validOutput({ reply: "See Surah 2 verse 155 for that." });
  response = await post({ text: "hello" });
  assert.equal(response.status, 200);
  assert.equal(response.json.reply, null, "verse references must be dropped");

  resetRateLimits();
  mock.content = validOutput({ reply: "The Quran mentions patience many times." });
  response = await post({ text: "hello" });
  assert.equal(response.status, 200);
  assert.equal(response.json.reply, null, "religious commentary must be dropped");
  noSecrets(response, "sanitised reply");
  unconfigureProvider();
});

test("over-long neutral replies and search-term lists are capped", async () => {
  configureProvider();
  cleanSlate();
  const longReply = "This is a calm and neutral sentence about the weather and coffee. ".repeat(8).trim();
  assert.ok(longReply.length > 300);
  const manyTerms = Array.from({ length: 10 }, (_, index) => `${"a".repeat(39)}${String.fromCharCode(98 + index)}`);
  mock.content = validOutput({ reply: longReply, searchTerms: manyTerms });

  const response = await post({ text: "hello" });
  assert.equal(response.status, 200);
  assert.ok(response.json.reply.length <= 300, "reply must be capped at 300 characters");
  assert.ok(longReply.startsWith(response.json.reply.slice(0, 40)));
  assert.ok(response.json.searchTerms.length <= 6, "at most six search terms");
  assert.ok(response.json.searchTerms.join(" ").length <= 200, "search terms must stay under 200 characters");
  assert.ok(response.json.searchTerms.every((term) => /^[\x20-\x7E]+$/.test(term)), "search terms are ASCII");
  unconfigureProvider();
});

// --- failure behaviour -----------------------------------------------------
test("provider errors and unreadable output return a generic 502 without leaking details", async () => {
  configureProvider();
  cleanSlate();
  mock.status = 500;
  let response = await post({ text: "hello" });
  assert.equal(response.status, 502);
  assert.equal(response.json.error, "AI assistance is temporarily unavailable. Please try again.");
  assert.equal(mock.calls.length, 1);
  noSecrets(response, "provider 500");

  resetRateLimits();
  mock.status = 200;
  mock.content = "I am not JSON at all.";
  response = await post({ text: "hello" });
  assert.equal(response.status, 502);
  noSecrets(response, "unreadable output");
  unconfigureProvider();
});

test("a provider timeout returns 504 instead of hanging the request", async () => {
  configureProvider();
  cleanSlate();
  config.ai.timeoutMs = 200;
  mock.delayMs = 800;
  try {
    const response = await post({ text: "hello" });
    assert.equal(response.status, 504);
    assert.equal(response.json.error, "AI assistance timed out. Please try again.");
    noSecrets(response, "timeout");
  } finally {
    config.ai.timeoutMs = 8000;
    mock.delayMs = 0;
    unconfigureProvider();
  }
});

test("the optional monthly call cap stops calls with 429 once exhausted", async () => {
  configureProvider();
  cleanSlate();
  config.ai.maxMonthlyCalls = 1;
  try {
    const first = await post({ text: "hello" });
    assert.equal(first.status, 200);
    const second = await post({ text: "hello" });
    assert.equal(second.status, 429);
    assert.equal(second.json.error, "AI assistance monthly limit reached. Please try again next month.");
    assert.equal(mock.calls.length, 1, "the budget must stop the provider call");
    noSecrets(second, "monthly cap");
  } finally {
    config.ai.maxMonthlyCalls = 0;
    resetBudget();
    unconfigureProvider();
  }
});

// --- middleware inheritance ------------------------------------------------
test("assistant routes inherit the app CORS preflight and helmet headers", async () => {
  cleanSlate();
  const origin = config.corsOrigins.find((entry) => entry !== "*") || "http://localhost:5173";
  const preflight = await request("OPTIONS", "/api/assistant", null, {
    origin,
    "access-control-request-method": "POST",
    "access-control-request-headers": "content-type",
  });
  assert.equal(preflight.status, 204);
  assert.equal(preflight.headers.get("access-control-allow-origin"), origin);
  assert.match(preflight.headers.get("access-control-allow-methods"), /POST/);

  const status = await request("GET", "/api/assistant/status");
  assert.equal(status.headers.get("x-content-type-options"), "nosniff");
  assert.equal(status.headers.get("x-powered-by"), null);
});
