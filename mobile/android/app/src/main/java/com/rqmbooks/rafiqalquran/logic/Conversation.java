package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Small, deterministic conversational layer for the Quran-only assistant.
 *
 * <p>It answers greetings, thanks, questions about the app, and politely handles
 * out-of-scope or "crazy" small talk without ever offering religious content of
 * its own. Anything that asks for advice is deliberately NOT answered here: it
 * returns {@code null} so the caller routes it to verse retrieval. Requests for
 * rulings, tafsir or interpretation get a fixed "not an authority" reply plus a
 * pointer to qualified scholars.
 *
 * <p>No Quranic text, translation, Hadith or Fiqh ruling is ever produced by this
 * class; the only scripture shown by the app comes from the validated dataset.
 */
public final class Conversation {
    public enum Kind { SALAM, GREETING, THANKS, FAREWELL, IDENTITY, CAPABILITIES, HOW_ARE_YOU, OFFTOPIC, RULING_REQUEST, ATTACHMENT_HELP, HELP }

    /** A conversational reply, ready to display. */
    public static final class Reply {
        public final Kind kind;
        public final String text;
        /** Optional short pointer shown under the reply. */
        public final String hint;

        Reply(Kind kind, String text, String hint) {
            this.kind = kind;
            this.text = text;
            this.hint = hint;
        }

        /** Public factory so the UI can show an attachment/AI explanation card. */
        public static Reply of(Kind kind, String text, String hint) {
            return new Reply(kind, text, hint);
        }
    }

    private static final List<String> SALAM_WORDS = Arrays.asList(
            "salam", "salaam", "assalam", "assalamu", "assalaamu", "as-salamu", "salamu alaikum", "assalamu alaikum", "السلام عليكم", "السلام", "سلام عليكم", "سلام");
    private static final List<String> GREETING_WORDS = Arrays.asList(
            "hi", "hii", "hello", "hellow", "hey", "good morning", "good afternoon", "good evening",
            "مرحبا", "أهلا", "اهلا", "hola");
    private static final List<String> THANKS_WORDS = Arrays.asList(
            "thank", "thanks", "thx", "shukran", "jazak", "jazakallah", "شكرا", "جزاك");
    private static final List<String> BYE_WORDS = Arrays.asList(
            "bye", "goodbye", "see you", "see ya", "good night", "مع السلامة");
    private static final List<String> IDENTITY_PHRASES = Arrays.asList(
            "who are you", "what are you", "your name", "who made you", "who created you",
            "who built you", "what is rafiq", "what is this app", "about this app", "are you human",
            "are you real", "are you an ai", "are you a robot");
    private static final List<String> CAPABILITY_PHRASES = Arrays.asList(
            "what can you do", "how do you work", "how does this work", "what do you do",
            "help me", "how can you help", "what can i ask", "how to use", "how do i use",
            "features", "what are your features", "can you help");
    private static final List<String> HOW_ARE_YOU_PHRASES = Arrays.asList(
            "how are you", "how are you doing", "how is it going", "how's it going", "whats up", "what's up");
    private static final List<String> RULING_PHRASES = Arrays.asList(
            "fatwa", "is it halal", "is it haram", "halal or haram", "permissible", "impermissible",
            "islamic ruling", "ruling on", "sharia ruling", "shariah ruling", "madhhab", "madhab",
            "tafsir", "tafseer", "interpretation", "interpret this", "explain this verse",
            "meaning of this verse", "what does this verse mean", "explain the verse",
            "hadith", "hadis", "sunnah ruling", "is it a sin", "is it haram to", "can i as a muslim",
            "what is the punishment", "which school of thought");
    private static final List<String> OFFTOPIC_PHRASES = Arrays.asList(
            "weather", "temperature", "forecast", "joke", "funny", "sing", "song", "music",
            "football", "cricket", "soccer", "basketball", "match score", "president", "prime minister",
            "election", "politics", "stock", "bitcoin", "crypto", "price of", "news", "headline",
            "movie", "film", "netflix", "game", "minecraft", "fortnite", "calculate", "math",
            "programming", "python", "javascript", "write code", "translate this", "recipe", "cook",
            "diet", "doctor", "medicine", "homework", "essay", "poem", "date today", "time now");

