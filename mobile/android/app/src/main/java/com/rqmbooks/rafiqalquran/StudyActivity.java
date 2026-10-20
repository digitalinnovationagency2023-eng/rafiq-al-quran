package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.StudyData;
import com.rqmbooks.rafiqalquran.logic.Verse;

import java.util.List;

/**
 * The study hub for device-local collections, personal notes, saved searches,
 * reading plans and plain-text/CSV exports.
 *
 * <p>All study data lives on the device in {@link StudyStore}; the Quran text and
 * translation previews come from the bundled {@link Dataset}. Nothing here calls
 * the network. A collection's verse list is rendered in place, so the system back
 * gesture returns to the hub.
 */
public final class StudyActivity extends Activity {
    private Prefs prefs;
    private StudyStore store;
    private Dataset dataset;
    private QuranMeta meta;
    private String language = "en";
    private boolean showingCollection;

    private ScrollView scroll;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        store = new StudyStore(this);
        language = prefs.language();

        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.bar(this, "Study tools", "Collections \u00B7 Notes \u00B7 Plans", () -> {
            if (showingCollection) renderHub();
            else finish();
        });
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
        content.addView(Ui.text(this, "Loading study data\u2026", 13f, Ui.MUTED));
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new Thread(() -> {
            Dataset loaded = null;
            QuranMeta loadedMeta = null;
            try {
                loaded = Assets.dataset(this);
                try {
                    Assets.ensureLanguage(this, loaded, prefs.language());
                } catch (Exception ignored) {
                    // The reference list still works without translations.
                }
                if (!"en".equals(prefs.language())) {
                    try {
                        Assets.ensureLanguage(this, loaded, "en");
                    } catch (Exception ignored) {
                        // English is only a preview fallback.
                    }
                }
                loadedMeta = Assets.meta(this);
            } catch (Exception ignored) {
                // Local-only sections stay usable without the dataset.
            }
            final Dataset loadedDataset = loaded;
            final QuranMeta loadedMetadata = loadedMeta;
            runOnUiThread(() -> {
                dataset = loadedDataset;
                meta = loadedMetadata;
                language = prefs.language();
                renderHub();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reading progress may have changed in the reader; refresh the hub.
        if (dataset != null && !showingCollection) {
            language = prefs.language();
            renderHub();
        }
    }

    @Override
    public void onBackPressed() {
        if (showingCollection) {
            renderHub();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------- hub
    private void renderHub() {
        showingCollection = false;
        scrollToTop();
        content.removeAllViews();

        TextView lede = Ui.text(this,
                "Collections, notes and plans are stored on this device only. The Quran text and "
                        + "translations always come from the bundled, validated dataset.",
                12f, Ui.MUTED);
        content.addView(lede);

        if (dataset == null) {
            TextView warning = Ui.text(this,
                    "The bundled Quran dataset could not be loaded, so verse references are shown as keys only.",
                    11f, Ui.DANGER);
            warning.setPadding(0, Ui.dp(this, 6), 0, 0);
            content.addView(warning);
        }

        renderOverview();
        renderCollections();
        renderNotes();
        renderSearches();
        renderPlans();
        renderExportSection();

        TextView footnote = Ui.text(this,
                "Reading the Quran is always free and offline. Exports leave the app only when you choose "
                        + "where to share them.",
                10f, Ui.MUTED);
        footnote.setPadding(0, Ui.dp(this, 22), 0, 0);
        content.addView(footnote);
    }

    private void renderOverview() {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(this);
        chips.setPadding(0, Ui.dp(this, 14), 0, 0);
        addCountChip(chips, store.bookmarkTotal() + " bookmarks", 0);
        addCountChip(chips, store.noteCount() + " notes", 1);
        addCountChip(chips, store.searches().size() + " searches", 2);
        addCountChip(chips, store.plans().size() + " plans", 3);
        strip.addView(chips);
        content.addView(strip);
    }

    private void addCountChip(LinearLayout row, String label, int position) {
        TextView chip = Ui.chip(this, label, Ui.GOLD_DEEP, Ui.GOLD_SOFT, Ui.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (position > 0) params.leftMargin = Ui.dp(this, 8);
        row.addView(chip, params);
    }

    // ----------------------------------------------------------- collections
    private void renderCollections() {
        LinearLayout header = sectionHeader("Bookmark collections");
        header.addView(textAction("New collection", () -> newCollectionDialog()));
        content.addView(header);

        LinearLayout card = card();
        List<String> collections = store.collections();
        for (int i = 0; i < collections.size(); i++) {
            final String name = collections.get(i);
            if (i > 0) card.addView(Ui.divider(this));
            card.addView(collectionRow(name));
        }
        if (collections.isEmpty()) card.addView(emptyText("No collections yet."));
        content.addView(card);

        TextView hint = Ui.text(this,
                "Tap a collection to browse it \u00B7 long-press to delete it (its bookmarks move to General).",
                10f, Ui.MUTED);
        hint.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(hint);
    }

    private LinearLayout collectionRow(final String name) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        LinearLayout text = Ui.column(this);
        TextView title = Ui.text(this, name, 14f, Ui.INK);
        text.addView(title);
        int count = store.bookmarkCount(name);
        TextView meta = Ui.text(this, count + (count == 1 ? " saved verse" : " saved verses"), 10f, Ui.MUTED);
        meta.setPadding(0, Ui.dp(this, 2), 0, 0);
        text.addView(meta);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = Ui.text(this, "\u203A", 18f, Ui.GOLD);
        chevron.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        row.addView(chevron);
        tap(row, () -> showCollection(name));
        row.setOnLongClickListener(view -> {
            confirmDeleteCollection(name);
            return true;
        });
        return row;
    }

    private void showCollection(final String name) {
        showingCollection = true;
        scrollToTop();
        content.removeAllViews();

        LinearLayout backRow = Ui.row(this);
        backRow.addView(textAction("\u2190 All collections", () -> renderHub()));
        content.addView(backRow);

        TextView title = Ui.text(this, name, 22f, Ui.INK);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        content.addView(title);

        List<String> keys = store.bookmarkedKeys(name);
        TextView meta = Ui.text(this,
                keys.size() + (keys.size() == 1 ? " saved verse" : " saved verses")
                        + " \u00B7 tap a verse to open it in the reader",
                11f, Ui.MUTED);
        content.addView(meta);

        if (keys.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(emptyText("Nothing saved in this collection yet. Open a verse in the reader and save it here."));
            content.addView(empty);
            return;
        }

        LinearLayout card = card();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) card.addView(Ui.divider(this));
            card.addView(collectionVerseRow(keys.get(i)));
        }
        content.addView(card);
    }

    private LinearLayout collectionVerseRow(final String key) {
        LinearLayout row = Ui.column(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        LinearLayout head = Ui.row(this);
        head.addView(Ui.label(this, referenceLabel(key)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.text(this, "\u203A", 16f, Ui.GOLD));
        row.addView(head);

        if (dataset != null) {
            Verse verse = dataset.verse(key);
            if (verse != null) {
                TextView arabic = Ui.text(this, verse.arabic, 20f * prefs.textScale(), Ui.INK);
                arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                arabic.setGravity(android.view.Gravity.RIGHT);
                arabic.setLineSpacing(Ui.dp(this, 6), 1.2f);
                arabic.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
                row.addView(arabic);
            }
            String preview = previewTranslation(key);
            if (!preview.isEmpty()) {
                TextView translation = Ui.text(this, preview, 12f, Ui.MUTED);
                translation.setPadding(0, Ui.dp(this, 4), 0, 0);
                row.addView(translation);
            }
            if (verse != null && meta != null) {
                TextView indicator = Ui.text(this, meta.indicator(verse.id), 10f, Ui.MUTED);
                indicator.setPadding(0, Ui.dp(this, 4), 0, 0);
                row.addView(indicator);
            }
        }
        tap(row, () -> openReader(key));
        return row;
    }

    private String previewTranslation(String key) {
        if (dataset == null) return "";
        String text = dataset.translationText(language, key);
        if (text == null || text.isEmpty()) text = dataset.translationText("en", key);
        if (text == null) return "";
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() > 180 ? clean.substring(0, 177) + "\u2026" : clean;
    }

    private void confirmDeleteCollection(final String name) {
        if (StudyData.DEFAULT_COLLECTION.equalsIgnoreCase(name)) {
            toast("The General collection cannot be deleted.");
            return;
        }
        int count = store.bookmarkCount(name);
        new AlertDialog.Builder(this)
                .setTitle("Delete collection?")
                .setMessage("\"" + name + "\" will be removed. " + count
                        + (count == 1 ? " saved verse moves" : " saved verses move")
                        + " to General; verses and notes are not deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.removeCollection(name);
                    renderHub();
                    toast("Collection deleted. Bookmarks moved to General.");
                })
                .show();
    }

    private void newCollectionDialog() {
        final EditText input = new EditText(this);
        input.setHint("Collection name");
        input.setSingleLine(true);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New collection")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String raw = input.getText().toString();
            if (raw.trim().isEmpty()) {
                input.setError("Enter a collection name");
                return;
            }
            String name = store.addCollection(StudyData.sanitizeCollectionName(raw));
            dialog.dismiss();
            renderHub();
            toast("Collection \"" + name + "\" ready.");
        });
    }

    // ----------------------------------------------------------------- notes
    private void renderNotes() {
        LinearLayout header = sectionHeader("Personal notes");
        header.addView(textAction("CSV", () -> share("Rafiq notes (CSV)", notesCsv())));
        header.addView(textAction("Text", () -> share("Rafiq notes", notesText())));
        content.addView(header);

        LinearLayout card = card();
        List<String> keys = store.noteKeys();
        int limit = Math.min(keys.size(), 50);
        for (int i = 0; i < limit; i++) {
            if (i > 0) card.addView(Ui.divider(this));
            card.addView(noteRow(keys.get(i)));
        }
        if (keys.isEmpty()) card.addView(emptyText("No personal notes yet. Open a verse in the reader and add a note."));
        else if (keys.size() > limit) card.addView(emptyText("Showing 50 of " + keys.size() + " notes. Export to read them all."));
        content.addView(card);
    }

    private LinearLayout noteRow(final String key) {
        LinearLayout row = Ui.column(this);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        LinearLayout head = Ui.row(this);
        head.addView(Ui.label(this, referenceLabel(key)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.text(this, "\u203A", 16f, Ui.GOLD));
        row.addView(head);
        String snippet = snippet(store.note(key), 140);
        if (!snippet.isEmpty()) {
            TextView note = Ui.text(this, snippet, 12f, Ui.MUTED);
            note.setPadding(0, Ui.dp(this, 4), 0, 0);
            row.addView(note);
        }
        tap(row, () -> openReader(key));
        return row;
    }

    // -------------------------------------------------------------- searches
    private void renderSearches() {
        LinearLayout header = sectionHeader("Saved searches");
        List<String> searches = store.searches();
        if (!searches.isEmpty()) {
            header.addView(textAction("Clear", () -> confirmClearSearches()));
        }
        content.addView(header);

        LinearLayout card = card();
        for (int i = 0; i < searches.size(); i++) {
            final String query = searches.get(i);
            if (i > 0) card.addView(Ui.divider(this));
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            row.addView(Ui.text(this, "\u201C" + query + "\u201D", 13f, Ui.INK),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, "\u203A", 16f, Ui.GOLD));
            tap(row, () -> askSearch(query));
            card.addView(row);
        }
        if (searches.isEmpty()) card.addView(emptyText("No saved searches yet. Searches you run in the assistant appear here."));
        content.addView(card);
    }

    private void askSearch(String query) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_ASK, query);
        try {
            startActivity(intent);
        } catch (Exception error) {
            toast("The assistant could not be opened.");
        }
    }

