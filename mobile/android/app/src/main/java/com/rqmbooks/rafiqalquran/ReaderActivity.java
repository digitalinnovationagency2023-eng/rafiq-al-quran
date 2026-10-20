package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.StudyData;
import com.rqmbooks.rafiqalquran.logic.TranslationMeta;
import com.rqmbooks.rafiqalquran.logic.Verse;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline ayah reader. Shows the exact dataset Arabic text beside an identified
 * translation, the juz/mushaf page position, per-verse actions (copy, share,
 * bookmark collections, notes, gated recitation), reading progress and the
 * required source/licence line. Never composes religious text.
 */
public final class ReaderActivity extends Activity {
    private static final String EXTRA_CHAPTER = "chapter";
    private static final String EXTRA_VERSE = "verse";
    private static final int PAGE_STEP = 40;

    private Prefs prefs;
    private StudyStore store;
    private Dataset dataset;
    private QuranMeta meta;
    private int chapterId;
    private String startVerseKey;

    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout listBox;
    private LinearLayout footerBox;
    private TextView info;
    private final List<String> renderedKeys = new ArrayList<>();
    private List<Verse> verses = new ArrayList<>();
    private int fromIndex;
    private long lastProgressAt;
    private String lastRecordedKey = "";

    public static Intent intent(Context context, int chapterId, String verseKey) {
        Intent intent = new Intent(context, ReaderActivity.class);
        intent.putExtra(EXTRA_CHAPTER, chapterId);
        if (verseKey != null && !verseKey.isEmpty()) intent.putExtra(EXTRA_VERSE, verseKey);
        return intent;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        store = new StudyStore(this);
        chapterId = getIntent().getIntExtra(EXTRA_CHAPTER, 1);
        if (chapterId < 1 || chapterId > 114) chapterId = 1;
        startVerseKey = getIntent().getStringExtra(EXTRA_VERSE);

        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.bar(this, "Reader", "Offline  \u00B7  source-tagged", this::finish);
        bar.addView(Ui.themeToggle(this));
        bar.addView(Ui.action(this, "Study", () -> startActivity(new Intent(this, StudyActivity.class))));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        info = Ui.text(this, "Loading the mushaf…", 11f, Ui.MUTED);
        info.setBackgroundColor(Ui.GOLD_SOFT);
        info.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(info);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);

