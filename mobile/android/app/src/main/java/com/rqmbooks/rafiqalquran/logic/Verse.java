package com.rqmbooks.rafiqalquran.logic;

import java.util.List;

public final class Verse {
    public final int id;
    public final String key;
    public final int chapterId;
    public final int number;
    public final String arabic;
    public final String plain;
    public final String normalized;
    public final List<String> tokens;
    public final Chapter chapter;

    Verse(int id, String key, int chapterId, int number, String arabic, Chapter chapter) {
        this.id = id;
        this.key = key;
        this.chapterId = chapterId;
        this.number = number;
        this.arabic = arabic;
        this.plain = ArabicText.plain(arabic);
        this.normalized = ArabicText.normalize(arabic);
        this.tokens = ArabicText.tokens(arabic);
        this.chapter = chapter;
    }
}
