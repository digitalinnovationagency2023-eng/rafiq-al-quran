package com.rqmbooks.rafiqalquran.logic;

/**
 * The single gate for in-app Quran recitation playback.
 *
 * <p>The Quran.com recitation rights review has been recorded: on 2026-10-21 the
 * publisher (Digital Innovative Agency FZ LLC) instructed that verse recitation
 * from Quran.com may be streamed in the app, with the reciter name and
 * "Source: Quran.com" attribution shown in the player and the public documents
 * updated. No audio is stored on the device; the player streams the public
 * Quran.com CDN verse files over HTTPS.
 *
 * <p>Every screen that offers audio reads this gate instead of hard-coding a
 * state, so a future decision to disable playback is a one-line change.
 */
public final class AudioGate {
    /** Human-readable record of the rights decision, shown in the player. */
    public static final String RIGHTS_DECISION =
            "Recitation rights review recorded 2026-10-21 — Quran.com verse audio streaming approved by the publisher.";

    /** Attribution required wherever recitation is played. */
    public static final String SOURCE_ATTRIBUTION = "Source: Quran.com";

    private AudioGate() {}

    /**
     * Whether recitation playback may be offered at all.
     *
     * @param rightsReviewRecorded    a reviewer has recorded the recitation rights review
     * @param serverRecitationEnabled the server reports recitation as enabled
     * @return {@code true} only when both conditions hold
     */
    public static boolean playbackAllowed(boolean rightsReviewRecorded, boolean serverRecitationEnabled) {
        return rightsReviewRecorded && serverRecitationEnabled;
    }

    /** The publisher-approved rights decision for this build. */
    public static boolean rightsReviewRecorded() {
        return true;
    }

    /** Playback decision for the current build. */
    public static boolean playbackAllowedInThisBuild() {
        return rightsReviewRecorded();
    }

    /** Short attribution line for the player and docs. */
    public static String attribution() {
        return SOURCE_ATTRIBUTION;
    }
}
