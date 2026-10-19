package com.qurancompanion.app.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable in-memory dataset built from the bundled asset files. Everything the
 * app shows comes from here; no network call is needed to read or search.
 */
public final class Dataset {
    final Map<Integer, Chapter> chapters = new LinkedHashMap<>();
    final Map<String, Chapter> chaptersByName = new HashMap<>();
    final List<Verse> verses = new ArrayList<>();
    final Map<String, Verse> verseByKey = new HashMap<>();
    final Map<String, TranslationMeta> translations = new LinkedHashMap<>();
    final Map<String, String> translationText = new HashMap<>();
    final Map<String, List<String>> lexiconByLanguage = new HashMap<>();
    final List<Concept> concepts = new ArrayList<>();
    final Provenance provenance = new Provenance();

    final Map<String, List<Integer>> arabicIndex = new HashMap<>();
    final Map<String, List<Integer>> translationIndex = new HashMap<>();

    public int verseCount() { return verses.size(); }
    public int chapterCount() { return chapters.size(); }
    public int translationCount() { return translations.size(); }
    public Provenance provenance() { return provenance; }
    public Map<String, TranslationMeta> translations() { return Collections.unmodifiableMap(translations); }

    public Chapter chapter(int id) { return chapters.get(id); }
    public Verse verse(String key) { return verseByKey.get(key); }

    public String translationText(String language, String verseKey) {
        return translationText.get(language + "|" + verseKey);
    }

    public TranslationMeta translationMeta(String language) {
        return translations.get(language);
    }

    public List<Verse> chapterVerses(int chapterId) {
        List<Verse> out = new ArrayList<>();
        for (Verse verse : verses) if (verse.chapterId == chapterId) out.add(verse);
        return out;
    }

    static Dataset build(List<String> chapterLines, List<String> quranLines, List<String> translationLines,
                         List<String> translationMetaLines, List<String> lexiconLines, List<String> provenanceLines) {
        Dataset dataset = new Dataset();

        for (String line : chapterLines) {
            if (line.trim().isEmpty()) continue;
            Chapter chapter = Chapter.parse(line);
            dataset.chapters.put(chapter.id, chapter);
            dataset.chaptersByName.put(chapter.nameEnglish.toLowerCase(), chapter);
            dataset.chaptersByName.put(chapter.nameArabic, chapter);
        }

        int id = 0;
        for (String line : quranLines) {
            if (line.trim().isEmpty()) continue;
            int tab = line.indexOf('\t');
            String key = line.substring(0, tab);
            String arabic = line.substring(tab + 1);
            String[] keyParts = key.split(":");
            int chapterId = Integer.parseInt(keyParts[0]);
            int number = Integer.parseInt(keyParts[1]);
            Chapter chapter = dataset.chapters.get(chapterId);
            if (chapter == null) throw new IllegalStateException("Chapter missing for verse " + key);
            Verse verse = new Verse(++id, key, chapterId, number, arabic, chapter);
            dataset.verses.add(verse);
            dataset.verseByKey.put(key, verse);
        }

        for (String line : translationMetaLines) {
            if (line.trim().isEmpty()) continue;
            TranslationMeta meta = TranslationMeta.parse(line);
            dataset.translations.put(meta.language, meta);
        }

        for (String line : translationLines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            dataset.translationText.put(parts[0] + "|" + parts[1], parts[2]);
        }

        for (String line : lexiconLines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t", -1);
            List<String> arabic = splitTerms(parts.length > 1 ? parts[1] : "");
            List<List<String>> byLanguage = new ArrayList<>();
            for (int i = 2; i < parts.length; i++) byLanguage.add(splitTerms(parts[i]));
            Concept concept = new Concept(parts[0], arabic, byLanguage);
            dataset.concepts.add(concept);
        }

        for (String line : provenanceLines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "quran":
                    dataset.provenance.dataset = parts[1];
                    dataset.provenance.version = parts[2];
                    dataset.provenance.sourceUrl = parts[3];
                    dataset.provenance.checksum = parts[4];
                    dataset.provenance.importedAt = parts[5];
                    break;
                case "verse-count":
                    dataset.provenance.verseCount = Integer.parseInt(parts[1]);
                    break;
                case "chapter-count":
                    dataset.provenance.chapterCount = Integer.parseInt(parts[1]);
                    break;
                case "translation-count":
                    dataset.provenance.translationCount = Integer.parseInt(parts[1]);
                    break;
                default:
                    break;
            }
        }

