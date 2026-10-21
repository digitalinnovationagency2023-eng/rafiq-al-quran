package com.rqmbooks.rafiqalquran.logic;

/**
 * Locale and spoken-text selection for the voice features.
 *
 * <p>Pure Java on purpose: the Android {@code Voice} wrapper needs the platform,
 * but these rules must be testable in the JVM logic suite. Two principles:
 * the TTS voice must match the language of the text that is actually spoken, and
 * other languages' translations must never be read by an English fallback voice.
 */
public final class VoiceLanguage {
    private VoiceLanguage() {}

    public static final String DEFAULT_LOCALE = "en-US";

    private static final String[] CODES = {"ar", "en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"};
    private static final String[] TAGS = {
            "ar-SA", "en-US", "ur-PK", "id-ID", "bn-BD", "tr-TR", "fa-IR", "ms-MY", "sw-KE", "fr-FR"
    };

    /** BCP-47 recognition/TTS locale for an interface-language code. */
    public static String localeTag(String language) {
        if (language != null) {
            for (int i = 0; i < CODES.length; i++) {
                if (CODES[i].equals(language)) return TAGS[i];
            }
        }
        return DEFAULT_LOCALE;
    }

    /**
     * The language actually spoken: the language of the spoken text, not the UI
     * setting. Passage translations win; English is spoken for the Arabic notice
     * fallback (Arabic has no translation rows) and for non-Arabic fallbacks.
     */
    public static String spokenLanguage(Answer answer) {
        if (answer == null) return "en";
        // Conversation replies are English by design (feature help, small talk); only
        // the fallback wording has an approved Arabic variant.
        if ("conversation".equals(answer.kind)) return "en";
        if ("fallback".equals(answer.kind)) return "ar".equals(answer.language) ? "ar" : "en";
        if (answer.passages != null) {
            for (Passage passage : answer.passages) {
                if (passage != null && passage.translationLanguage != null && !passage.translationLanguage.isEmpty()) {
                    return passage.translationLanguage;
                }
            }
        }
        return "en";
    }

    /**
     * The exact text spoken for an answer. For a non-English voice only the
     * translated passages are spoken: the English reference labels and connective
     * sentences stay on screen, because another language's voice mispronounces them.
     */
    public static String spokenText(Answer answer, int maxChars) {
        if (answer == null) return "";
        String text;
        if ("fallback".equals(answer.kind) || "conversation".equals(answer.kind)) {
            text = answer.message == null ? "" : answer.message;
        } else {
            String language = spokenLanguage(answer);
            StringBuilder builder = new StringBuilder();
            if (answer.passages != null) {
                for (Passage passage : answer.passages) {
                    if (passage == null) continue;
                    if (builder.length() > 0) builder.append(' ');
                    if ("en".equals(language)) builder.append(passage.referenceLabel).append(". ");
                    builder.append(passage.translationText == null ? "" : passage.translationText);
                }
            }
            // The connective/notice wording is Arabic only for Arabic answers; every
            // other answer's message is English. Speak it only when it matches the voice.
            String messageLanguage = "ar".equals(answer.language) ? "ar" : "en";
            boolean messageMatchesVoice = messageLanguage.equals(language);
            if (messageMatchesVoice && answer.message != null && !answer.message.trim().isEmpty()) {
                builder.append(' ').append(answer.message);
            }
            text = builder.toString();
        }
        return truncate(text.trim(), maxChars);
    }

    /** Caps spoken input at the platform limit without cutting a word in half. */
    public static String truncate(String value, int maxChars) {
        if (value == null || value.isEmpty()) return "";
        if (maxChars <= 0) return "";
        if (value.length() <= maxChars) return value;
        String cut = value.substring(0, maxChars);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > maxChars / 2) cut = cut.substring(0, lastSpace);
        return cut.trim();
    }
}
