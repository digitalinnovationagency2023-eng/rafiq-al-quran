// Arabic text helpers shared by the importer, the search index and the tests.
// Normalisation is only used for matching; the canonical Uthmani text is always
// stored and displayed exactly as received from the source.

const DIACRITICS = /[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640]/g;
const QURANIC_ANNOTATIONS = /[\u06D6-\u06ED\u08F0-\u08FF\uFEFF]/g;

/** Exact source text with only invisible control characters removed. */
export const canonicalText = (text) => String(text || "").replace(/[\uFEFF\u200B-\u200D]/g, "").trim();

/** Readable text with diacritics removed (for search display and word counts). */
export const plainArabic = (text) =>
  canonicalText(text)
    .replace(DIACRITICS, "")
    .replace(QURANIC_ANNOTATIONS, "")
    .replace(/\s+/g, " ")
    .trim();

/**
 * Search normalisation: unify alef/hamza forms, ya/alef-maqsura, ta-marbuta,
 * remove diacritics and tatweel. The result is never shown to users.
 */
export const normalizeArabic = (text) => {
  let value = plainArabic(text).toLowerCase();
  value = value
    .replace(/[\u0622\u0623\u0625\u0671]/g, "\u0627") // آ أ إ ٱ -> ا
    .replace(/\u0649/g, "\u064A") // ى -> ي
    .replace(/\u0629/g, "\u0647") // ة -> ه
    .replace(/\u0624/g, "\u0648") // ؤ -> و
    .replace(/\u0626/g, "\u064A") // ئ -> ي
    .replace(/[\u064B-\u065F\u0670]/g, "");
  value = value.replace(/[^\p{L}\p{N}\s]/gu, " ");
  return value.replace(/\s+/g, " ").trim();
};

export const tokenizeArabic = (text) => normalizeArabic(text).split(" ").filter(Boolean);

/**
 * Very light Arabic stemming used only for search matching: strips the definite
 * article, common conjunction/preposition prefixes and a few possessive/plural
 * suffixes. The stored and displayed Quranic text is never altered.
 */
export const stemArabic = (token) => {
  let value = String(token || "");
  if (value.length < 3) return value;
  for (let pass = 0; pass < 2; pass += 1) {
    if (/^(وال|بال|كال|فال)/.test(value) && value.length > 5) value = value.slice(3);
    else if (/^ال/.test(value) && value.length > 3) value = value.slice(2);
    else if (/^(و|ف|ب|ك|ل)/.test(value) && value.length > 4) value = value.slice(1);
    else break;
  }
  value = value.replace(/(ها|هم|هن|كم|نا|ات|ون|ين|ان)$/u, (match) => (value.length - match.length >= 3 ? "" : match));
  value = value.replace(/[يه]$/u, (match) => (value.length > 4 ? "" : match));
  return value;
};

/** Adds both the raw and the stemmed form so queries match inflected words. */
export const arabicSearchForms = (token) => {
  const stem = stemArabic(token);
  return stem && stem !== token ? [token, stem] : [token];
};

/** Strip Arabic diacritics from an arbitrary string, used by the UI helper. */
export const stripDiacritics = (text) => plainArabic(text);

export const isArabicText = (text) => /[\u0600-\u06FF]/.test(String(text || ""));
