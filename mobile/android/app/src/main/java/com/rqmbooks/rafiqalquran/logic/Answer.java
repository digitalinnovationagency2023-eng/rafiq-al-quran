package com.rqmbooks.rafiqalquran.logic;

import java.util.ArrayList;
import java.util.List;

/** The composed answer shown to the user. */
public final class Answer {
    public final String kind; // "answer" | "fallback"
    public final String language;
    public final String message;
    public final List<Passage> passages;
    public final double confidence;
    public final String notice;
    public final boolean noticeVerified;
    public final String translationNotice;
    public final List<String> limits;
    public final boolean suggestRefine;
    public final List<String> shownVerseKeys;
    /** Optional AI summary synthesised only from these passages (never Quran text). */
    public final String summary;

    public Answer(String kind, String language, String message, List<Passage> passages, double confidence,
                  String notice, boolean noticeVerified, String translationNotice, List<String> limits,
                  boolean suggestRefine, List<String> shownVerseKeys) {
        this(kind, language, message, passages, confidence, notice, noticeVerified, translationNotice,
                limits, suggestRefine, shownVerseKeys, null);
    }

    public Answer(String kind, String language, String message, List<Passage> passages, double confidence,
                  String notice, boolean noticeVerified, String translationNotice, List<String> limits,
                  boolean suggestRefine, List<String> shownVerseKeys, String summary) {
        this.summary = summary;
        this.kind = kind;
        this.language = language;
        this.message = message;
        this.passages = passages;
        this.confidence = confidence;
        this.notice = notice;
        this.noticeVerified = noticeVerified;
        this.translationNotice = translationNotice;
        this.limits = limits;
        this.suggestRefine = suggestRefine;
        this.shownVerseKeys = shownVerseKeys;
    }

    /** Copy of this answer carrying an AI summary (or null to clear it). */
    public Answer withSummary(String value) {
        return new Answer(kind, language, message, passages, confidence, notice, noticeVerified,
                translationNotice, limits, suggestRefine, shownVerseKeys, value);
    }

    public static Answer fallback(String language, List<String> limits) {
        return new Answer("fallback", language, Notices.fallbackFor(language), new ArrayList<Passage>(), 0,
                Notices.noticeFor(language), Notices.noticeVerified(language), null, limits, true,
                new ArrayList<String>());
    }
}
