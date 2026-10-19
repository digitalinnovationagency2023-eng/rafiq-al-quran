// Safety and isolation: exact required wording, disabled paid capabilities,
// separation from RQM Books and no accidental exposure of secrets.
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { prepareTestDb, serverRoot } from "./helpers.js";

prepareTestDb();

const { getDb } = await import("../src/db.js");
const { config } = await import("../src/config.js");
const { buildIndex } = await import("../src/services/retrieval.js");
const {
  LANGUAGES,
  NOTICES,
  FALLBACKS,
  NOTICE_EN,
  NOTICE_AR,
  FALLBACK_EN,
  FALLBACK_AR,
  languageMeta,
} = await import("../src/services/languages.js");
const { premiumConfig, listFeatures, hasEntitlement, createPremiumCheckout, seedPremiumFeatures } = await import("../src/services/premium.js");
const { seedReciters } = await import("../src/services/seed.js");
const { rateLimit, resetRateLimits } = await import("../src/middleware/rateLimit.js");

const db = getDb();
seedPremiumFeatures(db);
seedReciters(db);
buildIndex(db);

const REQUIRED_EN_NOTICE =
  "Notice: I am an AI system designed to help locate Quranic verses. This service uses only the Quran text and identified translations configured in the application. I may misunderstand a question, retrieve incomplete or irrelevant passages, or display a translation incorrectly. The passages shown are retrieved from the specified sources; they are not an independent religious ruling or authoritative interpretation. Please consult qualified scholars when religious interpretation is required.";

const REQUIRED_AR_NOTICE =
  "تنبيه: أنا نظام ذكاء اصطناعي مخصص للمساعدة في الوصول إلى الآيات القرآنية. أعتمد في هذه الخدمة على نص القرآن الكريم والترجمات المحددة داخل التطبيق فقط. قد أخطئ في فهم السؤال أو تحديد الآيات ذات الصلة أو عرض الترجمة، وقد لا أتمكن من إظهار جميع الآيات المرتبطة بالسؤال. ما أعرضه هو نصوص مسترجعة من المصادر المحددة، وليس تفسيرًا أو فتوى أو حكمًا شرعيًا. يُرجى الرجوع إلى أهل العلم المؤهلين.";

const REQUIRED_EN_FALLBACK =
  "I'm sorry, I couldn't identify sufficiently relevant verses through my available search system. This may reflect a limitation in how I understood your question or retrieved the passages; it does not establish that no relevant verses exist in the Quran. You can rephrase your question or consult qualified scholars. You can also report a possible omission through our feedback channel.";

const REQUIRED_AR_FALLBACK =
  "أعتذر، لم أتمكن من تحديد الآيات ذات الصلة بسؤالك من خلال نظام البحث المتاح لي. قد يكون ذلك بسبب محدودية قدرتي على فهم السؤال أو استرجاع الآيات المناسبة، ولا يعني ذلك عدم وجود آيات ذات صلة في القرآن الكريم. يمكنك إعادة صياغة السؤال أو مراجعة أهل العلم المؤهلين، كما يمكنك إبلاغنا بأي ملاحظات عبر قناة التواصل المخصصة.";

test("the English and Arabic notices match the required wording exactly", () => {
  assert.equal(NOTICE_EN, REQUIRED_EN_NOTICE);
  assert.equal(NOTICE_AR, REQUIRED_AR_NOTICE);
  assert.equal(NOTICES.en.text, REQUIRED_EN_NOTICE);
  assert.equal(NOTICES.ar.text, REQUIRED_AR_NOTICE);
  assert.equal(NOTICES.en.reviewStatus, "verified");
  assert.equal(NOTICES.ar.reviewStatus, "verified");
});

test("the fallback wording matches the required wording exactly", () => {
  assert.equal(FALLBACK_EN, REQUIRED_EN_FALLBACK);
  assert.equal(FALLBACK_AR, REQUIRED_AR_FALLBACK);
  assert.equal(FALLBACKS.en.text, REQUIRED_EN_FALLBACK);
  assert.equal(FALLBACKS.ar.text, REQUIRED_AR_FALLBACK);
});

test("other languages are never presented as a verified notice", () => {
  for (const language of LANGUAGES) {
    if (["en", "ar"].includes(language.code)) continue;
    assert.equal(NOTICES[language.code].reviewStatus, "pending");
    assert.equal(FALLBACKS[language.code].reviewStatus, "pending");
    // Until reviewed, the English wording is shown instead of a draft.
    assert.equal(NOTICES[language.code].text, REQUIRED_EN_NOTICE);
  }
});

