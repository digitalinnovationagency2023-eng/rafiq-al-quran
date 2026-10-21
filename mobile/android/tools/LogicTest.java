import com.rqmbooks.rafiqalquran.logic.Answer;
import com.rqmbooks.rafiqalquran.logic.AnswerComposer;
import com.rqmbooks.rafiqalquran.logic.AudioGate;
import com.rqmbooks.rafiqalquran.logic.Conversation;
import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.DatasetLoader;
import com.rqmbooks.rafiqalquran.logic.DonationRules;
import com.rqmbooks.rafiqalquran.logic.Notices;
import com.rqmbooks.rafiqalquran.logic.Passage;
import com.rqmbooks.rafiqalquran.logic.QuranMeta;
import com.rqmbooks.rafiqalquran.logic.StudyData;
import com.rqmbooks.rafiqalquran.logic.VoiceLanguage;

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

        // ------------------------------------------------- recorded recitation
        check("recitation rights review is recorded for this build", AudioGate.rightsReviewRecorded());
        check("recitation playback is enabled in this build", AudioGate.playbackAllowedInThisBuild());
        check("recitation gate still requires both conditions",
                !AudioGate.playbackAllowed(false, true) && AudioGate.playbackAllowed(true, true));
        check("recitation attribution names Quran.com",
                AudioGate.attribution().contains("Quran.com")
                        && AudioGate.RIGHTS_DECISION.contains("rights review recorded"));

        // -------------------------------------------------- conversation layer
        Conversation.Reply salute = Conversation.classify("Hello!");
        check("greeting gets a conversational reply",
                salute != null && salute.kind == Conversation.Kind.GREETING
                        && salute.text.toLowerCase().contains("quran"));
        Conversation.Reply salam = Conversation.classify("Assalamu alaikum");
        check("Islamic greeting is recognised", salam != null && salam.kind == Conversation.Kind.SALAM);
        Conversation.Reply thanks = Conversation.classify("Thank you so much");
        check("thanks gets a short reply", thanks != null && thanks.kind == Conversation.Kind.THANKS);
        Conversation.Reply ruling = Conversation.classify("Is it haram to listen to music? Give me a fatwa");
        check("ruling request refuses to interpret and points to scholars",
                ruling != null && ruling.kind == Conversation.Kind.RULING_REQUEST
                        && ruling.text.toLowerCase().contains("scholar"));
        Conversation.Reply offTopic = Conversation.classify("What is the weather today?");
        check("off-topic question is handled gracefully",
                offTopic != null && offTopic.kind == Conversation.Kind.OFFTOPIC);
        check("advice questions are routed to Quran retrieval",
                Conversation.classify("What does the Quran say about patience?") == null);
        Conversation.Reply bookmarkHelp = Conversation.classify("How do I bookmark a verse?");
        check("feature help answers bookmarks",
                bookmarkHelp != null && bookmarkHelp.kind == Conversation.Kind.HELP
                        && bookmarkHelp.text.toLowerCase().contains("save"));
        Conversation.Reply recitationHelp = Conversation.classify("How do I listen to recitation?");
        check("feature help answers recitation",
                recitationHelp != null && recitationHelp.kind == Conversation.Kind.HELP
                        && recitationHelp.text.toLowerCase().contains("quran.com"));
        Conversation.Reply languageHelp = Conversation.classify("Where can I change the language?");
        check("feature help answers language",
                languageHelp != null && languageHelp.kind == Conversation.Kind.HELP);
        Conversation.Reply aiHelp = Conversation.classify("How does the AI work?");
        check("feature help answers AI",
                aiHelp != null && aiHelp.kind == Conversation.Kind.HELP
                        && aiHelp.text.toLowerCase().contains("never"));
        Conversation.Reply helpOverview = Conversation.classify("help");
        check("feature help offers an overview", helpOverview != null && helpOverview.kind == Conversation.Kind.HELP);
        check("retrieval questions still bypass feature help",
                Conversation.classify("What does the Quran say about mercy?") == null);
        check("overlong messages are not treated as small talk",
                Conversation.classify(new String(new char[800]).replace('\0', 'x')) == null);
        check("guidance names scholars, Hadith and Fiqh study",
                Conversation.guidance().stream().anyMatch(line -> line.toLowerCase().contains("scholar"))
                        && Conversation.guidance().stream().anyMatch(line -> line.toLowerCase().contains("hadith")));
        check("guidance disclaims responsibility",
                Conversation.guidance().stream().anyMatch(line -> line.toLowerCase().contains("no responsibility")));
        check("attachment fallback mentions the AI service",
                Conversation.attachmentUnavailable().toLowerCase().contains("ai service"));
        check("AI consent text says verses never come from the AI",
                Conversation.aiConsentMessage().contains("never supplies Quran text"));

        // ---------------------------------------------------------- voice languages
        check("voice locale mapping covers all ten interface languages",
                "en-US".equals(VoiceLanguage.localeTag("en")) && "ar-SA".equals(VoiceLanguage.localeTag("ar"))
                        && "ur-PK".equals(VoiceLanguage.localeTag("ur")) && "bn-BD".equals(VoiceLanguage.localeTag("bn"))
                        && "fa-IR".equals(VoiceLanguage.localeTag("fa")) && "sw-KE".equals(VoiceLanguage.localeTag("sw")));
        for (String code : new String[]{"ar", "en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"}) {
            String tag = VoiceLanguage.localeTag(code);
            check("voice locale for " + code + " is a real BCP-47 tag",
                    java.util.Locale.forLanguageTag(tag).getLanguage().equals(code));
        }
        check("unknown voice language falls back to en-US", "en-US".equals(VoiceLanguage.localeTag("zz")));
        check("english answer is voiced and spoken in English",
                "en".equals(VoiceLanguage.spokenLanguage(english))
                        && VoiceLanguage.spokenText(english, 4000).contains(english.passages.get(0).translationText));
        check("arabic answer speaks the English translation (no Arabic translation rows)",
                "en".equals(VoiceLanguage.spokenLanguage(arabic)));
        check("arabic answer does not speak the Arabic connective with an English voice",
                !VoiceLanguage.spokenText(arabic, 4000).contains(Notices.connectiveFound("ar", 1)));
        check("arabic fallback is spoken in Arabic",
                "ar".equals(VoiceLanguage.spokenLanguage(Answer.fallback("ar", new ArrayList<String>()))));
        check("english fallback is spoken in English",
                "en".equals(VoiceLanguage.spokenLanguage(Answer.fallback("ur", new ArrayList<String>()))));
        Answer arabicConversation = new Answer("conversation", "ar", "Hello!", new ArrayList<Passage>(), 0,
                "", false, null, new ArrayList<String>(), false, new ArrayList<String>());
        check("conversation replies are spoken in English (their text is English)",
                "en".equals(VoiceLanguage.spokenLanguage(arabicConversation)));
        Answer urduReference = AnswerComposer.answer(dataset, "Show me 2:255", "ur");
        check("urdu answer speaks the Urdu translation", "ur".equals(VoiceLanguage.spokenLanguage(urduReference)));
        String urduSpoken = VoiceLanguage.spokenText(urduReference, 4000);
        check("non-english spoken text omits English reference labels",
                !urduSpoken.contains("Al-Baqarah") && !urduSpoken.contains("I found"));
        check("spoken text is capped at the platform length",
                VoiceLanguage.spokenText(urduReference, 20).length() <= 20);

        // ------------------------------------------------ named figures and topics
        Answer essa = AnswerComposer.answer(dataset, "Who is Hazrat Essa?", "en");
        check("Hazrat Essa resolves to Isa/Jesus and returns verses",
                "answer".equals(essa.kind) && !essa.passages.isEmpty());
        check("Essa answer names the figure", essa.message.contains("Isa") || essa.message.contains("Jesus"));
        check("Essa answer combines several verses", essa.passages.size() >= 3);
        Answer jacob = AnswerComposer.answer(dataset, "Who is Yaqoob?", "en");
        check("Yaqoob resolves to Jacob and returns verses",
                "answer".equals(jacob.kind) && !jacob.passages.isEmpty()
                        && (jacob.message.contains("Yaqub") || jacob.message.contains("Jacob")));
        Answer theft = AnswerComposer.answer(dataset, "What is the law about theft?", "en");
        check("theft/law question is answered with verses", "answer".equals(theft.kind) && !theft.passages.isEmpty());
        check("theft answer includes the Quranic theft verse 5:38",
                theft.passages.stream().anyMatch(p -> p.verseKey.equals("5:38")));
        Answer arabicIsa = AnswerComposer.answer(dataset, "من هو عيسى", "ar");
        check("Arabic name question (عيسى) is answered", "answer".equals(arabicIsa.kind) && !arabicIsa.passages.isEmpty());
        check("honorifics alone are not a named figure", dataset.findEntity("Who is the prophet?") == null);
        check("ordinary topic questions are not treated as a named figure",
                dataset.findEntity("What does the Quran say about patience?") == null);
        check("entity alias list is loaded from the bundled lexicon",
                dataset.findEntity("Tell me about Jesus") != null && dataset.findEntity("hazrat essa") != null);

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
