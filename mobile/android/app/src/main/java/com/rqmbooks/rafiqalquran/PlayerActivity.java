package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.AudioGate;
import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.Verse;

import java.util.ArrayList;
import java.util.List;

/**
 * Verse-by-verse audio player UI for candidate Quran.com recitations.
 *
 * <p>Playback is rights-gated in this build: the screen shows the verse, its
 * translation and the mushaf indicator, and lets the reader move between verses,
 * but no audio is requested, streamed, cached or played. Whether playback could
 * ever be offered is decided in exactly one place,
 * {@link com.rqmbooks.rafiqalquran.logic.AudioGate}, so this Activity only renders
 * the gate's answer.
 */
public final class PlayerActivity extends Activity {
    /** Chapter id extra for {@link #intent(Context, int, String)}. */
    public static final String EXTRA_CHAPTER = "chapter";
    /** Verse key ("2:255") extra for {@link #intent(Context, int, String)}. */
    public static final String EXTRA_VERSE = "verse";

    private Prefs prefs;
    private Dataset dataset;
    private QuranMeta meta;
    private Chapter chapter;
    private final List<Verse> verses = new ArrayList<>();
    private int chapterId = 1;
    private String requestedVerseKey;
    private int index;
    private boolean playbackAllowed;

    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout verseHolder;
    private TextView positionLabel;
    private Button previousButton;
    private Button nextButton;

