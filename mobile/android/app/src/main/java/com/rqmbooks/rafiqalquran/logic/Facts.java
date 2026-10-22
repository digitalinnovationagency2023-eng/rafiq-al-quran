package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic index facts for questions the Quran text itself cannot answer in
 * one verse: how many prophets are named, how often a name occurs, dataset totals.
 *
 * <p>Every number comes from the same validated dataset the reader uses (the
 * reviewed name index and the Quran text), so the count can be audited. These are
 * index facts, not tafsir, and the wording always says so. The optional AI answer
 * receives the compact fact for eloquence only; these deterministic values remain
 * the fallback and the source of the count.
 */
public final class Facts {
    private Facts() {}

    public static final class Result {
        public final String message;
        public final List<String> verseKeys;
        /** Compact fact for the AI prompt (max 300 chars, ASCII only). */
        public final String aiFact;

        Result(String message, List<String> verseKeys, String aiFact) {
            this.message = message;
            this.verseKeys = verseKeys;
            this.aiFact = aiFact;
        }
    }

    /** Canonical order of the prophets named in the Quran (reviewed index). */
    private static final String[] PROPHET_KEYS = {
            "entity-adam", "entity-idris", "entity-nuh", "entity-hud", "entity-salih",
            "entity-ibrahim", "entity-lut", "entity-ismail", "entity-ishaq", "entity-yaqub",
            "entity-yusuf", "entity-ayyub", "entity-shuayb", "entity-musa", "entity-harun",
            "entity-dhulkifl", "entity-dawud", "entity-sulaiman", "entity-ilyas", "entity-alyasa",
            "entity-yunus", "entity-zakariya", "entity-yahya", "entity-isa", "entity-muhammad"
    };

    private static final Pattern MENTION_COUNT = Pattern.compile(
            "how many times (?:is |are |was |were |does |do )?(?:the name )?([a-z' ]{3,40}?)"
                    + "(?: mentioned| named| appear| occurs| occur)");

    public static Result match(Dataset dataset, String question) {
        if (dataset == null || question == null) return null;
        String q = " " + question.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s']+", " ")
                .replaceAll("\\s+", " ").trim() + " ";

        Matcher mention = MENTION_COUNT.matcher(q);
        if (mention.find()) {
            Concept concept = dataset.findEntity(mention.group(1));
            if (concept != null) return mentions(dataset, concept);
        }

        if (asksProphets(q)) return prophets(dataset);
        if (q.contains("how many surah") || q.contains("number of surah")
                || q.contains("how many chapters") || q.contains("number of chapters")) {
            return new Result(
                    "The app's validated Quran dataset contains " + dataset.chapterCount() + " surahs. "
                            + "This is a dataset total, not a number stated in a single verse: the Quran refers to its "
                            + "surahs but does not list their total in one passage, so treat this as an index fact.",
                    new ArrayList<String>(),
                    "The app's validated dataset contains " + dataset.chapterCount() + " surahs (an index count, not a verse statement).");
        }
        if (q.contains("how many verse") || q.contains("how many ayat") || q.contains("number of verses")
                || q.contains("number of ayahs") || q.contains("how many ayahs")) {
            return new Result(
                    "The app's validated Quran dataset contains " + dataset.verseCount() + " verses. "
                            + "This is a dataset total, not a number stated in a single verse; the Quran speaks of its "
                            + "verses but does not give this total in one passage, so treat it as an index fact.",
                    new ArrayList<String>(),
                    "The app's validated dataset contains " + dataset.verseCount() + " verses (an index count, not a verse statement).");
        }
        return null;
    }

    private static boolean asksProphets(String q) {
        boolean topic = q.contains("prophet") || q.contains("prophets") || q.contains("messenger")
                || q.contains("messengers") || q.contains("nabi") || q.contains("rasul");
        if (!topic) return false;
        boolean askingCount = q.contains("how many") || q.contains("number of") || q.contains("total");
        boolean askingList = q.contains("names of") || q.contains("name of") || q.contains("list of")
                || q.contains("which prophets") || q.contains("which messengers") || q.contains("who are the prophets")
                || q.contains("who are the messengers") || q.contains("prophets mentioned")
                || q.contains("prophets named") || q.contains("prophets in the quran");
        return askingCount || askingList;
    }

    private static Result prophets(Dataset dataset) {
        List<String> names = new ArrayList<>();
        for (String key : PROPHET_KEYS) {
            Concept concept = dataset.entity(key);
            if (concept != null) names.add(Dataset.entityLabel(concept, false));
        }
        int count = names.isEmpty() ? PROPHET_KEYS.length : names.size();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                if (i == names.size() - 1) list.append(" and ");
                else list.append(", ");
            }
            list.append(names.get(i));
        }
        List<String> keys = new ArrayList<>();
        keys.add("4:164");
        keys.add("40:78");
        String message = "The Quran names " + count + " prophets: " + list + ".\n\n"
                + "That is a count of the prophets NAMED in the Quran, taken from the app's reviewed name index - "
                + "it is not a number stated in a single verse. The Quran itself adds that some messengers were not "
                + "mentioned (4:164) and that God sent messengers before Muhammad whose stories are not all told "
                + "(40:78), so the full number of prophets is known to God alone.\n\n"
                + "This is an index fact with its qualifying verses below - not tafsir, a fatwa or a ruling.";
        String compact = "App index: " + count + " named prophets (" + list + "); 4:164/40:78 say others were not mentioned.";
        if (compact.length() > 300) {
            compact = "App index: " + count + " named prophets in the Quran; 4:164/40:78 say others were not mentioned.";
        }
        return new Result(message, keys, compact);
    }

    private static Result mentions(Dataset dataset, Concept concept) {
        Set<String> matchedKeys = new LinkedHashSet<>();
        for (Verse verse : dataset.verses) {
            String normalized = ArabicText.normalize(verse.arabic);
            for (String term : concept.arabic) {
                String normalizedTerm = ArabicText.normalize(term);
                if (!normalizedTerm.isEmpty() && normalized.contains(normalizedTerm)) {
                    matchedKeys.add(verse.key);
                    break;
                }
            }
        }
        if (matchedKeys.isEmpty()) return null;
        List<String> keys = new ArrayList<>(matchedKeys);
        List<String> examples = new ArrayList<>(keys.subList(0, Math.min(5, keys.size())));
        String label = Dataset.entityLabel(concept, false);
        String message = "The name or aliases of " + label + " appear in " + keys.size()
                + " verses of the Quran, counted from the app's Quran text index. Counts cover named occurrences "
                + "only - not pronouns, parables or implied references - and this is an index count, not a number "
                + "stated in a verse. A few example verses are shown below.";
        String aiFact = "The app's index counts named occurrences of " + label + " in " + keys.size()
                + " verses (names only, not pronouns or implied references).";
        return new Result(message, examples, aiFact);
    }
}