    private Conversation() {}

    /**
     * Classifies a message.
     *
     * @return a conversational reply, or {@code null} when the message should be
     *         handled by Quran verse retrieval (advice/topic questions).
     */
    public static Reply classify(String question) {
        if (question == null) return null;
        String text = normalize(question);
        if (text.isEmpty()) return null;
        if (text.length() > 400) return null;
        if (containsAny(text, SALAM_WORDS)) {
            return new Reply(Kind.SALAM,
                    "Wa alaykum as-salam wa rahmatullah. I'm Rafiq Al-Quran — a Quran-only assistant. "
                            + "I can find verses for a topic or a reference, and I never add my own interpretation.",
                    "Try: “What does the Quran say about patience?” or “Show me 2:255”.");
        }
        if (containsAny(text, GREETING_WORDS)) {
            return new Reply(Kind.GREETING,
                    "Hello! I'm Rafiq Al-Quran, a Quran-only assistant. I can find verses for a topic "
                            + "or a reference and show you their source.",
                    "Try: “What does the Quran say about mercy?” or open the surah list to read.");
        }
        if (containsAny(text, THANKS_WORDS)) {
            return new Reply(Kind.THANKS, "You're welcome — ask any time.", null);
        }
        if (containsAny(text, BYE_WORDS)) {
            return new Reply(Kind.FAREWELL,
                    "Goodbye. Your bookmarks, notes and reading position stay saved on this device.",
                    null);
        }
        if (containsAny(text, HOW_ARE_YOU_PHRASES)) {
            return new Reply(Kind.HOW_ARE_YOU,
                    "I'm a small Quran-focused assistant, always ready to search. How can I help you study?",
                    null);
        }
        if (containsAny(text, IDENTITY_PHRASES)) {
            return new Reply(Kind.IDENTITY,
                    "I'm Rafiq Al-Quran: a native Android assistant that finds relevant verses in the "
                            + "Quran and shows them with an identified translation and the dataset source.",
                    "I don't interpret verses or issue rulings — I only retrieve and display them.");
        }
        if (containsAny(text, CAPABILITY_PHRASES)) {
            return new Reply(Kind.CAPABILITIES,
                    "I can search the Quran for a topic (for example patience or mercy), open any verse "
                            + "reference such as 2:255, read all 114 surahs offline, and let you bookmark, "
                            + "write notes, save searches and follow reading plans. You can also ask how to use "
                            + "any feature \u2014 for example \u201Chow do I bookmark a verse\u201D or "
                            + "\u201Chow do I listen to recitation\u201D.",
                    "You can also attach a text file, or an image when AI reading is enabled, and I'll look for matching verses.");
        }
        if (containsAny(text, RULING_PHRASES)) {
            return new Reply(Kind.RULING_REQUEST,
                    "I don't provide fatwas, tafsir or interpretation — this app only retrieves verses "
                            + "from the Quran with an identified translation and its source. For a ruling or interpretation, please consult a qualified scholar.",
                    "For a ruling or explanation, please consult a qualified scholar. I can still find verses about the topic.");
        }
        if (containsAny(text, OFFTOPIC_PHRASES)) {
            return new Reply(Kind.OFFTOPIC,
                    "I'm focused on the Quran, so I can't help with that. I don't write my own answers or "
                            + "interpretation — but I can find verses about a topic if you ask in that way.",
                    "For general questions outside the Quran, a general reference will serve you better.");
        }
        String help = helpFor(text);
        if (help != null) return new Reply(Kind.HELP, help, null);
        return null;
    }

    /** Static "where to look next" guidance shown with a safe fallback answer. */
    public static List<String> guidance() {
        List<String> lines = new ArrayList<>();
        lines.add("This app only shows verses from the Quran with their identified translation and source. "
                + "It does not give rulings, tafsir or its own explanations.");
        lines.add("For a ruling, tafsir or a personal religious question, consult a qualified scholar or your local imam.");
        lines.add("For Hadith and Fiqh, study with a qualified teacher; well-known Hadith collections include "
                + "Sahih al-Bukhari and Sahih Muslim, and Fiqh works follow your school of thought.");
        lines.add("For general knowledge outside the Quran, use a general reference — this app cannot help there.");
        lines.add("This app takes no responsibility for decisions made based on its output.");
        return lines;
    }

