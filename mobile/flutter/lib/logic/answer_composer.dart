import 'answer.dart';
import 'chapter.dart';
import 'concept.dart';
import 'dataset.dart';
import 'notices.dart';
import 'passage.dart';
import 'retrieval.dart';
import 'verse.dart';

/// Builds the answer shown in the app from verified dataset rows only.
class AnswerComposer {
  AnswerComposer._();

  static const double confidenceFloor = 0.42;
  static const int maxVerses = 8;
  static const int maxEntityVerses = 14;

  /// Reviewed central verses for major figures, merged ahead of retrieval results.
  static const Map<String, List<String>> _coreVerses = {
    'entity-isa': ['3:45', '3:55', '4:157', '4:158', '19:33', '5:110', '61:6'],
    'entity-maryam': ['3:42', '3:45', '19:16', '19:20', '19:27', '66:12'],
    'entity-musa': ['2:53', '7:103', '7:104', '20:9', '20:24', '28:7', '28:15'],
    'entity-ibrahim': ['2:124', '2:127', '3:67', '3:95', '6:74', '14:35', '21:51'],
    'entity-muhammad': ['3:144', '33:40', '47:2', '48:29', '9:128', '21:107'],
    'entity-nuh': ['11:25', '11:26', '11:36', '11:42', '11:43', '71:1', '71:10'],
    'entity-yusuf': ['12:4', '12:7', '12:8', '12:21', '12:90', '12:101'],
    'entity-dawud': ['2:251', '4:163', '17:55', '21:78', '21:79', '27:15', '34:10'],
    'entity-sulaiman': ['2:102', '21:78', '21:81', '27:15', '27:16', '27:17', '27:30'],
  };

  static const List<String> _limits = [
    'Passages are retrieved from the configured dataset with a keyword and concept search; results are not exhaustive.',
    'The translation shown is a human translation identified in each passage; it is not the Arabic text itself.',
  ];

  static final RegExp _verseReference = RegExp(r'(?:^|[^0-9])([0-9]{1,3})\s*[:.\-]\s*([0-9]{1,3})(?:[^0-9]|$)');

  static Answer answer(Dataset dataset, String question, String language, [List<String>? excludeVerseKeys]) {
    final reference = parseVerseReference(question);
    if (reference != null) {
      final verse = dataset.verse(reference);
      if (verse != null) {
        final passages = <Passage>[_buildPassage(dataset, verse, language)];
        return Answer(
            'answer',
            language,
            Notices.connectiveFound(language, 1),
            passages,
            1.0,
            Notices.noticeFor(language),
            Notices.noticeVerified(language),
            null,
            const ['This passage is shown because its reference was requested; it is not an interpretation.'],
            true,
            _keysOf(passages));
      }
    }

    final chapterId = parseChapterReference(dataset, question);
    if (chapterId != null) {
      final verses = dataset.chapterVerses(chapterId);
      final passages = <Passage>[
        for (var index = 0; index < (verses.length < 3 ? verses.length : 3); index++)
          _buildPassage(dataset, verses[index], language)
      ];
      return Answer(
          'answer',
          language,
          Notices.connectiveFound(language, passages.length),
          passages,
          1.0,
          Notices.noticeFor(language),
          Notices.noticeVerified(language),
          null,
          const ['Only the opening passages of the requested surah are shown here.'],
          true,
          _keysOf(passages));
    }

    final Concept? entity = dataset.findEntity(question);
    final verseLimit = entity != null ? maxEntityVerses : maxVerses;
    final matches = Retrieval.search(dataset, question, language, verseLimit, null, excludeVerseKeys?.toSet());
    if (matches.isEmpty || matches.first.confidence < confidenceFloor) {
      return Answer.fallback(language, const [
        'This fallback reflects the limits of the retrieval system and the question wording, not a claim about the Quran.'
      ]);
    }

    final best = matches.first.confidence;
    final threshold = entity != null
        ? (best * 0.55 > confidenceFloor ? best * 0.55 : confidenceFloor)
        : (best * 0.8 > confidenceFloor ? best * 0.8 : confidenceFloor);
    var passages = <Passage>[];
    var englishFallback = false;
    for (final match in matches) {
      if (match.confidence < threshold) continue;
      final passage = _buildPassage(dataset, match.verse, language);
      englishFallback = englishFallback || passage.fallbackToEnglish;
      passages.add(passage);
    }

    // Guarantee the central reviewed verses for well-known figures are part of the
    // supplied set, so reasoning questions ("Is Isa alive or dead?") can use them.
    if (entity != null) {
      final core = _coreVerses[entity.key];
      if (core != null) {
        var merged = <Passage>[];
        final seen = <String>{};
        for (final key in core) {
          final passage = passageFor(dataset, language, key);
          if (passage != null && seen.add(passage.verseKey)) merged.add(passage);
        }
        for (final passage in passages) {
          if (seen.add(passage.verseKey)) merged.add(passage);
        }
        if (merged.length > 16) merged = merged.sublist(0, 16);
        passages = merged;
      }
    }

    final message = entity != null
        ? Notices.entityFound(language, Dataset.entityLabel(entity, language == 'ar'), passages.length)
        : Notices.connectiveFound(language, passages.length);
    return Answer(
        'answer',
        language,
        message,
        passages,
        best,
        Notices.noticeFor(language),
        Notices.noticeVerified(language),
        englishFallback ? Notices.translationPending(language) : null,
        _limits,
        true,
        _keysOf(passages));
  }

