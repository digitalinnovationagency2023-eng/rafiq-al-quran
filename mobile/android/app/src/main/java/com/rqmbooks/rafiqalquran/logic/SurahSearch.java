package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Search the 114 surahs by English, Arabic or romanised name. */
public final class SurahSearch {
    private SurahSearch() {}

    private static final Map<Integer, String[]> ALIASES = new HashMap<>();
    static {
        ALIASES.put(1, new String[]{"fatiha", "fatihah", "opening"});
        ALIASES.put(2, new String[]{"baqarah", "baqara", "cow"});
        ALIASES.put(3, new String[]{"imran", "family of imran"});
        ALIASES.put(4, new String[]{"nisa", "women"});
        ALIASES.put(5, new String[]{"maidah", "maida", "table"});
        ALIASES.put(6, new String[]{"anam", "cattle"});
        ALIASES.put(7, new String[]{"araf", "heights"});
        ALIASES.put(8, new String[]{"anfal", "spoils"});
        ALIASES.put(9, new String[]{"tawbah", "tauba", "repentance"});
        ALIASES.put(10, new String[]{"yunus", "jonah"});
        ALIASES.put(11, new String[]{"hud"});
        ALIASES.put(12, new String[]{"yusuf", "joseph"});
        ALIASES.put(13, new String[]{"rad", "thunder"});
        ALIASES.put(14, new String[]{"ibrahim", "abraham"});
        ALIASES.put(15, new String[]{"hijr"});
        ALIASES.put(16, new String[]{"nahl", "bee"});
        ALIASES.put(17, new String[]{"isra", "night journey"});
        ALIASES.put(18, new String[]{"kahf", "cave"});
        ALIASES.put(19, new String[]{"maryam", "mary"});
        ALIASES.put(20, new String[]{"taha", "ta ha"});
        ALIASES.put(21, new String[]{"anbiya", "prophets"});
        ALIASES.put(22, new String[]{"hajj", "pilgrimage"});
        ALIASES.put(23, new String[]{"muminun", "believers"});
        ALIASES.put(24, new String[]{"nur", "light"});
        ALIASES.put(25, new String[]{"furqan", "criterion"});
        ALIASES.put(26, new String[]{"shuara", "poets"});
        ALIASES.put(27, new String[]{"naml", "ant"});
        ALIASES.put(28, new String[]{"qasas", "stories"});
        ALIASES.put(29, new String[]{"ankabut", "spider"});
        ALIASES.put(30, new String[]{"rum", "romans"});
        ALIASES.put(31, new String[]{"luqman"});
        ALIASES.put(32, new String[]{"sajdah", "prostration"});
        ALIASES.put(33, new String[]{"ahzab", "confederates"});
        ALIASES.put(34, new String[]{"saba", "sheba"});
        ALIASES.put(35, new String[]{"fatir", "originator"});
        ALIASES.put(36, new String[]{"yasin", "ya sin"});
        ALIASES.put(37, new String[]{"saffat", "ranged"});
        ALIASES.put(38, new String[]{"sad"});
        ALIASES.put(39, new String[]{"zumar", "groups"});
        ALIASES.put(40, new String[]{"ghafir", "forgiver"});
        ALIASES.put(41, new String[]{"fussilat", "explained"});
        ALIASES.put(42, new String[]{"shura", "consultation"});
        ALIASES.put(43, new String[]{"zukhruf", "gold"});
        ALIASES.put(44, new String[]{"dukhan", "smoke"});
        ALIASES.put(45, new String[]{"jathiyah", "kneeling"});
        ALIASES.put(46, new String[]{"ahqaf", "dunes"});
        ALIASES.put(47, new String[]{"muhammad"});
        ALIASES.put(48, new String[]{"fath", "victory"});
        ALIASES.put(49, new String[]{"hujurat", "rooms"});
        ALIASES.put(50, new String[]{"qaf"});
        ALIASES.put(51, new String[]{"dhariyat", "winds"});
        ALIASES.put(52, new String[]{"tur", "mountain"});
        ALIASES.put(53, new String[]{"najm", "star"});
        ALIASES.put(54, new String[]{"qamar", "moon"});
        ALIASES.put(55, new String[]{"rahman", "most gracious"});
        ALIASES.put(56, new String[]{"waqiah", "event"});
        ALIASES.put(57, new String[]{"hadid", "iron"});
        ALIASES.put(58, new String[]{"mujadilah", "dispute"});
        ALIASES.put(59, new String[]{"hashr", "exile"});
        ALIASES.put(60, new String[]{"mumtahanah", "tested"});
        ALIASES.put(61, new String[]{"saff", "ranks"});
        ALIASES.put(62, new String[]{"jumuah", "friday"});
        ALIASES.put(63, new String[]{"munafiqun", "hypocrites"});
        ALIASES.put(64, new String[]{"taghabun", "mutual loss"});
        ALIASES.put(65, new String[]{"talaq", "divorce"});
        ALIASES.put(66, new String[]{"tahrim", "prohibition"});
        ALIASES.put(67, new String[]{"mulk", "kingdom"});
        ALIASES.put(68, new String[]{"qalam", "pen"});
        ALIASES.put(69, new String[]{"haqqah", "reality"});
        ALIASES.put(70, new String[]{"maarij", "ascending"});
        ALIASES.put(71, new String[]{"nuh", "noah"});
        ALIASES.put(72, new String[]{"jinn"});
        ALIASES.put(73, new String[]{"muzzammil", "enfolded"});
        ALIASES.put(74, new String[]{"muddaththir", "cloaked"});
        ALIASES.put(75, new String[]{"qiyamah", "resurrection"});
        ALIASES.put(76, new String[]{"insan", "man"});
        ALIASES.put(77, new String[]{"mursalat", "sent forth"});
        ALIASES.put(78, new String[]{"naba", "news"});
        ALIASES.put(79, new String[]{"naziat", "those who drag"});
        ALIASES.put(80, new String[]{"abasa", "frowned"});
        ALIASES.put(81, new String[]{"takwir", "folding up"});
        ALIASES.put(82, new String[]{"infitar", "cleaving"});
        ALIASES.put(83, new String[]{"mutaffifin", "defrauding"});
        ALIASES.put(84, new String[]{"inshiqaq", "splitting"});
        ALIASES.put(85, new String[]{"buruj", "constellations"});
        ALIASES.put(86, new String[]{"tariq", "night star"});
        ALIASES.put(87, new String[]{"ala", "most high"});
        ALIASES.put(88, new String[]{"ghashiyah", "overwhelming"});
        ALIASES.put(89, new String[]{"fajr", "dawn"});
        ALIASES.put(90, new String[]{"balad", "city"});
        ALIASES.put(91, new String[]{"shams", "sun"});
        ALIASES.put(92, new String[]{"layl", "night"});
        ALIASES.put(93, new String[]{"duha", "morning"});
        ALIASES.put(94, new String[]{"sharh", "expansion"});
        ALIASES.put(95, new String[]{"tin", "fig"});
        ALIASES.put(96, new String[]{"alaq", "clot"});
        ALIASES.put(97, new String[]{"qadr", "power"});
        ALIASES.put(98, new String[]{"bayyinah", "clear evidence"});
        ALIASES.put(99, new String[]{"zalzalah", "earthquake"});
        ALIASES.put(100, new String[]{"adiyat", "chargers"});
        ALIASES.put(101, new String[]{"qariah", "calamity"});
        ALIASES.put(102, new String[]{"takathur", "rivalry"});
        ALIASES.put(103, new String[]{"asr", "time"});
        ALIASES.put(104, new String[]{"humazah", "slanderer"});
        ALIASES.put(105, new String[]{"fil", "elephant"});
        ALIASES.put(106, new String[]{"quraysh", "quraish"});
        ALIASES.put(107, new String[]{"maun", "small kindness"});
        ALIASES.put(108, new String[]{"kawthar", "abundance"});
        ALIASES.put(109, new String[]{"kafirun", "disbelievers"});
        ALIASES.put(110, new String[]{"nasr", "help"});
        ALIASES.put(111, new String[]{"masad", "palm fibre"});
        ALIASES.put(112, new String[]{"ikhlas", "sincerity"});
        ALIASES.put(113, new String[]{"falaq", "daybreak"});
        ALIASES.put(114, new String[]{"nas", "mankind"});
    }

