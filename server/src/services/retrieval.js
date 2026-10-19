// Deterministic retrieval over the validated local dataset. A language model is
// never asked to write religious text: it may only help turn a question into
// search terms, and every passage returned here is read straight from the
// imported Quran and translation rows.
import { normalizeArabic, tokenizeArabic, arabicSearchForms } from "./arabic.js";
import { expandTerms } from "./lexicon.js";

const ARABIC_STOPWORDS = new Set(
  "في من على إلى عن أن إن ما لا هل هو هي هم كان كانوا هذا هذه ذلك التي الذي الذين يا أيها ثم قد لقد ولا وما ومن وإلى وعلى أفلا أولم أفلم كذلك بين كل بعض غير سوى حيث كيف متى أين لماذا".split(" ")
);

const LATIN_STOPWORDS = new Set(
  "the a an and or of to in on for with about is are was were be been being what which who whom whose how why when where do does did can could should would may might will shall i you he she it we they me my your our their this that these those there here please tell show give find say says said mention mentions mentioned verse verses quran qur'an surah sura chapter".split(" ")
);

/** Small plural/normalisation forms for Latin tokens used only in matching. */
const latinForms = (token) => {
  const forms = new Set([token]);
  if (token.length > 4 && token.endsWith("ies")) forms.add(`${token.slice(0, -3)}y`);
  if (token.length > 3 && token.endsWith("es")) forms.add(token.slice(0, -2));
  if (token.length > 3 && token.endsWith("s")) forms.add(token.slice(0, -1));
  if (token.length > 5 && token.endsWith("ing")) forms.add(token.slice(0, -3));
  if (token.length > 4 && token.endsWith("ed")) forms.add(token.slice(0, -2));
  return [...forms];
};

const STOPWORDS_BY_LANGUAGE = {
  en: LATIN_STOPWORDS,
  fr: new Set([...LATIN_STOPWORDS, "le la les un une des du de et ou est sont que qui quoi comment pourquoi quand où ce cette ces pour dans sur avec sans".split(" ")]),
  id: new Set([...LATIN_STOPWORDS, "yang dan di ke dari untuk dengan apa bagaimana mengapa kapan dimana ini itu adalah tentang".split(" ")]),
  ms: new Set([...LATIN_STOPWORDS, "yang dan di ke dari untuk dengan apa bagaimana mengapa bila mana ini itu ialah tentang".split(" ")]),
  sw: new Set([...LATIN_STOPWORDS, "na ya ni za kwa katika juu kutoka wa ni nini vipi kwa nini lini wapi hii hiyo".split(" ")]),
  tr: new Set([...LATIN_STOPWORDS, "ve bir bu şu da de için ile ne nasıl neden nerede hangi olan".split(" ")]),
  id_extra: new Set(),
};

