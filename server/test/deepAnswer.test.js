// Deep grounded-answer endpoint: reasoning output over supplied verses, app index
// facts for counts/lists, citation whitelisting, Arabic rejection, validation.
import assert from "node:assert/strict";
import http from "node:http";
import test from "node:test";
import { prepareTestDb, startTestServer } from "./helpers.js";

prepareTestDb();
const { request, close, db } = await startTestServer();
const { config } = await import("../src/config.js");
const { resetRateLimits } = await import("../src/middleware/rateLimit.js");

const TEST_API_KEY = "test-answer-key-never-echoed";
const validAnswer =
  "The app's reviewed index names 25 prophets, from Adam to Muhammad, and the Quran itself says that some messengers were not mentioned [4:164]. It also confirms that God sent messengers before Muhammad and that their stories are not all told [40:78]. The count is therefore an index count of named figures, not a number stated in a single verse.";

const mock = { calls: [], status: 200, delayMs: 0, content: validAnswer };

const mockServer = http.createServer((req, res) => {
  let raw = "";
  req.on("data", (chunk) => { raw += chunk; });
  req.on("end", async () => {
    let body = null;
    try { body = raw ? JSON.parse(raw) : null; } catch { body = null; }
    mock.calls.push({ method: req.method, url: req.url, headers: req.headers, body, raw });
    if (mock.delayMs) await new Promise((resolve) => setTimeout(resolve, mock.delayMs));
    if (res.writableEnded || res.destroyed) return;
    try {
      if (mock.status !== 200) {
        res.writeHead(mock.status, { "content-type": "application/json" });
        res.end(JSON.stringify({ error: "upstream-secret-error-detail" }));
        return;
      }
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ choices: [{ index: 0, message: { role: "assistant", content: mock.content } }] }));
    } catch {
      // client aborted
    }
  });
});
await new Promise((resolve) => mockServer.listen(0, "127.0.0.1", resolve));
const mockBaseUrl = "http://127.0.0.1:" + mockServer.address().port + "/v1";

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
  config.ai.model = "gemini-flash-lite-latest";
  config.ai.maxMonthlyCalls = 0;
};
const cleanSlate = () => {
  resetRateLimits();
  mock.calls.length = 0;
  mock.status = 200;
  mock.delayMs = 0;
  mock.content = validAnswer;
  db.prepare("DELETE FROM settings WHERE key LIKE 'ai_assistant_calls:%'").run();
};
const post = (body) => request("POST", "/api/assistant/answer", body);
const validBody = () => ({
  question: "How many prophets are named in the Quran?",
  language: "en",
  verseKeys: ["4:164", "40:78"],
  facts: ["The app's reviewed index names 25 prophets, from Adam to Muhammad."],
});
const noSecrets = (response, label = "") => {
  assert.equal(response.text.includes(TEST_API_KEY), false, label + ": API key leaked");
  assert.equal(response.text.includes("upstream-secret"), false, label + ": upstream detail leaked");
};

test.after(async () => {
  await close();
  await new Promise((resolve) => mockServer.close(resolve));
});

test("answer endpoint is 503 until a provider is configured", async () => {
  unconfigureProvider();
  cleanSlate();
  const response = await post(validBody());
  assert.equal(response.status, 503);
  noSecrets(response, "503");
  assert.equal(mock.calls.length, 0);
});

