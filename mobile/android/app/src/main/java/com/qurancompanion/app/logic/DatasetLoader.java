package com.qurancompanion.app.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the bundled TSV assets into a Dataset. */
public final class DatasetLoader {
    private DatasetLoader() {}

    public static Dataset load(List<String> chapters, List<String> quran, List<String> translations,
                               List<String> translationMeta, List<String> lexicon, List<String> provenance) {
        return Dataset.build(chapters, quran, translations, translationMeta, lexicon, provenance);
    }

    public static List<String> readLines(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) lines.add(line);
            }
        }
        return lines;
    }
}