    /**
     * Opens the gated verse player at a chapter and (optionally) a verse key.
     *
     * @param context   caller context
     * @param chapterId chapter to open, 1–114
     * @param verseKey  verse to focus ("2:255"); the chapter's first verse is used when null
     */
    public static Intent intent(Context context, int chapterId, String verseKey) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_CHAPTER, chapterId);
        intent.putExtra(EXTRA_VERSE, verseKey);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        chapterId = getIntent().getIntExtra(EXTRA_CHAPTER, 1);
        requestedVerseKey = getIntent().getStringExtra(EXTRA_VERSE);
        playbackAllowed = AudioGate.playbackAllowedInThisBuild();

        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.bar(this, "Verse audio", "Quran.com \u00B7 rights pending", this::finish);
        bar.addView(Ui.themeToggle(this));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 28));
        content.setBackgroundColor(Ui.PAPER);
        TextView loading = Ui.text(this, "Loading the verse\u2026", 13f, Ui.MUTED);
        content.addView(loading);
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new Thread(() -> {
            Dataset loaded = null;
            QuranMeta loadedMeta = null;
            String failure = null;
            try {
                loaded = Assets.dataset(this);
                try {
                    Assets.ensureLanguage(this, loaded, prefs.language());
                } catch (Exception ignored) {
                    // English stays available as the reviewed fallback.
                }
                if (!"en".equals(prefs.language())) {
                    try {
                        Assets.ensureLanguage(this, loaded, "en");
                    } catch (Exception ignored) {
                        // Fallback translation remains whatever is already in memory.
                    }
                }
                loadedMeta = Assets.meta(this);
            } catch (Exception error) {
                failure = error.getMessage();
            }
            final Dataset loadedDataset = loaded;
            final QuranMeta loadedMetadata = loadedMeta;
            final String loadFailure = failure;
            runOnUiThread(() -> {
                dataset = loadedDataset;
                meta = loadedMetadata;
                if (loadFailure != null) showLoadError(loadFailure);
                else prepareChapter();
            });
        }).start();
    }

    // ---------------------------------------------------------------- loading
    private void showLoadError(String message) {
        content.removeAllViews();
        LinearLayout card = Ui.cardBox(this);
        TextView heading = Ui.text(this, "The bundled dataset could not be loaded", 15f, Ui.INK);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        card.addView(heading);
        TextView detail = Ui.text(this, message == null ? "Unknown error." : message, 12f, Ui.MUTED);
        detail.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(detail);
        content.addView(card);
    }

    private void prepareChapter() {
        chapter = dataset.chapter(chapterId);
        if (chapter == null) chapter = dataset.chapter(1);
        if (chapter == null) {
            showLoadError("Chapter " + chapterId + " is not in the dataset.");
            return;
        }
        verses.clear();
        verses.addAll(dataset.chapterVerses(chapter.id));
        index = 0;
        if (requestedVerseKey != null && !requestedVerseKey.isEmpty()) {
            for (int position = 0; position < verses.size(); position++) {
                if (requestedVerseKey.equals(verses.get(position).key)) {
                    index = position;
                    break;
                }
            }
        }
        renderContent();
    }

    // --------------------------------------------------------------- rendering
    private void renderContent() {
        content.removeAllViews();

        LinearLayout heading = Ui.row(this);
        TextView title = Ui.text(this, chapter.nameEnglish, 20f, Ui.INK);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.addView(title);
        heading.addView(Ui.spacer(this));
        TextView arabicName = Ui.text(this, chapter.nameArabic, 18f, Ui.MUTED);
        arabicName.setTextDirection(View.TEXT_DIRECTION_RTL);
        heading.addView(arabicName);
        content.addView(heading);
        TextView subhead = Ui.text(this,
                "Verse audio \u00B7 " + chapter.verseCount + " verses in this surah", 12f, Ui.MUTED);
        subhead.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        content.addView(subhead);

        verseHolder = Ui.column(this);
        content.addView(verseHolder);
        renderVerse();

        // Verse-by-verse navigation: functional, deliberately audio-free.
        LinearLayout navigation = Ui.row(this);
        navigation.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        previousButton = Ui.button(this, "\u2190 Previous verse", false);
        previousButton.setOnClickListener(view -> move(-1));
        nextButton = Ui.button(this, "Next verse \u2192", false);
        nextButton.setOnClickListener(view -> move(1));
        positionLabel = Ui.text(this, "", 11f, Ui.MUTED);
        positionLabel.setGravity(Gravity.CENTER);
        navigation.addView(previousButton);
        navigation.addView(positionLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        navigation.addView(nextButton);
        content.addView(navigation);
        updateNavigation();

        content.addView(buildGateBanner());
        content.addView(buildControls());
        content.addView(buildProgress());
        content.addView(buildSourceCard());

        TextView footer = Ui.text(this,
                "Recitation is a rights-gated feature. No audio request is made by this screen, "
                        + "and no recording is streamed, stored or redistributed. When the review is recorded, "
                        + "only the shared AudioGate decision changes.",
                11f, Ui.MUTED);
        footer.setPadding(0, Ui.dp(this, 20), 0, 0);
        content.addView(footer);

        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void renderVerse() {
        verseHolder.removeAllViews();
        if (verses.isEmpty()) {
            verseHolder.addView(Ui.text(this, "This surah has no verses in the dataset.", 13f, Ui.MUTED));
            return;
        }
        Verse verse = verses.get(index);
        LinearLayout card = Ui.cardBox(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));

        LinearLayout head = Ui.row(this);
        TextView reference = Ui.label(this, chapter.nameEnglish + " " + verse.key);
        reference.setTextColor(Ui.GOLD_DEEP);
        head.addView(reference);
        head.addView(Ui.spacer(this));
        head.addView(Ui.chip(this, "GATED", Ui.DANGER, Ui.PAPER, Ui.DANGER));
        card.addView(head);

        TextView arabic = Ui.text(this, verse.arabic, 24f * prefs.textScale(), Ui.INK);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setGravity(Gravity.RIGHT);
        arabic.setLineSpacing(Ui.dp(this, 8), 1.25f);
        arabic.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        card.addView(arabic);

        card.addView(Ui.divider(this));
        card.addView(Ui.label(this, "Translation"));
        String language = prefs.language();
        String translation = dataset.translationText(language, verse.key);
        boolean fallback = false;
        if (translation == null || translation.isEmpty()) {
            translation = dataset.translationText("en", verse.key);
            fallback = translation != null && !translation.isEmpty();
        }
        TextView translated = Ui.text(this,
                translation == null || translation.isEmpty()
                        ? "A reviewed translation is not available for this verse in this build."
                        : translation,
                15f * prefs.textScale(), translation == null || translation.isEmpty() ? Ui.MUTED : Ui.INK);
        translated.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        card.addView(translated);
        if (fallback) {
            card.addView(Ui.text(this,
                    "The English translation is shown because no reviewed translation is loaded for "
                            + language + " in this build.", 11f, Ui.MUTED));
        }

        if (meta != null) {
            TextView indicator = Ui.text(this, meta.indicator(verse.id), 11f, Ui.MUTED);
            indicator.setPadding(0, Ui.dp(this, 12), 0, 0);
            card.addView(indicator);
        }
        verseHolder.addView(card);
    }

    private View buildGateBanner() {
        LinearLayout banner = Ui.cardBox(this);
        banner.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.GOLD, 10, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        banner.setLayoutParams(params);

        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.label(this, "RECITATION RIGHTS PENDING"));
        labelRow.addView(Ui.spacer(this));
        labelRow.addView(Ui.chip(this, playbackAllowed ? "ENABLED" : "BLOCKED",
                playbackAllowed ? Ui.GREEN : Ui.DANGER,
                Ui.PAPER, playbackAllowed ? Ui.GREEN : Ui.DANGER));
        banner.addView(labelRow);

        TextView reason = Ui.text(this, AudioGate.pendingReason(), 13f, Ui.INK);
        reason.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        banner.addView(reason);

        banner.addView(kvRow("Rights review recorded", AudioGate.rightsReviewRecorded() ? "Yes" : "No"));
        banner.addView(kvRow("Playback allowed in this build", playbackAllowed ? "Yes" : "No"));
        return banner;
    }

    private View buildControls() {
        LinearLayout wrap = Ui.column(this);
        wrap.setPadding(0, Ui.dp(this, 16), 0, 0);

        wrap.addView(Ui.label(this, "Playback controls"));
        LinearLayout controls = Ui.row(this);
        controls.setPadding(0, Ui.dp(this, 8), 0, 0);
        Button play = Ui.button(this, "\u25B6 Play", true);
        Button pause = Ui.button(this, "\u23F8 Pause", false);
        Button replay = Ui.button(this, "\u21BA Replay", false);
        for (Button button : new Button[]{play, pause, replay}) {
            button.setEnabled(playbackAllowed);
            button.setOnClickListener(view ->
                    Toast.makeText(this, "Recitation rights pending", Toast.LENGTH_SHORT).show());
        }
        controls.addView(play);
        controls.addView(pause);
        controls.addView(replay);
        wrap.addView(controls);

        TextView note = Ui.text(this,
                "The controls are shown for layout review only. They are disabled while the gate is closed "
                        + "and never start an audio request.", 11f, Ui.MUTED);
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        wrap.addView(note);
        return wrap;
    }

    private View buildProgress() {
        LinearLayout wrap = Ui.column(this);
        wrap.setPadding(0, Ui.dp(this, 16), 0, 0);
        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.label(this, "Playback progress"));
        labelRow.addView(Ui.spacer(this));
        labelRow.addView(Ui.text(this, "0:00 / 0:00", 11f, Ui.MUTED));
        wrap.addView(labelRow);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(Ui.rounded(Ui.LINE, 0, 3, this));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        trackParams.topMargin = Ui.dp(this, 8);
        track.setLayoutParams(trackParams);
        // Playback is gated, so the gold fill is zero-width: the track stays empty.
        View fill = new View(this);
        fill.setBackground(Ui.rounded(Ui.GOLD, 0, 3, this));
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        View remainder = new View(this);
        track.addView(remainder, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        wrap.addView(track);

        TextView state = Ui.text(this, "Playback disabled \u2014 rights pending", 12f, Ui.DANGER);
        state.setPadding(0, Ui.dp(this, 8), 0, 0);
        wrap.addView(state);
        return wrap;
    }

    private View buildSourceCard() {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 18);
        card.setLayoutParams(params);
        card.addView(Ui.label(this, "Source & reciter"));
        card.addView(kvRow("Source", "Quran.com"));
        card.addView(kvRow("Reciter", "Selection pending rights review"));
        card.addView(kvRow("Rights status", "Unverified \u2014 review not recorded"));
        TextView note = Ui.text(this,
                "Candidate recordings are referenced by the public Quran.com source only after the "
                        + "rights review is recorded. No recording is downloaded in this build.",
                11f, Ui.MUTED);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(note);
        return card;
    }

    private View kvRow(String key, String value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        TextView label = Ui.text(this, key, 12f, Ui.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView content = Ui.text(this, value, 12f, Ui.INK);
        content.setGravity(Gravity.RIGHT);
        row.addView(content);
        return row;
    }

    private void updateNavigation() {
        if (positionLabel == null) return;
        positionLabel.setText("Verse " + (index + 1) + " of " + verses.size());
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(index < verses.size() - 1);
    }

    private void move(int delta) {
        int target = index + delta;
        if (target < 0 || target >= verses.size()) return;
        index = target;
        renderVerse();
        updateNavigation();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }
}