        new Thread(() -> {
            final Dataset loaded = Assets.dataset(this);
            try {
                Assets.ensureLanguage(this, loaded, prefs.language());
            } catch (Exception ignored) {
                // English remains available as the fallback translation.
            }
            final QuranMeta metaLoaded = Assets.meta(this);
            runOnUiThread(() -> {
                dataset = loaded;
                meta = metaLoaded;
                showChapter();
            });
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        recordProgress(true);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    // -------------------------------------------------------------- rendering
    private void showChapter() {
        Chapter chapter = dataset.chapter(chapterId);
        if (chapter == null) {
            info.setText("Chapter not found.");
            return;
        }
        store.recordHistoryChapter(chapterId);
        verses = dataset.chapterVerses(chapterId);
        fromIndex = 0;
        if (startVerseKey != null && !startVerseKey.isEmpty()) {
            for (int index = 0; index < verses.size(); index++) {
                if (verses.get(index).key.equals(startVerseKey)) {
                    fromIndex = index;
                    startVerseKey = null;
                    break;
                }
            }
        }
        renderedKeys.clear();
        content.removeAllViews();

        // Chapter heading card
        LinearLayout heading = Ui.cardBox(this);
        TextView name = Ui.text(this, chapter.nameEnglish, 24f, Ui.INK);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.addView(name);
        TextView arabicName = Ui.text(this, chapter.nameArabic, 22f, Ui.GOLD);
        arabicName.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabicName.setGravity(Gravity.RIGHT);
        arabicName.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.addView(arabicName);
        TextView chapterMeta = Ui.text(this,
                chapter.verseCount + " verses  \u00B7  " + (chapter.revelation == null ? "" : chapter.revelation)
                        + "  \u00B7  " + meta.chapterIndicator(dataset, chapterId),
                11f, Ui.MUTED);
        chapterMeta.setPadding(0, Ui.dp(this, 6), 0, 0);
        heading.addView(chapterMeta);
        TextView translationInfo = Ui.text(this, translationLine(), 10f, Ui.MUTED);
        translationInfo.setPadding(0, Ui.dp(this, 6), 0, 0);
        heading.addView(translationInfo);
        content.addView(heading);

        content.addView(readerControls());

        if (fromIndex > 0) {
            Button fromBeginning = Ui.button(this, "\u2191 Begin at the first verse", false);
            LinearLayout.LayoutParams beginningParams = Ui.withTop(this, 8);
            fromBeginning.setLayoutParams(beginningParams);
            fromBeginning.setOnClickListener(v -> {
                startVerseKey = null;
                showChapter();
            });
            content.addView(fromBeginning);
        }

        listBox = Ui.column(this);
        content.addView(listBox);
        footerBox = Ui.column(this);
        content.addView(footerBox);
        renderMore();
        updateInfoForFirstVisible();
    }

    /** Compact reading settings strip: text size, translation and layout. */
    private LinearLayout readerControls() {
        LinearLayout card = Ui.cardBox(this);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams params = Ui.withTop(this, 8);
        card.setLayoutParams(params);

        LinearLayout sizes = Ui.row(this);
        TextView minus = Ui.action(this, "A\u2212", () -> changeScale(-0.1f));
        TextView plus = Ui.action(this, "A+", () -> changeScale(0.1f));
        TextView sizeLabel = Ui.text(this, "Text size " + Math.round(prefs.textScale() * 100) + "%", 11f, Ui.MUTED);
        sizes.addView(minus);
        sizes.addView(plus);
        sizes.addView(sizeLabel);
        card.addView(sizes);

        LinearLayout toggles = Ui.row(this);
        toggles.setPadding(0, Ui.dp(this, 6), 0, 0);
        TextView translation = Ui.action(this, prefs.translationVisible() ? "Translation on" : "Translation off",
                () -> {
                    prefs.setTranslationVisible(!prefs.translationVisible());
                    rebuildKeepingPosition();
                });
        TextView layout = Ui.action(this, prefs.sideBySide() ? "Side by side" : "Stacked",
                () -> {
                    prefs.setSideBySide(!prefs.sideBySide());
                    rebuildKeepingPosition();
                });
        toggles.addView(translation);
        toggles.addView(layout);
        TextView theme = Ui.action(this, Ui.isDark() ? "Light theme" : "Dark theme",
                () -> Ui.toggleTheme(this));
        toggles.addView(theme);
        card.addView(toggles);
        return card;
    }

    private void changeScale(float delta) {
        float value = Math.max(0.85f, Math.min(1.6f, prefs.textScale() + delta));
        prefs.setTextScale(value);
        rebuildKeepingPosition();
    }

    private String currentVisibleKey() {
        if (listBox == null || scroll == null) return lastRecordedKey;
        int y = scroll.getScrollY() + Ui.dp(this, 20);
        int base = listBox.getTop();
        String best = renderedKeys.isEmpty() ? "" : renderedKeys.get(0);
        for (int index = 0; index < listBox.getChildCount() && index < renderedKeys.size(); index++) {
            View child = listBox.getChildAt(index);
            int top = base + child.getTop();
            int bottom = base + child.getBottom();
            if (bottom > y) {
                best = renderedKeys.get(index);
                break;
            }
        }
        return best;
    }

    private void rebuildKeepingPosition() {
        String key = currentVisibleKey();
        startVerseKey = key;
        showChapter();
        scroll.post(() -> scrollToKey(key));
    }

    private void scrollToKey(String key) {
        if (key == null || listBox == null) return;
        for (int index = 0; index < listBox.getChildCount() && index < renderedKeys.size(); index++) {
            if (renderedKeys.get(index).equals(key)) {
                int y = listBox.getTop() + listBox.getChildAt(index).getTop() - Ui.dp(this, 8);
                scroll.scrollTo(0, Math.max(0, y));
                return;
            }
        }
    }

    private void renderMore() {
        int end = Math.min(verses.size(), fromIndex + renderedKeys.size() + PAGE_STEP);
        for (int index = fromIndex + renderedKeys.size(); index < end; index++) {
            Verse verse = verses.get(index);
            listBox.addView(verseCard(verse));
            renderedKeys.add(verse.key);
        }
        buildFooter();
        wireScrollProgress();
    }

    private void wireScrollProgress() {
        final String firstKey = renderedKeys.isEmpty() ? "" : renderedKeys.get(0);
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            long now = System.currentTimeMillis();
            if (now - lastProgressAt < 700) return;
            String visible = currentVisibleKey();
            if (!visible.isEmpty()) {
                info.setText(indicatorFor(visible) + "  \u00B7  " + (renderedKeys.indexOf(visible) + fromIndex + 1)
                        + " / " + verses.size());
            }
            recordProgress(false);
            lastProgressAt = now;
        });
        if (!firstKey.isEmpty()) info.setText(indicatorFor(firstKey) + "  \u00B7  " + (fromIndex + 1) + " / " + verses.size());
    }

