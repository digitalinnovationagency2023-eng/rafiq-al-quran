import 'arabic_text.dart';
import 'concept.dart';
import 'dataset.dart';

/// Deterministic index facts for questions the Quran text itself cannot answer in
/// one verse: how many prophets are named, how often a name occurs, dataset totals.
///
/// Every number comes from the same validated dataset the reader uses, so the
/// count can be audited. These are index facts, not tafsir, and the wording
/// always says so.
class Facts {
  Facts._();

  /// Canonical order of the prophets named in the Quran (reviewed index).
  static const List<String> prophetKeys = [
    'entity-adam', 'entity-idris', 'entity-nuh', 'entity-hud', 'entity-salih', //
    'entity-ibrahim', 'entity-lut', 'entity-ismail', 'entity-ishaq', 'entity-yaqub', //
    'entity-yusuf', 'entity-ayyub', 'entity-shuayb', 'entity-musa', 'entity-harun', //
    'entity-dhulkifl', 'entity-dawud', 'entity-sulaiman', 'entity-ilyas', 'entity-alyasa', //
    'entity-yunus', 'entity-zakariya', 'entity-yahya', 'entity-isa', 'entity-muhammad',
  ];

  static final RegExp _mentionCount = RegExp(
      "how many times (?:is |are |was |were |does |do )?(?:the name )?([a-z' ]{3,40}?)(?: mentioned| named| appear| occurs| occur)");
  static final RegExp _nonWord = RegExp(r"[^\p{L}\p{N}\s']+", unicode: true);
  static final RegExp _spaces = RegExp(r'\s+');

  static FactResult? match(Dataset? dataset, String? question) {
    if (dataset == null || question == null) return null;
    final q = ' ${question.toLowerCase().replaceAll(_nonWord, ' ').replaceAll(_spaces, ' ').trim()} ';

    final mention = _mentionCount.firstMatch(q);
    if (mention != null) {
      final concept = dataset.findEntity(mention.group(1));
      if (concept != null) return _mentions(dataset, concept);
    }

    if (_asksProphets(q)) return _prophets(dataset);
    if (q.contains('how many surah') ||
        q.contains('number of surah') ||
        q.contains('how many chapters') ||
        q.contains('number of chapters')) {
      return FactResult(
          "The app's validated Quran dataset contains ${dataset.chapterCount()} surahs. "
          'This is a dataset total, not a number stated in a single verse: the Quran refers to its '
          'surahs but does not list their total in one passage, so treat this as an index fact.',
          <String>[],
          "The app's validated dataset contains ${dataset.chapterCount()} surahs (an index count, not a verse statement).");
    }
    if (q.contains('how many verse') ||
        q.contains('how many ayat') ||
        q.contains('number of verses') ||
        q.contains('number of ayahs') ||
        q.contains('how many ayahs')) {
      return FactResult(
          "The app's validated Quran dataset contains ${dataset.verseCount()} verses. "
          'This is a dataset total, not a number stated in a single verse; the Quran speaks of its '
          'verses but does not give this total in one passage, so treat it as an index fact.',
          <String>[],
          "The app's validated dataset contains ${dataset.verseCount()} verses (an index count, not a verse statement).");
    }
    return null;
  }

  static bool _asksProphets(String q) {
    final topic = q.contains('prophet') ||
        q.contains('prophets') ||
        q.contains('messenger') ||
        q.contains('messengers') ||
        q.contains('nabi') ||
        q.contains('rasul');
    if (!topic) return false;
    final askingCount = q.contains('how many') || q.contains('number of') || q.contains('total');
    final askingList = q.contains('names of') ||
        q.contains('name of') ||
        q.contains('list of') ||
        q.contains('which prophets') ||
        q.contains('which messengers') ||
        q.contains('who are the prophets') ||
        q.contains('who are the messengers') ||
        q.contains('prophets mentioned') ||
        q.contains('prophets named') ||
        q.contains('prophets in the quran');
    return askingCount || askingList;
  }

  static FactResult _prophets(Dataset dataset) {
    final names = <String>[];
    for (final key in prophetKeys) {
      final concept = dataset.entity(key);
      if (concept != null) names.add(Dataset.entityLabel(concept, false));
    }
    final count = names.isEmpty ? prophetKeys.length : names.length;
    final list = StringBuffer();
    for (var i = 0; i < names.length; i++) {
      if (i > 0) list.write(i == names.length - 1 ? ' and ' : ', ');
      list.write(names[i]);
    }
    final keys = <String>['4:164', '40:78'];
    final message = 'The Quran names $count prophets: $list.\n\n'
        "That is a count of the prophets NAMED in the Quran, taken from the app's reviewed name index - "
        'it is not a number stated in a single verse. The Quran itself adds that some messengers were not '
        'mentioned (4:164) and that God sent messengers before Muhammad whose stories are not all told '
        '(40:78), so the full number of prophets is known to God alone.\n\n'
        'This is an index fact with its qualifying verses below - not tafsir, a fatwa or a ruling.';
    var compact = 'App index: $count named prophets ($list); 4:164/40:78 say others were not mentioned.';
    if (compact.length > 300) {
      compact = 'App index: $count named prophets in the Quran; 4:164/40:78 say others were not mentioned.';
    }
    return FactResult(message, keys, compact);
  }

  static FactResult? _mentions(Dataset dataset, Concept concept) {
    final matchedKeys = <String>{};
    final normalizedTerms = [
      for (final term in concept.arabic)
        if (ArabicText.normalize(term).isNotEmpty) ArabicText.normalize(term)
    ];
    for (final verse in dataset.verses) {
      final normalized = verse.normalized;
      for (final normalizedTerm in normalizedTerms) {
        if (normalized.contains(normalizedTerm)) {
          matchedKeys.add(verse.key);
          break;
        }
      }
    }
    if (matchedKeys.isEmpty) return null;
    final keys = matchedKeys.toList();
    final examples = keys.sublist(0, keys.length < 5 ? keys.length : 5);
    final label = Dataset.entityLabel(concept, false);
    final message = 'The name or aliases of $label appear in ${keys.length}'
        " verses of the Quran, counted from the app's Quran text index. Counts cover named occurrences "
        'only - not pronouns, parables or implied references - and this is an index count, not a number '
        'stated in a verse. A few example verses are shown below.';
    final aiFact = "The app's index counts named occurrences of $label in ${keys.length}"
        ' verses (names only, not pronouns or implied references).';
    return FactResult(message, examples, aiFact);
  }
}

class FactResult {
  final String message;
  final List<String> verseKeys;

  /// Compact fact for the AI prompt (max 300 chars, ASCII only).
  final String aiFact;

  const FactResult(this.message, this.verseKeys, this.aiFact);
}
