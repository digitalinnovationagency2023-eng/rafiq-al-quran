package com.qurancompanion.app.logic;

import java.util.ArrayList;
import java.util.List;

/** Arabic text helpers. The canonical Quranic text is never modified. */
public final class ArabicText {
    private ArabicText() {}

    private static final char[] DIACRITICS = {
            '\u0610','\u0611','\u0612','\u0613','\u0614','\u0615','\u0616','\u0617','\u0618','\u0619','\u061A',
            '\u064B','\u064C','\u064D','\u064E','\u064F','\u0650','\u0651','\u0652','\u0653','\u0654','\u0655',
            '\u0656','\u0657','\u0658','\u0659','\u065A','\u065B','\u065C','\u065D','\u065E','\u065F','\u0670','\u0640'
    };

    public static String canonical(String text) {
        if (text == null) return "";
        return text.replace("\uFEFF", "").replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").trim();
    }

    /** Readable form without diacritics (used for display fallback and word counts). */
    public static String plain(String text) {
        String value = canonical(text);
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isDiacritic(c)) continue;
            builder.append(c);
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean isDiacritic(char c) {
        for (char d : DIACRITICS) if (c == d) return true;
        return c >= '\u06D6' && c <= '\u06ED';
    }

    /**
     * Search normalisation: unify alef/hamza forms, ya/alef-maqsura, ta-marbuta,
     * remove diacritics and punctuation. Never shown to users.
     */
    public static String normalize(String text) {
        String value = plain(text).toLowerCase();
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671') c = '\u0627';
            else if (c == '\u0649') c = '\u064A';
            else if (c == '\u0629') c = '\u0647';
            else if (c == '\u0624') c = '\u0648';
            else if (c == '\u0626') c = '\u064A';
            else if (isDiacritic(c)) continue;
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) builder.append(c);
            else builder.append(' ');
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    public static List<String> tokens(String text) {
        String normalized = normalize(text);
        List<String> out = new ArrayList<>();
        if (normalized.isEmpty()) return out;
        for (String token : normalized.split(" ")) if (!token.isEmpty()) out.add(token);
        return out;
    }

    /** Light stemming for search only: definite article and common affixes. */
    public static String stem(String token) {
        if (token == null) return "";
        String value = token;
        if (value.length() < 3) return value;
        for (int pass = 0; pass < 2; pass++) {
            if (value.length() > 5 && (value.startsWith("\u0648\u0627\u0644") || value.startsWith("\u0628\u0627\u0644")
                    || value.startsWith("\u0643\u0627\u0644") || value.startsWith("\u0641\u0627\u0644"))) {
                value = value.substring(3);
            } else if (value.length() > 3 && value.startsWith("\u0627\u0644")) {
                value = value.substring(2);
            } else if (value.length() > 4 && ("\u0648\u0641\u0628\u0643\u0644".indexOf(value.charAt(0)) >= 0)) {
                value = value.substring(1);
            } else {
                break;
            }
        }
        String[] suffixes = {"\u0647\u0627", "\u0647\u0645", "\u0647\u0646", "\u0643\u0645", "\u0646\u0627",
                "\u0627\u062A", "\u0648\u0646", "\u064A\u0646", "\u0627\u0646"};
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() - suffix.length() >= 3) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        if (value.length() > 4 && (value.endsWith("\u064A") || value.endsWith("\u0647"))) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static List<String> searchForms(String token) {
        List<String> forms = new ArrayList<>(2);
        forms.add(token);
        String stemmed = stem(token);
        if (!stemmed.equals(token) && !stemmed.isEmpty()) forms.add(stemmed);
        return forms;
    }

    public static boolean isArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0600' && c <= '\u06FF') return true;
        }
        return false;
    }
}
