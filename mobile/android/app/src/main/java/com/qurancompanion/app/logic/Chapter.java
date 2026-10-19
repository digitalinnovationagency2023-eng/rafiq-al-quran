package com.qurancompanion.app.logic;

public final class Chapter {
    public final int id;
    public final String nameArabic;
    public final String nameEnglish;
    public final int verseCount;
    public final String revelation;

    public Chapter(int id, String nameArabic, String nameEnglish, int verseCount, String revelation) {
        this.id = id;
        this.nameArabic = nameArabic;
        this.nameEnglish = nameEnglish;
        this.verseCount = verseCount;
        this.revelation = revelation;
    }

    public static Chapter parse(String line) {
        String[] parts = line.split("\t", -1);
        return new Chapter(
                Integer.parseInt(parts[0]),
                parts[1],
                parts[2],
                Integer.parseInt(parts[3]),
                parts.length > 4 ? parts[4] : ""
        );
    }
}
