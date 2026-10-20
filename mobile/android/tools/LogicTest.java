import com.rqmbooks.rafiqalquran.logic.Answer;
import com.rqmbooks.rafiqalquran.logic.AnswerComposer;
import com.rqmbooks.rafiqalquran.logic.AudioGate;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.DatasetLoader;
import com.rqmbooks.rafiqalquran.logic.DonationRules;
import com.rqmbooks.rafiqalquran.logic.Notices;
import com.rqmbooks.rafiqalquran.logic.Passage;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.StudyData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JVM verification of the pure app logic against the real bundled assets. This is
 * the same code the Android UI calls, so the checks are meaningful outside a device.
 */
public final class LogicTest {
    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args.length > 0 ? args[0] : "app/src/main/assets");
        Dataset dataset = DatasetLoader.load(
                read(assets.resolve("chapters.tsv")),
                read(assets.resolve("quran.tsv")),
                read(assets.resolve("translations.tsv")),
                read(assets.resolve("translation-meta.tsv")),
                read(assets.resolve("lexicon.tsv")),
                read(assets.resolve("provenance.tsv"))
        );

        check("dataset has 114 chapters", dataset.chapterCount() == 114);
        check("dataset has 6236 verses", dataset.verseCount() == 6236);
        check("dataset has 9 translations", dataset.translationCount() == 9);
        check("provenance checksum is recorded", dataset.provenance().checksum != null && dataset.provenance().checksum.length() == 64);
        check("first verse text is the Uthmani basmala", dataset.verse("1:1").arabic.startsWith("\u0628\u0650\u0633\u0652\u0645\u0650"));
        check("2:255 exists", dataset.verse("2:255") != null);

        Answer english = AnswerComposer.answer(dataset, "What does the Quran say about patience?", "en");
        check("english topic question is answered", "answer".equals(english.kind) && !english.passages.isEmpty());
        check("english answer carries the exact notice", english.notice.equals(Notices.NOTICE_EN));
        check("english answer keeps translation separate from Arabic", english.passages.get(0).translationText.length() > 10
                && !english.passages.get(0).translationText.equals(english.passages.get(0).arabic));
        for (Passage passage : english.passages) {
            check("passage " + passage.verseKey + " exists in dataset",
                    dataset.verse(passage.verseKey) != null
                            && dataset.verse(passage.verseKey).arabic.equals(passage.arabic));
        }

        Answer arabic = AnswerComposer.answer(dataset, "ماذا يقول القرآن عن الصبر", "ar");
        check("arabic topic question is answered", "answer".equals(arabic.kind) && !arabic.passages.isEmpty());
        check("arabic answer shows the exact Arabic notice", arabic.notice.equals(Notices.NOTICE_AR));
        check("arabic interface falls back to the English translation, labelled",
                arabic.passages.get(0).fallbackToEnglish && "en".equals(arabic.passages.get(0).translationLanguage));

        Answer reference = AnswerComposer.answer(dataset, "Show me 2:255", "en");
        check("explicit reference shows exactly that verse", "2:255".equals(reference.passages.get(0).verseKey));

        Answer nonsense = AnswerComposer.answer(dataset, "quantum tunnelling in superconductors", "en");
        check("unrelated question returns the fallback", "fallback".equals(nonsense.kind) && nonsense.passages.isEmpty());
        check("fallback wording is exact", nonsense.message.equals(Notices.FALLBACK_EN));
        check("fallback never claims the Quran does not mention something",
                !nonsense.message.toLowerCase().contains("does not mention"));

        Answer more = AnswerComposer.answer(dataset, "What does the Quran say about patience?", "en", english.shownVerseKeys);
        check("more requests do not repeat shown verses",
                more.passages.isEmpty() || more.passages.stream().noneMatch(p -> english.shownVerseKeys.contains(p.verseKey)));

        check("donation minimum is enforced", DonationRules.validate(10, "AED") != null);
        check("donation maximum is enforced", DonationRules.validate(9_000_000, "AED") != null);
        check("valid donation passes", DonationRules.validate(2500, "AED") == null);
        check("unsupported currency is rejected", DonationRules.validate(2500, "XYZ") != null);

        // ---------------------------------------------- structural mushaf meta
        QuranMeta meta = QuranMeta.parse(read(assets.resolve("quran-meta.tsv")));
        check("mushaf metadata covers all 6236 verses", meta.verseCount() == 6236);
        check("1:1 is juz 1 page 1",
                meta.juz(dataset.verse("1:1").id) == 1 && meta.page(dataset.verse("1:1").id) == 1);
        check("2:255 is juz 3 page 42",
                meta.juz(dataset.verse("2:255").id) == 3 && meta.page(dataset.verse("2:255").id) == 42);
        check("114:6 is juz 30 page 604",
                meta.juz(dataset.verse("114:6").id) == 30 && meta.page(dataset.verse("114:6").id) == 604);
        check("juz indicator text is exact",
                meta.indicator(dataset.verse("2:255").id).equals("Juz 3  \u00B7  Page 42"));
        check("page numbers never regress",
                meta.page(dataset.verse("1:1").id) <= meta.page(dataset.verse("114:6").id));

        // ------------------------------------------------------- study tools
        check("collection names are sanitized",
                StudyData.sanitizeCollectionName("  My | favourites\n").equals("My favourites"));
        check("empty collection falls back to General",
                StudyData.DEFAULT_COLLECTION.equals(StudyData.sanitizeCollectionName("   ")));
        List<String> collections = StudyData.defaultCollections();
        StudyData.addCollection(collections, "Reflections");
        StudyData.addCollection(collections, "reflections");
        check("collections deduplicate case-insensitively",
                collections.size() == 2 && collections.contains("Reflections"));
        check("collection names are bounded",
                StudyData.sanitizeCollectionName("x".repeat(80)).length() == StudyData.MAX_COLLECTION_LENGTH);
        check("progress percentage rounds to nearest",
                StudyData.percent(1, 3) == 33 && StudyData.percent(2, 3) == 67);
        check("progress text is exact",
                StudyData.formatProgress(3, 38).equals("3 of 38  \u00B7  8%"));
        check("notes are trimmed and bounded",
                StudyData.sanitizeNote("  note  ").equals("note")
                        && StudyData.sanitizeNote("x".repeat(3000)).length() == StudyData.MAX_NOTE_LENGTH);
        check("csv quoting follows RFC 4180",
                StudyData.csv("a,b", "say \"hi\"", "plain").equals("\"a,b\",\"say \"\"hi\"\"\",plain"));
        List<Integer> juz30 = StudyData.presetChapters(StudyData.PRESET_JUZ30);
        check("juz 30 preset is surahs 78-114",
                juz30.size() == 37 && juz30.get(0) == 78 && juz30.get(juz30.size() - 1) == 114);
        check("full preset is all 114 surahs", StudyData.presetChapters(StudyData.PRESET_FULL).size() == 114);
        check("verse keys sort numerically", StudyData.compareVerseKeys("2:9", "2:10") < 0);
        check("verse key validation rejects loose input",
                !StudyData.validVerseKey("2") && StudyData.validVerseKey("114:6"));
        check("next chapter skips completed ones",
                StudyData.nextChapter(Arrays.asList(1, 2, 3), Arrays.asList(1)) == 2);
        check("next chapter returns 0 when the plan is complete",
                StudyData.nextChapter(Arrays.asList(1, 2), Arrays.asList(1, 2)) == 0);
        check("plan chapters round-trip through the CSV encoding",
                StudyData.joinChapters(Arrays.asList(2, 5, 114)).equals("2,5,114")
                        && StudyData.parseChapters("2,5,114").equals(Arrays.asList(2, 5, 114)));

        // ------------------------------------------- rights-gated recitation
        check("recitation playback is gated off in this build",
                !AudioGate.playbackAllowedInThisBuild() && !AudioGate.rightsReviewRecorded());
        check("recitation gate requires both rights review and server flag",
                !AudioGate.playbackAllowed(false, true) && !AudioGate.playbackAllowed(true, false)
                        && AudioGate.playbackAllowed(true, true));
        check("recitation gate states the reason and source",
                AudioGate.pendingReason().contains("rights review")
                        && AudioGate.pendingReason().contains("Quran.com")
                        && AudioGate.pendingReason().contains("No audio is streamed"));

        System.out.println();
        System.out.println("Logic tests: " + passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            for (String failure : failures) System.out.println("FAIL: " + failure);
            System.exit(1);
        }
    }

    private static List<String> read(Path path) throws Exception {
        return Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