  /// Builds one verified passage for a verse reference (used by index-fact answers).
  static Passage? passageFor(Dataset? dataset, String language, String verseKey) {
    final verse = dataset?.verse(verseKey);
    return verse == null ? null : _buildPassage(dataset!, verse, language);
  }

  /// Answer built from a reviewed index fact (count/list) rather than retrieval.
  /// The passages are real translations and are the references cited by the fact.
  static Answer factAnswer(Dataset dataset, String language, String message, List<String> verseKeys) {
    final passages = <Passage>[];
    for (final key in verseKeys) {
      final passage = passageFor(dataset, language, key);
      if (passage != null) passages.add(passage);
    }
    String? translationNote;
    if (language != 'en' && language != 'ar') {
      translationNote = 'The count/list explanation is in English until a reviewed translation is available; '
          'the verses below use your selected language where available.';
    }
    return Answer(
        'answer',
        language,
        message,
        passages,
        1.0,
        Notices.noticeFor(language),
        Notices.noticeVerified(language),
        translationNote,
        const ["Counts and name lists come from the app's reviewed Quran index; they are not tafsir, a fatwa or a ruling."],
        true,
        List<String>.from(verseKeys));
  }

  static Passage _buildPassage(Dataset dataset, Verse verse, String language) {
    var translationText = dataset.translationText(language, verse.key);
    var effectiveLanguage = language;
    var fallbackToEnglish = false;
    if (translationText == null || translationText.isEmpty) {
      translationText = dataset.translationText('en', verse.key);
      effectiveLanguage = 'en';
      fallbackToEnglish = language != 'en';
    }
    final meta = dataset.translationMeta(effectiveLanguage);
    return Passage(verse.key, verse.chapter, verse.arabic, translationText ?? '', effectiveLanguage, meta,
        fallbackToEnglish, dataset.provenance());
  }

  static List<String> _keysOf(List<Passage> passages) => [for (final passage in passages) passage.verseKey];

  static String? parseVerseReference(String? question) {
    if (question == null) return null;
    final matcher = _verseReference.firstMatch(question);
    if (matcher == null) return null;
    final chapter = int.parse(matcher.group(1)!);
    final verse = int.parse(matcher.group(2)!);
    if (chapter < 1 || chapter > 114 || verse < 1) return null;
    return '$chapter:$verse';
  }

  static int? parseChapterReference(Dataset dataset, String? question) {
    if (question == null) return null;
    final lowered = question.toLowerCase();
    for (final Chapter chapter in _chapters(dataset)) {
      final english = chapter.nameEnglish.toLowerCase();
      if (english.length >= 3 && lowered.contains(english)) return chapter.id;
      if (chapter.nameArabic.length >= 3 && question.contains(chapter.nameArabic)) return chapter.id;
    }
    return null;
  }

  static List<Chapter> _chapters(Dataset dataset) {
    final chapters = <Chapter>[];
    for (var id = 1; id <= 114; id++) {
      final chapter = dataset.chapter(id);
      if (chapter != null) chapters.add(chapter);
    }
    return chapters;
  }
}
