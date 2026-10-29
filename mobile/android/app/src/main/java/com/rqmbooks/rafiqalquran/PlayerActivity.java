package com.rqmbooks.rafiqalquran;

import android.graphics.Color;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.AudioGate;
import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.Verse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verse-by-verse Quran.com recitation player.
 *
 * <p>The recitation rights review is recorded (see {@link AudioGate}); audio is
 * streamed from the public Quran.com CDN over HTTPS with the reciter name and
 * "Source: Quran.com" attribution shown. Nothing is downloaded, cached or stored
 * on the device, and no audio is generated or altered by the app.
 */
public final class PlayerActivity extends Activity {
    /** Chapter id extra for {@link #intent(Context, int, String)}. */
    public static final String EXTRA_CHAPTER = "chapter";
    /** Verse key ("2:255") extra for {@link #intent(Context, int, String)}. */
    public static final String EXTRA_VERSE = "verse";
    /** Opens the player with continuous car-mode playback across surahs. */
    public static final String EXTRA_CAR_MODE = "carMode";

    /** Verified Quran.com CDN reciters: display name, path template (chapter, verse). */
    public static final String[][] RECITERS = {
            {"Mishary Rashid Alafasy", "Alafasy/mp3/%03d%03d.mp3"},
            {"Abdul Basit Abdus Samad (Murattal)", "AbdulBaset/Murattal/mp3/%03d%03d.mp3"},
            {"Muhammad Siddiq Al-Minshawi (Murattal)", "Minshawi/Murattal/mp3/%03d%03d.mp3"},
    };

    private static final String CDN = "https://verses.quran.com/";

    private Prefs prefs;
    private Dataset dataset;
    private QuranMeta meta;
    private Chapter chapter;
    private final List<Verse> verses = new ArrayList<>();
    private int chapterId = 1;
    private String requestedVerseKey;
    private int index;

    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout verseHolder;
    private TextView positionLabel;
    private Button previousButton;
    private Button nextButton;

    private TextView reciterLabel;
    private TextView statusLabel;
    private TextView timeLabel;
    private SeekBar seekBar;
    private Button playButton;

    private MediaPlayer player;
    private Handler handler;
    private boolean prepared;
    private boolean tickerRunning;
    private int loadGeneration;
    private boolean carMode;
    private boolean continuous;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private boolean audioFocusHeld;
    private TextView carStatusLabel;

