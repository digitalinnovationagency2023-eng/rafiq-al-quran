package com.rqmbooks.rafiqalquran;

import android.content.Context;

import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.DatasetLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the bundled dataset. The Arabic text is parsed at startup; translations
 * are loaded one language at a time so the first screen appears quickly and
 * memory stays small on a phone.
 */
public final class Assets {
    private static Dataset cached;
    private static final Set<String> loadedLanguages = new HashSet<>();

    private Assets() {}

    public static synchronized Dataset dataset(Context context) {
        if (cached != null) return cached;
        try {
            List<String> emptyTranslations = new ArrayList<>();
            cached = DatasetLoader.load(
                    read(context, "chapters.tsv"),
                    read(context, "quran.tsv"),
                    emptyTranslations,
                    read(context, "translation-meta.tsv"),
                    read(context, "lexicon.tsv"),
                    read(context, "provenance.tsv")
            );
            return cached;
        } catch (Exception error) {
            throw new IllegalStateException("Bundled Quran dataset could not be loaded: " + error.getMessage(), error);
        }
    }

    /** Loads one language's translation rows if they are not in memory yet. */
    public static synchronized void ensureLanguage(Context context, Dataset dataset, String language) {
        if (loadedLanguages.contains(language)) return;
        try (InputStream stream = context.getAssets().open("translations.tsv")) {
            List<String> lines = new ArrayList<>();
            String prefix = language + "\t";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(prefix)) lines.add(line);
                }
            }
            dataset.addTranslations(lines);
            loadedLanguages.add(language);
        } catch (Exception error) {
            throw new IllegalStateException("Translation for " + language + " could not be loaded: " + error.getMessage(), error);
        }
    }

    private static List<String> read(Context context, String name) throws Exception {
        try (InputStream stream = context.getAssets().open(name)) {
            return DatasetLoader.readLines(stream);
        }
    }
}
