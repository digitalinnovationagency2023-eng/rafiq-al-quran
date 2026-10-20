package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers behind the study tools: bookmark collections, notes, saved
 * searches, reading plans and CSV exports. Kept free of Android dependencies so
 * the same code is verified by the JVM logic tests.
 */
public final class StudyData {
    public static final String DEFAULT_COLLECTION = "General";
    public static final int MAX_COLLECTION_LENGTH = 40;
    public static final int MAX_NOTE_LENGTH = 2000;
    public static final int MAX_SEARCHES = 30;
    public static final String PRESET_FULL = "full";
    public static final String PRESET_JUZ30 = "juz30";
    public static final String PRESET_LAST10 = "last10";

    private StudyData() {}

    // ------------------------------------------------------------ collections
    /** Collection names are single-line, pipe-free and bounded. */
    public static String sanitizeCollectionName(String raw) {
        if (raw == null) return DEFAULT_COLLECTION;
        String cleaned = raw.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) return DEFAULT_COLLECTION;
        if (cleaned.length() > MAX_COLLECTION_LENGTH) cleaned = cleaned.substring(0, MAX_COLLECTION_LENGTH).trim();
        return cleaned;
    }

    /** Adds a collection when it does not exist yet; returns the canonical name. */
    public static String addCollection(List<String> collections, String raw) {
        String name = sanitizeCollectionName(raw);
        for (String existing : collections) if (existing.equalsIgnoreCase(name)) return existing;
        collections.add(name);
        return name;
    }

    public static List<String> defaultCollections() {
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_COLLECTION);
        return names;
    }

    // ------------------------------------------------------------------ notes
    public static String sanitizeNote(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleaned.length() > MAX_NOTE_LENGTH) cleaned = cleaned.substring(0, MAX_NOTE_LENGTH);
        return cleaned;
    }

    // -------------------------------------------------------------- progress
    public static int percent(int done, int total) {
        if (total <= 0) return 0;
        int clamped = Math.max(0, Math.min(done, total));
        return (int) Math.round(clamped * 100.0 / total);
    }

    public static String formatProgress(int done, int total) {
        int clampedDone = Math.max(0, Math.min(done, total));
        return clampedDone + " of " + total + "  \u00B7  " + percent(done, total) + "%";
    }

    // --------------------------------------------------------------- presets
    public static List<Integer> presetChapters(String preset) {
        List<Integer> chapters = new ArrayList<>();
        String value = preset == null ? PRESET_FULL : preset;
        switch (value) {
            case PRESET_JUZ30:
                for (int chapter = 78; chapter <= 114; chapter++) chapters.add(chapter);
                break;
            case PRESET_LAST10:
                for (int chapter = 105; chapter <= 114; chapter++) chapters.add(chapter);
                break;
            case PRESET_FULL:
            default:
                for (int chapter = 1; chapter <= 114; chapter++) chapters.add(chapter);
                break;
        }
        return chapters;
    }

    public static String presetTitle(String preset) {
        switch (preset == null ? PRESET_FULL : preset) {
            case PRESET_JUZ30: return "Juz 30 (surahs 78\u2013114)";
            case PRESET_LAST10: return "Last ten surahs (105\u2013114)";
            case PRESET_FULL:
            default: return "Full Quran (114 surahs)";
        }
    }

    /** The next chapter in a plan that is not marked read, or 0 when finished. */
    public static int nextChapter(List<Integer> chapters, List<Integer> done) {
        for (Integer chapter : chapters) {
            if (!done.contains(chapter)) return chapter;
        }
        return 0;
    }

    // ---------------------------------------------------------------- export
    /** RFC 4180-style quoting so exported CSV opens cleanly in spreadsheets. */
    public static String csv(String... fields) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) builder.append(',');
            String value = fields[index] == null ? "" : fields[index];
            boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
            if (quote) builder.append('"').append(value.replace("\"", "\"\"")).append('"');
            else builder.append(value);
        }
        return builder.toString();
    }

    public static String csvHeader(String... columns) {
        return csv(columns);
    }

    public static List<Integer> parseChapters(String csv) {
        List<Integer> chapters = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return chapters;
        for (String part : csv.split(",")) {
            try {
                int chapter = Integer.parseInt(part.trim());
                if (chapter >= 1 && chapter <= 114 && !chapters.contains(chapter)) chapters.add(chapter);
            } catch (NumberFormatException ignored) {
                // ignore malformed entries
            }
        }
        return chapters;
    }

    public static String joinChapters(List<Integer> chapters) {
        StringBuilder builder = new StringBuilder();
        for (Integer chapter : chapters) {
            if (builder.length() > 0) builder.append(',');
            builder.append(chapter);
        }
        return builder.toString();
    }

    public static boolean validVerseKey(String key) {
        return key != null && key.matches("\\d{1,3}:\\d{1,3}");
    }

    /** Natural ordering for "2:255" style keys (numeric, not lexicographic). */
    public static int compareVerseKeys(String left, String right) {
        String[] a = left.split(":");
        String[] b = right.split(":");
        int chapterA = Integer.parseInt(a[0]);
        int chapterB = Integer.parseInt(b[0]);
        if (chapterA != chapterB) return Integer.compare(chapterA, chapterB);
        return Integer.compare(Integer.parseInt(a[1]), Integer.parseInt(b[1]));
    }
}
