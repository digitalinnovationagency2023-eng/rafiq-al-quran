// Retrieval quality against a curated set, plus the honesty rules that stop the
// system inventing verses or references.
import assert from "node:assert/strict";
import test from "node:test";
import { prepareTestDb } from "./helpers.js";

prepareTestDb();

const { getDb } = await import("../src/db.js");
const { buildIndex, search, getVerseByKey } = await import("../src/services/retrieval.js");
const { parseReference, detectLanguage, detectIntent } = await import("../src/services/query.js");
const { answerQuestion, fallbackResponse } = await import("../src/services/answer.js");
const { NOTICE_EN, NOTICE_AR, FALLBACK_EN, FALLBACK_AR } = await import("../src/services/languages.js");
const { config } = await import("../src/config.js");

const db = getDb();
const index = buildIndex(db);

test("English topical search returns relevant verses for a curated set", () => {
  const curated = [
    { question: "patience in hardship", pattern: /patien|patient|persever|endur/i },
    { question: "mercy and forgiveness", pattern: /mercy|merciful|forgiv|compassion/i },
    { question: "prayer", pattern: /prayer|pray|salah/i },
    { question: "charity and giving wealth", pattern: /charity|spend|almsgiv|zakat/i },
  ];
  for (const entry of curated) {
    const results = search(index, { question: entry.question, language: "en", limit: 8 });
    assert.ok(results.length > 0, `no results for ${entry.question}`);
    // At least one of the strongest passages must actually use the topic language.
    const matched = results.slice(0, 5).some((result) => {
      const verse = getVerseByKey(index, result.verseKey);
      const translation = db
        .prepare(
          "SELECT text FROM translation_verses tv JOIN translations t ON t.id = tv.translation_id WHERE t.language_code = 'en' AND tv.verse_id = ?"
        )
        .get(verse.id);
      return translation && entry.pattern.test(translation.text);
    });
    assert.ok(matched, `no relevant translation found for "${entry.question}"`);
  }
});

test("Arabic search works with the definite article and diacritics", () => {
  const results = search(index, { question: "ماذا يقول القرآن عن الصبر", language: "ar", limit: 8 });
  assert.ok(results.length > 0);
  assert.ok(results[0].confidence >= config.retrieval.confidenceFloor);
  assert.ok(results.every((result) => getVerseByKey(index, result.verseKey)));
});

test("an unrelated technical question returns no matches (safe fallback path)", () => {
  const results = search(index, {
    question: "What does the Quran say about quantum tunnelling in superconductors?",
    language: "en",
    limit: 8,
  });
  assert.deepEqual(results, []);
});

test("verse references are parsed and invalid references are rejected", () => {
  assert.deepEqual(parseReference("show me 2:255", index.chapters), {
    chapterId: 2,
    verseNumber: 255,
    verseKey: "2:255",
    via: "number",
  });
  assert.equal(parseReference("chapter 2 verse 9999", index.chapters), null);
  assert.equal(parseReference("no reference here", index.chapters), null);
});

test("language detection distinguishes Arabic, Urdu, Persian, Bengali and Latin languages", () => {
  assert.equal(detectLanguage("ماذا يقول القرآن عن الصبر؟"), "ar");
  assert.equal(detectLanguage("قرآن صبر کے بارے میں کیا کہتا ہے؟"), "ur");
  assert.equal(detectLanguage("قرآن در مورد صبر چه می‌گوید؟"), "fa");
  assert.equal(detectLanguage("কুরআন ধৈর্য সম্পর্কে কী বলে?"), "bn");
  assert.equal(detectLanguage("What does the Quran say about patience?"), "en");
  assert.equal(detectLanguage("Apa yang Al-Quran katakan tentang kesabaran?"), "id");
  assert.equal(detectLanguage("Que dit le Coran sur la patience ?"), "fr");
});

test("the more-intent is recognised for follow-up requests", () => {
  assert.equal(detectIntent("more"), "more");
  assert.equal(detectIntent("show me another verse"), "more");
  assert.equal(detectIntent("what does the Quran say about patience"), "topic");
});

test("every returned passage matches the dataset exactly", () => {
  const answer = answerQuestion(index, { question: "patience", language: "en" });
  assert.equal(answer.kind, "answer");
  for (const passage of answer.passages) {
    const verse = getVerseByKey(index, passage.verseKey);
    assert.ok(verse, `unknown reference ${passage.verseKey}`);
    assert.equal(passage.arabic, verse.text_uthmani);
    assert.equal(passage.translation.text.length > 0, true);
  }
});

test("the fallback is used when nothing clears the confidence floor", () => {
  const answer = answerQuestion(index, { question: "quantum tunnelling superconductors", language: "en" });
  assert.equal(answer.kind, "fallback");
  assert.equal(answer.passages.length, 0);
  assert.equal(answer.message, FALLBACK_EN);
  // The fallback must never claim the Quran does not mention something.
  assert.doesNotMatch(answer.message, /does not mention|not mentioned|no verse/i);
});

test("notices are attached verbatim and the unreviewed languages are marked pending", () => {
  const english = answerQuestion(index, { question: "patience", language: "en" });
  assert.equal(english.notice.text, NOTICE_EN);
  assert.equal(english.notice.reviewStatus, "verified");
  const arabic = answerQuestion(index, { question: "الصبر", language: "ar" });
  assert.equal(arabic.notice.text, NOTICE_AR);
  assert.equal(arabic.notice.reviewStatus, "verified");
  const bengali = answerQuestion(index, { question: "ধৈর্য", language: "bn" });
  assert.equal(bengali.notice.reviewStatus, "pending");
});

test("the Arabic interface falls back to the English translation, clearly labelled", () => {
  const answer = answerQuestion(index, { question: "patience", language: "ar" });
  if (answer.kind === "answer") {
    for (const passage of answer.passages) {
      assert.equal(passage.translation.fallbackToEnglish, true);
      assert.equal(passage.translation.language, "en");
    }
    assert.ok(answer.translationNotice);
  }
});

test("fallback responses never carry passages or confidence", () => {
  const fallback = fallbackResponse("ar");
  assert.equal(fallback.kind, "fallback");
  assert.equal(fallback.passages.length, 0);
  assert.equal(fallback.confidence, 0);
  assert.equal(fallback.message, FALLBACK_AR);
  assert.ok(fallback.limits.length > 0);
});