    /** Shown when a non-text attachment cannot be read because AI assistance is off. */
    public static String attachmentUnavailable() {
        return "I can read .txt and .docx files on this device. Reading images or PDFs needs the optional "
                + "AI service, which is not enabled right now. You can type your question instead.";
    }

    /** Shown when the AI service is configured but unreachable. */
    public static String aiUnavailable() {
        return "The optional AI assistance service could not be reached. I can still search the Quran for "
                + "a typed question; images and PDFs need the AI service.";
    }

    /** Shown when the local daily AI allowance is used up. */
    public static String aiLimitReached() {
        return "The free AI assistance limit for today has been reached. Typed Quran search still works "
                + "offline, and you can try attachments again tomorrow.";
    }

    /** One-time disclosure before the first AI request. */
    public static String aiConsentTitle() {
        return "AI assistance (optional)";
    }

    public static String aiConsentMessage() {
        return "To understand natural conversation and read attached screenshots, your question and "
                + "attachment are sent to this app's server, which processes them with an AI provider. "
                + "The AI never supplies Quran text — verses always come from the app's validated dataset. "
                + "The app does not store your question or attachment on the server. You can turn AI "
                + "assistance off in the menu at any time.";
    }

    /**
     * In-app feature help. This is deliberately local and deterministic: it
     * describes how to use the app and never gives religious content, so asking
     * "how do I ..." cannot produce a fatwa or an interpretation.
     */
    private static String helpFor(String text) {
        boolean asksHow = containsAny(text, Arrays.asList(
                "help", "how do i", "how can i", "how to", "where is", "where do i", "where can i",
                "what is this app", "features", "guide", "instructions", "what can i do here"));
        if (containsAny(text, Arrays.asList("recitation", "recite", "reciter", "verse audio", "quran audio",
                "listen to quran", "play quran", "listen"))) {
            return "You can listen to Quran recitation inside the app:\n"
                    + "\u2022 \u22EE menu \u2192 \u201CVerse audio \u2014 Quran.com recitation\u201D\n"
                    + "\u2022 Browse surahs \u2192 tap \u201C\u25B7 Listen\u201D on any surah\n"
                    + "\u2022 Or open a verse and tap \u201CRecitation \u2014 Quran.com\u201D\n"
                    + "Choose a reciter, then play/pause, replay, seek or move verse by verse; it auto-advances. "
                    + "Audio streams from Quran.com (internet needed) and is never saved on the device. To hear "
                    + "an answer read aloud, tap Listen on the answer card (device text-to-speech).";
        }
        if (containsAny(text, Arrays.asList("bookmark", "save verse", "saved verse", "note", "saved search",
                "reading plan", "study tool", "study tools", "collection", "export", "side-by-side",
                "compare translation"))) {
            return "Study tools are free and stored on this device:\n"
                    + "\u2022 Open a surah and tap Save on a verse to bookmark it into a collection.\n"
                    + "\u2022 \u22EE menu \u2192 \u201CStudy tools \u2014 collections, notes, plans\u201D gives bookmark "
                    + "collections, personal notes, saved searches, reading plans with progress, side-by-side "
                    + "comparison and export.\n"
                    + "\u2022 The Browse surahs screen also has Bookmarks and History shortcuts in its top bar.";
        }
        if (containsAny(text, Arrays.asList("language", "dark mode", "light mode", "theme", "text size",
                "font size", "arabic font", "interface"))) {
            return "Top bar controls: tap the language code (for example en \u25BE) to choose from ten large "
                    + "language rows; A\u2212 and A+ change the reading text size; the moon/sun button switches "
                    + "dark and light theme. The same controls appear on most screens.";
        }
        if (containsAny(text, Arrays.asList("attach", "attachment", "upload", "docx", "pdf", "screenshot",
                "image", "file"))) {
            return "Tap \uD83D\uDCCE ATTACH in the composer. Text, .md and .docx files are read on this device "
                    + "and used to search the Quran. Images can be read only when optional AI assistance is "
                    + "enabled. PDF attachments are not supported yet. You can send a message with only an "
                    + "attachment and no typed question.";
        }
        if (containsAny(text, Arrays.asList("ai", "artificial intelligence", "ai assistance", "ai conversation",
                "chatbot", "gemini"))) {
            return "AI assistance is optional and free to the customer. \u22EE menu \u2192 \u201CAI conversation\u201D "
                    + "turns it on or off; the first use shows a one-time consent notice. It is used only for "
                    + "short chat replies and reading attached screenshots \u2014 it never supplies Quran text or "
                    + "interpretation. Limit: 30 AI requests per day on this device (plus free-tier server "
                    + "limits). Without it the app works fully offline and images cannot be read.";
        }
        if (containsAny(text, Arrays.asList("privacy", "my data", "data stored", "tracking", "personal data"))) {
            return "Your questions, bookmarks, notes, reading history and reading plans are stored on this "
                    + "device only. There is no account and no ads, and the app never reads RQM Books accounting "
                    + "data. Open \u22EE menu \u2192 Privacy for the full policy, including what the optional AI and "
                    + "feedback features send.";
        }
        if (containsAny(text, Arrays.asList("report", "feedback", "bug", "complaint", "suggest a feature"))) {
            return "\u22EE menu \u2192 \u201CReport an issue\u201D sends a bug report, translation concern, "
                    + "missing-verse note or suggestion. Diagnostics and a contact detail are included only if "
                    + "you tick or enter them. Reports go to the app's own support API, never to WhatsApp "
                    + "automatically.";
        }
        if (containsAny(text, Arrays.asList("support", "donate", "donation", "subscription", "paid", "price",
                "premium", "free"))) {
            return "The app is free: reading, search, conversation, recitation and all study tools have no paid "
                    + "plan, subscription or add-on. \u22EE menu \u2192 \u201CSupport the app\u201D explains voluntary "
                    + "ways to help; donation collection is currently disabled, so no checkout form is shown.";
        }
        if (containsAny(text, Arrays.asList("voice", "microphone", "mic", "text to speech", "tts", "dictate",
                "speak"))) {
            return "Tap the microphone (Voice) button to dictate a question; voice input uses the device's "
                    + "speech recognition. On an answer, tap Listen to hear the translation and summary through "
                    + "the device's text-to-speech \u2014 it is never presented as Quranic recitation. Voice "
                    + "features can be turned off in the menu.";
        }
        if (containsAny(text, Arrays.asList("search", "find verse", "find a verse", "look up a verse",
                "search the quran", "verse reference"))) {
            return "Type a topic (for example patience) or a verse reference such as 2:255 in the composer and "
                    + "tap Send. Answers show the Arabic verse, an identified translation, the surah:verse "
                    + "reference and the dataset source. Use More verses for the next matches, Listen to hear "
                    + "it read aloud, or New conversation to start over.";
        }
        if (!asksHow) return null;
        return "I can help you use the app. Try asking:\n"
                + "\u2022 \u201Chow do I bookmark a verse\u201D\n"
                + "\u2022 \u201Chow do I listen to recitation\u201D\n"
                + "\u2022 \u201Chow do I change the language\u201D\n"
                + "\u2022 \u201Chow do I attach a file\u201D\n"
                + "\u2022 \u201Cis there a paid plan\u201D or \u201Cprivacy\u201D\n"
                + "I can also search the Quran for any topic or a reference such as 2:255.";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'')
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) {
            if (needle.contains(" ") || !isAscii(needle)) {
                // Phrases and non-Latin greetings are matched as substrings.
                if (text.contains(needle)) return true;
            } else {
                // Whole-word match for single ASCII words so "hi" does not match "thing".
                if (text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(needle) + "\\b.*")) return true;
            }
        }
        return false;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) > 127) return false;
        return true;
    }
}
