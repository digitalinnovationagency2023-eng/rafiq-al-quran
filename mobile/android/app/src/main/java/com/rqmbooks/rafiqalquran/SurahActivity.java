package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.Verse;

import java.util.ArrayList;
import java.util.List;

/** Surah list plus an offline ayah reader (Milestone A, first slice). */
public final class SurahActivity extends Activity {
    private Prefs prefs;
    private Dataset dataset;
    private LinearLayout root;
    private ListView list;
    private ScrollView reader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        root = Ui.column(this);
        root.setBackgroundColor(Ui.PAPER);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Ui.INK);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        TextView back = Ui.text(this, "\u2190", 20f, Ui.GOLD);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(view -> onBackPressed());
        TextView title = Ui.text(this, "SURAH LIST", 16f, Ui.GOLD);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        bar.addView(back);
        bar.addView(title);
        root.addView(bar);

        list = new ListView(this);
        list.setBackgroundColor(Ui.PAPER);
        list.setDividerHeight(1);
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(list);

        reader = new ScrollView(this);
        reader.setBackgroundColor(Ui.PAPER);
        reader.setVisibility(View.GONE);
        reader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(reader);

        new Thread(() -> {
            final Dataset loaded = Assets.dataset(this);
            try {
                Assets.ensureLanguage(this, loaded, prefs.language());
            } catch (Exception ignored) {
                // English stays available as the fallback translation.
            }
            runOnUiThread(() -> {
                dataset = loaded;
                showList();
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (reader.getVisibility() == View.VISIBLE) {
            reader.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            return;
        }
        super.onBackPressed();
    }

    private void showList() {
        reader.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        List<String> rows = new ArrayList<>();
        for (int id = 1; id <= 114; id++) {
            Chapter chapter = dataset.chapter(id);
            if (chapter == null) continue;
            rows.add(id + ". " + chapter.nameEnglish + "  \u00B7  " + chapter.nameArabic
                    + "\n" + chapter.verseCount + " verses" + (chapter.revelation == null ? "" : " \u00B7 " + chapter.revelation));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Ui.INK);
                view.setTextSize(14f);
                view.setPadding(Ui.dp(SurahActivity.this, 14), Ui.dp(SurahActivity.this, 10),
                        Ui.dp(SurahActivity.this, 14), Ui.dp(SurahActivity.this, 10));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showChapter(position + 1);
            }
        });
    }

    private void showChapter(int chapterId) {
        Chapter chapter = dataset.chapter(chapterId);
        if (chapter == null) return;
        String language = prefs.language();
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 24));

        TextView heading = Ui.text(this, chapter.nameEnglish + " \u00B7 " + chapter.nameArabic, 20f, Ui.GOLD);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        content.addView(heading);
        TextView meta = Ui.text(this, chapter.verseCount + " verses \u00B7 " + (chapter.revelation == null ? "" : chapter.revelation),
                12f, Ui.MUTED);
        meta.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        content.addView(meta);

        int shown = 0;
        for (Verse verse : dataset.chapterVerses(chapterId)) {
            if (shown++ >= 60) break;
            TextView reference = Ui.label(this, versionLabel(verse));
            reference.setTextColor(Ui.GOLD_DEEP);
            reference.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 2));
            content.addView(reference);

            TextView arabic = Ui.text(this, verse.arabic, 22f * prefs.textScale(), Ui.INK);
            arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            arabic.setGravity(Gravity.RIGHT);
            arabic.setLineSpacing(Ui.dp(this, 6), 1.2f);
            content.addView(arabic);

            String translation = dataset.translationText(language, verse.key);
            if (translation == null || translation.isEmpty()) translation = dataset.translationText("en", verse.key);
            if (translation != null && !translation.isEmpty()) {
                TextView translated = Ui.text(this, translation, 14f * prefs.textScale(), Ui.MUTED);
                translated.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                content.addView(translated);
            }
            LinearLayout verseActions = Ui.row(this);
            verseActions.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));
            final String shareText = versionLabel(verse) + "\n\n" + verse.arabic + "\n\n" + (translation == null ? "" : translation);
            TextView copy = Ui.label(this, "Copy");
            copy.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Quran verse", shareText));
                android.widget.Toast.makeText(this, "Verse copied", android.widget.Toast.LENGTH_SHORT).show();
            });
            TextView share = Ui.label(this, "Share");
            share.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            share.setOnClickListener(v -> {
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
                startActivity(android.content.Intent.createChooser(send, "Share verse"));
            });
            final String bookmarkKey = verse.key;
            final TextView save = Ui.label(this, isBookmarked(bookmarkKey) ? "Saved" : "Save");
            save.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            save.setOnClickListener(v -> {
                boolean now = toggleBookmark(bookmarkKey);
                save.setText(now ? "Saved" : "Save");
                android.widget.Toast.makeText(this, now ? "Verse bookmarked" : "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show();
            });
            verseActions.addView(copy);
            verseActions.addView(share);
            verseActions.addView(save);
            content.addView(verseActions);
            content.addView(Ui.divider(this));
        }
        if (chapter.verseCount > 60) {
            content.addView(Ui.text(this, "Showing the first 60 verses of this surah in this build.", 12f, Ui.MUTED));
        }
        reader.removeAllViews();
        reader.addView(content);
        list.setVisibility(View.GONE);
        reader.setVisibility(View.VISIBLE);
        reader.scrollTo(0, 0);
    }

    private android.content.SharedPreferences store() {
        return getSharedPreferences("rafiq_reader", MODE_PRIVATE);
    }

    private boolean isBookmarked(String key) {
        return store().getStringSet("bookmarks", new java.util.HashSet<String>()).contains(key);
    }

    private boolean toggleBookmark(String key) {
        java.util.Set<String> set = new java.util.HashSet<String>(store().getStringSet("bookmarks", new java.util.HashSet<String>()));
        boolean added = set.add(key);
        if (!added) { set.remove(key); }
        store().edit().putStringSet("bookmarks", set).apply();
        return added;
    }

    private String versionLabel(Verse verse) {
        return verse.chapter.nameEnglish + " " + verse.key;
    }
}
