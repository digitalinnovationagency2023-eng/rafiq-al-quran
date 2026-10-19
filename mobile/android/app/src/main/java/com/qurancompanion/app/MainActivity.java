package com.qurancompanion.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.qurancompanion.app.logic.Answer;
import com.qurancompanion.app.logic.AnswerComposer;
import com.qurancompanion.app.logic.Dataset;
import com.qurancompanion.app.logic.Language;
import com.qurancompanion.app.logic.Notices;
import com.qurancompanion.app.logic.Passage;

import java.util.ArrayList;
import java.util.List;

/** The conversational screen. Everything runs against the bundled dataset. */
public final class MainActivity extends Activity {
    private static final int MIC_REQUEST = 41;

    private Dataset dataset;
    private Prefs prefs;
    private final List<HistoryCodec.Turn> turns = new ArrayList<>();
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private TextView headerStatus;
    private Spinner languageSpinner;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean listening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        View root = buildRoot();
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        new Thread(() -> {
            try {
                final Dataset loaded = Assets.dataset(this);
                Assets.ensureLanguage(this, loaded, "en");
                runOnUiThread(() -> {
                    dataset = loaded;
                    loadHistory();
                    status.setText("");
                });
            } catch (final Exception error) {
                runOnUiThread(() -> status.setText("Dataset error: " + error.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (recognizer != null) recognizer.destroy();
        saveHistory();
    }

    // ---------------------------------------------------------------- layout
    private View buildRoot() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout topbar = Ui.row(this);
        topbar.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        topbar.setBackgroundColor(Ui.INK);
        topbar.setBackgroundColor(Ui.PAPER);

        LinearLayout titleBox = Ui.column(this);
        TextView title = Ui.text(this, "RAFIQ AL-QURAN", 16f, Ui.GOLD);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        TextView tagline = Ui.text(this, "AI Assistant - Quran verse search", 11f, Ui.SILVER);
        titleBox.addView(title);
        titleBox.addView(tagline);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topbar.addView(titleBox, titleParams);

        LinearLayout controls = Ui.row(this);
        TextView smaller = Ui.text(this, "A-", 13f, Ui.SILVER);
        smaller.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        smaller.setOnClickListener(view -> changeTextScale(-0.05f));
        TextView larger = Ui.text(this, "A+", 15f, Ui.SILVER);
        larger.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        larger.setOnClickListener(view -> changeTextScale(0.05f));
        languageSpinner = new Spinner(this);
        ArrayAdapter<String> languages = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Language.codes());
        languageSpinner.setAdapter(languages);
        languageSpinner.setSelection(Math.max(0, Language.codes().indexOf(prefs.language())));
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.setLanguage(Language.codes().get(position));
                final String chosen = Language.codes().get(position);
                new Thread(() -> { if (dataset != null) { try { Assets.ensureLanguage(MainActivity.this, dataset, chosen); } catch (Exception ignored) { } } }).start();
                updatePlaceholder();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        TextView menu = Ui.text(this, "\u22EE", 20f, Ui.GOLD);
        menu.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        menu.setOnClickListener(this::showMenu);
        controls.addView(smaller);
        controls.addView(larger);
        controls.addView(languageSpinner);
        controls.addView(menu);
        topbar.addView(controls);

        scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);
        messages = Ui.column(this);
        messages.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 16));
        scroll.addView(messages);