    private void updateInfoForFirstVisible() {
        if (!renderedKeys.isEmpty()) {
            String key = renderedKeys.get(0);
            info.setText(indicatorFor(key) + "  \u00B7  " + (fromIndex + 1) + " / " + verses.size());
            store.recordProgress(chapterId, key);
            lastRecordedKey = key;
        }
    }

    private void recordProgress(boolean force) {
        String visible = currentVisibleKey();
        if (visible.isEmpty() || (!force && visible.equals(lastRecordedKey))) return;
        lastRecordedKey = visible;
        store.recordProgress(chapterId, visible);
    }

    private String translationLine() {
        TranslationMeta translation = dataset.translationMeta(prefs.language());
        if (translation == null) translation = dataset.translationMeta("en");
        if (translation == null) return "Translation source: dataset";
        return "Translation: " + translation.title
                + (translation.author == null || translation.author.isEmpty() ? "" : " \u2014 " + translation.author)
                + "  \u00B7  licence " + String.valueOf(translation.licenseStatus).toUpperCase();
    }

    // ------------------------------------------------------------------ cards
    private View verseCard(Verse verse) {
        float scale = prefs.textScale();
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.card(this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 10));
        card.setTag(verse.key);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 10);
        card.setLayoutParams(params);

        LinearLayout head = Ui.row(this);
        TextView reference = Ui.text(this, verse.chapter.nameEnglish + "  " + verse.key, 12f, Ui.GOLD_DEEP);
        reference.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(reference);
        head.addView(Ui.spacer(this));
        TextView page = Ui.chip(this, "PAGE " + meta.page(verse.id) + "  \u00B7  JUZ " + meta.juz(verse.id),
                Ui.MUTED, Ui.GOLD_SOFT, Ui.LINE);
        head.addView(page);
        card.addView(head);

        String language = prefs.language();
        String translation = dataset.translationText(language, verse.key);
        boolean fallback = false;
        if (translation == null || translation.isEmpty()) {
            translation = dataset.translationText("en", verse.key);
            fallback = true;
        }
        if (translation == null) translation = "";

        TextView arabic = Ui.text(this, verse.arabic, 24f * scale, Ui.INK);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setGravity(Gravity.RIGHT);
        arabic.setLineSpacing(Ui.dp(this, 7), 1.18f);
        arabic.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));

        if (prefs.sideBySide() && prefs.translationVisible() && !TextUtils.isEmpty(translation)) {
            LinearLayout columns = Ui.row(this);
            columns.setGravity(Gravity.TOP);
            columns.addView(arabic, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout translationBox = Ui.column(this);
            translationBox.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.LINE, 6, this));
            translationBox.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            translationBox.addView(Ui.label(this, "Translation"));
            translationBox.addView(Ui.text(this, translation, 13f * scale, Ui.INK));
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            boxParams.leftMargin = Ui.dp(this, 8);
            columns.addView(translationBox, boxParams);
            card.addView(columns);
        } else {
            card.addView(arabic);
            if (prefs.translationVisible() && !TextUtils.isEmpty(translation)) {
                card.addView(Ui.divider(this));
                TextView translated = Ui.text(this, translation, 15f * scale, Ui.MUTED);
                card.addView(translated);
                if (fallback) card.addView(Ui.text(this, "English translation shown for this interface language.", 10f, Ui.MUTED));
            }
        }

        String note = store.note(verse.key);
        if (!note.isEmpty()) {
            LinearLayout noteBox = Ui.column(this);
            noteBox.setBackground(Ui.rounded(Ui.GOLD_SOFT, Ui.LINE, 6, this));
            noteBox.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            LinearLayout.LayoutParams noteParams = Ui.withTop(this, 8);
            noteBox.setLayoutParams(noteParams);
            noteBox.addView(Ui.label(this, "Personal note"));
            noteBox.addView(Ui.text(this, note, 12f, Ui.INK));
            noteBox.setOnClickListener(v -> editNote(verse.key));
            card.addView(noteBox);
        }

        final String translationText = translation == null ? "" : translation;
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 6), 0, 0);
        actions.addView(Ui.action(this, "Copy", () -> copyVerse(verse, translationText)));
        actions.addView(Ui.action(this, "Share", () -> shareVerse(verse, translationText)));
        boolean bookmarked = store.isBookmarked(verse.key);
        actions.addView(Ui.action(this, bookmarked ? "Saved \u2713" : "Save",
                () -> bookmarkDialog(verse.key, bookmarked)));
        actions.addView(Ui.action(this, note.isEmpty() ? "Note" : "Edit note", () -> editNote(verse.key)));
        actions.addView(Ui.action(this, "Listen", () -> startActivity(PlayerActivity.intent(this, chapterId, verse.key))));
        card.addView(actions);
        return card;
    }

    private void buildFooter() {
        footerBox.removeAllViews();
        if (renderedKeys.size() < verses.size()) {
            Button more = Ui.button(this, "Show the next " + Math.min(PAGE_STEP, verses.size() - renderedKeys.size()) + " verses", false);
            LinearLayout.LayoutParams params = Ui.withTop(this, 12);
            params.leftMargin = Ui.dp(this, 2);
            params.rightMargin = Ui.dp(this, 2);
            more.setLayoutParams(params);
            more.setOnClickListener(v -> renderMore());
            footerBox.addView(more);
            return;
        }
        LinearLayout end = Ui.cardBox(this);
        LinearLayout.LayoutParams params = Ui.withTop(this, 14);
        end.setLayoutParams(params);
        end.addView(Ui.sectionLabel(this, "End of surah"));
        Chapter chapter = dataset.chapter(chapterId);
        end.addView(Ui.text(this, "You have reached the end of " + chapter.nameEnglish
                + ". Source: " + dataset.provenance().dataset + " " + dataset.provenance().version + ".", 11f, Ui.MUTED));

        int planCount = 0;
        for (StudyStore.Plan plan : store.plans()) {
            if (plan.chapters.contains(chapterId) && !plan.done.contains(chapterId)) planCount++;
        }
        if (planCount > 0) {
            Button mark = Ui.button(this, "Mark " + chapter.nameEnglish + " read in " + planCount
                    + (planCount == 1 ? " plan" : " plans"), true);
            LinearLayout.LayoutParams markParams = Ui.withTop(this, 10);
            mark.setLayoutParams(markParams);
            mark.setOnClickListener(v -> {
                for (StudyStore.Plan plan : store.plans()) store.markChapterDone(plan.id, chapterId);
                toast("Reading plan progress updated.");
                buildFooter();
            });
            end.addView(mark);
        } else {
            TextView hint = Ui.text(this, "Reading plans live in Study tools: pick a plan and this surah will count toward it.", 11f, Ui.MUTED);
            hint.setPadding(0, Ui.dp(this, 8), 0, 0);
            end.addView(hint);
            Button openStudy = Ui.button(this, "Open study tools", false);
            LinearLayout.LayoutParams studyParams = Ui.withTop(this, 8);
            openStudy.setLayoutParams(studyParams);
            openStudy.setOnClickListener(v -> startActivity(new Intent(this, StudyActivity.class)));
            end.addView(openStudy);
        }

        TextView licence = Ui.text(this, "Dataset: " + dataset.provenance().dataset + " " + dataset.provenance().version
                + "  \u00B7  checksum " + shortChecksum() + "\n" + translationLine()
                + "\nThe Arabic text is the exact dataset text; the translation is a separate human translation and is not the Arabic text.",
                9f, Ui.MUTED);
        licence.setPadding(0, Ui.dp(this, 8), 0, 0);
        end.addView(licence);
        footerBox.addView(end);

        Button back = Ui.button(this, "Back to the surah list", false);
        LinearLayout.LayoutParams backParams = Ui.withTop(this, 8);
        back.setLayoutParams(backParams);
        back.setOnClickListener(v -> finish());
        footerBox.addView(back);
    }

    private String shortChecksum() {
        String checksum = dataset.provenance().checksum;
        if (checksum == null || checksum.length() < 16) return String.valueOf(checksum);
        return checksum.substring(0, 16) + "\u2026";
    }

    // ---------------------------------------------------------------- actions
    private void copyVerse(Verse verse, String translation) {
        String text = referenceLabel(verse) + "\n\n" + verse.arabic + "\n\n" + translation
                + "\n\nSource: " + dataset.provenance().dataset + " " + dataset.provenance().version;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Quran verse", text));
        toast("Verse copied");
    }

    private void shareVerse(Verse verse, String translation) {
        String text = referenceLabel(verse) + "\n\n" + verse.arabic + "\n\n" + translation
                + "\n\nShared from Rafiq Al-Quran \u2014 Quran-only assistant.\nSource: "
                + dataset.provenance().dataset + " " + dataset.provenance().version;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share verse"));
    }

    private String referenceLabel(Verse verse) {
        return verse.chapter.nameEnglish + " " + verse.key;
    }

    private void bookmarkDialog(String verseKey, boolean bookmarked) {
        final List<String> collections = store.collections();
        final List<String> options = new ArrayList<>(collections);
        options.add("+ New collection\u2026");
        if (bookmarked) options.add("Remove bookmark");
        new AlertDialog.Builder(this)
                .setTitle(bookmarked ? "Saved in " + store.collectionOf(verseKey) : "Save verse")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == options.size() - (bookmarked ? 2 : 1)) {
                        newCollectionForVerse(verseKey);
                    } else if (bookmarked && which == options.size() - 1) {
                        store.removeBookmark(verseKey);
                        toast("Bookmark removed");
                        rebuildKeepingPosition();
                    } else {
                        store.bookmark(verseKey, collections.get(which));
                        toast("Saved in " + collections.get(which));
                        rebuildKeepingPosition();
                    }
                })
                .show();
    }

    private void newCollectionForVerse(String verseKey) {
        final EditText input = new EditText(this);
        input.setHint("Collection name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("New collection")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = store.addCollection(input.getText().toString());
                    store.bookmark(verseKey, name);
                    toast("Saved in " + name);
                    rebuildKeepingPosition();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editNote(String verseKey) {
        final EditText input = new EditText(this);
        input.setHint("Your personal note (stored on this device)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setText(store.note(verseKey));
        new AlertDialog.Builder(this)
                .setTitle("Note on " + verseKey)
                .setMessage("Only for you. Never sent anywhere.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    store.setNote(verseKey, input.getText().toString());
                    toast("Note saved");
                    rebuildKeepingPosition();
                })
                .setNeutralButton("Delete", (dialog, which) -> {
                    store.setNote(verseKey, "");
                    toast("Note removed");
                    rebuildKeepingPosition();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String indicatorFor(String verseKey) {
        Verse verse = dataset == null ? null : dataset.verse(verseKey);
        return verse == null || meta == null ? "" : meta.indicator(verse.id);
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
