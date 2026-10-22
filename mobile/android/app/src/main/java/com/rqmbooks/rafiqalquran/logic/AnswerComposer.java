package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the answer shown in the app from verified dataset rows only. */
public final class AnswerComposer {
    private AnswerComposer() {}

    public static final double CONFIDENCE_FLOOR = 0.42;
    public static final int MAX_VERSES = 8;
    public static final int MAX_ENTITY_VERSES = 14;

    /** Reviewed central verses for major figures, merged ahead of retrieval results. */
    private static final Map<String, String[]> CORE_VERSES = new HashMap<>();
    static {
        CORE_VERSES.put("entity-isa", new String[]{"3:45", "3:55", "4:157", "4:158", "19:33", "5:110", "61:6"});
        CORE_VERSES.put("entity-maryam", new String[]{"3:42", "3:45", "19:16", "19:20", "19:27", "66:12"});
        CORE_VERSES.put("entity-musa", new String[]{"2:53", "7:103", "7:104", "20:9", "20:24", "28:7", "28:15"});
        CORE_VERSES.put("entity-ibrahim", new String[]{"2:124", "2:127", "3:67", "3:95", "6:74", "14:35", "21:51"});
        CORE_VERSES.put("entity-muhammad", new String[]{"3:144", "33:40", "47:2", "48:29", "9:128", "21:107"});
        CORE_VERSES.put("entity-nuh", new String[]{"11:25", "11:26", "11:36", "11:42", "11:43", "71:1", "71:10"});
        CORE_VERSES.put("entity-yusuf", new String[]{"12:4", "12:7", "12:8", "12:21", "12:90", "12:101"});
        CORE_VERSES.put("entity-dawud", new String[]{"2:251", "4:163", "17:55", "21:78", "21:79", "27:15", "34:10"});
        CORE_VERSES.put("entity-sulaiman", new String[]{"2:102", "21:78", "21:81", "27:15", "27:16", "27:17", "27:30"});
    }

    private static final String[][] LIMITS = {
            {"Passages are retrieved from the configured dataset with a keyword and concept search; results are not exhaustive."},
            {"The translation shown is a human translation identified in each passage; it is not the Arabic text itself."}
    };

    public static Answer answer(Dataset dataset, String question, String language) {
        return answer(dataset, question, language, new ArrayList<String>());
    }

    public static Answer answer(Dataset dataset, String question, String language, List<String> excludeVerseKeys) {
        String reference = parseVerseReference(question);
        if (reference != null) {
            Verse verse = dataset.verse(reference);
            if (verse != null) {
                List<Passage> passages = new ArrayList<>();
                passages.add(buildPassage(dataset, verse, language));
                return new Answer("answer", language, Notices.connectiveFound(language, 1), passages, 1.0,
                        Notices.noticeFor(language), Notices.noticeVerified(language), null,
                        Arrays.asList("This passage is shown because its reference was requested; it is not an interpretation."),
                        true, keysOf(passages));
            }
        }

        Integer chapterId = parseChapterReference(dataset, question);
        if (chapterId != null) {
            List<Verse> verses = dataset.chapterVerses(chapterId);
            List<Passage> passages = new ArrayList<>();
            for (int index = 0; index < Math.min(3, verses.size()); index++) {
                passages.add(buildPassage(dataset, verses.get(index), language));
            }
            return new Answer("answer", language, Notices.connectiveFound(language, passages.size()), passages, 1.0,
                    Notices.noticeFor(language), Notices.noticeVerified(language), null,
                    Arrays.asList("Only the opening passages of the requested surah are shown here."),
                    true, keysOf(passages));
        }

        Concept entity = dataset.findEntity(question);
        int verseLimit = entity != null ? MAX_ENTITY_VERSES : MAX_VERSES;
        List<Retrieval.Match> matches = Retrieval.search(dataset, question, language, verseLimit, null, toSet(excludeVerseKeys));
        if (matches.isEmpty() || matches.get(0).confidence < CONFIDENCE_FLOOR) {
            return Answer.fallback(language, Arrays.asList(
                    "This fallback reflects the limits of the retrieval system and the question wording, not a claim about the Quran."));
        }

        double best = matches.get(0).confidence;
        double threshold = entity != null
                ? Math.max(CONFIDENCE_FLOOR, best * 0.55)
                : Math.max(CONFIDENCE_FLOOR, best * 0.8);
        List<Passage> passages = new ArrayList<>();
        boolean englishFallback = false;
        for (Retrieval.Match match : matches) {
            if (match.confidence < threshold) continue;
            Passage passage = buildPassage(dataset, match.verse, language);
            englishFallback = englishFallback || passage.fallbackToEnglish;
            passages.add(passage);
        }

        // Guarantee the central reviewed verses for well-known figures are part of the
        // supplied set, so reasoning questions ("Is Isa alive or dead?") can use them.
        if (entity != null) {
            String[] core = CORE_VERSES.get(entity.key);
            if (core != null) {
                List<Passage> merged = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (String key : core) {
                    Passage passage = passageFor(dataset, language, key);
                    if (passage != null && seen.add(passage.verseKey)) merged.add(passage);
                }
                for (Passage passage : passages) {
                    if (seen.add(passage.verseKey)) merged.add(passage);
                }
                if (merged.size() > 16) merged = new ArrayList<>(merged.subList(0, 16));
                passages = merged;
            }
        }

        String message = entity != null
                ? Notices.entityFound(language, Dataset.entityLabel(entity, "ar".equals(language)), passages.size())
                : Notices.connectiveFound(language, passages.size());
        return new Answer("answer", language, message, passages,
                best, Notices.noticeFor(language), Notices.noticeVerified(language),
                englishFallback ? Notices.translationPending(language) : null,
                Arrays.asList(LIMITS[0][0], LIMITS[1][0]), true, keysOf(passages));
    }

