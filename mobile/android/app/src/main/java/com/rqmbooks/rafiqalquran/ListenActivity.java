package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.SurahSearch;

/**
 * Listening home for Quran.com recitation.
 *
 * <p>The recitation player itself remains {@link PlayerActivity}; this screen is
 * the calm entry point: choose a reciter, resume the last surah, or pick any of
 * the 114 surahs without going through Settings.
 */
public final class ListenActivity extends Activity {
    private Prefs prefs;
    private StudyStore store;
    private Dataset dataset;
    private LinearLayout root;
    private ListView list;
    private LinearLayout reciterRow;
    private EditText search;
    private final java.util.List<Integer> visibleIds = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        store = new StudyStore(this);
        root = Ui.screen(this);
        root.setBackground(Ui.pageBackground(this));

        LinearLayout bar = Ui.bar(this, "Listen", "Quran.com  \u00B7  streamed", this::finish);
        bar.addView(Ui.themeToggle(this));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 20));
        scroll.addView(content);
        root.addView(scroll);

        LinearLayout hero = Ui.column(this);
        hero.setBackground(Ui.heroBackground(this));
        hero.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 18));
        hero.setClipToOutline(true);
        hero.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(ListenActivity.this, 26));
            }
        });
        TextView eyebrow = Ui.text(this, "QURAN.COM RECITATION", 11f, Ui.SUN);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hero.addView(eyebrow);
        TextView title = Ui.display(this, "Listen anywhere.", 28f);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        hero.addView(title);
        TextView intro = Ui.text(this,
                "Choose a reciter, then a surah.",
                15f, Color.parseColor("#EAF8FF"));
        intro.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 6));
        hero.addView(intro);
        TextView heroStars = Ui.text(this, "\u2726   \u2727   \u2726", 15f, Ui.SUN);
        heroStars.setLetterSpacing(0.2f);
        hero.addView(heroStars);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.bottomMargin = Ui.dp(this, 14);
        content.addView(hero, heroParams);

        LinearLayout reciterCard = Ui.cardBox(this);
        reciterCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 118)));
        TextView reciterLabel = Ui.label(this, "RECITER");
        reciterLabel.setTextColor(Ui.GOLD_DEEP);
        reciterCard.addView(reciterLabel);
        reciterRow = Ui.row(this);
        reciterRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        reciterRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));
        reciterCard.addView(reciterRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));
        content.addView(reciterCard);
        renderReciterChips();

        if (store.hasProgress()) {
            LinearLayout resume = Ui.cardBox(this);
            resume.setLayoutParams(Ui.withTop(this, 14));
            TextView resumeLabel = Ui.label(this, "CONTINUE LISTENING");
            resumeLabel.setTextColor(Ui.GOLD_DEEP);
            resume.addView(resumeLabel);
            TextView resumeTitle = Ui.display(this, "Your last surah is waiting.", 20f);
            resumeTitle.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
            resume.addView(resumeTitle);
            android.widget.Button open = Ui.button(this, "Open the player \u2192", true);
            open.setOnClickListener(view -> startActivity(PlayerActivity.intent(this,
                    store.lastChapter(), store.lastVerseKey())));
            resume.addView(open);
            content.addView(resume);
        }

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
        searchParams.topMargin = Ui.dp(this, 4);
        searchParams.bottomMargin = Ui.dp(this, 4);
        content.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) { refreshList(); }
        });

        LinearLayout tools = Ui.row(this);
        tools.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        TextView browse = Ui.textAction(this, "\u25A4 Browse & read", () -> startActivity(new Intent(this, SurahActivity.class)));
        tools.addView(browse);
        tools.addView(Ui.spacer(this));
        TextView car = Ui.textAction(this, "\u266B Car mode · continuous", () -> {
            startActivity(PlayerActivity.carIntent(this, store.hasProgress() ? store.lastChapter() : 1,
                    store.hasProgress() ? store.lastVerseKey() : null));
        });
        tools.addView(car);
        content.addView(tools);

        TextView allLabel = Ui.label(this, "MUSHAF ORDER · 1 → 114");
        allLabel.setTextColor(Ui.GOLD_DEEP);
        allLabel.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        content.addView(allLabel);

        list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 540)));
        content.addView(list);

        TextView footer = Ui.text(this,
                "Audio streams from Quran.com's public CDN. Source and rights review are recorded in the app. "
                        + "No audio is downloaded or stored on this device.",
                11.5f, Ui.MUTED);
        footer.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(footer);
        setContentView(root);

        new Thread(() -> {
            try {
                final Dataset loaded = Assets.dataset(this);
                runOnUiThread(() -> {
                    dataset = loaded;
                    refreshList();
                });
            } catch (final Exception error) {
                runOnUiThread(() -> {
                    TextView message = Ui.text(this, "The Quran dataset could not be loaded: " + error.getMessage(),
                            14f, Ui.DANGER);
                    message.setPadding(0, Ui.dp(this, 12), 0, 0);
                    content.addView(message);
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderReciterChips();
    }

    private void renderReciterChips() {
        if (reciterRow == null) return;
        reciterRow.removeAllViews();
        int selected = prefs.reciterIndex();
        if (selected < 0 || selected >= PlayerActivity.RECITERS.length) selected = 0;
        int[] chipAccents = {Ui.TEAL, Ui.PURPLE, Ui.AMBER};
        for (int index = 0; index < PlayerActivity.RECITERS.length; index++) {
            final int choice = index;
            int accent = chipAccents[index % chipAccents.length];
            String label = shortReciterName(PlayerActivity.RECITERS[index][0]);
            TextView chip = Ui.text(this, label, 12f, index == selected ? Ui.ON_BAR : Ui.INK);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setSingleLine(true);
            chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 9), Ui.dp(this, 8));
            chip.setBackground(Ui.rounded(index == selected ? accent : Ui.alpha(accent, 30),
                    index == selected ? 0 : Ui.alpha(accent, 120), 22, this));
            chip.setOnClickListener(view -> {
                prefs.setReciterIndex(choice);
                renderReciterChips();
            });
            if (index > 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = Ui.dp(this, 8);
                reciterRow.addView(chip, params);
            } else {
                reciterRow.addView(chip);
            }
        }
    }

    private static String shortReciterName(String full) {
        if (full == null || full.isEmpty()) return "Reciter";
        if (full.startsWith("Mishary")) return "Alafasy";
        if (full.startsWith("Abdul Basit")) return "Basit";
        if (full.startsWith("Muhammad Siddiq")) return "Siddiq";
        return full;
    }

    private void refreshList() {
        if (dataset == null) return;
        visibleIds.clear();
        visibleIds.addAll(SurahSearch.filter(dataset, search == null ? "" : search.getText().toString()));
        list.setAdapter(new ChapterAdapter());
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleIds.size()) return;
            Chapter chapter = dataset.chapter(visibleIds.get(position));
            if (chapter != null) startActivity(PlayerActivity.intent(this, chapter.id, null));
        });
    }

    private final class ChapterAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleIds.size(); }
        @Override public Object getItem(int position) { return dataset == null || position >= visibleIds.size() ? null : dataset.chapter(visibleIds.get(position)); }
        @Override public long getItemId(int position) { return position < visibleIds.size() ? visibleIds.get(position) : 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position < 0 || position >= visibleIds.size()) return new View(ListenActivity.this);
            Chapter chapter = dataset == null ? null : dataset.chapter(visibleIds.get(position));
            if (chapter == null) return new View(ListenActivity.this);

            LinearLayout row = Ui.row(ListenActivity.this);
            row.setPadding(Ui.dp(ListenActivity.this, 9), Ui.dp(ListenActivity.this, 13),
                    Ui.dp(ListenActivity.this, 9), Ui.dp(ListenActivity.this, 13));
            row.setBackgroundColor(Color.TRANSPARENT);
            int[] accents = {Ui.CORAL, Ui.TEAL, Ui.PURPLE, Ui.AMBER};
            int accent = accents[(chapter.id - 1) % accents.length];

            TextView number = Ui.text(ListenActivity.this, String.valueOf(chapter.id), 14f, accent);
            number.setTypeface(Typeface.create("serif", Typeface.BOLD));
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.rounded(Ui.alpha(accent, 30), Ui.alpha(accent, 100), 22, ListenActivity.this));
            number.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ListenActivity.this, 42),
                    Ui.dp(ListenActivity.this, 42)));
            row.addView(number);

            LinearLayout titles = Ui.column(ListenActivity.this);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.leftMargin = Ui.dp(ListenActivity.this, 14);
            TextView english = Ui.text(ListenActivity.this, chapter.nameEnglish, 18.5f, Ui.INK);
            english.setTypeface(Typeface.create("serif", Typeface.BOLD));
            titles.addView(english);
            TextView arabic = Ui.arabic(ListenActivity.this, chapter.nameArabic, 18f, accent);
            arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            arabic.setGravity(Gravity.RIGHT);
            titles.addView(arabic);
            TextView meta = Ui.text(ListenActivity.this,
                    chapter.verseCount + " verses  \u00B7  " + (chapter.revelation == null ? "Qur\u2019an" : chapter.revelation),
                    12.5f, Ui.MUTED);
            meta.setPadding(0, Ui.dp(ListenActivity.this, 3), 0, 0);
            titles.addView(meta);
            row.addView(titles, titleParams);

            TextView play = Ui.circle(ListenActivity.this, "\u25B6", Ui.alpha(accent, 34), accent, 42, null);
            play.setContentDescription("Listen to " + chapter.nameEnglish);
            row.addView(play);
            row.setOnClickListener(view -> startActivity(PlayerActivity.intent(ListenActivity.this, chapter.id, null)));
            return row;
        }
    }
}
