// Question interpretation: language detection, reference lookup and intent.
// A configured language model may assist here, but its output is only allowed to
// influence search terms and intent — never the religious content shown.
import { config } from "../config.js";
import { LANGUAGE_CODES } from "./languages.js";

const URDU_MARKERS = ["ے", "ٹ", "ڈ", "ڑ", "ں", "ھ", "کیا", "کی", "ہے", "کا", "میں", "اور", "نہیں", "کیسے"];
const PERSIAN_MARKERS = ["چه", "چگونه", "چرا", "است", "که", "این", "آن", "پ", "چ", "ژ", "گ", "می‌"];
const LATIN_MARKERS = {
  en: ["the", "what", "does", "quran", "about", "which", "how", "why", "verse", "say"],
  id: ["apa", "bagaimana", "yang", "dan", "tentang", "ayat", "mengapa", "kapan"],
  ms: ["apa", "bagaimana", "yang", "dan", "tentang", "ayat", "mengapa", "bila"],
  tr: ["nedir", "nasıl", "için", "ve", "ayet", "hakkında", "neden", "hangi"],
  fr: ["quel", "quelle", "comment", "pourquoi", "verset", "coran", "dieu", "que", "qui", "dit", "sur", "dans", "est", "le", "la", "les"],
  sw: ["nini", "vipi", "kwa", "na", "aya", "kuhusu", "kwa nini", "gani"],
};

// Short markers must match a whole word, otherwise "le" would match "people".
const scoreMarkers = (text, markers) => {
  const value = String(text).toLowerCase();
  return markers.reduce((score, marker) => {
    if (marker.length <= 3) {
      const escaped = marker.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return new RegExp(`(^|\\s)${escaped}(\\s|$)`, "u").test(value) ? score + 1 : score;
    }
    return value.includes(marker) ? score + 1 : score;
  }, 0);
};

export const detectLanguage = (text, fallback = "en") => {
  const value = String(text || "");
  if (!value.trim()) return fallback;
  if (/[\u0980-\u09FF]/.test(value)) return "bn";
  if (/[\u0600-\u06FF]/.test(value)) {
    const urdu = scoreMarkers(value, URDU_MARKERS);
    const persian = scoreMarkers(value, PERSIAN_MARKERS);
    if (urdu > persian && urdu > 0) return "ur";
    if (persian > urdu && persian > 0) return "fa";
    return "ar";
  }
  const scores = Object.entries(LATIN_MARKERS).map(([language, markers]) => [language, scoreMarkers(value, markers)]);
  scores.sort((a, b) => b[1] - a[1] || 0);
  if (scores[0][1] === 0) return LANGUAGE_CODES.includes(fallback) ? fallback : "en";
  return scores[0][0];
};

const REFERENCE_PATTERN = /(?:^|[^\d])(\d{1,3})\s*[:.\-]\s*(\d{1,3})(?:[^\d]|$)/;

export const parseReference = (text, chapters) => {
  const value = String(text || "");
  const match = value.match(REFERENCE_PATTERN);
  if (match) {
    const chapterId = Number(match[1]);
    const verseNumber = Number(match[2]);
    const chapter = chapters.get(chapterId);
    if (chapter && verseNumber >= 1 && verseNumber <= chapter.verses_count) {
      return { chapterId, verseNumber, verseKey: `${chapterId}:${verseNumber}`, via: "number" };
    }
  }
  // Surah name + ayah number, in English transliteration or Arabic.
  const normalized = value.toLowerCase().replace(/[^a-z0-9\u0600-\u06FF\s-]/g, " ");
  for (const chapter of chapters.values()) {
    const names = [
      String(chapter.name_simple || "").toLowerCase(),
      String(chapter.name_english || "").toLowerCase(),
      String(chapter.name_arabic || ""),
    ].filter(Boolean);
    for (const name of names) {
      if (!name || name.length < 3) continue;
      if (!normalized.includes(name)) continue;
      const ayahMatch = value.match(/(?:ayah|aya|آية|آیت|verse|آیہ)\s*(\d{1,3})/i);
      if (ayahMatch) {
        const verseNumber = Number(ayahMatch[1]);
        if (verseNumber >= 1 && verseNumber <= chapter.verses_count) {
          return { chapterId: chapter.id, verseNumber, verseKey: `${chapter.id}:${verseNumber}`, via: "name" };
        }
      }
      return { chapterId: chapter.id, verseNumber: null, verseKey: null, via: "chapter-name" };
    }
  }
  return null;
};

export const detectIntent = (text) => {
  const value = String(text || "").toLowerCase();
  if (/(more|another|other|else|additional|مزيد|المزيد|أخرى|مزید|اور|lebih|lagi|daha|autres|zaidi|zaidi)/.test(value) && value.trim().length < 60) {
    return "more";
  }
  return "topic";
};

/**
 * Optional model-assisted query understanding. The model may only return search
 * terms, an intent and a language. Anything else is discarded, and if the call
 * fails the deterministic path is used instead.
 */
export const interpretWithModel = async ({ question, language }) => {
  if (config.ai.provider === "none" || !config.ai.apiKey || !config.ai.baseUrl || !config.ai.model) return null;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.ai.timeoutMs);
  try {
    const response = await fetch(`${config.ai.baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${config.ai.apiKey}`,
      },
      body: JSON.stringify({
        model: config.ai.model,
        temperature: 0,
        max_tokens: 220,
        messages: [
          {
            role: "system",
            content:
              "You convert a user's question into Quran search metadata. Reply with JSON only: " +
              '{"language":"<one of ar,en,ur,id,bn,tr,fa,ms,sw,fr>","intent":"topic|reference|more","searchTerms":["2-6 short terms, English or Arabic"],"reference":"chapter:verse or empty"}. ' +
              "Never quote Quranic verses, never answer the question, never add commentary.",
          },
          { role: "user", content: question },
        ],
        response_format: { type: "json_object" },
      }),
    });
    if (!response.ok) return null;
    const payload = await response.json();
    const content = payload.choices?.[0]?.message?.content;
    if (!content) return null;
    const parsed = JSON.parse(content);
    const allowedLanguages = new Set(LANGUAGE_CODES);
    const searchTerms = Array.isArray(parsed.searchTerms)
      ? parsed.searchTerms.filter((term) => typeof term === "string" && term.trim().length > 0).slice(0, 8)
      : [];
    return {
      language: allowedLanguages.has(String(parsed.language || "").toLowerCase()) ? String(parsed.language).toLowerCase() : language,
      intent: ["topic", "reference", "more"].includes(parsed.intent) ? parsed.intent : "topic",
      searchTerms,
      reference: typeof parsed.reference === "string" ? parsed.reference : "",
    };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
};
