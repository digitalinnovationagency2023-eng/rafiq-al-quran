package com.rqmbooks.rafiqalquran;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rqmbooks.rafiqalquran.logic.Answer;
import com.rqmbooks.rafiqalquran.logic.AnswerComposer;
import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Conversation;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.Facts;
import com.rqmbooks.rafiqalquran.logic.Language;
import com.rqmbooks.rafiqalquran.logic.Passage;
import com.rqmbooks.rafiqalquran.logic.Verse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The conversational home screen.
 *
 * <p>Quran answers always come from the bundled dataset. A small deterministic
 * conversation layer answers greetings and small talk without religious content,
 * and an optional server-side AI service (only when the operator has configured a
 * free provider) can add natural conversation and read attached screenshots. The
 * AI never supplies verses or interpretation.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "RafiqVoice";
    public static final String EXTRA_ASK = "ask";
    private static final int MIC_REQUEST = 41;
    private static final int ATTACH_REQUEST = 42;
    private static final int REQUEST_HISTORY = 43;
    private static final int VOICE_FALLBACK_REQUEST = 44;
    // A grounded summary is a second AI request per question, so the device cap is
    // slightly higher than the old single-call allowance.
    private static final int AI_DAILY_LIMIT = 10;

    private Dataset dataset;
    private Prefs prefs;
    private String conversationId = java.util.UUID.randomUUID().toString();
    private StudyStore store;
    private final List<HistoryCodec.Turn> turns = new ArrayList<>();
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private Runnable busyTicker;
    private TextView languageButton;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsFailed;
    private Answer pendingSpeech;
    private boolean listening;
    private Answer speakingAnswer;
    private boolean handsFree;
    private boolean systemVoiceAttempted;
    private TextView voiceChatButton;

    private LinearLayout attachmentRow;
    private TextView attachmentLabel;
    private TextView attachButton;
    private Attachments.File currentAttachment;
    private boolean aiAvailable;
    private boolean operatorUnlocked;
    private int titleTapCount;
    private long titleTapWindowStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        handsFree = prefs.handsFree();
        store = new StudyStore(this);
        View root = buildRoot();
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (prefs.voiceEnabled()) ensureTts();
        new Thread(() -> {
            try {
                final Dataset loaded = Assets.dataset(this);
                Assets.ensureLanguage(this, loaded, "en");
                // A saved non-English interface language must also be loaded on a cold
                // start, otherwise answers fall back to English while the UI says e.g. "ur".
                if (!"en".equals(prefs.language())) Assets.ensureLanguage(this, loaded, prefs.language());
                runOnUiThread(() -> {
                    dataset = loaded;
                    loadHistory();
                    status.setText("");
                    handleAskIntent();
                });
            } catch (final Exception error) {
                runOnUiThread(() -> status.setText("Dataset error: " + error.getMessage()));
            }
        }).start();
        new Thread(() -> {
            boolean available = false;
            try {
                available = Api.assistantStatus(getResources()).optBoolean("enabled", false);
            } catch (Exception ignored) {
                // AI is optional; offline builds simply use local conversation.
            }
            final boolean result = available;
            runOnUiThread(() -> aiAvailable = result);
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the screen must not keep reading aloud or recording.
        if (handsFree) {
            stopHandsFree(false);
        } else {
            Voice.stop(tts);
            speakingAnswer = null;
            pendingSpeech = null;
            stopListening();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBusyTicker();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
        pendingSpeech = null;
        speakingAnswer = null;
        saveHistory();
    }

    /** Saved-search deep link: run the query as soon as the dataset is ready. */
    private void handleAskIntent() {
        String ask = getIntent().getStringExtra(EXTRA_ASK);
        if (ask == null || ask.trim().isEmpty()) return;
        getIntent().removeExtra(EXTRA_ASK);
        input.setText(ask);
        submit();
    }

    // ---------------------------------------------------------------- layout
    private View buildRoot() {
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.pageBackground(this));

        // Editorial top bar: menu, wordmark, new conversation, theme.
        LinearLayout topbar = Ui.row(this);
        topbar.setBackground(Ui.headerBackground(this));
        topbar.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        TextView menuIcon = Ui.text(this, "\u2630", 30f, Ui.ON_BAR);
        menuIcon.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        menuIcon.setContentDescription("Menu");
        menuIcon.setOnClickListener(this::showMenu);
        topbar.addView(menuIcon);

        LinearLayout brand = Ui.column(this);
        TextView heading = Ui.display(this, "Rafiq", 23f);
        heading.setTextColor(Ui.ON_BAR);
        brand.addView(heading);
        TextView brandLine = Ui.text(this, "QUR\u2019AN COMPANION", 8.5f, Ui.GOLD_BRIGHT);
        brandLine.setLetterSpacing(0.11f);
        brandLine.setSingleLine(true);
        brandLine.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brandLine.setPadding(0, Ui.dp(this, 1), 0, 0);
        brand.addView(brandLine);
        topbar.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView newChat = Ui.text(this, "\uFF0B", 30f, Ui.ON_BAR);
        newChat.setPadding(Ui.dp(this, 9), Ui.dp(this, 2), Ui.dp(this, 9), Ui.dp(this, 2));
        newChat.setContentDescription("New conversation");
        newChat.setOnClickListener(view -> newConversation());
        topbar.addView(newChat);
        topbar.addView(Ui.themeToggle(this));
        attachOperatorUnlock(topbar);

        messages = Ui.column(this);
        messages.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 18));

        status = Ui.text(this, "", 12.5f, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, Ui.dp(this, 4));

        LinearLayout composer = Ui.column(this);
        composer.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 12));
        composer.setBackgroundColor(Color.TRANSPARENT);

        attachmentRow = Ui.row(this);
        attachmentRow.setBackground(Ui.rounded(Ui.GREEN_SOFT, 0, 12, this));
        attachmentRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        attachmentRow.setVisibility(View.GONE);
        attachmentLabel = Ui.text(this, "", 12f, Ui.INK);
        attachmentRow.addView(attachmentLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        attachmentRow.addView(Ui.textAction(this, "Remove", () -> {
            currentAttachment = null;
            updateAttachmentRow();
        }));
        composer.addView(attachmentRow);

        LinearLayout pill = Ui.row(this);
        pill.setBackground(Ui.rounded(Ui.CARD, Ui.LINE, 30, this));
        pill.setElevation(Ui.dp(this, 4));
        pill.setPadding(Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6));
        TextView attach = Ui.text(this, "\uD83D\uDCCE", 19f, Ui.MUTED);
        attach.setGravity(Gravity.CENTER);
        attach.setContentDescription("Attach an image");
        attach.setOnClickListener(view -> pickAttachment());
        pill.addView(attach, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 46)));
        input = new EditText(this);
        input.setHint("Ask Qur\u2019an\u2026");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(null);
        input.setPadding(Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11));
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        pill.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mic = Ui.text(this, "\uD83C\uDFA4", 21f, Ui.MUTED);
        mic.setGravity(Gravity.CENTER);
        mic.setContentDescription("Speak your question");
        mic.setOnClickListener(view -> startListening());
        pill.addView(mic, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 46)));
        pill.addView(Ui.circle(this, "\u2191", Ui.GREEN, Color.WHITE, 46, () -> submit()));
        composer.addView(pill);

        root.addView(topbar);
        View rule = new View(this);
        rule.setBackgroundColor(Ui.LINE);
        rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        root.addView(rule);

        LinearLayout scrollBody = Ui.column(this);
        scrollBody.addView(messages);
        scrollBody.addView(status);
        ScrollView outer = new ScrollView(this);
        outer.setBackgroundColor(Ui.PAPER);
        outer.setFillViewport(true);
        outer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(scrollBody);
        root.addView(outer);
        root.addView(composer);
        renderEmptyState();
        scroll = outer;
        updateVoiceChatButton();
        return root;
    }

    private void updateVoiceChatButton() {
        if (voiceChatButton == null) return;
        voiceChatButton.setTextColor(handsFree ? Ui.GREEN : Ui.MUTED);
        voiceChatButton.setContentDescription(handsFree
                ? "End hands-free voice chat" : "Start hands-free voice chat");
    }

    private String languageButtonLabel() {
        return prefs.language() + "  \u25BE";
    }

    /** Large, readable interface-language chooser (replaces the tiny spinner). */
    private void showLanguageDialog() {
        final List<Language> languages = Language.ALL;
        final String[] labels = new String[languages.size()];
        int selected = 0;
        String current = prefs.language();
        for (int i = 0; i < languages.size(); i++) {
            Language language = languages.get(i);
            labels[i] = language.englishName + "  \u2014  " + language.nativeName + "  (" + language.code + ")";
            if (language.code.equals(current)) selected = i;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert);
        builder.setTitle("Interface language");
        builder.setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss();
            applyLanguage(languages.get(which).code);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void applyLanguage(String code) {
        prefs.setLanguage(code);
        if (languageButton != null) languageButton.setText(languageButtonLabel());
        updatePlaceholder();
        new Thread(() -> {
            if (dataset != null) {
                try {
                    Assets.ensureLanguage(this, dataset, code);
                } catch (Exception ignored) {
                    // English remains the fallback translation.
                }
            }
        }).start();
        toast("Interface language: " + Language.byCode(code).englishName);
    }

    private void updatePlaceholder() {
        boolean rtl = Language.byCode(prefs.language()).rtl;
        input.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    private void changeTextScale(float delta) {
        float value = Math.max(0.9f, Math.min(1.5f, prefs.textScale() + delta));
        prefs.setTextScale(value);
        rebuildMessages();
    }

    // ------------------------------------------------------------------ menu
    private void showMenu(View anchor) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout drawer = Ui.column(this);
        drawer.setBackground(Ui.pageBackground(this));
        drawer.setPadding(0, 0, 0, Ui.dp(this, 24));

        // Compact branded header instead of an empty top gap.
        FrameLayout header = new FrameLayout(this);
        header.setBackground(Ui.heroBackground(this));
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.calligraphy);
        art.setAlpha(0.24f);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 220), Ui.dp(this, 155), Gravity.TOP | Gravity.END);
        artParams.rightMargin = Ui.dp(this, 2);
        artParams.topMargin = Ui.dp(this, 2);
        header.addView(art, artParams);

        LinearLayout headerText = Ui.column(this);
        headerText.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20));
        TextView eyebrow = Ui.text(this, "QUR\u2019AN COMPANION", 10.5f, Ui.SUN);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerText.addView(eyebrow);
        TextView title = Ui.display(this, "Rafiq", 32f);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, Ui.dp(this, 4), 0, 0);
        headerText.addView(title);
        TextView tagline = Ui.text(this, "Ask. Read. Listen.", 13.5f, Color.parseColor("#EAF8FF"));
        tagline.setPadding(0, Ui.dp(this, 4), 0, 0);
        headerText.addView(tagline);
        header.addView(headerText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        drawer.addView(header);

        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));

        body.addView(menuSection("EXPLORE"));
        body.addView(drawerRow("\u266B", "Listen to Qur\u2019an", "Car & home", Ui.CORAL, () -> {
            dialog.dismiss();
            startActivity(new Intent(this, ListenActivity.class));
        }));
        body.addView(drawerRow("\u25A4", "Read the Qur\u2019an", "114 surahs \u00B7 offline", Ui.TEAL, () -> {
            dialog.dismiss();
            startActivity(new Intent(this, SurahActivity.class));
        }));
        body.addView(drawerRow("\uFF0B", "New chat", "Fresh conversation", Ui.PURPLE, () -> {
            dialog.dismiss();
            newConversation();
        }));

        body.addView(menuSection("YOUR SPACE"));
        body.addView(drawerRow("\u25F7", "History", "Recent history", Ui.AMBER, () -> {
            dialog.dismiss();
            openHistory();
        }));
        body.addView(drawerRow("\u25C9", "Voice chat",
                handsFree ? "Hands-free \u00B7 On" : "Hands-free \u00B7 Off", Ui.SKY, () -> {
                    dialog.dismiss();
                    toggleHandsFree();
                }));
        body.addView(drawerRow("\u2699", "Settings", "Language & privacy", Ui.LAVENDER, () -> {
            dialog.dismiss();
            showSettings();
        }));

        body.addView(menuSection("ABOUT"));
        body.addView(drawerRow("\u21BB", "Update app", "Check for updates", Ui.MINT, () -> {
            dialog.dismiss();
            checkForUpdates();
        }));
        body.addView(drawerRow("\u24D8", "About & legal", "Sources & licences", Ui.PINK, () -> {
            dialog.dismiss();
            showLegal();
        }));

        TextView footer = Ui.text(this,
                "Rafiq Al-Quran  \u00B7  v" + installedVersionName(), 11f, Ui.MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(this, 16), 0, 0);
        body.addView(footer);
        drawer.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(drawer);
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.START);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            window.setAttributes(params);
            int drawerWidth = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    Ui.dp(this, 420));
            window.setLayout(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private TextView menuSection(String label) {
        TextView view = Ui.label(this, label);
        view.setTextColor(Ui.GOLD_DEEP);
        view.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 6));
        return view;
    }

    private LinearLayout drawerRow(String glyph, String label, String detail, Runnable action) {
        return drawerRow(glyph, label, detail, Ui.TEAL, action);
    }
    private LinearLayout drawerRow(String glyph, String label, String detail, int accent, Runnable action) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = Ui.dp(this, 5);
        row.setLayoutParams(rowParams);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        row.setBackground(Ui.rounded(Ui.CARD, Ui.alpha(Ui.LINE, 220), 16, this));
        row.setElevation(Ui.dp(this, 1));
        row.setClickable(true);

        TextView icon = Ui.text(this, glyph, 20f, Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(accent, 0, 20, this));
        icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        row.addView(icon);

        LinearLayout labels = Ui.column(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(this, 12);
        TextView main = Ui.text(this, label, 16f, Ui.INK);
        main.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        main.setSingleLine(true);
        main.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(main);
        if (detail != null && !detail.isEmpty()) {
            TextView sub = Ui.text(this, detail, 12f, Ui.MUTED);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, Ui.dp(this, 2), 0, 0);
            labels.addView(sub);
        }
        row.addView(labels, labelParams);
        TextView chevron = Ui.text(this, "\u203A", 24f, Ui.alpha(accent, 180));
        chevron.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 2), 0);
        row.addView(chevron);
        row.setOnClickListener(view -> action.run());
        return row;
    }
    private void showSettings() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = Ui.column(this);
        sheet.setBackgroundColor(Ui.PAPER);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 44));

        TextView title = Ui.text(this, "Settings", 24f, Ui.INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sheet.addView(title);

        settingsSection(sheet, "Assistant");
        settingsRow(sheet, "Language", Language.byCode(prefs.language()).englishName, dialog,
                this::showLanguageDialog);
        settingsRow(sheet, "AI answers", prefs.aiAssistEnabled() ? "On" : "Off", dialog, () -> {
            prefs.setAiAssistEnabled(!prefs.aiAssistEnabled());
            toast(prefs.aiAssistEnabled()
                    ? (aiAvailable ? "AI answers on" : "AI answers on \u2014 server AI is not configured yet; local replies are used")
                    : "AI answers off \u2014 Quran verses and index facts only");
        });
        settingsRow(sheet, "Voice chat", handsFree ? "On" : "Off", dialog, this::toggleHandsFree);

        settingsSection(sheet, "Voice & reading");
        settingsRow(sheet, "Voice features", prefs.voiceEnabled() ? "On" : "Off", dialog, () -> {
            prefs.setVoiceEnabled(!prefs.voiceEnabled());
            if (prefs.voiceEnabled()) ensureTts();
            else if (tts != null) Voice.stop(tts);
            toast(prefs.voiceEnabled() ? "Voice features on" : "Voice features off");
        });
        settingsRow(sheet, "Text size", Math.round(prefs.textScale() * 100) + "%", dialog, () -> toast("Use the A\u2212 and A+ rows below."));
        settingsRow(sheet, "Text size smaller", "A\u2212", dialog, () -> changeTextScale(-0.05f));
        settingsRow(sheet, "Text size larger", "A+", dialog, () -> changeTextScale(0.05f));
        settingsRow(sheet, "Theme", Ui.isDark() ? "Dark" : "Light", dialog, () -> Ui.toggleTheme(this));
        settingsRow(sheet, "Study tools", "Bookmarks", dialog,
                () -> startActivity(new Intent(this, StudyActivity.class)));
        settingsRow(sheet, "Browse surahs", "114 surahs", dialog,
                () -> startActivity(new Intent(this, SurahActivity.class)));

        settingsSection(sheet, "Support & legal");
        settingsRow(sheet, "Sources & licences", null, dialog, () -> openPage("sources"));
        settingsRow(sheet, "Report an issue", null, dialog, () -> openPage("report"));
        settingsRow(sheet, "Privacy", "Data handling", dialog, () -> openPage("privacy"));
        settingsRow(sheet, "Support the app", "Donation", dialog, () -> openPage("support"));
        settingsRow(sheet, "Check for updates", "v" + installedVersionName(), dialog, this::checkForUpdates);
        if (operatorUnlocked) {
            settingsRow(sheet, "Operator admin", "Restricted", dialog,
                    () -> startActivity(new Intent(this, AdminActivity.class)));
        }

        android.widget.Button done = Ui.button(this, "Done", true);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneParams.topMargin = Ui.dp(this, 18);
        done.setLayoutParams(doneParams);
        done.setOnClickListener(view -> dialog.dismiss());
        sheet.addView(done);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(sheet);
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            window.setAttributes(params);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void settingsSection(LinearLayout sheet, String label) {
        TextView section = Ui.text(this, label.toUpperCase(Locale.ROOT), 12f, Ui.GREEN);
        section.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        section.setLetterSpacing(0.08f);
        section.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        sheet.addView(section);
    }

    private void settingsRow(LinearLayout sheet, String label, String value, Dialog dialog, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 13), Ui.dp(this, 2), Ui.dp(this, 13));
        row.setClickable(true);
        TextView main = Ui.text(this, label, 17f, Ui.INK);
        main.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView detail = Ui.text(this, value == null ? "" : value, 14f, Ui.MUTED);
        detail.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        row.addView(detail);
        row.addView(Ui.text(this, "\u203A", 21f, Ui.MUTED));
        row.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
        sheet.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        sheet.addView(line);
    }

    private void showLegal() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = Ui.column(this);
        sheet.setBackgroundColor(Ui.PAPER);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 26), Ui.dp(this, 20), Ui.dp(this, 44));
        TextView title = Ui.text(this, "About & legal", 24f, Ui.INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sheet.addView(title);
        TextView sub = Ui.text(this, "Transparency, sources and support", 13.5f, Ui.MUTED);
        sub.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 12));
        sheet.addView(sub);

        sheet.addView(drawerRow("\u24D8", "About", "What Rafiq Al-Quran is", () -> {
            dialog.dismiss();
            openPage("about");
        }));
        sheet.addView(drawerRow("\u26BF", "Privacy", "How data is handled", () -> {
            dialog.dismiss();
            openPage("privacy");
        }));
        sheet.addView(drawerRow("\u2713", "Sources & licences", "Dataset provenance and checksums", () -> {
            dialog.dismiss();
            openPage("sources");
        }));
        sheet.addView(drawerRow("\u2709", "Report an issue", "Send feedback to the team", () -> {
            dialog.dismiss();
            openPage("report");
        }));
        sheet.addView(drawerRow("\u2661", "Support the app", "Voluntary donation", () -> {
            dialog.dismiss();
            openPage("support");
        }));
        android.widget.Button done = Ui.button(this, "Done", true);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneParams.topMargin = Ui.dp(this, 16);
        done.setLayoutParams(doneParams);
        done.setOnClickListener(view -> dialog.dismiss());
        sheet.addView(done);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(sheet);
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void attachOperatorUnlock(LinearLayout topbar) {
        View heading = findTextContains(topbar, "rafiq");
        if (heading != null) heading.setOnClickListener(view -> registerTitleTap());
    }

    private View findTextContains(ViewGroup group, String value) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString().toLowerCase();
                if (text.contains(value)) return child;
            }
            if (child instanceof ViewGroup) {
                View found = findTextContains((ViewGroup) child, value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findText(ViewGroup group, String value) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && value.contentEquals(((TextView) child).getText())) return child;
            if (child instanceof ViewGroup) {
                View found = findText((ViewGroup) child, value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void registerTitleTap() {
        long now = System.currentTimeMillis();
        if (titleTapCount == 0 || now - titleTapWindowStart > 4000L) {
            titleTapCount = 0;
            titleTapWindowStart = now;
        }
        titleTapCount++;
        if (titleTapCount >= 5) {
            titleTapCount = 0;
            if (!operatorUnlocked) {
                operatorUnlocked = true;
                toast("Operator admin unlocked for this session");
            }
        }
    }

    private void openPage(String page) {
        Intent intent = new Intent(this, PagesActivity.class);
        intent.putExtra("page", page);
        startActivity(intent);
    }

    // ------------------------------------------------------------- updates
    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.3.2" : info.versionName;
        } catch (Exception ignored) {
            return "1.3.2";
        }
    }

    private int installedVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (Exception ignored) {
            return 17;
        }
    }

    private void checkForUpdates() {
        toast("Checking for updates\u2026");
        new Thread(() -> {
            try {
                JSONObject remote = Api.appVersion(getResources());
                final int latestCode = remote.optInt("versionCode", installedVersionCode());
                final String latestName = remote.optString("versionName", "");
                final String storeUrl = remote.optString("url",
                        "https://play.google.com/store/apps/details?id=" + getPackageName());
                runOnUiThread(() -> {
                    if (latestCode > installedVersionCode()) {
                        new AlertDialog.Builder(MainActivity.this,
                                android.R.style.Theme_Material_Light_Dialog_Alert)
                                .setTitle("Update available")
                                .setMessage("Rafiq Al-Quran " + latestName + " is available. You have "
                                        + installedVersionName() + ". Update for the latest voice features and fixes.")
                                .setPositiveButton("Update", (dialog, which) -> openStore(storeUrl))
                                .setNegativeButton("Later", null)
                                .show();
                    } else {
                        toast("You're on the latest version (" + installedVersionName() + ").");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("Couldn't check for updates. Check your connection and try again."));
            }
        }).start();
    }

    private void openStore(String webUrl) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            return;
        } catch (Exception ignored) {
            // Fall back to the web listing.
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
        } catch (Exception ignored) {
            toast("Couldn't open the store. Search for Rafiq Al-Quran on Google Play.");
        }
    }

    // ------------------------------------------------------------- messages
    private void renderEmptyState() {
        messages.removeAllViews();
        boolean tablet = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, tablet ? 16 : 8), 0, Ui.dp(this, tablet ? 18 : 10));
        String[] quickGlyphs = {"\u2726", "\u25A4", "\u266B", "\u270E"};
        String[] quickLabels = {"Ask", "Read", "Listen", "Study"};
        int[] quickAccents = {Ui.CORAL, Ui.TEAL, Ui.PURPLE, Ui.AMBER};
        Runnable[] quickActions = {
                () -> { input.requestFocus(); input.setSelection(input.getText().length()); },
                () -> startActivity(new Intent(this, SurahActivity.class)),
                () -> startActivity(new Intent(this, ListenActivity.class)),
                () -> startActivity(new Intent(this, StudyActivity.class))
        };
        LinearLayout quickStrip = Ui.row(this);
        quickStrip.setPadding(0, 0, 0, Ui.dp(this, 12));
        for (int index = 0; index < quickGlyphs.length; index++) {
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, 0, 0, Ui.dp(this, 2));
            item.setClickable(true);
            int iconSize = tablet ? 46 : 42;
            TextView icon = Ui.text(this, quickGlyphs[index], tablet ? 20f : 18f, Color.WHITE);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(Ui.rounded(quickAccents[index], 0, iconSize, this));
            icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, iconSize), Ui.dp(this, iconSize)));
            item.addView(icon);
            TextView quickLabel = Ui.text(this, quickLabels[index], tablet ? 13f : 11.5f, Ui.INK);
            quickLabel.setSingleLine(true);
            quickLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            quickLabel.setGravity(Gravity.CENTER);
            quickLabel.setPadding(0, Ui.dp(this, 5), 0, 0);
            item.addView(quickLabel);
            final int choice = index;
            item.setOnClickListener(view -> quickActions[choice].run());
            quickStrip.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(quickStrip);

        // Illustrated hero: geometric art, crescent, and an Amiri calligraphy watermark.
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.heroBackground(this));
        hero.setClipToOutline(true);
        hero.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(MainActivity.this, 26));
            }
        });
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.bg_hero);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setAlpha(0.92f);
        hero.addView(art, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView calligraphy = new ImageView(this);
        calligraphy.setImageResource(R.drawable.calligraphy);
        calligraphy.setAlpha(0.22f);
        calligraphy.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 190), Ui.dp(this, 135), Gravity.TOP | Gravity.END);
        markParams.topMargin = Ui.dp(this, 2);
        markParams.rightMargin = Ui.dp(this, 2);
        hero.addView(calligraphy, markParams);

        LinearLayout heroContent = Ui.column(this);
        heroContent.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        TextView welcome = Ui.text(this, "ASSALAMU ALAYKUM", 11.5f, Ui.SUN);
        welcome.setLetterSpacing(0.16f);
        welcome.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroContent.addView(welcome);

        TextView heading = Ui.display(this, "How can I help?", tablet ? 30f : 26f);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, Ui.dp(this, 6), 0, 0);
        heroContent.addView(heading);

        TextView sub = Ui.text(this,
                "Ask, read or listen.",
                tablet ? 16f : 14f, Color.parseColor("#EAF8FF"));
        sub.setLineSpacing(Ui.dp(this, 3), 1.35f);
        sub.setPadding(0, Ui.dp(this, 6), 0, 0);
        if (tablet) heroContent.addView(sub);

        TextView stars = Ui.text(this, "\u2726   \u2727   \u2726", 18f, Ui.SUN);
        stars.setLetterSpacing(0.2f);
        stars.setPadding(0, Ui.dp(this, 8), 0, 0);
        heroContent.addView(stars);

        hero.addView(heroContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, tablet ? 300 : 150));
        heroParams.bottomMargin = Ui.dp(this, 14);
        box.addView(hero, heroParams);
        if (!tablet) {
            LinearLayout quote = Ui.cardBox(this);
            quote.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.alpha(Ui.PURPLE, 90), 22, this));
            quote.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            LinearLayout quoteHead = Ui.row(this);
            TextView quoteLabel = Ui.label(this, "DUA FOR KNOWLEDGE");
            quoteLabel.setTextColor(Ui.PURPLE);
            quoteHead.addView(quoteLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView quoteRef = Ui.text(this, "20:114", 11.5f, Ui.MUTED);
            quoteHead.addView(quoteRef);
            quote.addView(quoteHead);
            TextView quoteArabic = Ui.arabic(this, "\u0648\u064E\u0642\u064F\u0644 \u0631\u064E\u0651\u0628\u0650\u0651 \u0632\u0650\u062F\u0646\u0650\u064A \u0639\u0650\u0644\u0645\u064B\u0627", 23f, Ui.INK);
            quoteArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            quoteArabic.setGravity(Gravity.RIGHT);
            quoteArabic.setPadding(0, Ui.dp(this, 4), 0, 0);
            quote.addView(quoteArabic);
            quote.setOnClickListener(view -> startActivity(ReaderActivity.intent(this, 20, "20:114")));
            box.addView(quote);
        }

        if (tablet) {
        TextView tryLabel = Ui.label(this, "TRY ASKING");
        tryLabel.setTextColor(Ui.GOLD_DEEP);
        tryLabel.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));
        box.addView(tryLabel);

        String[][] suggestions = {
                {"Who is Hazrat Essa?", "Prophets and their stories"},
                {"What does the Qur\u2019an say about sabr and shukr?", "Patience and gratitude \u00B7 Roman Urdu works"},
                {"Show me 2:255", "Open a verse by reference"}
        };
        for (String[] suggestion : suggestions) {
            LinearLayout card = suggestionCard(suggestion[0], suggestion[1]);
            card.setOnClickListener(view -> {
                input.setText(suggestion[0]);
                submit();
            });
            box.addView(card);
        }

        }
        if (store.hasProgress() && dataset != null) {
            Chapter chapter = dataset.chapter(store.lastChapter());
            String key = store.lastVerseKey();
            Verse last = key == null || key.isEmpty() ? null : dataset.verse(key);
            LinearLayout card = Ui.cardBox(this);
            card.setLayoutParams(Ui.withTop(this, 18));
            TextView label = Ui.label(this, "CONTINUE READING");
            label.setTextColor(Ui.GOLD_DEEP);
            card.addView(label);
            String labelText = chapter == null ? (key == null ? "Saved reading position" : key)
                    : chapter.nameEnglish + "  \u00B7  " + key;
            TextView big = Ui.display(this, labelText, 21f);
            big.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));
            card.addView(big);
            StringBuilder metaLine = new StringBuilder();
            if (last != null) {
                try {
                    metaLine.append(Assets.meta(this).indicator(last.id));
                    metaLine.append("  \u00B7  ");
                } catch (Exception ignored) {
                    // page metadata is optional
                }
                metaLine.append("about ").append(Math.max(1, Math.round(last.id * 100f / 6236f))).append("% of the Qur\u2019an read");
            } else {
                metaLine.append("Saved on this device");
            }
            card.addView(Ui.text(this, metaLine.toString(), 13f, Ui.MUTED));
            android.widget.Button resume = Ui.button(this, "Resume reading \u2192", true);
            LinearLayout.LayoutParams resumeParams = Ui.withTop(this, 12);
            resume.setLayoutParams(resumeParams);
            resume.setOnClickListener(view -> startActivity(ReaderActivity.intent(this, store.lastChapter(), store.lastVerseKey())));
            card.addView(resume);
            box.addView(card);
        }

        messages.addView(box);
    }

    private LinearLayout emptyTile(String glyph, String title, String hint, Runnable action, int accent,
                                   LinearLayout.LayoutParams params) {
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.alpha(accent, 85), 18, this));
        card.setElevation(Ui.dp(this, 2));
        card.setPadding(Ui.dp(this, wide ? 10 : 12), Ui.dp(this, wide ? 10 : 11), Ui.dp(this, wide ? 10 : 12), Ui.dp(this, wide ? 10 : 11));
        card.setClickable(true);
        card.setLayoutParams(params);

        TextView badge = Ui.text(this, glyph, 18f, accent);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(Ui.alpha(accent, 34), 0, 18, this));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        card.addView(badge);

        TextView cardTitle = Ui.text(this, title, wide ? 13.5f : 15.5f, Ui.INK);
        cardTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cardTitle.setSingleLine(true);
        cardTitle.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(cardTitle);
        TextView cardHint = Ui.text(this, hint, wide ? 10.5f : 11.5f, Ui.MUTED);
        cardHint.setSingleLine(true);
        cardHint.setPadding(0, Ui.dp(this, 2), 0, 0);
        card.addView(cardHint);
        card.setOnClickListener(view -> action.run());
        return card;
    }

    private LinearLayout suggestionCard(String title, String hint) {
        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.LINE, 20, this));
        card.setElevation(Ui.dp(this, 1));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16));
        card.setLayoutParams(Ui.withTop(this, 8));
        LinearLayout labels = Ui.column(this);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        TextView cardTitle = Ui.text(this, title, wide ? 13.5f : 15.5f, Ui.INK);
        cardTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
        labels.addView(cardTitle);
        TextView cardHint = Ui.text(this, hint, 13f, Ui.MUTED);
        cardHint.setPadding(0, Ui.dp(this, 3), 0, 0);
        labels.addView(cardHint);
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = Ui.text(this, "\u2192", 22f, Ui.GOLD_DEEP);
        arrow.setPadding(Ui.dp(this, 8), 0, 0, 0);
        card.addView(arrow);
        return card;
    }
    private void rebuildMessages() {
        messages.removeAllViews();
        if (turns.isEmpty()) {
            renderEmptyState();
            return;
        }
        for (HistoryCodec.Turn turn : turns) {
            if ("user".equals(turn.role)) messages.addView(userBubble(turn.question));
            else if (turn.answer != null) messages.addView(answerView(turn.answer));
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View userBubble(String text) {
        TextView bubble = Ui.text(this, text, 15f, Ui.INK);
        bubble.setBackground(Ui.rounded(Ui.BUBBLE, 0, 20, this));
        bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout wrap = Ui.row(this);
        wrap.setGravity(Gravity.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 10);
        params.leftMargin = Ui.dp(this, 52);
        wrap.addView(bubble, params);
        return wrap;
    }

    private View answerView(Answer answer) {
        if ("conversation".equals(answer.kind)) return conversationView(answer);
        LinearLayout block = Ui.column(this);
        block.setPadding(0, Ui.dp(this, 10), 0, 0);

        boolean hasSummary = answer.summary != null && !answer.summary.trim().isEmpty();
        if (hasSummary) {
            block.addView(summaryCard(answer.summary));
            TextView caption = Ui.text(this, answer.message, 12f, Ui.MUTED);
            caption.setPadding(0, Ui.dp(this, 8), 0, 0);
            block.addView(caption);
        } else {
            block.addView(Ui.text(this, answer.message, 15f, Ui.INK));
        }

        if ("fallback".equals(answer.kind)) {
            for (String limit : answer.limits) {
                block.addView(Ui.text(this, "\u2022 " + limit, 11f, Ui.MUTED));
            }
            block.addView(guidanceCard());
            block.addView(noticeView(answer));
            block.addView(followupRow(answer));
            return block;
        }

        for (Passage passage : answer.passages) block.addView(passageCard(passage));
        if (answer.translationNotice != null) {
            TextView note = Ui.text(this, answer.translationNotice, 11f, Ui.MUTED);
            note.setPadding(0, Ui.dp(this, 6), 0, 0);
            block.addView(note);
        }
        block.addView(noticeView(answer));
        block.addView(followupRow(answer));
        return block;
    }

    private View conversationView(Answer answer) {
        LinearLayout block = Ui.column(this);
        block.setPadding(0, Ui.dp(this, 10), 0, 0);
        block.addView(Ui.label(this, "Assistant"));
        TextView message = Ui.text(this, answer.message, 15f, Ui.INK);
        message.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        block.addView(message);
        for (String line : answer.limits) {
            TextView hint = Ui.text(this, "\u2022 " + line, 11f, Ui.MUTED);
            hint.setPadding(0, Ui.dp(this, 2), 0, 0);
            block.addView(hint);
        }
        return block;
    }

    private View summaryCard(String summary) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.GREEN_SOFT, 0, 16, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        TextView label = Ui.text(this, "Quran-only answer", 11f, Ui.GREEN);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(label);
        boolean animated = summary.length() > 240;
        TextView text = Ui.text(this, animated ? "" : summary, 15f, Ui.INK);
        text.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(text);
        if (animated) revealText(text, summary);
        TextView meta = Ui.text(this,
                "Reasoned only from the verses below \u2014 not tafsir or a fatwa. Consult a qualified scholar for rulings.",
                11f, Ui.MUTED);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(meta);
        return card;
    }

    /** ChatGPT-style reveal of the already-verified answer (no unverified tokens). */
    private void revealText(final TextView view, final String full) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int step = Math.max(14, full.length() / 70);
        handler.post(new Runnable() {
            private int shown = 0;
            @Override public void run() {
                shown = Math.min(full.length(), shown + step);
                view.setText(full.substring(0, shown));
                if (shown < full.length() && view.isAttachedToWindow()) handler.postDelayed(this, 24);
            }
        });
    }

    private View guidanceCard() {
        LinearLayout card = Ui.cardBox(this);
        card.setLayoutParams(Ui.withTop(this, 10));
        card.addView(Ui.sectionLabel(this, "Where to look next"));
        for (String line : Conversation.guidance()) {
            TextView text = Ui.text(this, "\u2022 " + line, 11f, Ui.MUTED);
            text.setPadding(0, Ui.dp(this, 3), 0, 0);
            card.addView(text);
        }
        return card;
    }

    private View passageCard(final Passage passage) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.card(this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 10);
        card.setLayoutParams(params);

        LinearLayout head = Ui.row(this);
        TextView reference = Ui.text(this, passage.referenceLabel, 11.5f, Ui.GOLD);
        reference.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(reference);
        head.addView(Ui.spacer(this));
        head.addView(Ui.text(this, passage.chapterNameArabic, 12f, Ui.MUTED));
        card.addView(head);

        TextView arabic = Ui.text(this, passage.arabic, 25f * prefs.textScale(), Ui.INK);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setGravity(Gravity.RIGHT);
        arabic.setLineSpacing(Ui.dp(this, 8), 1.2f);
        arabic.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        card.addView(arabic);

        card.addView(Ui.divider(this));
        card.addView(Ui.label(this, "Translation"));
        String meta = passage.translationTitle == null ? passage.translationLanguage
                : passage.translationTitle + (passage.translationAuthor == null ? "" : " \u2014 " + passage.translationAuthor);
        TextView source = Ui.text(this, meta, 11f, Ui.MUTED);
        source.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        card.addView(source);
        TextView translation = Ui.text(this, passage.translationText, 15f * prefs.textScale(), Ui.INK);
        card.addView(translation);
        if (passage.fallbackToEnglish) {
            TextView note = Ui.text(this,
                    "A reviewed translation is not available for this interface language, so the English translation is shown.",
                    11f, Ui.MUTED);
            card.addView(note);
        }

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 2), 0, 0);
        actions.addView(Ui.textAction(this, "Source", () -> showSourceDialog(passage)));
        actions.addView(Ui.textAction(this, "Recitation \u2014 Quran.com", () -> {
            Verse verse = dataset == null ? null : dataset.verse(passage.verseKey);
            if (verse != null) startActivity(PlayerActivity.intent(this, verse.chapterId, verse.key));
        }));
        card.addView(actions);

        TextView licence = Ui.text(this,
                "Licence status: " + passage.translationLicenseStatus.toUpperCase() + "  \u00B7  dataset " + passage.datasetVersion,
                10f, Ui.MUTED);
        licence.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(licence);
        return card;
    }

    private View noticeView(final Answer answer) {
        LinearLayout wrap = Ui.column(this);
        wrap.setPadding(0, Ui.dp(this, 10), 0, 0);
        final TextView body = Ui.text(this, answer.notice, 11f, Ui.MUTED);
        body.setVisibility(View.GONE);
        TextView toggle = Ui.text(this, "Required notice" + (answer.noticeVerified ? "" : " \u00B7 translation pending review") + " \u25B8", 11f, Ui.MUTED);
        toggle.setOnClickListener(view -> {
            body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        wrap.addView(toggle);
        wrap.addView(body);
        return wrap;
    }

    private View followupRow(final Answer answer) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 4), 0, 0);
        row.addView(Ui.textAction(this, "Copy", () -> copyAnswer(answer)));
        row.addView(Ui.textAction(this, "Listen", () -> speakAnswer(answer)));
        if (!"fallback".equals(answer.kind) && !answer.passages.isEmpty()) {
            row.addView(Ui.textAction(this, "More verses", () -> askMore(answer)));
        }
        row.addView(Ui.textAction(this, "New chat", () -> newConversation()));
        return row;
    }

    private void copyAnswer(Answer answer) {
        StringBuilder text = new StringBuilder();
        if (answer.summary != null && !answer.summary.trim().isEmpty()) {
            text.append(answer.summary.trim()).append("\n\n");
        }
        if (answer.message != null && !answer.message.trim().isEmpty()) {
            text.append(answer.message.trim()).append("\n\n");
        }
        for (Passage passage : answer.passages) {
            text.append(passage.referenceLabel).append("\n")
                    .append(passage.translationText).append("\n\n");
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Rafiq Al-Quran answer", text.toString().trim()));
                toast("Copied to clipboard");
            }
        } catch (Exception ignored) {
            toast("This device could not copy the answer.");
        }
    }

    // ---------------------------------------------------------------- submit
    private void submit() {
        if (dataset == null) {
            toast("The dataset is still loading.");
            return;
        }
        final String question = input.getText().toString().trim();
        final Attachments.File attachment = currentAttachment;
        if (question.isEmpty() && attachment == null) return;
        if (!question.isEmpty()) store.addSearch(question);

        Conversation.Reply chat = attachment == null ? Conversation.classify(question) : null;
        if (chat != null) {
            startConversationTurn(question, chat);
            return;
        }
        if (attachment != null && !attachment.readableLocally && !attachment.image) {
            startConversationTurn(question,
                    Conversation.Reply.of(Conversation.Kind.ATTACHMENT_HELP,
                            attachment.error == null ? Conversation.attachmentUnavailable() : attachment.error, null));
            clearAttachment();
            return;
        }
        boolean aiNeeded = aiAvailable && prefs.aiAssistEnabled()
                && (attachment != null ? attachment.image : true);
        if (aiNeeded && !prefs.aiConsentShown()) {
            askAiConsent(question, attachment);
            return;
        }
        performSubmit(question, attachment);
    }

    private void askAiConsent(final String question, final Attachments.File attachment) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(Conversation.aiConsentTitle())
                .setMessage(Conversation.aiConsentMessage())
                .setPositiveButton("Continue", (dialog, which) -> {
                    prefs.setAiConsentShown(true);
                    performSubmit(question, attachment);
                })
                .setNegativeButton("Use offline only", (dialog, which) -> {
                    prefs.setAiAssistEnabled(false);
                    prefs.setAiConsentShown(true);
                    performSubmit(question, attachment);
                })
                .show();
    }

    private void performSubmit(final String originalQuestion, final Attachments.File attachment) {
        input.setText("");
        if (turns.isEmpty()) messages.removeAllViews();
        String bubbleText = originalQuestion.isEmpty()
                ? "[Attachment: " + (attachment == null ? "file" : attachment.name) + "]"
                : originalQuestion;
        turns.add(new HistoryCodec.Turn("user", bubbleText, null));
        messages.addView(userBubble(bubbleText));
        clearAttachment();
        final String language = prefs.language();
        busy(attachment != null && attachment.image ? "Reading the attachment" : "Searching the Quran");
        new Thread(() -> {
            Answer answer;
            Facts.Result indexFact = null;
            String note = null;
            try {
                String searchText = originalQuestion;
                if (attachment != null) {
                    if (attachment.readableLocally) {
                        searchText = (originalQuestion + "\n" + attachment.text).trim();
                        note = "Read \u201C" + attachment.name + "\u201D on this device.";
                    } else if (attachment.image) {
                        AiResult result = callAi(originalQuestion, attachment, language);
                        if (result.imageText != null && !result.imageText.trim().isEmpty()) {
                            searchText = (originalQuestion + "\n" + result.imageText).trim();
                            note = "Text from the attached image was read by the AI service.";
                        } else {
                            answer = conversationAnswer(language, Conversation.attachmentUnavailable(), null);
                            final Answer unread = answer;
                            runOnUiThread(() -> finishAnswer(unread));
                            return;
                        }
                    }
                }
                if (searchText.trim().isEmpty()) {
                    answer = conversationAnswer(language, Conversation.attachmentUnavailable(), null);
                } else {
                    AiResult ai = null;
                    if (aiAvailable && prefs.aiAssistEnabled() && originalQuestion.length() > 0
                            && (attachment == null || !attachment.image)) {
                        ai = callAi(originalQuestion, null, language);
                    }
                    if (ai != null && ("smalltalk".equals(ai.intent) || "offtopic".equals(ai.intent))
                            && ai.reply != null && !ai.reply.isEmpty()) {
                        answer = conversationAnswer(language, ai.reply, null);
                    } else {
                        if (ai != null && ai.searchText != null && !ai.searchText.trim().isEmpty()) {
                            searchText = ai.searchText;
                        }
                        // Make sure the selected translation is in memory before composing.
                        Assets.ensureLanguage(MainActivity.this, dataset, language);
                        indexFact = Facts.match(dataset, originalQuestion);
                        if (indexFact != null) {
                            answer = AnswerComposer.factAnswer(dataset, language, indexFact.message, indexFact.verseKeys);
                        } else {
                            answer = AnswerComposer.answer(dataset, searchText, language);
                        }
                    }
                }
            } catch (Exception error) {
                answer = AnswerComposer.answer(dataset, originalQuestion, language);
            }
            // Optional deeper reasoning answer: the AI may only use the verses (and the
            // reviewed index fact) we already retrieved.
            if (answer != null && "answer".equals(answer.kind) && !answer.passages.isEmpty()
                    && aiAvailable && prefs.aiAssistEnabled() && prefs.aiConsentShown()) {
                answer = deepenAnswer(answer, originalQuestion, language, indexFact == null ? null : indexFact.aiFact);
            }
            final Answer finalAnswer = answer;
            final String finalNote = note;
            runOnUiThread(() -> {
                finishAnswer(finalAnswer);
                if (finalNote != null) toast(finalNote);
            });
        }).start();
    }

    private void finishAnswer(Answer answer) {
        idle();
        turns.add(new HistoryCodec.Turn("assistant", null, answer));
        messages.addView(answerView(answer));
        saveHistory();
        messages.post(() -> {
            View last = messages.getChildAt(messages.getChildCount() - 1);
            if (last != null) scroll.smoothScrollTo(0, Math.max(0, last.getTop() - Ui.dp(this, 8)));
        });
        if (handsFree && answer != null) {
            status.setText("Thinking\u2026");
            messages.postDelayed(() -> speakAnswer(answer, true), 350);
        }
    }

    /**
     * Optional deeper Quran-only answer: the app sends up to 18 retrieved verse
     * keys plus an optional reviewed index fact. The server reloads the
     * authoritative translations and rejects any answer that cites outside verses
     * or writes Arabic script; on any failure the deterministic answer stays.
     */
    private Answer deepenAnswer(Answer answer, String question, String language, String indexFact) {
        if (question == null || question.trim().isEmpty()) return answer;
        if (answer.passages.isEmpty()) return answer;
        if (!consumeAiAllowance()) return answer;
        try {
            JSONObject body = new JSONObject();
            String trimmed = question.trim();
            body.put("question", trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed);
            body.put("language", language);
            JSONArray keys = new JSONArray();
            if (!answer.shownVerseKeys.isEmpty()) {
                for (String key : answer.shownVerseKeys) keys.put(key);
            } else {
                for (Passage passage : answer.passages) keys.put(passage.verseKey);
            }
            body.put("verseKeys", keys);
            if (indexFact != null && !indexFact.trim().isEmpty()) {
                JSONArray facts = new JSONArray();
                facts.put(indexFact.trim());
                body.put("facts", facts);
            }
            JSONArray history = new JSONArray();
            for (HistoryCodec.Turn turn : recentHistory(6)) {
                JSONObject item = new JSONObject();
                if ("user".equals(turn.role)) {
                    if (turn.question == null || turn.question.trim().isEmpty() || turn.question.startsWith("[")) continue;
                    item.put("role", "user");
                    item.put("content", clip(turn.question, 400));
                } else {
                    if (turn.answer == null) continue;
                    String text = turn.answer.summary != null && !turn.answer.summary.trim().isEmpty()
                            ? turn.answer.summary : turn.answer.message;
                    if (text == null || text.trim().isEmpty()) continue;
                    item.put("role", "assistant");
                    item.put("content", clip(text, 400));
                }
                history.put(item);
            }
            if (history.length() > 0) body.put("history", history);
            runOnUiThread(() -> busy("Thinking through the verses"));
            JSONObject response = Api.assistantAnswer(getResources(), body);
            String text = response.isNull("answer") ? "" : response.optString("answer", "");
            if (!text.trim().isEmpty()) return answer.withSummary(text.trim());
        } catch (Exception ignored) {
            // The verses are still shown; the reasoning answer is optional.
        }
        return answer;
    }

    private void startConversationTurn(String question, Conversation.Reply reply) {
        input.setText("");
        clearAttachment();
        if (turns.isEmpty()) messages.removeAllViews();
        String bubbleText = question == null || question.isEmpty() ? "[Message]" : question;
        turns.add(new HistoryCodec.Turn("user", bubbleText, null));
        messages.addView(userBubble(bubbleText));
        List<String> limits = new ArrayList<>();
        if (reply.hint != null) limits.add(reply.hint);
        if (reply.kind == Conversation.Kind.RULING_REQUEST) limits.addAll(Conversation.guidance());
        finishAnswer(conversationAnswer(prefs.language(), reply.text, limits));
    }

    private static Answer conversationAnswer(String language, String message, List<String> limits) {
        return new Answer("conversation", language, message, new ArrayList<Passage>(), 0,
                "", false, null, limits == null ? new ArrayList<String>() : limits, false,
                new ArrayList<String>());
    }

    // -------------------------------------------------------------- AI calls
    private static final class AiResult {
        String intent = "";
        String reply = "";
        String searchText = "";
        String imageText = "";
    }

    /** Calls the optional server AI service; returns an empty result on any failure. */
    private AiResult callAi(String question, Attachments.File image, String language) {
        AiResult result = new AiResult();
        if (!consumeAiAllowance()) return result;
        try {
            JSONObject body = new JSONObject();
            if (question != null) body.put("text", question);
            body.put("language", language);
            if (image != null && image.imageBytes != null) {
                JSONObject attachment = new JSONObject();
                attachment.put("name", image.name);
                attachment.put("mime", image.mime.isEmpty() ? "image/jpeg" : image.mime);
                attachment.put("dataBase64", Attachments.base64(image.imageBytes));
                body.put("attachment", attachment);
            }
            JSONObject response = Api.assistant(getResources(), body);
            result.intent = response.optString("intent", "");
            result.reply = response.isNull("reply") ? "" : response.optString("reply", "");
            result.imageText = response.isNull("imageText") ? "" : response.optString("imageText", "");
            org.json.JSONArray terms = response.optJSONArray("searchTerms");
            if (terms != null) {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < terms.length(); i++) {
                    String term = terms.optString(i, "").trim();
                    if (term.isEmpty()) continue;
                    if (joined.length() > 0) joined.append(' ');
                    joined.append(term);
                }
                result.searchText = joined.toString();
            } else {
                result.searchText = response.optString("searchTerms", "");
            }
        } catch (Exception ignored) {
            // Offline, unconfigured AI or rate limited: fall back to local replies.
        }
        return result;
    }

    /** The last few conversation turns (excluding the current question) for AI context. */
    private List<HistoryCodec.Turn> recentHistory(int limit) {
        List<HistoryCodec.Turn> recent = new ArrayList<>();
        for (int index = turns.size() - 2; index >= 0 && recent.size() < limit; index--) {
            recent.add(0, turns.get(index));
        }
        return recent;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Daily allowance (40) so the free AI tier cannot be exhausted. */
    private boolean consumeAiAllowance() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        int count = prefs.aiCallsToday();
        if (!today.equals(prefs.aiDay())) {
            count = 0;
            prefs.setAiDay(today);
        }
        if (count >= AI_DAILY_LIMIT) return false;
        prefs.setAiCallsToday(count + 1);
        return true;
    }

    private void askMore(Answer previous) {
        if (dataset == null) return;
        final String language = prefs.language();
        String question = "";
        for (int index = turns.size() - 1; index >= 0; index--) {
            if ("user".equals(turns.get(index).role)) {
                question = turns.get(index).question;
                break;
            }
        }
        if (question == null || question.trim().isEmpty() || question.startsWith("[")) {
            toast("Ask a topic question first.");
            return;
        }
        final String asking = question;
        final List<String> shown = previous.shownVerseKeys;
        busy("Looking for more verses");
        new Thread(() -> {
            Assets.ensureLanguage(MainActivity.this, dataset, language);
            final Answer answer = AnswerComposer.answer(dataset, asking, language, shown);
            runOnUiThread(() -> {
                idle();
                if ("fallback".equals(answer.kind)) {
                    toast("No additional relevant passages were found.");
                    return;
                }
                turns.add(new HistoryCodec.Turn("assistant", null, answer));
                messages.addView(answerView(answer));
                saveHistory();
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }

    private void newConversation() {
        if (handsFree) stopHandsFree(false);
        archiveCurrentConversation();
        conversationId = java.util.UUID.randomUUID().toString();
        turns.clear();
        prefs.clearHistory();
        clearAttachment();
        rebuildMessages();
    }

    private void openHistory() {
        archiveCurrentConversation();
        startActivityForResult(new Intent(this, HistoryActivity.class), REQUEST_HISTORY);
    }

    /** Archives the current chat so it appears on the History screen (device only). */
    private void archiveCurrentConversation() {
        if (turns.isEmpty()) return;
        String title = "";
        for (HistoryCodec.Turn turn : turns) {
            if ("user".equals(turn.role) && turn.question != null && !turn.question.trim().isEmpty()
                    && !turn.question.startsWith("[")) {
                title = turn.question.trim();
                break;
            }
        }
        if (title.isEmpty()) title = "Attachment conversation";
        org.json.JSONArray turnsJson = HistoryCodec.encode(turns);
        org.json.JSONArray list = prefs.conversations();
        try {
            org.json.JSONArray next = new org.json.JSONArray();
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("id", conversationId);
            item.put("title", clip(title, 90));
            item.put("savedAt", System.currentTimeMillis());
            item.put("turns", turnsJson);
            next.put(item);
            for (int index = 0; index < list.length() && next.length() < 20; index++) {
                org.json.JSONObject existing = list.optJSONObject(index);
                if (existing == null) continue;
                // one entry per conversation: the current chat replaces its previous archive
                if (conversationId.equals(existing.optString("id"))) continue;
                next.put(existing);
            }
            prefs.setConversations(next);
        } catch (Exception ignored) {
            // history is a convenience; the chat itself is unaffected
        }
    }

    private void loadArchivedConversation(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            org.json.JSONObject item = new org.json.JSONObject(json);
            String restoredId = item.optString("id", "");
            conversationId = restoredId.isEmpty() ? java.util.UUID.randomUUID().toString() : restoredId;
            org.json.JSONArray encoded = item.optJSONArray("turns");
            if (encoded == null) return;
            List<HistoryCodec.Turn> restored = HistoryCodec.decode(encoded.toString());
            turns.clear();
            for (HistoryCodec.Turn turn : restored) {
                if (turn.answer == null || "conversation".equals(turn.answer.kind)) {
                    turns.add(turn);
                    continue;
                }
                boolean valid = true;
                for (Passage passage : turn.answer.passages) {
                    Verse verse = dataset == null ? null : dataset.verse(passage.verseKey);
                    if (verse == null || !verse.arabic.equals(passage.arabic)) valid = false;
                }
                if (valid) turns.add(turn);
            }
            rebuildMessages();
            saveHistory();
            toast("Conversation restored");
        } catch (Exception ignored) {
            toast("That conversation could not be restored.");
        }
    }

    private void showSourceDialog(Passage passage) {
        String text = "Verse: " + passage.referenceLabel + "\n\n"
                + "Dataset: " + passage.dataset + " (" + passage.datasetVersion + ")\n\n"
                + "Checksum SHA-256:\n" + passage.datasetChecksum + "\n\n"
                + "Source: " + (passage.datasetSourceUrl == null ? "recorded at import" : passage.datasetSourceUrl) + "\n\n"
                + "Translation: " + (passage.translationTitle == null ? passage.translationLanguage : passage.translationTitle)
                + (passage.translationAuthor == null ? "" : " \u2014 " + passage.translationAuthor) + "\n"
                + "Translation language: " + passage.translationLanguage + "\n"
                + "Licence status: " + passage.translationLicenseStatus + "\n\n"
                + "The Arabic line is the exact dataset text. The translation is a separate human translation and is not the Arabic text.";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Source & licence")
                .setMessage(text)
                .setPositiveButton("Close", null)
                .show();
    }

    // ------------------------------------------------------------- history
    private void loadHistory() {
        List<HistoryCodec.Turn> restored = HistoryCodec.decode(prefs.history());
        turns.clear();
        for (HistoryCodec.Turn turn : restored) {
            if (turn.answer == null) {
                turns.add(turn);
                continue;
            }
            if ("conversation".equals(turn.answer.kind)) {
                turns.add(turn);
                continue;
            }
            boolean valid = true;
            for (Passage passage : turn.answer.passages) {
                Verse verse = dataset == null ? null : dataset.verse(passage.verseKey);
                if (verse == null || !verse.arabic.equals(passage.arabic)) valid = false;
            }
            if (valid) turns.add(turn);
        }
        rebuildMessages();
    }

    private void saveHistory() {
        prefs.setHistory(HistoryCodec.encode(turns));
    }

    // ---------------------------------------------------------- attachments
    private void pickAttachment() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, ATTACH_REQUEST);
        } catch (Exception error) {
            toast("No file picker is available on this device.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_FALLBACK_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    input.setText(matches.get(0));
                    input.setSelection(input.getText().length());
                    if (handsFree) {
                        status.setText("Thinking\u2026");
                        input.postDelayed(this::submit, 300);
                    } else {
                        toast("You can edit the transcript before sending.");
                    }
                }
            } else if (handsFree) {
                status.setText("Voice input cancelled. Tap \u25C9 to try again.");
            }
            return;
        }
        if (requestCode == REQUEST_HISTORY) {
            if (resultCode == RESULT_OK && data != null) {
                loadArchivedConversation(data.getStringExtra(HistoryActivity.EXTRA_CONVERSATION));
            }
            return;
        }
        if (requestCode != ATTACH_REQUEST || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        busy("Reading the attachment");
        new Thread(() -> {
            final Attachments.File file = Attachments.read(this, uri);
            runOnUiThread(() -> {
                idle();
                currentAttachment = file;
                updateAttachmentRow();
                if (file.error != null) toast(file.error);
            });
        }).start();
    }

    private void updateAttachmentRow() {
        if (currentAttachment == null) {
            attachmentRow.setVisibility(View.GONE);
            attachmentLabel.setText("");
            return;
        }
        attachmentRow.setVisibility(View.VISIBLE);
        if (currentAttachment.image) {
            String mode = aiAvailable && prefs.aiAssistEnabled()
                    ? "image \u2014 will be read by the AI service"
                    : "image \u2014 AI service is off";
            attachmentLabel.setText("Attached: " + currentAttachment.name + " (" + mode + ")");
        } else {
            attachmentLabel.setText("Attached: " + currentAttachment.summary());
        }
    }

    private void clearAttachment() {
        currentAttachment = null;
        updateAttachmentRow();
    }

    // --------------------------------------------------------------- speech
    private void toggleHandsFree() {
        if (handsFree) {
            stopHandsFree(true);
            return;
        }
        if (!prefs.voiceEnabled()) {
            prefs.setVoiceEnabled(true);
            toast("Voice features enabled.");
        }
        handsFree = true;
        prefs.setHandsFree(true);
        systemVoiceAttempted = false;
        updateVoiceChatButton();
        ensureTts();
        status.setText("Listening\u2026 speak your question.");
        toast("Voice chat on. Speak, and the answer will be read aloud.");
        startListening();
    }

    private void stopHandsFree(boolean notify) {
        boolean was = handsFree;
        handsFree = false;
        prefs.setHandsFree(false);
        updateVoiceChatButton();
        stopListening();
        Voice.stop(tts);
        speakingAnswer = null;
        pendingSpeech = null;
        if (status != null) status.setText("");
        if (notify && was) toast("Voice chat off. You can type instead.");
    }

    private void ensureTts() {
        if (ttsFailed) {
            // A failed engine is shut down so the next tap can try a fresh one.
            if (tts != null) {
                try { tts.shutdown(); } catch (Exception ignored) { }
            }
            tts = null;
            ttsReady = false;
            ttsFailed = false;
        }
        if (tts != null) return;
        tts = Voice.createTts(this, this::onTtsReady, message -> {
            ttsReady = false;
            ttsFailed = true;
            speakingAnswer = null;
            if (tts != null) {
                try { tts.shutdown(); } catch (Exception ignored) { }
                tts = null;
            }
            toast(message);
        });
    }

    private void onTtsReady() {
        ttsReady = true;
        ttsFailed = false;
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { Log.i(TAG, "tts utterance start"); }
                @Override public void onDone(String utteranceId) { onTtsDone(); }
                @Override public void onError(String utteranceId) { onTtsUtteranceError(0); }
                @Override public void onError(String utteranceId, int errorCode) { onTtsUtteranceError(errorCode); }
            });
        }
        if (pendingSpeech != null) {
            Answer answer = pendingSpeech;
            pendingSpeech = null;
            speakAnswer(answer, handsFree);
        }
    }

    private void onTtsDone() {
        Log.i(TAG, "tts utterance done");
        speakingAnswer = null;
        if (!handsFree || isFinishing() || isDestroyed()) return;
        if (status != null) status.setText("Listening\u2026");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (handsFree) startListening();
        }, 500);
    }

    /** Synthesis failures arrive after queuing; tell the user instead of failing silently. */
    private void onTtsUtteranceError(int errorCode) {
        Log.w(TAG, "tts utterance error " + errorCode);
        speakingAnswer = null;
        if (isFinishing() || isDestroyed()) return;
        toast("The device voice could not read this text aloud. Read it on screen, or install/update speech "
                + "voice data in Android Settings.");
    }

    private void speakAnswer(Answer answer) {
        speakAnswer(answer, false);
    }

    private void speakAnswer(Answer answer, boolean automatic) {
        if (answer == null) return;
        if (!prefs.voiceEnabled()) {
            if (!automatic) toast("Voice features are disabled in the menu.");
            return;
        }
        if (listening) stopListening();
        if (Voice.isSpeaking(tts)) {
            Voice.stop(tts);
            if (!automatic && answer == speakingAnswer) {
                speakingAnswer = null;
                toast("Stopped reading aloud.");
                return;
            }
        }
        if (tts == null || ttsFailed || !ttsReady) {
            pendingSpeech = answer;
            ensureTts();
            if (automatic) status.setText("Preparing the voice\u2026");
            else toast("Preparing the device voice\u2026");
            return;
        }
        // Never read the text with the UI language's voice: the voice follows the
        // language of the text that is actually spoken.
        Log.i(TAG, "listen ui=" + prefs.language() + " spoken=" + Voice.spokenLanguage(answer));
        String error = Voice.speak(tts, Voice.spokenText(answer), Voice.spokenLanguage(answer));
        if (error != null) {
            speakingAnswer = null;
            toast(error);
            if (automatic) status.setText("Tap \u25C9 to try again.");
        } else {
            speakingAnswer = answer;
            if (automatic) status.setText("Speaking\u2026");
            else toast("Reading the translation and summary aloud. This is not a Quranic recitation.");
        }
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { status.setText("\u25CF Listening\u2026 speak your question. Tap the mic to stop."); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { status.setText(""); }
            @Override public void onError(int error) {
                status.setText("");
                listening = false;
                Log.w(TAG, "asr error=" + error + " ui=" + prefs.language());
                boolean fallback = error == SpeechRecognizer.ERROR_CLIENT
                        || error == SpeechRecognizer.ERROR_SERVER
                        || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                        || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED;
                if (fallback && !systemVoiceAttempted) {
                    systemVoiceAttempted = true;
                    abandonRecognizer();
                    toast("Trying the system voice input\u2026");
                    launchSystemVoice();
                    return;
                }
                if (fallback) abandonRecognizer();
                toast(voiceErrorMessage(error));
                if (handsFree) status.setText("Tap \u25C9 to try again, or type instead.");
            }
            @Override public void onResults(Bundle results) {
                status.setText("");
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    input.setText(matches.get(0));
                    input.setSelection(input.getText().length());
                    if (handsFree) {
                        status.setText("Thinking\u2026");
                        input.postDelayed(MainActivity.this::submit, 300);
                    } else {
                        toast("You can edit the transcript before sending.");
                    }
                } else if (handsFree) {
                    status.setText("I didn't catch that. Speak again, or type instead.");
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partials = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partials != null && !partials.isEmpty()) input.setText(partials.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void startListening() {
        if (!prefs.voiceEnabled()) {
            toast("Voice features are disabled in the menu.");
            return;
        }
        if (listening) {
            stopListening();
            if (status != null) status.setText("");
            return;
        }
        systemVoiceAttempted = false;
        if (!Voice.recognizerAvailable(this)) {
            launchSystemVoice();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
            return;
        }
        if (Voice.isSpeaking(tts)) {
            Voice.stop(tts);
            speakingAnswer = null;
        }
        ensureRecognizer();
        final String language = prefs.language();
        final Intent intent = Voice.recognitionIntent(language);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                recognizer.checkRecognitionSupport(intent, getMainExecutor(), new RecognitionSupportCallback() {
                    @Override public void onSupportResult(RecognitionSupport support) {
                        String tag = Voice.localeFor(language);
                        Log.i(TAG, "asr support " + language + " tag=" + tag
                                + " installed=" + support.getInstalledOnDeviceLanguages()
                                + " pending=" + support.getPendingOnDeviceLanguages()
                                + " online=" + support.getOnlineLanguages());
                        if (hasLanguage(support.getInstalledOnDeviceLanguages(), tag)
                                || hasLanguage(support.getOnlineLanguages(), tag)) {
                            beginListening(intent);
                        } else if (hasLanguage(support.getSupportedOnDeviceLanguages(), tag)
                                || hasLanguage(support.getPendingOnDeviceLanguages(), tag)) {
                            try {
                                recognizer.triggerModelDownload(intent);
                            } catch (Exception ignored) {
                                // Model download is best effort.
                            }
                            toast("The " + Language.byCode(language).englishName
                                    + " speech model is downloading. Try Voice again in a moment.");
                        } else {
                            toast(unsupportedLanguageMessage(language));
                        }
                    }

                    @Override public void onError(int error) {
                        // Older engines may not support the preflight; start normally and
                        // let the regular error path report the outcome.
                        beginListening(intent);
                    }
                });
                return;
            } catch (Exception ignored) {
                // Fall through to a direct start on engines without support checks.
            }
        }
        beginListening(intent);
    }

    /** Fallback to the system's own voice UI when the in-app recognizer fails. */
    private void launchSystemVoice() {
        if (status != null) status.setText("Opening voice input\u2026");
        Intent intent = Voice.recognitionIntent(prefs.language());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask the Quran\u2026");
        try {
            startActivityForResult(intent, VOICE_FALLBACK_REQUEST);
        } catch (Exception error) {
            if (status != null) status.setText("");
            toast("Voice input is not available on this device. You can type instead.");
        }
    }

    private void beginListening(Intent intent) {
        if (recognizer == null) return;
        try {
            Log.i(TAG, "asr start ui=" + prefs.language() + " locale=" + Voice.localeFor(prefs.language()));
            listening = true;
            recognizer.startListening(intent);
        } catch (Exception error) {
            listening = false;
            toast("Voice input could not start. You can type instead.");
        }
    }

    private void stopListening() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
                // Recognizer already gone.
            }
        }
        listening = false;
        status.setText("");
    }

    private void abandonRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
                // Best effort.
            }
            recognizer = null;
        }
        listening = false;
    }

    private static boolean hasLanguage(List<String> tags, String wanted) {
        if (tags == null || wanted == null) return false;
        String lowerWanted = wanted.toLowerCase(Locale.ROOT);
        String code = lowerWanted.split("-")[0];
        for (String tag : tags) {
            if (tag == null) continue;
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.equals(lowerWanted) || lower.equals(code) || lower.startsWith(code + "-")) return true;
        }
        return false;
    }

    private String unsupportedLanguageMessage(String language) {
        String name = Language.byCode(language).englishName;
        return "Voice input for " + name + " isn't installed on this device. Download its speech pack in the "
                + "Google app (Settings > Voice > Offline speech recognition), or type instead.";
    }

    private String voiceErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "I didn't hear anything. Tap Voice and speak again, or type instead.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "I couldn't catch that. Try again or type instead.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Voice input needs a network connection right now. You can type instead.";
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                return "The voice service had a problem. Try again, or type instead.";
            case SpeechRecognizer.ERROR_AUDIO:
                return "The microphone could not be used. Check the device microphone and try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission is needed for voice input.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice input is still finishing. Try again in a moment.";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return "Voice input is busy right now. Try again in a moment.";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return unsupportedLanguageMessage(prefs.language());
            case SpeechRecognizer.ERROR_CLIENT:
                // Google's recognizer reports a missing language pack as a client error;
                // give the user an actionable next step instead of a generic failure.
                if (!"en".equals(prefs.language())) return unsupportedLanguageMessage(prefs.language());
                return "Voice input did not complete. You can type instead.";
            default:
                return "Voice input did not complete. You can type instead.";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else toast("Microphone permission was not granted. You can type instead.");
        }
    }

    /** ChatGPT-like working indicator: a short message with animated trailing dots. */
    private void busy(String message) {
        stopBusyTicker();
        final String base = message;
        busyTicker = new Runnable() {
            private int step = 1;
            @Override public void run() {
                if (status == null) return;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < step % 4; i++) dots.append('.');
                status.setText(base + dots);
                step++;
                status.postDelayed(this, 420);
            }
        };
        status.setText(base);
        status.postDelayed(busyTicker, 420);
    }

    private void idle() {
        stopBusyTicker();
        if (status != null) status.setText("");
    }

    private void stopBusyTicker() {
        if (busyTicker != null && status != null) status.removeCallbacks(busyTicker);
        busyTicker = null;
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