    public static boolean matches(Chapter chapter, String rawQuery) {
        if (chapter == null) return false;
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) return true;
        String number = query.replaceAll("[^0-9]", "");
        if (!number.isEmpty() && query.length() <= 4 && Integer.parseInt(number) == chapter.id) return true;

        String arabicQuery = ArabicText.normalize(query);
        if (!arabicQuery.isEmpty() && ArabicText.normalize(chapter.nameArabic).contains(arabicQuery)) return true;

        String english = Roman.canonical(stripPrefixes(chapter.nameEnglish));
        String q = Roman.canonical(stripPrefixes(query));
        if (!q.isEmpty() && (english.contains(q) || q.contains(english))) return true;

        String[] queryTokens = query.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        for (String token : queryTokens) {
            if (token.length() < 3) continue;
            String canonical = Roman.canonical(token);
            if (canonical.isEmpty()) continue;
            if (english.contains(canonical)) return true;
            String[] aliases = ALIASES.get(chapter.id);
            if (aliases != null) {
                for (String alias : aliases) {
                    String aliasCanonical = Roman.canonical(alias);
                    if (canonical.equals(aliasCanonical) || Roman.fuzzyMatch(canonical, aliasCanonical)) return true;
                }
            }
        }
        return false;
    }

    public static List<Integer> filter(Dataset dataset, String query) {
        List<Integer> ids = new ArrayList<>();
        if (dataset == null) return ids;
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            for (int id = 1; id <= 114; id++) ids.add(id);
            return ids;
        }
        for (int id = 1; id <= 114; id++) {
            if (matches(dataset.chapter(id), value)) ids.add(id);
        }
        return ids;
    }

    private static String stripPrefixes(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.US).trim();
        text = text.replaceFirst("^surah\\s+", "").replaceFirst("^surat\\s+", "").replaceFirst("^chapter\\s+", "");
        text = text.replaceFirst("^al[-\\s]+", "").replaceFirst("^el[-\\s]+", "");
        return text;
    }
}