        dataset.buildIndexes();
        dataset.validate();
        return dataset;
    }

    private static List<String> splitTerms(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return out;
        for (String term : value.split(",")) {
            String trimmed = term.trim().toLowerCase();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private void buildIndexes() {
        for (int index = 0; index < verses.size(); index++) {
            Verse verse = verses.get(index);
            for (String token : new java.util.LinkedHashSet<>(verse.tokens)) {
                for (String form : ArabicText.searchForms(token)) {
                    List<Integer> bucket = arabicIndex.get(form);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        arabicIndex.put(form, bucket);
                    }
                    bucket.add(index);
                }
            }
        }

        Map<String, Integer> verseIndexByKey = new HashMap<>();
        for (int index = 0; index < verses.size(); index++) verseIndexByKey.put(verses.get(index).key, index);

        for (Map.Entry<String, String> entry : translationText.entrySet()) {
            String composite = entry.getKey();
            int pipe = composite.indexOf('|');
            String language = composite.substring(0, pipe);
            Integer verseIndex = verseIndexByKey.get(composite.substring(pipe + 1));
            if (verseIndex == null) continue;
            for (String token : new java.util.LinkedHashSet<>(latinTokens(entry.getValue()))) {
                if (token.length() < 2) continue;
                for (String form : latinForms(token)) {
                    String key = language + "|" + form;
                    List<Integer> bucket = translationIndex.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        translationIndex.put(key, bucket);
                    }
                    bucket.add(verseIndex);
                }
            }
        }

        for (Concept concept : concepts) {
            for (int languageIndex = 0; languageIndex < Language.ALL.size(); languageIndex++) {
                if (languageIndex >= concept.byLanguage.size()) continue;
                String language = Language.ALL.get(languageIndex).code;
                List<String> terms = lexiconByLanguage.get(language);
                if (terms == null) {
                    terms = new ArrayList<>();
                    lexiconByLanguage.put(language, terms);
                }
                for (String term : concept.byLanguage.get(languageIndex)) {
                    if (term.length() > 2 && !terms.contains(term)) terms.add(term);
                }
            }
        }
    }

    static List<String> latinTokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String normalized = text.toLowerCase().replaceAll("[^\\p{L}\\p{N}\\s'-]+", " ");
        for (String token : normalized.split("\\s+")) if (!token.isEmpty()) out.add(token);
        return out;
    }

    static List<String> latinForms(String token) {
        List<String> forms = new ArrayList<>();
        forms.add(token);
        if (token.length() > 4 && token.endsWith("ies")) forms.add(token.substring(0, token.length() - 3) + "y");
        if (token.length() > 3 && token.endsWith("es")) forms.add(token.substring(0, token.length() - 2));
        if (token.length() > 3 && token.endsWith("s")) forms.add(token.substring(0, token.length() - 1));
        if (token.length() > 5 && token.endsWith("ing")) forms.add(token.substring(0, token.length() - 3));
        if (token.length() > 4 && token.endsWith("ed")) forms.add(token.substring(0, token.length() - 2));
        return forms;
    }

    private void validate() {
        if (chapters.size() != 114) throw new IllegalStateException("Expected 114 chapters, found " + chapters.size());
        if (verses.size() != 6236) throw new IllegalStateException("Expected 6236 verses, found " + verses.size());
        for (Chapter chapter : chapters.values()) {
            int actual = chapterVerses(chapter.id).size();
            if (actual != chapter.verseCount) {
                throw new IllegalStateException("Chapter " + chapter.id + " verse count mismatch: " + actual);
            }
        }
    }
}
