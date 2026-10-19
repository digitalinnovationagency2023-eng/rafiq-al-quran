package com.qurancompanion.app;

import android.content.Context;

import com.qurancompanion.app.logic.Dataset;
import com.qurancompanion.app.logic.DatasetLoader;

import java.io.InputStream;
import java.util.List;

/** Loads the bundled dataset once; afterwards everything works offline. */
public final class Assets {
    private static Dataset cached;

    private Assets() {}

    public static synchronized Dataset dataset(Context context) {
        if (cached != null) return cached;
        try {
            cached = DatasetLoader.load(
                    read(context, "chapters.tsv"),
                    read(context, "quran.tsv"),
                    read(context, "translations.tsv"),
                    read(context, "translation-meta.tsv"),
                    read(context, "lexicon.tsv"),
                    read(context, "provenance.tsv")
            );
            return cached;
        } catch (Exception error) {
            throw new IllegalStateException("Bundled Quran dataset could not be loaded: " + error.getMessage(), error);
        }
    }

    private static List<String> read(Context context, String name) throws Exception {
        try (InputStream stream = context.getAssets().open(name)) {
            return DatasetLoader.readLines(stream);
        }
    }
}
