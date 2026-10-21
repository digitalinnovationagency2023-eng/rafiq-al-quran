package com.rqmbooks.rafiqalquran.logic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Romanised-input normalisation and fuzzy matching for the chat box.
 *
 * <p>People type Islamic terms in English letters ("sabr", "namaz", "gunah"),
 * romanised names with spelling variants ("Hazrath Eesa", "Yaqoob", "Moosa") and
 * often mix English with Roman Urdu/Arabic in one sentence ("what quran says
 * about sabr aur shukr"). This helper normalises such tokens and provides a
 * small bounded edit-distance match so small typos still resolve. It is a search
 * aid only: every verse shown still comes from the validated dataset.
 */
public final class Roman {
    private Roman() {}

    private static final Set<String> HONORIFICS = new HashSet<>(Arrays.asList(
            "hazrat", "hazrath", "hadrat", "hadrath", "hazrate", "hazrat-e", "hzt", "hz",
            "prophet", "prophets", "nabi", "nabe", "paighambar", "paigambar", "payghambar",
            "peer", "pir", "saint", "imam", "maulana", "moulana", "maulvi", "shaikh", "sheikh",
            "alaihi", "alaihis", "alaihissalam", "salam", "rahimahullah", "hafiz", "qari"));

    public static boolean isHonorific(String token) {
        if (token == null) return false;
        String value = token.toLowerCase().replace("\u2019", "'").trim();
        return HONORIFICS.contains(value);
    }

    /** Search normal form: handles the common transliteration variants people type. */
    public static String canonical(String token) {
        if (token == null) return "";
        String value = token.toLowerCase().replace("\u2019", "'").replace("\u2018", "'");
        value = value.replaceAll("[^a-z0-9]", "");
        if (value.isEmpty()) return "";
        value = value.replace("aa", "a").replace("ee", "i").replace("ii", "i")
                .replace("oo", "u").replace("uu", "u").replace("ou", "u")
                .replace("kh", "k").replace("gh", "g").replace("dh", "d")
                .replace("th", "t").replace("ph", "f").replace("q", "k")
                .replace("v", "w");
        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != previous) builder.append(current);
            previous = current;
        }
        return builder.toString();
    }

    public static boolean sameRoman(String left, String right) {
        String a = canonical(left);
        return !a.isEmpty() && a.equals(canonical(right));
    }

    /**
     * True when a romanised word is close enough to a known alias/term. Short
     * words need an exact canonical match; 4-5 letters allow one typo, 6-7 allow
     * one and 8+ allow two, always with the same first letter to avoid noise.
     */
    public static boolean fuzzyMatch(String token, String term) {
        String a = canonical(token);
        String b = canonical(term);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() < 4 || b.length() < 4) return false;
        if (a.charAt(0) != b.charAt(0)) return false;
        if (Math.abs(a.length() - b.length()) > 2) return false;
        int limit = Math.min(a.length(), b.length()) >= 8 ? 2 : 1;
        return distance(a, b, limit) <= limit;
    }

    public static boolean isAscii(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 127) return false;
        }
        return !value.isEmpty();
    }

    private static int distance(String left, String right, int limit) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                if (current[j] < rowMin) rowMin = current[j];
            }
            if (rowMin > limit) return limit + 1;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