    private void confirmClearSearches() {
        new AlertDialog.Builder(this)
                .setTitle("Clear saved searches?")
                .setMessage("This removes the saved search list from this device. Your reading, notes and bookmarks are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    store.clearSearches();
                    renderHub();
                    toast("Saved searches cleared.");
                })
                .show();
    }

    // ----------------------------------------------------------------- plans
    private void renderPlans() {
        LinearLayout header = sectionHeader("Reading plans");
        header.addView(textAction("New plan", () -> newPlanDialog()));
        content.addView(header);

        List<StudyStore.Plan> plans = store.plans();
        for (StudyStore.Plan plan : plans) content.addView(planCard(plan));
        if (plans.isEmpty()) {
            LinearLayout card = card();
            card.addView(emptyText("No reading plans yet. A plan tracks which surahs you have completed, at your own pace."));
            content.addView(card);
        }
    }

    private View planCard(final StudyStore.Plan plan) {
        LinearLayout card = card();

        LinearLayout head = Ui.row(this);
        TextView title = Ui.text(this, plan.name, 15f, Ui.INK);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(this, plan.percent() + "%", Ui.GOLD_DEEP, Ui.GOLD_SOFT, Ui.LINE));
        card.addView(head);

        TextView meta = Ui.text(this, StudyData.presetTitle(plan.preset) + "  \u00B7  " + plan.days + "-day goal",
                11f, Ui.MUTED);
        meta.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        card.addView(meta);

        card.addView(progressBar(plan.percent()));

        TextView progress = Ui.text(this, plan.progressText(), 11f, Ui.MUTED);
        progress.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(progress);

        LinearLayout actions = Ui.row(this);
        Button continueButton = Ui.button(this, "Continue", true);
        boolean complete = plan.isComplete();
        final int next = plan.nextChapter();
        continueButton.setEnabled(!complete && next > 0);
        continueButton.setOnClickListener(view -> openReader(next, null));
        Button delete = Ui.button(this, "Delete", false);
        delete.setTextColor(Ui.DANGER);
        delete.setOnClickListener(view -> confirmDeletePlan(plan));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteParams.leftMargin = Ui.dp(this, 8);
        actions.addView(continueButton);
        actions.addView(delete, deleteParams);
        card.addView(actions);

        if (complete) {
            TextView done = Ui.text(this, "Plan complete. Nothing left to read in this plan.", 10f, Ui.GREEN);
            done.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(done);
        }
        return card;
    }

    /** Gold fill on silver track, sized by plan percentage with view weights. */
    private View progressBar(int percent) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(Ui.rounded(Ui.LINE, 0, 3, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        track.setLayoutParams(params);
        float fraction = Math.max(0f, Math.min(1f, percent / 100f));
        if (fraction > 0f) {
            View fill = new View(this);
            fill.setBackground(Ui.rounded(Ui.GOLD, 0, 3, this));
            track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fraction));
        }
        if (fraction < 1f) {
            View remainder = new View(this);
            track.addView(remainder, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - fraction));
        }
        return track;
    }

    private void confirmDeletePlan(final StudyStore.Plan plan) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reading plan?")
                .setMessage("\"" + plan.name + "\" is removed from this device. Quran text, bookmarks and notes are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deletePlan(plan.id);
                    renderHub();
                    toast("Reading plan deleted.");
                })
                .show();
    }

    private void newPlanDialog() {
        final String[] presetKeys = {StudyData.PRESET_FULL, StudyData.PRESET_JUZ30, StudyData.PRESET_LAST10};

        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);

        final EditText name = new EditText(this);
        name.setHint("Plan name (optional)");
        name.setSingleLine(true);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        form.addView(name);

        final Spinner preset = new Spinner(this);
        String[] labels = {
                StudyData.presetTitle(StudyData.PRESET_FULL),
                StudyData.presetTitle(StudyData.PRESET_JUZ30),
                StudyData.presetTitle(StudyData.PRESET_LAST10)
        };
        preset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(preset);

        final EditText days = new EditText(this);
        days.setHint("Days (for example 30)");
        days.setSingleLine(true);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        days.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        form.addView(days);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New reading plan")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String rawDays = days.getText().toString().trim();
            int dayCount = 30;
            if (!rawDays.isEmpty()) {
                try {
                    dayCount = Integer.parseInt(rawDays);
                } catch (NumberFormatException error) {
                    days.setError("Enter a number of days");
                    return;
                }
            }
            dayCount = Math.max(1, Math.min(365, dayCount));
            String presetKey = presetKeys[preset.getSelectedItemPosition()];
            StudyStore.Plan created = store.createPlan(name.getText().toString(), presetKey, dayCount);
            dialog.dismiss();
            renderHub();
            toast("Reading plan \"" + created.name + "\" created.");
        });
    }

    // ------------------------------------------------------ export & share
    private void renderExportSection() {
        content.addView(sectionHeader("Export & share"));

        LinearLayout card = card();
        card.addView(Ui.text(this,
                "Plain-text exports you can send to any app on the device. Nothing is uploaded automatically.",
                12f, Ui.MUTED));

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        Button bookmarks = Ui.button(this, "Bookmarks (CSV)", false);
        bookmarks.setOnClickListener(view -> share("Rafiq bookmarks (CSV)", bookmarksCsv()));
        Button references = Ui.button(this, "References (text)", false);
        references.setOnClickListener(view -> share("Rafiq references", bookmarksText()));
        LinearLayout.LayoutParams referencesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        referencesParams.leftMargin = Ui.dp(this, 8);
        actions.addView(bookmarks);
        actions.addView(references, referencesParams);
        card.addView(actions);
        content.addView(card);
    }

    private String bookmarksCsv() {
        StringBuilder out = new StringBuilder();
        out.append(StudyData.csvHeader("Reference", "Collection", "Note")).append('\n');
        for (String key : store.bookmarkedKeys(null)) {
            String collection = store.collectionOf(key);
            if (collection == null || collection.isEmpty()) collection = StudyData.DEFAULT_COLLECTION;
            out.append(StudyData.csv(referenceLabel(key), collection, store.note(key))).append('\n');
        }
        return out.toString();
    }

    private String bookmarksText() {
        List<String> keys = store.bookmarkedKeys(null);
        if (keys.isEmpty()) return "";
        StringBuilder out = new StringBuilder("Bookmarked references \u2014 Rafiq Al-Quran\n\n");
        for (String key : keys) out.append(referenceLabel(key)).append('\n');
        out.append('\n').append("Exported from Rafiq Al-Quran\n");
        return out.toString();
    }

    private String notesCsv() {
        StringBuilder out = new StringBuilder();
        out.append(StudyData.csvHeader("Reference", "Collection", "Note")).append('\n');
        for (String key : store.noteKeys()) {
            String collection = store.collectionOf(key);
            if (collection == null) collection = "";
            out.append(StudyData.csv(referenceLabel(key), collection, store.note(key))).append('\n');
        }
        return out.toString();
    }

    private String notesText() {
        List<String> keys = store.noteKeys();
        if (keys.isEmpty()) return "";
        StringBuilder out = new StringBuilder("Personal notes \u2014 Rafiq Al-Quran\n\n");
        for (String key : keys) {
            out.append(referenceLabel(key)).append('\n').append(store.note(key)).append("\n\n");
        }
        out.append("Exported from Rafiq Al-Quran\n");
        return out.toString();
    }

    private void share(String subject, String body) {
        if (body == null || body.trim().isEmpty()) {
            toast("There is nothing to export yet. Save a verse or write a note first.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(Intent.createChooser(send, subject));
        } catch (Exception error) {
            toast("No app is available to share the export.");
        }
    }

    // --------------------------------------------------------------- helpers
    private LinearLayout sectionHeader(String title) {
        LinearLayout header = Ui.row(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 24);
        header.setLayoutParams(params);
        header.addView(Ui.sectionLabel(this, title));
        header.addView(Ui.spacer(this));
        return header;
    }

    private LinearLayout card() {
        LinearLayout box = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 8);
        box.setLayoutParams(params);
        return box;
    }

    /** Gold-deep text action that reads correctly on the paper background. */
    private TextView textAction(String label, Runnable action) {
        TextView view = Ui.text(this, label.toUpperCase(), 11f, Ui.GOLD_DEEP);
        view.setLetterSpacing(0.08f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView emptyText(String message) {
        TextView view = Ui.text(this, message, 12f, Ui.MUTED);
        view.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        return view;
    }

    /** Adds a click listener plus the platform ripple without changing the card fill. */
    private void tap(View view, Runnable action) {
        view.setOnClickListener(v -> action.run());
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            try {
                view.setForeground(getDrawable(value.resourceId));
            } catch (Exception ignored) {
                // A foreground is a nicety, never a requirement.
            }
        }
    }

    private String referenceLabel(String key) {
        int chapterId = chapterIdOf(key);
        Chapter chapter = dataset == null || chapterId <= 0 ? null : dataset.chapter(chapterId);
        return chapter == null ? key : chapter.nameEnglish + " " + key;
    }

    private int chapterIdOf(String key) {
        try {
            return Integer.parseInt(key.split(":")[0]);
        } catch (Exception error) {
            return 0;
        }
    }

    private String snippet(String text, int max) {
        if (text == null) return "";
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() > max ? clean.substring(0, Math.max(0, max - 1)) + "\u2026" : clean;
    }

    private void openReader(String key) {
        openReader(chapterIdOf(key), key);
    }

    private void openReader(int chapterId, String verseKey) {
        if (chapterId <= 0) {
            toast("That reference could not be opened.");
            return;
        }
        try {
            startActivity(ReaderActivity.intent(this, chapterId, verseKey));
        } catch (Exception error) {
            toast("The reader could not be opened.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void scrollToTop() {
        if (scroll == null) return;
        scroll.post(() -> scroll.scrollTo(0, 0));
    }
}
