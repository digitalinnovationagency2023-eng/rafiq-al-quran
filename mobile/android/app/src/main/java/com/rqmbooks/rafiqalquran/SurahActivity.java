package com.rqmbooks.rafiqalquran;

import android.graphics.Color;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.SurahSearch;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline surah list (114 chapters) plus the device-local Bookmarks and Reading
 * history screens. Tapping any row opens the reader at that verse.
 */
public final class SurahActivity extends Activity {
    private Prefs prefs;
    private StudyStore store;
    private Dataset dataset;
    private LinearLayout root;
    private LinearLayout body;
    private TextView paneTitle;
    private TextView paneSubtitle;
    private ListView list;
    private ScrollView pane;
    private boolean showingList = true;
    private EditText search;
    private final List<Integer> visibleIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        store = new StudyStore(this);
        root = Ui.screen(this);

        LinearLayout bar = Ui.bar(this, "Surahs", "114  \u00B7  offline", this::finish);
        TextView bookmarks = Ui.action(this, "\u2606", this::showBookmarks);
        bookmarks.setTextSize(22f);
        bookmarks.setContentDescription("Bookmarks");
        bar.addView(bookmarks);
        TextView history = Ui.action(this, "\u25F7", this::showHistory);
        history.setTextSize(21f);
        history.setContentDescription("Reading history");
        bar.addView(history);
        bar.addView(Ui.themeToggle(this));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        LinearLayout paneBar = Ui.row(this);
        paneBar.setBackgroundColor(Color.TRANSPARENT);
        paneBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));
        LinearLayout titleBox = Ui.column(this);
        paneTitle = Ui.text(this, "THE 114 SURAHS", 11f, Ui.GOLD_DEEP);
        paneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        paneTitle.setLetterSpacing(0.14f);
        paneSubtitle = Ui.text(this, "Tap a surah to read", 11f, Ui.MUTED);
        titleBox.addView(paneTitle);
        titleBox.addView(paneSubtitle);
        paneBar.addView(titleBox);
        paneBar.addView(Ui.spacer(this));
        root.addView(paneBar);

        search = new EditText(this);
        search.setHint("Search surah");
        search.setSingleLine(true);
        search.setTextSize(14f);
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.MUTED);
        search.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 10, this));
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        searchParams.leftMargin = Ui.dp(this, 14);
        searchParams.rightMargin = Ui.dp(this, 14);
        searchParams.topMargin = Ui.dp(this, 4);
        searchParams.bottomMargin = Ui.dp(this, 4);
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) { applyFilter(); }
        });

        body = Ui.column(this);
        body.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(body);

        list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDividerHeight(1);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(list);

        pane = new ScrollView(this);
        pane.setBackgroundColor(Color.TRANSPARENT);
        pane.setVisibility(View.GONE);
        pane.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(pane);
        setContentView(root);

        TextView loading = Ui.text(this, "Loading the bundled Quran dataset\u2026", 13f, Ui.MUTED);
        loading.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), 0);
        body.addView(loading);
        list.setVisibility(View.GONE);

        new Thread(() -> {
            final Dataset loaded = Assets.dataset(this);
            runOnUiThread(() -> {
                dataset = loaded;
                body.removeView(loading);
                showList();
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (!showingList) {
            showList();
            return;
        }
        super.onBackPressed();
    }

    private void showList() {
        showingList = true;
        if (search != null) search.setVisibility(View.VISIBLE);
        paneTitle.setText("THE 114 SURAHS");
        paneSubtitle.setText("Mushaf order 1 → 114 · search in English, Arabic or Roman");
        pane.removeAllViews();
        pane.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        refreshAdapter();
    }

    private void refreshAdapter() {
        if (dataset == null) return;
        applyFilter();
        list.setAdapter(new ChapterAdapter());
        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position < 0 || position >= visibleIds.size()) return;
            Chapter chapter = dataset.chapter(visibleIds.get(position));
            if (chapter != null) startActivity(ReaderActivity.intent(this, chapter.id, null));
        });
    }

    private void applyFilter() {
        if (dataset == null) return;
        visibleIds.clear();
        visibleIds.addAll(SurahSearch.filter(dataset, search == null ? "" : search.getText().toString()));
        if (paneSubtitle != null && showingList) {
            paneSubtitle.setText(visibleIds.size() + " of 114 surahs · Mushaf order · search English / Arabic / Roman");
        }
        if (list != null && list.getAdapter() instanceof BaseAdapter) ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    private final class ChapterAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleIds.size(); }
        @Override public Object getItem(int position) { return dataset.chapter(visibleIds.get(position)); }
        @Override public long getItemId(int position) { return visibleIds.get(position); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position < 0 || position >= visibleIds.size()) return new View(SurahActivity.this);
            Chapter chapter = dataset.chapter(visibleIds.get(position));
            LinearLayout row = Ui.row(parent.getContext());
            row.setPadding(Ui.dp(SurahActivity.this, 14), Ui.dp(SurahActivity.this, 12),
                    Ui.dp(SurahActivity.this, 12), Ui.dp(SurahActivity.this, 12));
            row.setBackgroundColor(Color.TRANSPARENT);

            TextView number = Ui.text(SurahActivity.this, String.valueOf(chapter.id), 15f, Ui.GOLD_DEEP);
            number.setTypeface(Typeface.create("serif", Typeface.BOLD));
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.LINE, 22, SurahActivity.this));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(Ui.dp(SurahActivity.this, 44), Ui.dp(SurahActivity.this, 44));
            number.setLayoutParams(numberParams);
            row.addView(number);

            LinearLayout titles = Ui.column(parent.getContext());
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.leftMargin = Ui.dp(SurahActivity.this, 14);
            TextView english = Ui.text(SurahActivity.this, chapter.nameEnglish, 18.5f, Ui.INK);
            english.setTypeface(Typeface.create("serif", Typeface.BOLD));
            TextView arabic = Ui.arabic(SurahActivity.this, chapter.nameArabic, 18f, Ui.GOLD_DEEP);
            arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            arabic.setGravity(Gravity.RIGHT);
            titles.addView(english);
            titles.addView(arabic);
            TextView meta = Ui.text(SurahActivity.this,
                    chapter.verseCount + " verses  \u00B7  "
                            + (chapter.revelation == null ? "Qur\u2019an" : chapter.revelation),
                    11.5f, Ui.MUTED);
            meta.setPadding(0, Ui.dp(SurahActivity.this, 2), 0, 0);
            titles.addView(meta);
            row.addView(titles, titleParams);

            TextView listen = Ui.circle(SurahActivity.this, "\u25B6", Ui.GREEN_SOFT, Ui.GREEN, 40, null);
            listen.setContentDescription("Listen to " + chapter.nameEnglish + " recitation from Quran.com");
            listen.setOnClickListener(view -> startActivity(PlayerActivity.intent(SurahActivity.this, chapter.id, null)));
            LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(Ui.dp(SurahActivity.this, 40), Ui.dp(SurahActivity.this, 40));
            listenParams.leftMargin = Ui.dp(SurahActivity.this, 10);
            row.addView(listen, listenParams);
            return row;
        }
    }

    // ------------------------------------------------------------- bookmarks
    private void showBookmarks() {
        if (dataset == null) {
            toast("The dataset is still loading.");
            return;
        }
        showingList = false;
        if (search != null) search.setVisibility(View.GONE);
        paneTitle.setText("BOOKMARKS");
        paneSubtitle.setText(store.bookmarkTotal() + " saved verses across " + store.collections().size() + " collections");
        list.setVisibility(View.GONE);
        pane.setVisibility(View.VISIBLE);
        pane.removeAllViews();
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 20));

        TextView back = Ui.action(this, "\u2190 Back to surahs", this::showList);
        content.addView(back);

        if (store.bookmarkTotal() == 0) {
            LinearLayout empty = Ui.cardBox(this);
            LinearLayout.LayoutParams params = Ui.withTop(this, 10);
            empty.setLayoutParams(params);
            empty.addView(Ui.sectionLabel(this, "Nothing saved yet"));
            empty.addView(Ui.text(this, "Open a surah, tap Save on any verse and it will appear here in the collection you choose.", 12f, Ui.MUTED));
            content.addView(empty);
        }
        for (String collection : store.collections()) {
            List<String> keys = store.bookmarkedKeys(collection);
            if (keys.isEmpty()) continue;
            TextView section = Ui.sectionLabel(this, collection + "  \u00B7  " + keys.size());
            section.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
            content.addView(section);
            for (String key : keys) {
                content.addView(verseRow(key, collection));
            }
        }
        if (store.bookmarkTotal() > 0) {
            addExportButton(content);
        }
        pane.addView(content);
    }

    private void addExportButton(LinearLayout content) {
        android.widget.Button export = Ui.button(this, "Export bookmarks (CSV) via share sheet", false);
        LinearLayout.LayoutParams params = Ui.withTop(this, 16);
        export.setLayoutParams(params);
        export.setOnClickListener(v -> shareBookmarksCsv());
        content.addView(export);
    }

    private View verseRow(String key, String collection) {
        com.rqmbooks.rafiqalquran.logic.Verse verse = dataset.verse(key);
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = Ui.withTop(this, 6);
        card.setLayoutParams(params);
        String reference = verse == null ? key : verse.chapter.nameEnglish + "  " + verse.key;
        TextView head = Ui.text(this, reference, 13f, Ui.INK);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);
        String translation = dataset.translationText(prefs.language(), key);
        if (translation == null || translation.isEmpty()) translation = dataset.translationText("en", key);
        if (translation != null && !translation.isEmpty()) {
            TextView preview = Ui.text(this, translation.length() > 120 ? translation.substring(0, 120) + "\u2026" : translation, 11f, Ui.MUTED);
            preview.setPadding(0, Ui.dp(this, 3), 0, 0);
            card.addView(preview);
        }
        String note = store.note(key);
        if (!note.isEmpty()) {
            TextView noteView = Ui.text(this, "Note: " + note, 11f, Ui.GOLD_DEEP);
            noteView.setPadding(0, Ui.dp(this, 3), 0, 0);
            card.addView(noteView);
        }
        TextView meta = Ui.text(this, collection.toUpperCase(), 9f, Ui.GOLD_DEEP);
        meta.setLetterSpacing(0.12f);
        meta.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(meta);
        card.setOnClickListener(v -> openVerse(key));
        return card;
    }

    private void shareBookmarksCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append(com.rqmbooks.rafiqalquran.logic.StudyData.csvHeader("Reference", "Collection", "Note")).append('\n');
        for (String collection : store.collections()) {
            for (String key : store.bookmarkedKeys(collection)) {
                com.rqmbooks.rafiqalquran.logic.Verse verse = dataset.verse(key);
                String reference = verse == null ? key : verse.chapter.nameEnglish + " " + verse.key;
                builder.append(com.rqmbooks.rafiqalquran.logic.StudyData.csv(reference, collection, store.note(key))).append('\n');
            }
        }
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_SUBJECT, "Rafiq Al-Quran bookmarks (CSV)");
        send.putExtra(android.content.Intent.EXTRA_TEXT, builder.toString());
        startActivity(android.content.Intent.createChooser(send, "Share bookmarks"));
    }

    // --------------------------------------------------------------- history
    private void showHistory() {
        if (dataset == null) {
            toast("The dataset is still loading.");
            return;
        }
        showingList = false;
        if (search != null) search.setVisibility(View.GONE);
        paneTitle.setText("READING HISTORY");
        paneSubtitle.setText("Most recent chapters you opened");
        list.setVisibility(View.GONE);
        pane.setVisibility(View.VISIBLE);
        pane.removeAllViews();
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 20));
        content.addView(Ui.action(this, "\u2190 Back to surahs", this::showList));

        List<Integer> chapters = store.historyChapters();
        if (chapters.isEmpty()) {
            LinearLayout empty = Ui.cardBox(this);
            LinearLayout.LayoutParams params = Ui.withTop(this, 10);
            empty.setLayoutParams(params);
            empty.addView(Ui.sectionLabel(this, "No reading history yet"));
            empty.addView(Ui.text(this, "Chapters you open in the reader are listed here so you can continue where you left off.", 12f, Ui.MUTED));
            content.addView(empty);
        }
        String lastKey = store.lastVerseKey();
        for (Integer chapterId : chapters) {
            Chapter chapter = dataset.chapter(chapterId);
            if (chapter == null) continue;
            LinearLayout card = Ui.cardBox(this);
            LinearLayout.LayoutParams params = Ui.withTop(this, 6);
            card.setLayoutParams(params);
            TextView head = Ui.text(this, chapter.nameEnglish + "  \u00B7  " + chapter.nameArabic, 14f, Ui.INK);
            head.setTypeface(Typeface.SERIF, Typeface.BOLD);
            card.addView(head);
            String detail = chapter.verseCount + " verses  \u00B7  " + (chapter.revelation == null ? "" : chapter.revelation);
            if (chapterId == store.lastChapter() && lastKey != null && lastKey.startsWith(chapterId + ":")) {
                detail += "  \u00B7  last position " + lastKey;
            }
            TextView meta = Ui.text(this, detail, 11f, Ui.MUTED);
            meta.setPadding(0, Ui.dp(this, 3), 0, 0);
            card.addView(meta);
            final int id = chapterId;
            card.setOnClickListener(v -> startActivity(ReaderActivity.intent(this, id, null)));
            content.addView(card);
        }
        if (!chapters.isEmpty()) {
            TextView clear = Ui.action(this, "Clear reading history", () -> {
                store.clearHistory();
                toast("Reading history cleared");
                showHistory();
            });
            clear.setPadding(0, Ui.dp(this, 14), 0, 0);
            content.addView(clear);
        }
        pane.addView(content);
    }

    private void openVerse(String key) {
        if (key == null || !key.contains(":")) return;
        int chapterId = Integer.parseInt(key.split(":")[0]);
        startActivity(ReaderActivity.intent(this, chapterId, key));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