const tokenizeLatin = (text) =>
  String(text || "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s'-]+/gu, " ")
    .split(/\s+/)
    .filter(Boolean);

export const meaningfulTokens = (text, language = "en") => {
  const rtl = /[\u0600-\u06FF]/.test(text || "");
  if (rtl) {
    return tokenizeArabic(text).filter((token) => token.length > 1 && !ARABIC_STOPWORDS.has(token));
  }
  const stopwords = STOPWORDS_BY_LANGUAGE[language] || LATIN_STOPWORDS;
  return tokenizeLatin(text).filter((token) => token.length > 1 && !stopwords.has(token));
};

/**
 * Builds the in-memory index once per process. Reads only local rows.
 */
export const buildIndex = (db) => {
  const chapters = new Map(
    db
      .prepare("SELECT id, name_arabic, name_simple, name_english, verses_count, revelation_place FROM chapters")
      .all()
      .map((row) => [row.id, row])
  );

  const verses = db
    .prepare(
      "SELECT id, chapter_id, verse_number, verse_key, text_uthmani, text_plain, text_normalized FROM verses ORDER BY id"
    )
    .all()
    .map((row) => ({
      ...row,
      tokens: row.text_normalized.split(" ").filter(Boolean),
      chapter: chapters.get(row.chapter_id),
    }));

  // Arabic inverted index: token (and its light stem) -> Set(verse index).
  const arabicIndex = new Map();
  verses.forEach((verse, index) => {
    for (const token of new Set(verse.tokens)) {
      for (const form of arabicSearchForms(token)) {
        let bucket = arabicIndex.get(form);
        if (!bucket) {
          bucket = new Set();
          arabicIndex.set(form, bucket);
        }
        bucket.add(index);
      }
    }
  });

  // Translation index per language: language -> token -> Set(verse id).
  const translationRows = db
    .prepare(
      `SELECT t.id AS translation_id, t.language_code, t.title, t.author, t.direction, t.license_status, t.enabled,
              tv.verse_id, tv.text
       FROM translations t JOIN translation_verses tv ON tv.translation_id = t.id
       WHERE t.enabled = 1`
    )
    .all();

  const translationsByLanguage = new Map();
  const translationIndex = new Map(); // `${language}:${token}` -> Set(verseId)
  const translationTextByLanguage = new Map(); // `${language}:${verseId}` -> text
  for (const row of translationRows) {
    const language = row.language_code;
    if (!translationsByLanguage.has(language)) {
      translationsByLanguage.set(language, {
        id: row.translation_id,
        title: row.title,
        author: row.author,
        direction: row.direction,
        language,
        licenseStatus: row.license_status,
      });
    }
    translationTextByLanguage.set(`${language}:${row.verse_id}`, row.text);
    for (const token of new Set(tokenizeLatin(row.text))) {
      if (token.length < 2) continue;
      for (const form of latinForms(token)) {
        const key = `${language}:${form}`;
        let bucket = translationIndex.get(key);
        if (!bucket) {
          bucket = new Set();
          translationIndex.set(key, bucket);
        }
        bucket.add(row.verse_id);
      }
    }
  }

  const verseById = new Map(verses.map((verse) => [verse.id, verse]));
  const verseByKey = new Map(verses.map((verse) => [verse.verse_key, verse]));
  const dataset = db
    .prepare("SELECT dataset, version, source_url, checksum, imported_at FROM dataset_versions WHERE dataset = 'quran-uthmani' ORDER BY id DESC LIMIT 1")
    .get();

  return {
    chapters,
    verses,
    verseById,
    verseByKey,
    arabicIndex,
    translationIndex,
    translationsByLanguage,
    translationTextByLanguage,
    dataset,
    size: verses.length,
  };
};

export const translationFor = (index, language, verseId) => {
  const exact = index.translationTextByLanguage.get(`${language}:${verseId}`);
  if (exact !== undefined) return { text: exact, language, pendingEnglishFallback: false };
  const english = index.translationTextByLanguage.get(`en:${verseId}`);
  return english !== undefined
    ? { text: english, language: "en", pendingEnglishFallback: true }
    : { text: "", language, pendingEnglishFallback: false };
};

/**
 * Scores candidate verses. Returns ranked matches with the evidence used, so the
 * answer layer can quote only what was actually found.
 */
export const search = (index, { question, language = "en", limit = 8, extraTerms = [], excludeVerseKeys = [] } = {}) => {
  const queryTokens = [
    ...new Set([...meaningfulTokens(question, language), ...extraTerms.flatMap((term) => meaningfulTokens(term, language))]),
  ];
  if (queryTokens.length === 0) return [];
  const excluded = new Set(excludeVerseKeys);

  const arabicQuery = /[\u0600-\u06FF]/.test(question || "");
  const conceptTerms = expandTerms(queryTokens).map(normalizeArabic).filter(Boolean);
  const querySet = new Set(queryTokens);

  const scores = new Map(); // verseIndex -> { score, matchedQuery:Set, matchedConcept:Set }
  const add = (verseIndex, weight, term, kind) => {
    const entry = scores.get(verseIndex) || { score: 0, matchedQuery: new Set(), matchedConcept: new Set() };
    entry.score += weight;
    if (kind === "concept") entry.matchedConcept.add(term);
    else if (kind === "query") entry.matchedQuery.add(term);
    scores.set(verseIndex, entry);
  };

  const totalVerses = index.verses.length;
  const idf = (frequency) => Math.log(1 + totalVerses / (1 + frequency));

  // Arabic index: direct query words, then curated concept terms.
  const lookupArabic = (term, kind, weightMultiplier = 1) => {
    for (const form of arabicSearchForms(normalizeArabic(term))) {
      const bucket = index.arabicIndex.get(form);
      if (!bucket) continue;
      const weight = idf(bucket.size) * (arabicQuery ? 1.3 : 1) * weightMultiplier;
      for (const verseIndex of bucket) add(verseIndex, weight, term, kind);
    }
  };
  for (const term of queryTokens) lookupArabic(term, "query");
  for (const term of conceptTerms) lookupArabic(term, "concept", 0.9);

  // Translation index: the question's language first, English as a bridge.
  for (const term of queryTokens) {
    for (const targetLanguage of [...new Set([language, "en"])]) {
      for (const form of latinForms(term)) {
        const bucket = index.translationIndex.get(`${targetLanguage}:${form}`);
        if (!bucket) continue;
        const weight = idf(bucket.size) * (targetLanguage === language ? 1 : 0.6);
        for (const verseId of bucket) {
          const verse = index.verseById.get(verseId);
          if (verse) add(index.verses.indexOf(verse), weight, term, "query");
        }
      }
    }
  }

  // Phrase bonus: the normalised question appears verbatim in the verse.
  const normalizedQuestion = normalizeArabic(question || "");
  if (normalizedQuestion.length > 6) {
    for (const [verseIndex, verse] of index.verses.entries()) {
      if (verse.text_normalized.includes(normalizedQuestion)) add(verseIndex, 4, "phrase", "query");
    }
  }

  const ranked = [...scores.entries()]
    .map(([verseIndex, entry]) => {
      const verse = index.verses[verseIndex];
      const directCoverage = [...entry.matchedQuery].filter((term) => querySet.has(term)).length / Math.max(1, queryTokens.length);
      return {
        verse,
        score: entry.score,
        directCoverage,
        conceptHits: entry.matchedConcept.size,
        matched: [...entry.matchedQuery, ...entry.matchedConcept],
      };
    })
    // A verse must match either a word from the question or a curated concept.
    .filter((entry) => entry.directCoverage > 0 || entry.conceptHits > 0)
    .filter((entry) => !excluded.has(entry.verse.verse_key))
    .sort((a, b) => b.score - a.score || a.verse.id - b.verse.id)
    .slice(0, Math.max(1, limit));

  const bestScore = ranked[0]?.score || 0;
  return ranked.map((entry) => {
    const relevance = Math.min(1, entry.score / Math.max(bestScore, 1));
    const conceptCredit = entry.conceptHits > 0 && entry.directCoverage === 0 ? 0.5 : 0;
    const confidence = 0.5 * Math.min(1, entry.directCoverage + conceptCredit) + 0.3 * relevance + 0.2 * Math.min(1, (entry.directCoverage + entry.conceptHits) / 2);
    return {
      verseKey: entry.verse.verse_key,
      chapterId: entry.verse.chapter_id,
      verseNumber: entry.verse.verse_number,
      chapter: entry.verse.chapter,
      arabic: entry.verse.text_uthmani,
      score: Number(entry.score.toFixed(4)),
      coverage: Number(entry.directCoverage.toFixed(4)),
      directMatches: Number(entry.directCoverage.toFixed(4)),
      conceptMatches: entry.conceptHits,
      confidence: Number(confidence.toFixed(4)),
      matched: entry.matched.slice(0, 12),
    };
  });
};

/** Exact verse lookup by reference, e.g. "2:255". */
export const getVerseByKey = (index, verseKey) => index.verseByKey.get(String(verseKey || "").trim()) || null;

export const chapterVerses = (index, chapterId) =>
  index.verses.filter((verse) => verse.chapter_id === Number(chapterId));