    /** Builds one verified passage for a verse reference (used by index-fact answers). */
    public static Passage passageFor(Dataset dataset, String language, String verseKey) {
        Verse verse = dataset == null ? null : dataset.verse(verseKey);
        return verse == null ? null : buildPassage(dataset, verse, language);
    }

    /**
     * Answer built from a reviewed index fact (count/list) rather than retrieval.
     * The passages are real translations and are the references cited by the fact.
     */
    public static Answer factAnswer(Dataset dataset, String language, String message, List<String> verseKeys) {
        List<Passage> passages = new ArrayList<>();
        for (String key : verseKeys) {
            Passage passage = passageFor(dataset, language, key);
            if (passage != null) passages.add(passage);
        }
        String translationNote = null;
        if (!"en".equals(language) && !"ar".equals(language)) {
            translationNote = "The count/list explanation is in English until a reviewed translation is available; "
                    + "the verses below use your selected language where available.";
        }
        return new Answer("answer", language, message, passages, 1.0,
                Notices.noticeFor(language), Notices.noticeVerified(language), translationNote,
                Arrays.asList("Counts and name lists come from the app's reviewed Quran index; they are not tafsir, a fatwa or a ruling."),
                true, new ArrayList<String>(verseKeys));
    }

    private static Passage buildPassage(Dataset dataset, Verse verse, String language) {
        String translationText = dataset.translationText(language, verse.key);
        String effectiveLanguage = language;
        boolean fallbackToEnglish = false;
        if (translationText == null || translationText.isEmpty()) {
            translationText = dataset.translationText("en", verse.key);
            effectiveLanguage = "en";
            fallbackToEnglish = !"en".equals(language);
        }
        TranslationMeta meta = dataset.translationMeta(effectiveLanguage);
        return new Passage(verse.key, verse.chapter, verse.arabic, translationText == null ? "" : translationText,
                effectiveLanguage, meta, fallbackToEnglish, dataset.provenance());
    }

    private static List<String> keysOf(List<Passage> passages) {
        List<String> keys = new ArrayList<>();
        for (Passage passage : passages) keys.add(passage.verseKey);
        return keys;
    }

    private static java.util.Set<String> toSet(List<String> values) {
        return values == null ? null : new java.util.HashSet<>(values);
    }

    public static String parseVerseReference(String question) {
        if (question == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|[^0-9])([0-9]{1,3})\\s*[:.\\-]\\s*([0-9]{1,3})(?:[^0-9]|$)")
                .matcher(question);
        if (!matcher.find()) return null;
        int chapter = Integer.parseInt(matcher.group(1));
        int verse = Integer.parseInt(matcher.group(2));
        if (chapter < 1 || chapter > 114 || verse < 1) return null;
        return chapter + ":" + verse;
    }

    public static Integer parseChapterReference(Dataset dataset, String question) {
        if (question == null) return null;
        String lowered = question.toLowerCase();
        for (com.rqmbooks.rafiqalquran.logic.Chapter chapter : chapters(dataset)) {
            String english = chapter.nameEnglish.toLowerCase();
            if (english.length() >= 3 && lowered.contains(english)) return chapter.id;
            if (chapter.nameArabic.length() >= 3 && question.contains(chapter.nameArabic)) return chapter.id;
        }
        return null;
    }

    private static List<Chapter> chapters(Dataset dataset) {
        List<Chapter> chapters = new ArrayList<>();
        for (int id = 1; id <= 114; id++) {
            Chapter chapter = dataset.chapter(id);
            if (chapter != null) chapters.add(chapter);
        }
        return chapters;
    }
}
