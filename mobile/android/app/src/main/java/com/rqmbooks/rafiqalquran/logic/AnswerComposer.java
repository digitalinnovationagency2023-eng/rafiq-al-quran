package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds the answer shown in the app from verified dataset rows only. */
public final class AnswerComposer {
    private AnswerComposer() {}

    public static final double CONFIDENCE_FLOOR = 0.42;
    public static final int MAX_VERSES = 8;

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

        List<Retrieval.Match> matches = Retrieval.search(dataset, question, language, MAX_VERSES, null, toSet(excludeVerseKeys));
        if (matches.isEmpty() || matches.get(0).confidence < CONFIDENCE_FLOOR) {
            return Answer.fallback(language, Arrays.asList(
                    "This fallback reflects the limits of the retrieval system and the question wording, not a claim about the Quran."));
        }

        double best = matches.get(0).confidence;
        List<Passage> passages = new ArrayList<>();
        boolean englishFallback = false;
        for (Retrieval.Match match : matches) {
            if (match.confidence < Math.max(CONFIDENCE_FLOOR, best * 0.8)) continue;
            Passage passage = buildPassage(dataset, match.verse, language);
            englishFallback = englishFallback || passage.fallbackToEnglish;
            passages.add(passage);
        }

        return new Answer("answer", language, Notices.connectiveFound(language, passages.size()), passages,
                best, Notices.noticeFor(language), Notices.noticeVerified(language),
                englishFallback ? Notices.translationPending(language) : null,
                Arrays.asList(LIMITS[0][0], LIMITS[1][0]), true, keysOf(passages));
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
