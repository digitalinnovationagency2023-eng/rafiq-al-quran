package com.rqmbooks.rafiqalquran;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
    // A grounded summary is a second AI request per question, so the device cap is
    // slightly higher than the old single-call allowance.
    private static final int AI_DAILY_LIMIT = 40;

    private Dataset dataset;
    private Prefs prefs;
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
        Voice.stop(tts);
        speakingAnswer = null;
        pendingSpeech = null;
        stopListening();
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
        root.setBackgroundColor(Ui.PAPER);

        // Minimal ChatGPT-like top bar: menu, app/model name, new chat, theme.
        LinearLayout topbar = Ui.row(this);
        topbar.setBackgroundColor(Ui.BAR);
        topbar.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        TextView menuIcon = Ui.text(this, "\u2630", 20f, Ui.ON_BAR);
        menuIcon.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        menuIcon.setContentDescription("Menu");
        menuIcon.setOnClickListener(this::showMenu);
        topbar.addView(menuIcon);
        TextView heading = Ui.text(this, "Rafiq Al-Quran", 13.8f, Ui.ON_BAR);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        topbar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView historyIcon = Ui.text(this, "\u25F7", 21f, Ui.ON_BAR);
        historyIcon.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        historyIcon.setContentDescription("History");
        historyIcon.setOnClickListener(view -> openHistory());
        topbar.addView(historyIcon);
        TextView newChat = Ui.text(this, "\uFF0B", 22f, Ui.ON_BAR);
        newChat.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        newChat.setContentDescription("New conversation");
        newChat.setOnClickListener(view -> newConversation());
        topbar.addView(newChat);
        topbar.addView(Ui.themeToggle(this));
        attachOperatorUnlock(topbar);

        messages = Ui.column(this);
        messages.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 12));

        status = Ui.text(this, "", 12f, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, Ui.dp(this, 4));

        LinearLayout composer = Ui.column(this);
        composer.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 10));
        composer.setBackgroundColor(Ui.PAPER);

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
        pill.setBackground(Ui.rounded(Ui.CARD, Ui.LINE, 28, this));
        pill.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        pill.addView(Ui.glyph(this, "\uD83D\uDCCE", this::pickAttachment));
        input = new EditText(this);
        input.setHint("Ask the Quran\u2026");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(null);
        input.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        pill.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pill.addView(Ui.glyph(this, "\uD83C\uDFA4", this::startListening));
        pill.addView(Ui.circle(this, "\u2191", Ui.GREEN, Color.WHITE, 40, () -> submit()));
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
        return root;
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
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "New chat");
        menu.getMenu().add(0, 18, 1, "History");
        menu.getMenu().add(0, 19, 2, "Settings");
        menu.getMenu().add(0, 20, 3, "About & legal");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: newConversation(); return true;
                case 18: openHistory(); return true;
                case 19: showSettings(); return true;
                case 20: showLegal(); return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void showSettings() {
        List<String> items = new ArrayList<>();
        items.add("Interface language \u2014 " + prefs.language());
        items.add("Text size smaller (A\u2212)");
        items.add("Text size larger (A+)");
        items.add(Ui.isDark() ? "Switch to light theme" : "Switch to dark theme");
        items.add(prefs.aiAssistEnabled() ? "AI answers: on" : "AI answers: off");
        items.add(prefs.voiceEnabled() ? "Voice: on" : "Voice: off");
        items.add("Study tools");
        items.add("Browse surahs");
        items.add("Verse audio \u2014 Quran.com");
        items.add("Sources & licences");
        items.add("Report an issue");
        items.add("Privacy");
        items.add("Support the app");
        if (operatorUnlocked) items.add("Operator admin");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Settings")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    switch (which) {
                        case 0: showLanguageDialog(); break;
                        case 1: changeTextScale(-0.05f); break;
                        case 2: changeTextScale(0.05f); break;
                        case 3: Ui.toggleTheme(this); break;
                        case 4:
                            prefs.setAiAssistEnabled(!prefs.aiAssistEnabled());
                            toast(prefs.aiAssistEnabled()
                                    ? (aiAvailable ? "AI answers on" : "AI answers on \u2014 server AI is not configured yet; local replies are used")
                                    : "AI answers off \u2014 Quran verses and index facts only");
                            break;
                        case 5:
                            prefs.setVoiceEnabled(!prefs.voiceEnabled());
                            if (prefs.voiceEnabled()) ensureTts();
                            else if (tts != null) Voice.stop(tts);
                            toast(prefs.voiceEnabled() ? "Voice features on" : "Voice features off");
                            break;
                        case 6: startActivity(new Intent(this, StudyActivity.class)); break;
                        case 7: startActivity(new Intent(this, SurahActivity.class)); break;
                        case 8:
                            startActivity(PlayerActivity.intent(this,
                                    store.hasProgress() ? store.lastChapter() : 1,
                                    store.hasProgress() ? store.lastVerseKey() : null));
                            break;
                        case 9: openPage("sources"); break;
                        case 10: openPage("report"); break;
                        case 11: openPage("privacy"); break;
                        case 12: openPage("support"); break;
                        case 13: startActivity(new Intent(this, AdminActivity.class)); break;
                        default: break;
                    }
                })
                .show();
    }

    private void showLegal() {
        String[] items = {"About", "Privacy", "Sources & licences", "Report an issue", "Support the app"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("About & legal")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: openPage("about"); break;
                        case 1: openPage("privacy"); break;
                        case 2: openPage("sources"); break;
                        case 3: openPage("report"); break;
                        default: openPage("support"); break;
                    }
                })
                .show();
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

    // ------------------------------------------------------------- messages
    private void renderEmptyState() {
        messages.removeAllViews();
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, 44), 0, 0);

        TextView heading = Ui.text(this, "What can I help with?", 23f, Ui.INK);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        box.addView(heading);

        TextView sub = Ui.text(this,
                "Quran-only answers \u2014 in English, Roman Urdu/Arabic, or with typos.",
                13f, Ui.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 22));
        box.addView(sub);

        String[][] suggestions = {
                {"Who is Hazrat Essa?", "Prophets and their stories"},
                {"What is the law about theft?", "Quranic guidance, verse by verse"},
                {"What does the Quran say about sabr and shukr?", "Patience and gratitude \u00b7 Roman Urdu works"},
                {"Show me 2:255", "Open a verse by reference"}
        };
        for (String[] suggestion : suggestions) {
            LinearLayout card = Ui.cardBox(this);
            card.setLayoutParams(Ui.withTop(this, 8));
            TextView title = Ui.text(this, suggestion[0], 14.5f, Ui.INK);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            card.addView(title);
            TextView hint = Ui.text(this, suggestion[1], 11f, Ui.MUTED);
            hint.setPadding(0, Ui.dp(this, 4), 0, 0);
            card.addView(hint);
            card.setOnClickListener(view -> {
                input.setText(suggestion[0]);
                submit();
            });
            box.addView(card);
        }

        LinearLayout links = Ui.row(this);
        links.setPadding(0, Ui.dp(this, 14), 0, 0);
        links.addView(Ui.textAction(this, "Study tools", () -> startActivity(new Intent(this, StudyActivity.class))));
        links.addView(Ui.textAction(this, "Browse surahs", () -> startActivity(new Intent(this, SurahActivity.class))));
        if (store.hasProgress()) {
            links.addView(Ui.textAction(this, "Resume reading",
                    () -> startActivity(ReaderActivity.intent(this, store.lastChapter(), store.lastVerseKey()))));
        }
        box.addView(links);
        messages.addView(box);

        if (store.hasProgress() && dataset != null) {
            Chapter chapter = dataset.chapter(store.lastChapter());
            String key = store.lastVerseKey();
            Verse last = key == null || key.isEmpty() ? null : dataset.verse(key);
            LinearLayout card = Ui.cardBox(this);
            card.setLayoutParams(Ui.withTop(this, 22));
            card.addView(Ui.label(this, "Continue reading"));
            String label = chapter == null ? (key == null ? "" : key) : chapter.nameEnglish + "  \u00b7  " + key;
            TextView big = Ui.text(this, label, 17f, Ui.INK);
            big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            big.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            card.addView(big);
            StringBuilder metaLine = new StringBuilder();
            if (last != null) {
                try {
                    metaLine.append(Assets.meta(this).indicator(last.id));
                    metaLine.append("  \u00b7  ");
                } catch (Exception ignored) {
                    // page metadata is optional for this card
                }
                metaLine.append("about ").append(Math.max(1, Math.round(last.id * 100f / 6236f))).append("% of the Quran read");
            } else {
                metaLine.append("Saved on this device");
            }
            card.addView(Ui.text(this, metaLine.toString(), 11f, Ui.MUTED));
            android.widget.Button resume = Ui.button(this, "Resume reading", true);
            LinearLayout.LayoutParams resumeParams = Ui.withTop(this, 10);
            resume.setLayoutParams(resumeParams);
            resume.setOnClickListener(view -> startActivity(ReaderActivity.intent(this, store.lastChapter(), store.lastVerseKey())));
            card.addView(resume);
            messages.addView(card);
        }
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
        TextView text = Ui.text(this, summary, 15f, Ui.INK);
        text.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(text);
        TextView meta = Ui.text(this,
                "Reasoned only from the verses below \u2014 not tafsir or a fatwa. Consult a qualified scholar for rulings.",
                11f, Ui.MUTED);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(meta);
        return card;
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
        archiveCurrentConversation();
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
        String signature = title + "|" + turns.size() + "|" + turnsJson.toString().hashCode();
        org.json.JSONArray list = prefs.conversations();
        for (int index = 0; index < list.length(); index++) {
            org.json.JSONObject existing = list.optJSONObject(index);
            if (existing != null && signature.equals(existing.optString("sig"))) return;
        }
        try {
            org.json.JSONArray next = new org.json.JSONArray();
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("title", clip(title, 90));
            item.put("savedAt", System.currentTimeMillis());
            item.put("sig", signature);
            item.put("turns", turnsJson);
            next.put(item);
            for (int index = 0; index < list.length() && next.length() < 20; index++) {
                next.put(list.get(index));
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
                @Override public void onDone(String utteranceId) { Log.i(TAG, "tts utterance done"); speakingAnswer = null; }
                @Override public void onError(String utteranceId) { onTtsUtteranceError(0); }
                @Override public void onError(String utteranceId, int errorCode) { onTtsUtteranceError(errorCode); }
            });
        }
        if (pendingSpeech != null) {
            Answer answer = pendingSpeech;
            pendingSpeech = null;
            speakAnswer(answer);
        }
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
        if (!prefs.voiceEnabled()) {
            toast("Voice features are disabled in the menu.");
            return;
        }
        if (listening) stopListening();
        if (Voice.isSpeaking(tts)) {
            Voice.stop(tts);
            if (answer == speakingAnswer) {
                speakingAnswer = null;
                toast("Stopped reading aloud.");
                return;
            }
        }
        if (tts == null || ttsFailed) {
            pendingSpeech = answer;
            ensureTts();
            toast("Preparing the device voice\u2026");
            return;
        }
        if (!ttsReady) {
            pendingSpeech = answer;
            toast("Preparing the device voice\u2026");
            return;
        }
        // Never read the text with the UI language's voice: the voice follows the
        // language of the text that is actually spoken.
        Log.i(TAG, "listen ui=" + prefs.language() + " spoken=" + Voice.spokenLanguage(answer));
        String error = Voice.speak(tts, Voice.spokenText(answer), Voice.spokenLanguage(answer));
        if (error != null) {
            speakingAnswer = null;
            toast(error);
        } else {
            speakingAnswer = answer;
            toast("Reading the translation and summary aloud. This is not a Quranic recitation.");
        }
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { status.setText("\u25CF Listening\u2026 speak your question."); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { status.setText(""); }
            @Override public void onError(int error) {
                status.setText("");
                listening = false;
                Log.w(TAG, "asr error=" + error + " ui=" + prefs.language());
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                    abandonRecognizer();
                }
                toast(voiceErrorMessage(error));
            }
            @Override public void onResults(Bundle results) {
                status.setText("");
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    input.setText(matches.get(0));
                    input.setSelection(input.getText().length());
                    toast("You can edit the transcript before sending.");
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
        if (!Voice.recognizerAvailable(this)) {
            toast("Speech recognition is not available on this device. You can type instead.");
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