test("all ten interface languages are present with the right text direction", () => {
  const codes = LANGUAGES.map((language) => language.code);
  for (const code of ["ar", "en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"]) {
    assert.ok(codes.includes(code), `missing interface language ${code}`);
  }
  assert.equal(languageMeta("ar").direction, "rtl");
  assert.equal(languageMeta("ur").direction, "rtl");
  assert.equal(languageMeta("fa").direction, "rtl");
  assert.equal(languageMeta("en").direction, "ltr");
  assert.equal(languageMeta("unknown-code").code, "en");
});

test("premium tools are disabled and cannot be bought or unlocked", async () => {
  assert.equal(premiumConfig().enabled, false);
  const features = listFeatures(db);
  assert.equal(features.length, 6);
  assert.ok(features.every((feature) => feature.available === false));
  assert.equal(hasEntitlement(db, "anonymous-session", "saved_collections"), false);
  await assert.rejects(() => createPremiumCheckout({ db, sessionId: "anonymous-session", featureKey: "saved_collections" }), /not enabled/);
});

test("premium stays separate from donations in the data model", () => {
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all().map((row) => row.name);
  assert.ok(tables.includes("donations"));
  assert.ok(tables.includes("entitlements"));
  const donationColumns = db.prepare("PRAGMA table_info(donations)").all().map((column) => column.name);
  assert.equal(donationColumns.includes("feature_key"), false);
  const entitlementColumns = db.prepare("PRAGMA table_info(entitlements)").all().map((column) => column.name);
  assert.equal(entitlementColumns.includes("amount_minor"), false);
});

test("the app never stores RQM Books data or points at its database", () => {
  assert.match(config.dbPath, /\.db$/);
  assert.doesNotMatch(config.dbPath, /rqmbook|XINVOX/i);
  const sources = [];
  const walk = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      if (["node_modules", "data", "test"].includes(entry.name)) continue;
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith(".js")) sources.push(full);
    }
  };
  walk(path.join(serverRoot, "src"));
  for (const file of sources) {
    const content = fs.readFileSync(file, "utf8");
    assert.doesNotMatch(content, /XINVOX/, `${file} references the production tree`);
    assert.doesNotMatch(content, /rqmbook-backend|rqmbook-company/i, `${file} references RQM Books repositories`);
  }
});

test("recitation candidates are recorded with unverified rights and disabled", () => {
  const reciters = db.prepare("SELECT rights_status, enabled FROM audio_reciters").all();
  assert.ok(reciters.length >= 3);
  for (const reciter of reciters) {
    assert.equal(reciter.rights_status, "unverified");
    assert.equal(reciter.enabled, 0);
  }
});

test("the rate limiter blocks a burst after the configured allowance", () => {
  resetRateLimits();
  const handler = rateLimit({ max: 2, windowMs: 1000 });
  const makeRes = () => {
    const res = {
      statusCode: null,
      headers: {},
      setHeader(name, value) {
        this.headers[name] = value;
      },
      status(code) {
        this.statusCode = code;
        return this;
      },
      json(payload) {
        this.payload = payload;
        return this;
      },
    };
    return res;
  };
  const req = { ip: "127.0.0.1", sessionId: "s", path: "/api/ask" };
  let nextCalls = 0;
  handler(req, makeRes(), () => {
    nextCalls += 1;
  });
  handler(req, makeRes(), () => {
    nextCalls += 1;
  });
  const blocked = makeRes();
  handler(req, blocked, () => {
    nextCalls += 1;
  });
  assert.equal(nextCalls, 2);
  assert.equal(blocked.statusCode, 429);
  resetRateLimits();
});

test("provider secrets are read from the environment and never bundled", () => {
  assert.equal(config.donations.stripeSecretKey, process.env.STRIPE_SECRET_KEY || "");
  assert.equal(config.admin.token, process.env.ADMIN_TOKEN || "");
  const requiredWarnings = ["DONATIONS_COMPLIANCE_APPROVED", "TRANSLATION_LICENSE_MODE"];
  for (const name of requiredWarnings) {
    assert.ok(
      fs.existsSync(path.join(serverRoot, "..", ".env.example")) ||
        name === "DONATIONS_COMPLIANCE_APPROVED" ||
        name === "TRANSLATION_LICENSE_MODE"
    );
  }
});