    /**
     * Opens the verse player at a chapter and (optionally) a verse key.
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

    /** Car mode: continuous recitation across the whole mushaf. */
    public static Intent carIntent(Context context, int chapterId, String verseKey) {
        Intent intent = intent(context, chapterId, verseKey);
        intent.putExtra(EXTRA_CAR_MODE, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        handler = new Handler(Looper.getMainLooper());
        chapterId = getIntent().getIntExtra(EXTRA_CHAPTER, 1);
        requestedVerseKey = getIntent().getStringExtra(EXTRA_VERSE);
        carMode = getIntent().getBooleanExtra(EXTRA_CAR_MODE, false);
        continuous = carMode || prefs.continuousListen();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setupMediaSession();

        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.bar(this, "Listen", "Quran.com  \u00B7  recitation", this::finish);
        bar.addView(Ui.themeToggle(this));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 28));
        content.setBackgroundColor(Color.TRANSPARENT);
        content.addView(Ui.text(this, "Loading the verse\u2026", 13f, Ui.MUTED));
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
                    // The reference card still renders without a translation row.
                }
                if (!"en".equals(prefs.language())) {
                    try {
                        Assets.ensureLanguage(this, loaded, "en");
                    } catch (Exception ignored) {
                        // English is only the preview fallback.
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

    @Override
    protected void onPause() {
        // Keep the recitation playing when the screen locks or the user switches
        // to navigation. The activity only releases audio when it is closed.
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        abandonAudioFocus();
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
        TextView title = Ui.display(this, chapter.nameEnglish, 28f);
        heading.addView(title);
        heading.addView(Ui.spacer(this));
        TextView arabicName = Ui.arabic(this, chapter.nameArabic, 22f, Ui.GOLD_DEEP);
        arabicName.setTextDirection(View.TEXT_DIRECTION_RTL);
        heading.addView(arabicName);
        content.addView(heading);
        TextView subhead = Ui.text(this,
                "Recitation \u00B7 " + chapter.verseCount + " verses in this surah", 13.5f, Ui.MUTED);
        subhead.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        content.addView(subhead);

        if (carMode) content.addView(buildCarCard());

        verseHolder = Ui.column(this);
        content.addView(verseHolder);
        renderVerse();

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

        content.addView(buildPlayerCard());
        content.addView(buildSourceCard());

        TextView footer = Ui.text(this,
                "Audio streams from Quran.com's public CDN over HTTPS and is not stored on this device. "
                        + "Recitation rights review recorded 2026-10-21. The app never modifies or generates "
                        + "recitation content.",
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
        head.addView(Ui.chip(this, "STREAMED", Ui.GREEN, Ui.GREEN_SOFT, Ui.GREEN));
        card.addView(head);

        TextView arabic = Ui.arabic(this, verse.arabic, 30f * prefs.textScale(), Ui.INK);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setGravity(Gravity.RIGHT);
        arabic.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
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
                17f * prefs.textScale(), translation == null || translation.isEmpty() ? Ui.MUTED : Ui.INK);
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

    private View buildCarCard() {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = Ui.withTop(this, 4);
        card.setLayoutParams(params);
        card.addView(Ui.label(this, "CAR MODE · CONTINUOUS"));
        TextView headline = Ui.text(this, "The whole journey, surah after surah.", 18f, Ui.INK);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        card.addView(headline);
        carStatusLabel = Ui.text(this, bluetoothStatus(), 12f, Ui.MUTED);
        carStatusLabel.setPadding(0, 0, 0, Ui.dp(this, 8));
        card.addView(carStatusLabel);
        TextView note = Ui.text(this,
                "Audio uses the phone's media route, so a paired car Bluetooth system plays it automatically. "
                        + "Keep this screen open or lock the phone; playback continues through the surah and then the next surah in mushaf order.",
                11.5f, Ui.MUTED);
        card.addView(note);
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        Button nextSurah = Ui.button(this, "Next surah \u2192", false);
        nextSurah.setOnClickListener(view -> playNextChapter());
        actions.addView(nextSurah);
        card.addView(actions);
        return card;
    }

    private String bluetoothStatus() {
        try {
            if (audioManager == null) return "Connect your car Bluetooth in Android settings.";
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "Allow Bluetooth access to detect your car, or connect it in Android settings.";
            }
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    CharSequence name = device.getProductName();
                    return "Connected audio: " + (name == null ? "car / Bluetooth" : name) + ". Playback will route there automatically.";
                }
            }
        } catch (Exception ignored) {
            // permission or device enumeration can fail on some OEMs
        }
        return "No car audio detected yet. Pair your car in Android Bluetooth settings; playback will route there automatically.";
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "RafiqAlQuran");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if (!isPlaying()) togglePlay(); }
            @Override public void onPause() { if (isPlaying()) togglePlay(); }
            @Override public void onSkipToNext() { playNextChapter(); }
            @Override public void onSkipToPrevious() { previousSurah(); }
        });
        mediaSession.setActive(true);
        updateMediaState();
    }

    private boolean isPlaying() {
        try { return player != null && prepared && player.isPlaying(); } catch (Exception ignored) { return false; }
    }

    private void updateMediaState() {
        if (mediaSession == null) return;
        int state = isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, player == null ? 0 : 0, 1f)
                .build());
    }

    private void requestAudioFocus() {
        if (audioManager == null || audioFocusHeld) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(change -> { if (change <= 0) pauseForFocusLoss(); })
                        .build();
                audioManager.requestAudioFocus(request);
            } else {
                audioManager.requestAudioFocus(change -> { if (change <= 0) pauseForFocusLoss(); },
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
            audioFocusHeld = true;
        } catch (Exception ignored) { }
    }

    private void pauseForFocusLoss() {
        runOnUiThread(() -> {
            try {
                if (player != null && player.isPlaying()) player.pause();
            } catch (Exception ignored) { }
            updatePlayButton();
            updateMediaState();
            setStatus("Paused because another app started playing.", false);
        });
    }

    private void abandonAudioFocus() {
        if (audioManager == null || !audioFocusHeld) return;
        try { audioManager.abandonAudioFocus(null); } catch (Exception ignored) { }
        audioFocusHeld = false;
    }

    private void playNextChapter() {
        if (chapterId >= 114) {
            stopPlayback(true);
            setStatus("Completed the recitation through Surah An-Nas.", false);
            return;
        }
        chapterId++;
        requestedVerseKey = null;
        prepareChapter();
        prepareAndPlay();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void previousSurah() {
        if (chapterId <= 1) { move(-1); return; }
        chapterId--;
        requestedVerseKey = null;
        prepareChapter();
    }

    // ----------------------------------------------------------------- player
    private View buildPlayerCard() {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        card.setLayoutParams(params);

        card.addView(Ui.label(this, "Reciter"));
        LinearLayout reciterRow = Ui.row(this);
        reciterRow.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        reciterLabel = Ui.text(this, reciterName(), 15f, Ui.INK);
        reciterLabel.setTypeface(Typeface.DEFAULT_BOLD);
        reciterRow.addView(reciterLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        reciterRow.addView(Ui.action(this, "Change", this::showReciterDialog));
        reciterRow.setOnClickListener(view -> showReciterDialog());
        card.addView(reciterRow);
        card.addView(Ui.text(this, AudioGate.attribution(), 11f, Ui.MUTED));

        statusLabel = Ui.text(this, "Tap play to stream this verse from Quran.com.", 12f, Ui.MUTED);
        statusLabel.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(statusLabel);

        LinearLayout progressRow = Ui.row(this);
        progressRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && timeLabel != null) {
                    timeLabel.setText(formatTime(durationMs() * progress / 1000) + " / " + formatTime(durationMs()));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && prepared) {
                    try {
                        player.seekTo(durationMs() * bar.getProgress() / 1000);
                    } catch (Exception ignored) {
                        // seeking is best effort
                    }
                }
            }
        });
        progressRow.addView(seekBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timeLabel = Ui.text(this, "0:00 / 0:00", 11f, Ui.MUTED);
        timeLabel.setPadding(Ui.dp(this, 10), 0, 0, 0);
        progressRow.addView(timeLabel);
        card.addView(progressRow);

        LinearLayout controls = Ui.row(this);
        controls.setPadding(0, Ui.dp(this, 10), 0, 0);
        playButton = Ui.button(this, "\u25B6 Play", true);
        playButton.setOnClickListener(view -> togglePlay());
        Button replay = Ui.button(this, "\u21BA Replay", false);
        replay.setOnClickListener(view -> replay());
        Button stop = Ui.button(this, "\u23F9 Stop", false);
        stop.setOnClickListener(view -> stopPlayback(true));
        controls.addView(playButton);
        controls.addView(replay);
        controls.addView(stop);
        card.addView(controls);

        TextView hint = Ui.text(this,
                "Playback needs an internet connection. Audio is streamed verse by verse from "
                        + "Quran.com and is never saved on this device.", 11f, Ui.MUTED);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(hint);
        return card;
    }

    private View buildSourceCard() {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        card.setLayoutParams(params);
        card.addView(Ui.label(this, "Source & rights"));
        card.addView(kvRow("Audio source", "Quran.com"));
        card.addView(kvRow("Reciter", reciterName()));
        card.addView(kvRow("Rights status", "Review recorded 2026-10-21"));
        TextView note = Ui.text(this,
                "Verse audio is streamed from Quran.com's public CDN. The Arabic text and translation on "
                        + "this screen still come from the app's validated dataset, not from the audio service.",
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
        stopPlayback(false);
        index = target;
        renderVerse();
        updateNavigation();
        if (reciterLabel != null) reciterLabel.setText(reciterName());
        setStatus("Ready to play " + chapter.nameEnglish + " " + verses.get(index).key + ".", false);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void showReciterDialog() {
        final String[] names = new String[RECITERS.length];
        for (int i = 0; i < RECITERS.length; i++) names[i] = RECITERS[i][0];
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Reciter")
                .setSingleChoiceItems(names, currentReciterIndex(), (dialog, which) -> {
                    prefs.setReciterIndex(which);
                    dialog.dismiss();
                    stopPlayback(false);
                    if (reciterLabel != null) reciterLabel.setText(reciterName());
                    setStatus("Reciter set to " + reciterName() + ". Tap play to listen.", false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int currentReciterIndex() {
        int value = prefs.reciterIndex();
        return value >= 0 && value < RECITERS.length ? value : 0;
    }

    private String reciterName() {
        return RECITERS[currentReciterIndex()][0];
    }

    private String verseUrl() {
        Verse verse = verses.get(index);
        String template = RECITERS[currentReciterIndex()][1];
        return CDN + String.format(Locale.US, template, verse.chapterId, verse.number);
    }

    private void togglePlay() {
        if (prepared && player != null) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    updatePlayButton();
                    updateMediaState();
                    setStatus("Paused.", false);
                } else {
                    player.start();
                    updatePlayButton();
                    updateMediaState();
                    setStatus("Playing \u2014 Source: Quran.com", false);
                    startTicker();
                }
                return;
            } catch (Exception error) {
                releasePlayer();
            }
        }
        prepareAndPlay();
    }

    private void replay() {
        if (player != null && prepared) {
            try {
                player.seekTo(0);
                player.start();
                updatePlayButton();
                startTicker();
                setStatus("Replaying \u2014 Source: Quran.com", false);
                return;
            } catch (Exception ignored) {
                // fall through to a fresh stream
            }
        }
        prepareAndPlay();
    }

    private void prepareAndPlay() {
        if (verses.isEmpty()) return;
        releasePlayer();
        requestAudioFocus();
        final int generation = ++loadGeneration;
        final String url = verseUrl();
        setStatus("Buffering from Quran.com\u2026", false);
        playButton.setEnabled(false);
        playButton.setText("\u2026 Buffering");
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setOnPreparedListener(ready -> {
                if (generation != loadGeneration) return;
                prepared = true;
                playButton.setEnabled(true);
                try {
                    ready.start();
                } catch (Exception ignored) {
                    // start failure is handled by the error listener path below
                }
                updatePlayButton();
                updateMediaState();
                startTicker();
                setStatus("Playing \u2014 Source: Quran.com", false);
            });
            mediaPlayer.setOnCompletionListener(finished -> {
                if (generation != loadGeneration) return;
                stopTicker();
                prepared = false;
                if (index < verses.size() - 1) {
                    index++;
                    renderVerse();
                    updateNavigation();
                    if (reciterLabel != null) reciterLabel.setText(reciterName());
                    prepareAndPlay();
                } else if (continuous && chapterId < 114) {
                    playNextChapter();
                } else {
                    playButton.setText("\u25B6 Play");
                    playButton.setEnabled(true);
                    updateMediaState();
                    setStatus(continuous ? "Completed the recitation through Surah An-Nas." : "Finished reciting this surah.", false);
                    seekBar.setProgress(1000);
                }
            });
            mediaPlayer.setOnErrorListener((failed, what, extra) -> {
                if (generation != loadGeneration) return true;
                stopTicker();
                prepared = false;
                playButton.setEnabled(true);
                playButton.setText("\u25B6 Play");
                setStatus("Could not play this verse. Check your internet connection and try again.", true);
                releasePlayer();
                return true;
            });
            mediaPlayer.setDataSource(url);
            player = mediaPlayer;
            mediaPlayer.prepareAsync();
        } catch (Exception error) {
            prepared = false;
            playButton.setEnabled(true);
            playButton.setText("\u25B6 Play");
            setStatus("Could not start audio: " + error.getMessage(), true);
            releasePlayer();
        }
    }

    private void stopPlayback(boolean userRequested) {
        if (player != null) {
            try {
                if (player.isPlaying()) player.pause();
            } catch (Exception ignored) {
                // best effort
            }
        }
        seekBar.setProgress(0);
        if (timeLabel != null) timeLabel.setText("0:00 / 0:00");
        if (userRequested) {
            releasePlayer();
            setStatus("Stopped.", false);
        }
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (playButton == null) return;
        boolean playing = false;
        try {
            playing = player != null && prepared && player.isPlaying();
        } catch (Exception ignored) {
            // treat as not playing
        }
        playButton.setText(playing ? "\u23F8 Pause" : "\u25B6 Play");
        playButton.setEnabled(true);
    }

    private void setStatus(String message, boolean error) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setTextColor(error ? Ui.DANGER : Ui.MUTED);
        }
    }

    private int durationMs() {
        try {
            if (player != null && prepared) return Math.max(1, player.getDuration());
        } catch (Exception ignored) {
            // no duration yet
        }
        return 1;
    }

    private void startTicker() {
        if (tickerRunning) return;
        tickerRunning = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!tickerRunning) return;
                try {
                    if (player != null && prepared) {
                        int position = player.getCurrentPosition();
                        int duration = durationMs();
                        seekBar.setProgress(Math.min(1000, position * 1000 / duration));
                        timeLabel.setText(formatTime(position) + " / " + formatTime(duration));
                    }
                } catch (Exception ignored) {
                    // stop ticking on a dead player
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void stopTicker() {
        tickerRunning = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    private void releasePlayer() {
        stopTicker();
        abandonAudioFocus();
        updateMediaState();
        loadGeneration++;
        prepared = false;
        if (player != null) {
            try {
                player.reset();
            } catch (Exception ignored) {
                // already released
            }
            try {
                player.release();
            } catch (Exception ignored) {
                // already released
            }
            player = null;
        }
    }

    private static String formatTime(int millis) {
        if (millis <= 0) return "0:00";
        int totalSeconds = millis / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
