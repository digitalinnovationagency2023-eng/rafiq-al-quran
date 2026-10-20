package com.rqmbooks.rafiqalquran.logic;

/**
 * The single gate for in-app Quran recitation playback.
 *
 * <p>Recitation audio is a rights-gated feature. Playback is allowed only when a
 * reviewer has recorded the recitation rights review for the exact recording
 * source <em>and</em> the configured server reports that recitation is enabled.
 * In this build the rights review has not been recorded, so the gate is closed:
 * the app must not stream, cache or play any recitation audio.
 *
 * <p>Every screen that offers audio must read the gate from here instead of
 * hard-coding a disabled state, so that flipping the decision later happens in
 * exactly one place.
 */
public final class AudioGate {
    /**
     * The exact operator-facing sentence shown wherever playback is gated. Keep
     * this wording and the wording in the operator/admin documentation aligned.
     */
    public static final String PENDING_REASON =
            "Playback is disabled until the Quran.com recitation rights review is recorded. "
                    + "No audio is streamed or stored in this build.";

    private AudioGate() {}

    /**
     * Returns whether recitation playback may be offered at all.
     *
     * @param rightsReviewRecorded       a reviewer has recorded the recitation rights review
     * @param serverRecitationEnabled    the server reports recitation as enabled
     * @return {@code true} only when both conditions hold. In this build both are
     *         false, so this always returns {@code false}.
     */
    public static boolean playbackAllowed(boolean rightsReviewRecorded, boolean serverRecitationEnabled) {
        return rightsReviewRecorded && serverRecitationEnabled;
    }

    /**
     * Whether the Quran.com recitation rights review has been recorded for this
     * build.
     *
     * <p>This is intentionally hard-coded to {@code false}. A reviewer must
     * record the rights review (and the server must enable recitation) before
     * this may change; do not flip it without that recorded review.
     */
    public static boolean rightsReviewRecorded() {
        return false;
    }

    /**
     * Playback decision for the current build: the rights review is not recorded
     * and no server recitation flag is available, so playback is never allowed.
     */
    public static boolean playbackAllowedInThisBuild() {
        return playbackAllowed(rightsReviewRecorded(), false);
    }

    /** The exact sentence explaining why playback is unavailable right now. */
    public static String pendingReason() {
        return PENDING_REASON;
    }
}
