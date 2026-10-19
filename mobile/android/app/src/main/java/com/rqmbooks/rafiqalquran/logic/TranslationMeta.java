package com.rqmbooks.rafiqalquran.logic;

public final class TranslationMeta {
    public final String language;
    public final String title;
    public final String author;
    public final String direction;
    public final String licenseStatus;

    public TranslationMeta(String language, String title, String author, String direction, String licenseStatus) {
        this.language = language;
        this.title = title;
        this.author = author;
        this.direction = direction;
        this.licenseStatus = licenseStatus;
    }

    static TranslationMeta parse(String line) {
        String[] parts = line.split("\t", -1);
        return new TranslationMeta(parts[0], parts[1], parts.length > 2 ? parts[2] : "",
                parts.length > 3 ? parts[3] : "ltr", parts.length > 4 ? parts[4] : "unverified");
    }
}
