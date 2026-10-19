package com.qurancompanion.app.logic;

import java.util.List;

public final class Concept {
    public final String key;
    public final List<String> arabic;
    public final List<List<String>> byLanguage;

    Concept(String key, List<String> arabic, List<List<String>> byLanguage) {
        this.key = key;
        this.arabic = arabic;
        this.byLanguage = byLanguage;
    }
}
