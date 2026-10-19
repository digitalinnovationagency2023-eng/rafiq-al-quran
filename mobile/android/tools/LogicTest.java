import com.qurancompanion.app.logic.Answer;
import com.qurancompanion.app.logic.AnswerComposer;
import com.qurancompanion.app.logic.Dataset;
import com.qurancompanion.app.logic.DatasetLoader;
import com.qurancompanion.app.logic.DonationRules;
import com.qurancompanion.app.logic.Notices;
import com.qurancompanion.app.logic.Passage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