test("answer validation rejects bad requests before any provider call", async () => {
  configureProvider();
  const cases = [
    [{}, /question is required/],
    [{ question: "   ", verseKeys: ["4:164"] }, /question is required/],
    [{ question: "x".repeat(501), verseKeys: ["4:164"] }, /at most 500/],
    [{ question: "prophets" }, /verseKeys must be an array/],
    [{ question: "prophets", verseKeys: [] }, /must not be empty/],
    [{ question: "prophets", verseKeys: Array.from({ length: 19 }, () => "4:164") }, /at most 18/],
    [{ question: "prophets", verseKeys: ["4:164"], facts: "count 25" }, /facts must be an array/],
    [{ question: "prophets", verseKeys: ["4:164"], facts: [123] }, /facts entries must be strings/],
    [{ question: "prophets", verseKeys: ["4:164"], facts: ["x".repeat(301)] }, /at most 300/],
    [{ question: "prophets", verseKeys: ["4:164"], facts: ["یہاں 25 prophets ہیں"] }, /must not contain Arabic script/],
  ];
  for (const [body, expected] of cases) {
    cleanSlate();
    const response = await post(body);
    assert.equal(response.status, 400, JSON.stringify(body).slice(0, 120));
    assert.match(response.json.error, expected);
    noSecrets(response, "validation");
    assert.equal(mock.calls.length, 0);
  }
  cleanSlate();
  const unknown = await post({ question: "prophets", verseKeys: ["999:1"] });
  assert.equal(unknown.status, 400);
});

test("a grounded reasoning answer is returned with index facts in the prompt", async () => {
  configureProvider();
  cleanSlate();
  const response = await post(validBody());
  assert.equal(response.status, 200, response.text.slice(0, 300));
  assert.match(response.json.answer, /25 prophets/);
  assert.deepEqual(response.json.citations, ["4:164", "40:78"]);
  assert.equal(mock.calls.length, 1);
  const sent = mock.calls[0].body;
  assert.equal(sent.messages[0].role, "system");
  assert.match(sent.messages[0].content, /Quran-only research assistant/);
  assert.match(sent.messages[0].content, /distinguish explicitly/i);
  const user = sent.messages[1].content;
  assert.match(user, /App index facts/);
  assert.match(user, /names 25 prophets/);
  assert.match(user, /\[4:164\]/);
  assert.match(user, /\[40:78\]/);
  assert.match(user, /untrusted data/);
  noSecrets(response);
});

test("an answer citing a reference outside the supplied verses is rejected", async () => {
  configureProvider();
  cleanSlate();
  mock.content = "The count is 25 named prophets [4:164], and the Quran says others were not mentioned [40:78]. A second passage outside the set is [2:255].";
  const response = await post(validBody());
  assert.equal(response.status, 502);
  noSecrets(response);
});

test("Arabic script or a missing citation is rejected", async () => {
  configureProvider();
  cleanSlate();
  mock.content = "The app's reviewed index names 25 prophets [4:164], and the Quran says some messengers were not mentioned [40:78]. ".repeat(2);
  mock.content = mock.content.replace("[4:164]", "[4:164] عيسى");
  const arabic = await post(validBody());
  assert.equal(arabic.status, 502);

  cleanSlate();
  mock.content = "The app's reviewed index names 25 prophets. The Quran says some messengers were not mentioned. The count is an index count, not a verse number, and the passages support the qualification but the reply deliberately avoids references. ".repeat(1);
  const uncited = await post(validBody());
  assert.equal(uncited.status, 502);
});

test("conversation history is passed as context only", async () => {
  configureProvider();
  cleanSlate();
  const body = validBody();
  body.history = [
    { role: "user", content: "Who is Isa?" },
    { role: "assistant", content: "The Quran mentions Isa in several passages." },
  ];
  const response = await post(body);
  assert.equal(response.status, 200, response.text.slice(0, 300));
  const user = mock.calls[0].body.messages[1].content;
  assert.match(user, /Recent conversation/);
  assert.match(user, /User: Who is Isa\?/);
  assert.match(user, /never a source of Quran text/i);
});

test("provider failures and timeouts map to 502 and 504", async () => {
  configureProvider();
  cleanSlate();
  mock.status = 500;
  const failed = await post(validBody());
  assert.equal(failed.status, 502);
  noSecrets(failed);

  cleanSlate();
  mock.delayMs = 200;
  config.ai.timeoutMs = 50;
  const timedOut = await post(validBody());
  assert.equal(timedOut.status, 504);
  config.ai.timeoutMs = 8000;
});
