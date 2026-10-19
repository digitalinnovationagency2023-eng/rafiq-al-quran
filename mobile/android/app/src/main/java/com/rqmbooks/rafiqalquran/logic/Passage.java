package com.rqmbooks.rafiqalquran.logic;

/** A single verified passage ready for display. */
public final class Passage {
    public final String verseKey;
    public final String chapterNameEnglish;
    public final String chapterNameArabic;
    public final String arabic;
    public final String translationText;
    public final String translationLanguage;
    public final String translationTitle;
    public final String translationAuthor;
    public final String translationDirection;
    public final String translationLicenseStatus;
    public final boolean fallbackToEnglish;
    public final String referenceLabel;
    public final String dataset;
    public final String datasetVersion;
    public final String datasetSourceUrl;
    public final String datasetChecksum;

    public Passage(String verseKey, Chapter chapter, String arabic, String translationText, String translationLanguage,
                   TranslationMeta meta, boolean fallbackToEnglish, Provenance provenance) {
        this.verseKey = verseKey;
        this.chapterNameEnglish = chapter.nameEnglish;
        this.chapterNameArabic = chapter.nameArabic;
        this.arabic = arabic;
        this.translationText = translationText;
        this.translationLanguage = translationLanguage;
        this.translationTitle = meta == null ? null : meta.title;
        this.translationAuthor = meta == null ? null : meta.author;
        this.translationDirection = meta == null ? "ltr" : meta.direction;
        this.translationLicenseStatus = meta == null ? "unverified" : meta.licenseStatus;
        this.fallbackToEnglish = fallbackToEnglish;
        this.referenceLabel = chapter.nameEnglish + " " + verseKey;
        this.dataset = provenance.dataset;
        this.datasetVersion = provenance.version;
        this.datasetSourceUrl = provenance.sourceUrl;
        this.datasetChecksum = provenance.checksum;
    }
}
