// Browser voice support. Two clearly separated capabilities:
//  - speech recognition for dictation (the transcript is editable before send);
//  - speech synthesis for reading the *translation and summary* aloud.
// Speech synthesis is never used to imitate a Quranic recitation: authentic
// recitation is a separate, rights-gated audio feature served by the API.

export const VOICE_LOCALES = {
  ar: "ar-SA",
  en: "en-US",
  ur: "ur-PK",
  id: "id-ID",
  bn: "bn-BD",
  tr: "tr-TR",
  fa: "fa-IR",
  ms: "ms-MY",
  sw: "sw-KE",
  fr: "fr-FR",
};

export const localeFor = (language) => VOICE_LOCALES[language] || VOICE_LOCALES.en;

export const recognitionAvailable = () =>
  typeof window !== "undefined" && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);

export const synthesisAvailable = () => typeof window !== "undefined" && Boolean(window.speechSynthesis);

/**
 * Creates a recognition session. Callers must show the recording state and offer
 * an editable transcript before sending.
 */
export const createRecognition = ({ language = "en", onResult, onError, onEnd, onStart } = {}) => {
  if (!recognitionAvailable()) return null;
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  const recognition = new Recognition();
  recognition.lang = localeFor(language);
  recognition.interimResults = true;
  recognition.continuous = false;
  recognition.maxAlternatives = 1;
  recognition.onstart = () => onStart?.();
  recognition.onend = () => onEnd?.();
  recognition.onerror = (event) => onError?.(event?.error || "speech-error");
  recognition.onresult = (event) => {
    let transcript = "";
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index][0].transcript;
    }
    onResult?.(transcript, event.results[event.results.length - 1]?.isFinal === true);
  };
  return recognition;
};

/**
 * Speaks the provided text (translation + connective summary). The caller must
 * never pass Quranic Arabic here — that is what the recitation feature is for.
 */
export const speak = ({ text, language = "en", rate = 1, onEnd } = {}) => {
  if (!synthesisAvailable() || !text) return false;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = localeFor(language);
  utterance.rate = Math.min(2, Math.max(0.5, rate));
  utterance.onend = () => onEnd?.();
  window.speechSynthesis.speak(utterance);
  return true;
};

export const stopSpeaking = () => {
  if (synthesisAvailable()) window.speechSynthesis.cancel();
};

/** Copy used for a spoken answer: translation + reference, never the Arabic text. */
export const spokenTextFor = (answer) => {
  if (!answer) return "";
  const parts = [];
  if (answer.kind === "fallback") {
    parts.push(answer.message);
    return parts.join(" ");
  }
  for (const passage of answer.passages || []) {
    parts.push(`${passage.referenceLabel}. ${passage.translation.text}`);
  }
  if (answer.message) parts.push(answer.message);
  return parts.join(" ");
};