        status = Ui.text(this, "Loading the Quran dataset…", 12f, Ui.MUTED);
        status.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 6));

        LinearLayout composer = Ui.column(this);
        composer.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        composer.setBackgroundColor(Ui.PAPER);
        input = new EditText(this);
        input.setHint("Ask about a topic, or give a reference such as 2:255…");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 12, this));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        android.widget.Button mic = Ui.button(this, "\uD83C\uDFA4 Voice", false);
        mic.setOnClickListener(view -> startListening());
        android.widget.Button send = Ui.button(this, "Send", true);
        send.setOnClickListener(view -> submit());
        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(mic);
        actions.addView(new View(this), spacer);
        actions.addView(send);
        composer.addView(input);
        composer.addView(actions);

        root.addView(topbar);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.GOLD);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        root.addView(divider);
        root.addView(scroll);
        root.addView(status);
        root.addView(composer);
        renderEmptyState();
        return root;
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

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "New conversation");
        menu.getMenu().add(0, 2, 1, prefs.voiceEnabled() ? "Turn voice off" : "Turn voice on");
        menu.getMenu().add(0, 3, 2, "Sources & licences");
        menu.getMenu().add(0, 4, 3, "Support the app");
        menu.getMenu().add(0, 5, 4, "Optional tools");
        menu.getMenu().add(0, 6, 5, "Report an issue");
        menu.getMenu().add(0, 7, 6, "About");
        menu.getMenu().add(0, 8, 7, "Privacy");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    newConversation();
                    return true;
                case 2:
                    prefs.setVoiceEnabled(!prefs.voiceEnabled());
                    toast(prefs.voiceEnabled() ? "Voice features on" : "Voice features off");
                    return true;
                case 3: openPage("sources"); return true;
                case 4: openPage("support"); return true;
                case 5: openPage("tools"); return true;
                case 6: openPage("report"); return true;
                case 7: openPage("about"); return true;
                case 8: openPage("privacy"); return true;
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
                "Answers show the Arabic verse, an identified translation, the surah and verse reference, and the source of the dataset. The assistant never writes religious text of its own.",
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
        messages.addView(box);
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
                TextView note = Ui.text(this, "• " + limit, 11f, Ui.MUTED);
                block.addView(note);
            }
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
        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(new View(this), spacer);
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
                : passage.translationTitle + (passage.translationAuthor == null ? "" : " — " + passage.translationAuthor);
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
        android.widget.Button recitation = Ui.button(this, "Recitation — disabled", false);
        recitation.setEnabled(false);
        actions.addView(details);
        actions.addView(recitation);
        card.addView(actions);

        TextView licence = Ui.text(this,
                "Licence status: " + passage.translationLicenseStatus.toUpperCase() + " · dataset " + passage.datasetVersion,
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
        TextView toggle = Ui.text(this, "Required notice" + (answer.noticeVerified ? "" : " · translation pending review") + " ▸", 11f, Ui.MUTED);
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
        listen.setOnClickListener(view -> speak(answer));
        row.addView(listen);
        android.widget.Button newChat = Ui.button(this, "New conversation", false);
        newChat.setOnClickListener(view -> newConversation());
        row.addView(newChat);
        return row;
    }

    // -------------------------------------------------------------- actions
    private void submit() {
        if (dataset == null) {
            toast("The dataset is still loading.");
            return;
        }
        String question = input.getText().toString().trim();
        if (question.isEmpty()) return;
        input.setText("");
        final String language = prefs.language();
        turns.add(new HistoryCodec.Turn("user", question, null));
        messages.addView(userBubble(question));
        status.setText("Searching the configured Quran dataset…");
        new Thread(() -> {
            final Answer answer = AnswerComposer.answer(dataset, question, language);
            runOnUiThread(() -> {
                status.setText("");
                turns.add(new HistoryCodec.Turn("assistant", null, answer));
                messages.addView(answerView(answer));
                saveHistory();
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
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
        final String asking = question;
        final List<String> shown = previous.shownVerseKeys;
        status.setText("Searching for more passages…");
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
        rebuildMessages();
    }

    private void showSourceDialog(Passage passage) {
        String text = "Verse: " + passage.referenceLabel + "\n\n"
                + "Dataset: " + passage.dataset + " (" + passage.datasetVersion + ")\n\n"
                + "Checksum SHA-256:\n" + passage.datasetChecksum + "\n\n"
                + "Source: " + (passage.datasetSourceUrl == null ? "recorded at import" : passage.datasetSourceUrl) + "\n\n"
                + "Translation: " + (passage.translationTitle == null ? passage.translationLanguage : passage.translationTitle)
                + (passage.translationAuthor == null ? "" : " — " + passage.translationAuthor) + "\n"
                + "Translation language: " + passage.translationLanguage + "\n"
                + "Licence status: " + passage.translationLicenseStatus + "\n\n"
                + "The Arabic line is the exact dataset text. The translation is a separate human translation and is not the Arabic text.";
        new AlertDialog.Builder(this)
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
            boolean valid = true;
            for (Passage passage : turn.answer.passages) {
                com.qurancompanion.app.logic.Verse verse = dataset == null ? null : dataset.verse(passage.verseKey);
                if (verse == null || !verse.arabic.equals(passage.arabic)) valid = false;
            }
            if (valid) turns.add(turn);
        }
        rebuildMessages();
    }

    private void saveHistory() {
        prefs.setHistory(HistoryCodec.encode(turns));
    }

    // --------------------------------------------------------------- voice
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
                @Override public void onReadyForSpeech(Bundle params) { status.setText("● Listening… speak your question."); }
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

    private void speak(Answer answer) {
        if (!prefs.voiceEnabled()) {
            toast("Voice features are disabled in the menu.");
            return;
        }
        if (tts == null) tts = Voice.createTts(this, null);
        Voice.speak(tts, Voice.spokenText(answer), prefs.language());
        toast("Reading the translation and summary. This is not a Quranic recitation.");
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
