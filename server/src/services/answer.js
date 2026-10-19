// Composes the response shown to the user.
//
// Rules enforced here:
//  - every passage is read from the validated dataset and re-verified against it;
//  - the Arabic text is quoted verbatim, the translation is labelled separately;
//  - the required notice is attached to every substantive answer;
//  - when retrieval is not confident, the exact fallback wording is returned and
//    no claim is made about what the Quran does or does not contain.
import { config } from "../config.js";
import {
  connectivesFor,
  fallbackFor,
  languageMeta,
  noticeFor,
} from "./languages.js";
import { search, translationFor, getVerseByKey, chapterVerses } from "./retrieval.js";

export class CitationVerificationError extends Error {
  constructor(message) {
    super(message);
    this.name = "CitationVerificationError";
  }
}

/** Re-checks every passage against the dataset. Throws on any mismatch. */
export const verifyPassages = (index, passages = []) => {
  for (const passage of passages) {
    const verse = index.verseByKey.get(passage.verseKey);
    if (!verse) throw new CitationVerificationError(`Unknown verse reference: ${passage.verseKey}`);
    if (verse.text_uthmani !== passage.arabic) {
      throw new CitationVerificationError(`Arabic text does not match the dataset for ${passage.verseKey}`);
    }
    if (passage.chapter?.id !== verse.chapter_id) {
      throw new CitationVerificationError(`Chapter id mismatch for ${passage.verseKey}`);
    }
  }
  return true;
};

const sourceFor = (index) => ({
  dataset: index.dataset?.dataset || "quran-uthmani",
  version: index.dataset?.version || "unknown",
  sourceUrl: index.dataset?.source_url || null,
  checksum: index.dataset?.checksum || null,
  importedAt: index.dataset?.imported_at || null,
});

const buildPassage = (index, match, language) => {
  const verse = index.verseByKey.get(match.verseKey);
  if (!verse) throw new CitationVerificationError(`Unknown verse reference: ${match.verseKey}`);
  const translation = translationFor(index, language, verse.id);
  const meta = index.translationsByLanguage.get(translation.language) || null;
  const chapter = index.chapters.get(verse.chapter_id);
  return {
    verseKey: verse.verse_key,
    chapterId: verse.chapter_id,
    verseNumber: verse.verse_number,
    chapter: {
      id: chapter.id,
      nameArabic: chapter.name_arabic,
      nameEnglish: chapter.name_simple,
      revelationPlace: chapter.revelation_place,
    },
    referenceLabel: `${chapter.name_simple} ${verse.verse_key}`,
    arabic: verse.text_uthmani,
    arabicPlain: verse.text_plain,
    translation: {
      text: translation.text,
      language: translation.language,
      direction: languageMeta(translation.language).direction,
      translationId: meta?.id ?? null,
      title: meta?.title ?? null,
      author: meta?.author ?? null,
      licenseStatus: meta?.licenseStatus ?? "unverified",
      fallbackToEnglish: translation.pendingEnglishFallback,
    },
    relevance: match.confidence,
    source: sourceFor(index),
  };
};

const baseResponse = (language) => ({
  language,
  direction: languageMeta(language).direction,
  notice: noticeFor(language),
  createdAt: new Date().toISOString(),
});

export const fallbackResponse = (language) => {
  const fallback = fallbackFor(language);
  return {
    ...baseResponse(language),
    kind: "fallback",
    message: fallback.text,
    messageReviewStatus: fallback.reviewStatus,
    passages: [],
    confidence: 0,
    // Explicitly separates "we could not find" from "the Quran does not contain".
    limits: [
      "This fallback reflects the limits of the retrieval system and the question wording, not a claim about the Quran.",
    ],
    suggestRefine: true,
    usedSearchTerms: [],
  };
};

/**
 * Answers a question from the dataset.
 * @param {object} index built by buildIndex()
 * @param {object} input { question, language, intent, reference, limit, conversationContext }
 */
export const answerQuestion = (index, { question, language = "en", intent = "topic", reference = null, limit = config.retrieval.maxVerses, extraTerms = [], excludeVerseKeys = [] } = {}) => {
  void intent;
  const connectives = connectivesFor(language);

  // 1. Explicit reference: show exactly that verse.
  if (reference?.verseKey) {
    const verse = getVerseByKey(index, reference.verseKey);
    if (verse) {
      const match = {
        verseKey: verse.verse_key,
        chapterId: verse.chapter_id,
        verseNumber: verse.verse_number,
        chapter: verse.chapter,
        verse,
        confidence: 1,
      };
      const passage = buildPassage(index, match, language);
      verifyPassages(index, [passage]);
      return {
        ...baseResponse(language),
        kind: "answer",
        message: connectives.found(1),
        passages: [passage],
        confidence: 1,
        limits: ["This passage is shown because its reference was requested; it is not an interpretation."],
        suggestRefine: true,
        usedSearchTerms: [],
      };
    }
  }

  // 2. Chapter requested by name without an ayah: show the opening verses.
  if (reference?.chapterId && !reference?.verseNumber) {
    const chapter = index.chapters.get(reference.chapterId);
    const verses = chapterVerses(index, reference.chapterId).slice(0, Math.min(3, limit));
    if (chapter && verses.length) {
      const passages = verses.map((verse) =>
        buildPassage(
          index,
          { verseKey: verse.verse_key, chapterId: verse.chapter_id, verseNumber: verse.verse_number, chapter: verse.chapter, verse, confidence: 1 },
          language
        )
      );
      verifyPassages(index, passages);
      return {
        ...baseResponse(language),
        kind: "answer",
        message: connectives.found(passages.length),
        passages,
        confidence: 1,
        limits: ["Only the opening passages of the requested surah are shown here."],
        suggestRefine: true,
        usedSearchTerms: [],
      };
    }
  }

  // 3. Retrieval.
  const matches = search(index, { question, language, limit, extraTerms, excludeVerseKeys });
  const best = matches[0];
  if (!best || best.confidence < config.retrieval.confidenceFloor) {
    return fallbackResponse(language);
  }

  // Keep the strongest matches that are close to the best one, so the answer
  // does not dilute a good match with weak ones.
  const selected = matches
    .filter((match) => match.confidence >= Math.max(config.retrieval.confidenceFloor, best.confidence * 0.8))
    .slice(0, Math.max(1, limit));

  const passages = selected.map((match) => buildPassage(index, match, language));
  verifyPassages(index, passages);

  const pendingNotice = passages.some((passage) => passage.translation.fallbackToEnglish)
    ? connectives.translationPending
    : null;

  return {
    ...baseResponse(language),
    kind: "answer",
    message: connectives.found(passages.length),
    passages,
    confidence: best.confidence,
    usedSearchTerms: best.matched,
    translationNotice: pendingNotice,
    limits: [
      "Passages are retrieved from the configured dataset with a keyword and concept search; results are not exhaustive.",
      "The translation shown is a human translation identified in each passage; it is not the Arabic text itself.",
    ],
    suggestRefine: true,
  };
};
