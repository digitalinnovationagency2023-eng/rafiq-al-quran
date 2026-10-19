// Language catalogue and the mandatory notices.
//
// The English and Arabic notices are the exact wording supplied by the product
// owner. Every other language is marked "pending" until a professional review
// has signed it off: the UI shows the English wording plus a visible note that
// the translation is not yet reviewed, so an unreviewed translation is never
// presented as verified.

export const LANGUAGES = [
  { code: "ar", name: "Arabic", nativeName: "العربية", direction: "rtl" },
  { code: "en", name: "English", nativeName: "English", direction: "ltr" },
  { code: "ur", name: "Urdu", nativeName: "اردو", direction: "rtl" },
  { code: "id", name: "Indonesian", nativeName: "Bahasa Indonesia", direction: "ltr" },
  { code: "bn", name: "Bengali", nativeName: "বাংলা", direction: "ltr" },
  { code: "tr", name: "Turkish", nativeName: "Türkçe", direction: "ltr" },
  { code: "fa", name: "Persian", nativeName: "فارسی", direction: "rtl" },
  { code: "ms", name: "Malay", nativeName: "Bahasa Melayu", direction: "ltr" },
  { code: "sw", name: "Swahili", nativeName: "Kiswahili", direction: "ltr" },
  { code: "fr", name: "French", nativeName: "Français", direction: "ltr" },
];

export const LANGUAGE_CODES = LANGUAGES.map((language) => language.code);
export const isSupportedLanguage = (code) => LANGUAGE_CODES.includes(String(code || "").toLowerCase());

export const NOTICE_EN =
  "Notice: I am an AI system designed to help locate Quranic verses. This service uses only the Quran text and identified translations configured in the application. I may misunderstand a question, retrieve incomplete or irrelevant passages, or display a translation incorrectly. The passages shown are retrieved from the specified sources; they are not an independent religious ruling or authoritative interpretation. Please consult qualified scholars when religious interpretation is required.";

export const NOTICE_AR =
  "تنبيه: أنا نظام ذكاء اصطناعي مخصص للمساعدة في الوصول إلى الآيات القرآنية. أعتمد في هذه الخدمة على نص القرآن الكريم والترجمات المحددة داخل التطبيق فقط. قد أخطئ في فهم السؤال أو تحديد الآيات ذات الصلة أو عرض الترجمة، وقد لا أتمكن من إظهار جميع الآيات المرتبطة بالسؤال. ما أعرضه هو نصوص مسترجعة من المصادر المحددة، وليس تفسيرًا أو فتوى أو حكمًا شرعيًا. يُرجى الرجوع إلى أهل العلم المؤهلين.";

export const FALLBACK_EN =
  "I'm sorry, I couldn't identify sufficiently relevant verses through my available search system. This may reflect a limitation in how I understood your question or retrieved the passages; it does not establish that no relevant verses exist in the Quran. You can rephrase your question or consult qualified scholars. You can also report a possible omission through our feedback channel.";

export const FALLBACK_AR =
  "أعتذر، لم أتمكن من تحديد الآيات ذات الصلة بسؤالك من خلال نظام البحث المتاح لي. قد يكون ذلك بسبب محدودية قدرتي على فهم السؤال أو استرجاع الآيات المناسبة، ولا يعني ذلك عدم وجود آيات ذات صلة في القرآن الكريم. يمكنك إعادة صياغة السؤال أو مراجعة أهل العلم المؤهلين، كما يمكنك إبلاغنا بأي ملاحظات عبر قناة التواصل المخصصة.";

/**
 * Notices per language. `reviewStatus` is "verified" only for the wording
 * supplied by the owner (en/ar). Drafts for other languages stay "pending" and
 * are never presented as verified.
 */
export const NOTICES = LANGUAGES.reduce((accumulator, language) => {
  if (language.code === "en") {
    accumulator.en = { text: NOTICE_EN, reviewStatus: "verified" };
  } else if (language.code === "ar") {
    accumulator.ar = { text: NOTICE_AR, reviewStatus: "verified" };
  } else {
    accumulator[language.code] = { text: NOTICE_EN, reviewStatus: "pending", sourceLanguage: "en" };
  }
  return accumulator;
}, {});

export const FALLBACKS = LANGUAGES.reduce((accumulator, language) => {
  if (language.code === "en") {
    accumulator.en = { text: FALLBACK_EN, reviewStatus: "verified" };
  } else if (language.code === "ar") {
    accumulator.ar = { text: FALLBACK_AR, reviewStatus: "verified" };
  } else {
    accumulator[language.code] = { text: FALLBACK_EN, reviewStatus: "pending", sourceLanguage: "en" };
  }
  return accumulator;
}, {});

export const noticeFor = (language) => NOTICES[language] || NOTICES.en;
export const fallbackFor = (language) => FALLBACKS[language] || FALLBACKS.en;

/** Short connective sentences. They never add religious content. */
export const CONNECTIVES = {
  en: {
    found: (count) => (count === 1 ? "I found one passage that refers to this:" : `I found ${count} passages that refer to this:`),
    more: "You can ask for more passages, another translation, or a different wording.",
    translationPending: "A reviewed translation is not yet available for this language, so the English translation is shown.",
  },
  ar: {
    found: (count) => (count === 1 ? "وجدت آية واحدة تتعلق بهذا:" : `وجدت ${count} آيات تتعلق بهذا:`),
    more: "يمكنك طلب المزيد من الآيات أو ترجمة أخرى أو صياغة مختلفة للسؤال.",
    translationPending: "لا تتوفر ترجمة مُراجعة لهذه اللغة بعد، لذلك تُعرض الترجمة الإنجليزية.",
  },
};

export const connectivesFor = (language) => CONNECTIVES[language] || CONNECTIVES.en;

export const languageMeta = (code) =>
  LANGUAGES.find((language) => language.code === code) || LANGUAGES.find((language) => language.code === "en");
