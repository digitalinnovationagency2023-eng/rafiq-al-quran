package com.rqmbooks.rafiqalquran;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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
import com.rqmbooks.rafiqalquran.logic.Language;
import com.rqmbooks.rafiqalquran.logic.Passage;
import com.rqmbooks.rafiqalquran.logic.Verse;

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
    public static final String EXTRA_ASK = "ask";
    private static final int MIC_REQUEST = 41;
    private static final int ATTACH_REQUEST = 42;
    private static final int AI_DAILY_LIMIT = 30;

    private Dataset dataset;
    private Prefs prefs;
    private StudyStore store;
    private final List<HistoryCodec.Turn> turns = new ArrayList<>();
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private TextView languageButton;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Answer pendingSpeech;
    private boolean listening;

    private LinearLayout attachmentRow;
    private TextView attachmentLabel;
    private TextView attachButton;
    private Attachments.File currentAttachment;
    private boolean aiAvailable;

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
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (recognizer != null) recognizer.destroy();
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

        LinearLayout topbar = Ui.bar(this, "RAFIQ AL-QURAN", "Quran-only  \u00B7  offline", null);
        TextView smaller = Ui.action(this, "A\u2212", () -> changeTextScale(-0.05f));
        TextView larger = Ui.action(this, "A+", () -> changeTextScale(0.05f));
        languageButton = Ui.text(this, languageButtonLabel(), 12f, Ui.GOLD_BRIGHT);
        languageButton.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        languageButton.setContentDescription("Change interface language");
        languageButton.setOnClickListener(view -> showLanguageDialog());
        TextView menu = Ui.text(this, "\u22EE", 20f, Ui.GOLD_BRIGHT);
        menu.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        menu.setContentDescription("Menu");
        menu.setOnClickListener(this::showMenu);
        topbar.addView(smaller);
        topbar.addView(larger);
        topbar.addView(languageButton);
        topbar.addView(Ui.themeToggle(this));
        topbar.addView(menu);

        messages = Ui.column(this);
        messages.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 16));

        status = Ui.text(this, "Loading the Quran dataset\u2026", 12f, Ui.MUTED);
        status.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 6));

        LinearLayout composer = Ui.column(this);
        composer.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        composer.setBackgroundColor(Ui.PAPER);

        attachmentRow = Ui.row(this);
        attachmentRow.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.LINE, 8, this));
        attachmentRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        attachmentRow.setVisibility(View.GONE);
        attachmentLabel = Ui.text(this, "", 12f, Ui.INK);
        attachmentRow.addView(attachmentLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView removeAttachment = Ui.action(this, "Remove", () -> {
            currentAttachment = null;
            updateAttachmentRow();
        });
        attachmentRow.addView(removeAttachment);
        composer.addView(attachmentRow);

        input = new EditText(this);
        input.setHint("Ask about a topic, or give a reference such as 2:255\u2026");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 12, this));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        android.widget.Button mic = Ui.button(this, "\uD83C\uDFA4 Voice", false);
        mic.setOnClickListener(view -> startListening());
        attachButton = Ui.action(this, "\uD83D\uDCCE Attach", this::pickAttachment);
        attachButton.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        android.widget.Button send = Ui.button(this, "Send", true);
        send.setOnClickListener(view -> submit());
        actions.addView(mic);
        actions.addView(attachButton);
        actions.addView(Ui.spacer(this));
        actions.addView(send);
        composer.addView(input);
        composer.addView(actions);

        root.addView(topbar);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.GOLD);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        root.addView(divider);

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
        languageButton.setText(languageButtonLabel());
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
        menu.getMenu().add(0, 1, 0, "New conversation");
        menu.getMenu().add(0, 10, 1, "Study tools \u2014 collections, notes, plans");
        menu.getMenu().add(0, 9, 2, "Browse surahs");
        menu.getMenu().add(0, 11, 3, "Verse audio \u2014 Quran.com recitation");
        menu.getMenu().add(0, 14, 4, prefs.aiAssistEnabled() ? "AI conversation: on" : "AI conversation: off");
        menu.getMenu().add(0, 2, 5, prefs.voiceEnabled() ? "Turn voice off" : "Turn voice on");
        menu.getMenu().add(0, 13, 6, Ui.isDark() ? "Switch to light theme" : "Switch to dark theme");
        menu.getMenu().add(0, 3, 7, "Sources & licences");
        menu.getMenu().add(0, 6, 8, "Report an issue");
        menu.getMenu().add(0, 7, 9, "About");
        menu.getMenu().add(0, 8, 10, "Privacy");
        menu.getMenu().add(0, 12, 11, "Operator admin");
        menu.getMenu().add(0, 4, 12, "Support the app");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    newConversation();
                    return true;
                case 2:
                    prefs.setVoiceEnabled(!prefs.voiceEnabled());
                    if (prefs.voiceEnabled()) ensureTts();
                    else if (tts != null) Voice.stop(tts);
                    toast(prefs.voiceEnabled() ? "Voice features on" : "Voice features off");
                    return true;
                case 4: openPage("support"); return true;
                case 6: openPage("report"); return true;
                case 7: openPage("about"); return true;
                case 8: openPage("privacy"); return true;
                case 9: startActivity(new Intent(this, SurahActivity.class)); return true;
                case 10: startActivity(new Intent(this, StudyActivity.class)); return true;
                case 11:
                    startActivity(PlayerActivity.intent(this,
                            store.hasProgress() ? store.lastChapter() : 1,
                            store.hasProgress() ? store.lastVerseKey() : null));
                    return true;
                case 12: startActivity(new Intent(this, AdminActivity.class)); return true;
                case 13: Ui.toggleTheme(this); return true;
                case 14:
                    prefs.setAiAssistEnabled(!prefs.aiAssistEnabled());
                    toast(prefs.aiAssistEnabled()
                            ? (aiAvailable ? "AI conversation on" : "AI conversation on \u2014 server AI is not configured yet; local replies are used")
                            : "AI conversation off \u2014 offline replies only");
                    return true;
                case 3: openPage("sources"); return true;
                default: return false;
            }
        });
        menu.show();
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
        box.setPadding(0, Ui.dp(this, 24), 0, 0);
        TextView heading = Ui.text(this, "Ask a question, or open a reference", 20f, Ui.INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(heading);
        TextView body = Ui.text(this,
                "Answers show the Arabic verse, an identified translation, the surah and verse reference, "
                        + "and the source of the dataset. The assistant never writes religious text of its own.",
                13f, Ui.MUTED);
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        box.addView(body);
        String[] examples = {
                "What does the Quran say about patience?",
                "What does the Quran say about mercy?",
                "Show me 2:255"
        };
        for (String example : examples) {
            android.widget.Button button = Ui.button(this, example, false);
            LinearLayout.LayoutParams params = Ui.withTop(this, 6);
            button.setLayoutParams(params);
            button.setOnClickListener(view -> {
                input.setText(example);
                submit();
            });
            box.addView(button);
        }

        android.widget.Button study = Ui.button(this, "Study tools \u2014 collections, notes, reading plans", false);
        study.setLayoutParams(Ui.withTop(this, 6));
        study.setOnClickListener(view -> startActivity(new Intent(this, StudyActivity.class)));
        box.addView(study);

        messages.addView(box);

        if (store.hasProgress() && dataset != null) {
            Chapter chapter = dataset.chapter(store.lastChapter());
            String key = store.lastVerseKey();
            Verse last = key == null || key.isEmpty() ? null : dataset.verse(key);
            LinearLayout card = Ui.cardBox(this);
            card.setLayoutParams(Ui.withTop(this, 22));
            card.addView(Ui.sectionLabel(this, "Continue reading"));
            String label = chapter == null ? (key == null ? "" : key) : chapter.nameEnglish + "  \u00B7  " + key;
            TextView big = Ui.text(this, label, 17f, Ui.INK);
            big.setTypeface(Typeface.SERIF, Typeface.BOLD);
            big.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            card.addView(big);
            StringBuilder metaLine = new StringBuilder();
            if (last != null) {
                try {
                    metaLine.append(Assets.meta(this).indicator(last.id));
                    metaLine.append("  \u00B7  ");
                } catch (Exception ignored) {
                    // page metadata is optional for this card
                }
                metaLine.append("about ").append(Math.max(1, Math.round(last.id * 100f / 6236f))).append("% of the Quran read");
            } else {
                metaLine.append("Saved on this device");
            }
            card.addView(Ui.text(this, metaLine.toString(), 11f, Ui.MUTED));
            android.widget.Button resume = Ui.button(this, "Resume reading \u2192", true);
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
        TextView bubble = Ui.text(this, text, 15f, Ui.PAPER);
        bubble.setBackground(Ui.rounded(Ui.INK, Ui.INK, 16, this));
        bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout wrap = Ui.row(this);
        wrap.setGravity(Gravity.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        params.leftMargin = Ui.dp(this, 40);
        wrap.addView(bubble, params);
        return wrap;
    }

    private View answerView(Answer answer) {
        if ("conversation".equals(answer.kind)) return conversationView(answer);
        LinearLayout block = Ui.column(this);
        block.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, 0);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.topMargin = Ui.dp(this, 16);
        block.setLayoutParams(blockParams);

        if ("fallback".equals(answer.kind)) {
            TextView label = Ui.label(this, "No confident match");
            block.addView(label);
            TextView message = Ui.text(this, answer.message, 14f, Ui.INK);
            message.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            block.addView(message);
            for (String limit : answer.limits) {
                TextView note = Ui.text(this, "\u2022 " + limit, 11f, Ui.MUTED);
                block.addView(note);
            }
            block.addView(guidanceCard());
            block.addView(noticeView(answer));
            block.addView(followupRow(answer));
            return block;
        }

        block.addView(Ui.text(this, answer.message, 14f, Ui.INK));
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
        block.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, 0);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.topMargin = Ui.dp(this, 16);
        block.setLayoutParams(blockParams);
        block.addView(Ui.label(this, "Assistant \u00B7 no Quranic verse requested"));
        TextView message = Ui.text(this, answer.message, 15f, Ui.INK);
        message.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        block.addView(message);
        for (String line : answer.limits) {
            TextView hint = Ui.text(this, "\u2022 " + line, 11f, Ui.MUTED);
            hint.setPadding(0, Ui.dp(this, 2), 0, 0);
            block.addView(hint);
        }
        TextView small = Ui.text(this,
                "Conversation replies contain no Quranic text or interpretation. Quran passages are only "
                        + "shown when you ask what the Quran says about a topic or give a reference.",
                10f, Ui.MUTED);
        small.setPadding(0, Ui.dp(this, 6), 0, 0);
        block.addView(small);
        return block;
    }

    private View guidanceCard() {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = Ui.withTop(this, 10);
        card.setLayoutParams(params);
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
        TextView reference = Ui.label(this, passage.referenceLabel);
        reference.setTextColor(Ui.GOLD);
        head.addView(reference);
        head.addView(Ui.spacer(this));
        head.addView(Ui.text(this, passage.chapterNameArabic, 13f, Ui.MUTED));
        card.addView(head);

        TextView arabic = Ui.text(this, passage.arabic, 24f * prefs.textScale(), Ui.INK);
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
            TextView note = Ui.text(this, "A reviewed translation is not available for this interface language, so the English translation is shown.", 11f, Ui.MUTED);
            card.addView(note);
        }

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        android.widget.Button details = Ui.button(this, "Source details", false);
        details.setOnClickListener(view -> showSourceDialog(passage));
        android.widget.Button recitation = Ui.button(this, "Recitation \u2014 Quran.com", false);
        recitation.setOnClickListener(view -> {
            Verse verse = dataset == null ? null : dataset.verse(passage.verseKey);
            if (verse != null) startActivity(PlayerActivity.intent(this, verse.chapterId, verse.key));
        });
        actions.addView(details);
        actions.addView(recitation);
        card.addView(actions);

        TextView licence = Ui.text(this,
                "Licence status: " + passage.translationLicenseStatus.toUpperCase() + " \u00B7 dataset " + passage.datasetVersion,
                10f, Ui.MUTED);
        licence.setPadding(0, Ui.dp(this, 8), 0, 0);
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
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        android.widget.Button more = Ui.button(this, "More verses", false);
        more.setOnClickListener(view -> askMore(answer));
        row.addView(more);
        android.widget.Button listen = Ui.button(this, "Listen", false);
        listen.setOnClickListener(view -> speakAnswer(answer));
        row.addView(listen);
        android.widget.Button newChat = Ui.button(this, "New conversation", false);
        newChat.setOnClickListener(view -> newConversation());
        row.addView(newChat);
        return row;
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
        status.setText(attachment != null && attachment.image ? "Reading the attachment\u2026" : "Searching the Quran dataset\u2026");
        new Thread(() -> {
            Answer answer;
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
                        answer = AnswerComposer.answer(dataset, searchText, language);
                    }
                }
            } catch (Exception error) {
                answer = AnswerComposer.answer(dataset, originalQuestion, language);
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
        status.setText("");
        turns.add(new HistoryCodec.Turn("assistant", null, answer));
        messages.addView(answerView(answer));
        saveHistory();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
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

    /** Daily allowance (30) so the free AI tier cannot be exhausted. */
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
        status.setText("Searching for more passages\u2026");
        new Thread(() -> {
            final Answer answer = AnswerComposer.answer(dataset, asking, language, shown);
            runOnUiThread(() -> {
                status.setText("");
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
        turns.clear();
        prefs.clearHistory();
        clearAttachment();
        rebuildMessages();
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
        if (requestCode != ATTACH_REQUEST || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        status.setText("Reading the attachment\u2026");
        new Thread(() -> {
            final Attachments.File file = Attachments.read(this, uri);
            runOnUiThread(() -> {
                status.setText("");
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
        attachmentLabel.setText("Attached: " + currentAttachment.summary());
    }

    private void clearAttachment() {
        currentAttachment = null;
        updateAttachmentRow();
    }

    // --------------------------------------------------------------- speech
    private void ensureTts() {
        if (tts != null) return;
        tts = Voice.createTts(this, this::onTtsReady, message -> toast(message));
    }

    private void onTtsReady() {
        ttsReady = true;
        if (pendingSpeech != null) {
            Answer answer = pendingSpeech;
            pendingSpeech = null;
            speakAnswer(answer);
        }
    }

    private void speakAnswer(Answer answer) {
        if (!prefs.voiceEnabled()) {
            toast("Voice features are disabled in the menu.");
            return;
        }
        if (Voice.isSpeaking(tts)) {
            Voice.stop(tts);
            toast("Stopped reading aloud.");
            return;
        }
        if (tts == null) {
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
        String error = Voice.speak(tts, Voice.spokenText(answer), prefs.language());
        if (error != null) {
            toast(error);
        } else {
            toast("Reading the translation and summary aloud. This is not a Quranic recitation.");
        }
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
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { status.setText("\u25CF Listening\u2026 speak your question."); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { status.setText(""); }
                @Override public void onError(int error) { status.setText(""); listening = false; toast("Voice input did not complete. You can type instead."); }
                @Override public void onResults(Bundle results) {
                    status.setText("");
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
        listening = true;
        recognizer.startListening(Voice.recognitionIntent(prefs.language()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else toast("Microphone permission was not granted. You can type instead.");
        }
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
