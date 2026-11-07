import 'arabic_text.dart';
import 'chapter.dart';
import 'concept.dart';
import 'provenance.dart';
import 'roman.dart';
import 'translation_meta.dart';
import 'verse.dart';

/// Immutable in-memory dataset built from the bundled asset files. Everything the
/// app shows comes from here; no network call is needed to read or search.
class Dataset {
  final Map<int, Chapter> chapters = <int, Chapter>{};
  final Map<String, Chapter> chaptersByName = <String, Chapter>{};
  final List<Verse> verses = <Verse>[];
  final Map<String, Verse> verseByKey = <String, Verse>{};
  final Map<String, TranslationMeta> _translations = <String, TranslationMeta>{};
  final Map<String, String> _translationText = <String, String>{};
  final List<Concept> concepts = <Concept>[];
  final Provenance _provenance = Provenance();

  /// Arabic search form -> verse indexes (0-based positions in [verses]).
  final Map<String, List<int>> arabicIndex = <String, List<int>>{};

  /// "lang|form" -> verse indexes.
  final Map<String, List<int>> translationIndex = <String, List<int>>{};

  Dataset._();

  int verseCount() => verses.length;
  int chapterCount() => chapters.length;
  int translationCount() => _translations.length;
  Provenance provenance() => _provenance;
  Map<String, TranslationMeta> translations() => Map<String, TranslationMeta>.unmodifiable(_translations);

  Chapter? chapter(int id) => chapters[id];
  Verse? verse(String key) => verseByKey[key];

  String? translationText(String language, String verseKey) => _translationText['$language|$verseKey'];

  TranslationMeta? translationMeta(String language) => _translations[language];

  List<Verse> chapterVerses(int chapterId) => [for (final verse in verses) if (verse.chapterId == chapterId) verse];

  static Dataset build(List<String> chapterLines, List<String> quranLines, List<String> translationLines,
      List<String> translationMetaLines, List<String> lexiconLines, List<String> provenanceLines) {
    final dataset = Dataset._();

    for (final line in chapterLines) {
      if (line.trim().isEmpty) continue;
      final chapter = Chapter.parse(line);
      dataset.chapters[chapter.id] = chapter;
      dataset.chaptersByName[chapter.nameEnglish.toLowerCase()] = chapter;
      dataset.chaptersByName[chapter.nameArabic] = chapter;
    }

    var id = 0;
    for (final line in quranLines) {
      if (line.trim().isEmpty) continue;
      final tab = line.indexOf('\t');
      final key = line.substring(0, tab);
      final arabic = line.substring(tab + 1);
      final keyParts = key.split(':');
      final chapterId = int.parse(keyParts[0]);
      final number = int.parse(keyParts[1]);
      final chapter = dataset.chapters[chapterId];
      if (chapter == null) throw StateError('Chapter missing for verse $key');
      final verse = Verse(++id, key, chapterId, number, arabic, chapter);
      dataset.verses.add(verse);
      dataset.verseByKey[key] = verse;
    }

    for (final line in translationMetaLines) {
      if (line.trim().isEmpty) continue;
      final meta = TranslationMeta.parse(line);
      dataset._translations[meta.language] = meta;
    }

    dataset.addTranslations(translationLines);

    for (final line in lexiconLines) {
      if (line.trim().isEmpty) continue;
      final parts = line.split('\t');
      final arabic = _splitTerms(parts.length > 1 ? parts[1] : '');
      final byLanguage = <List<String>>[for (var i = 2; i < parts.length; i++) _splitTerms(parts[i])];
      dataset.concepts.add(Concept(parts[0], arabic, byLanguage));
    }

    for (final line in provenanceLines) {
      if (line.trim().isEmpty) continue;
      final parts = line.split('\t');
      switch (parts[0]) {
        case 'quran':
          dataset._provenance.dataset = parts[1];
          dataset._provenance.version = parts[2];
          dataset._provenance.sourceUrl = parts[3];
          dataset._provenance.checksum = parts[4];
          dataset._provenance.importedAt = parts[5];
          break;
        case 'verse-count':
          dataset._provenance.verseCount = int.parse(parts[1]);
          break;
        case 'chapter-count':
          dataset._provenance.chapterCount = int.parse(parts[1]);
          break;
        case 'translation-count':
          dataset._provenance.translationCount = int.parse(parts[1]);
          break;
        default:
          break;
      }
    }

    dataset._buildIndexes();
    dataset._validate();
    return dataset;
  }

  static List<String> _splitTerms(String? value) {
    final out = <String>[];
    if (value == null || value.trim().isEmpty) return out;
    for (final term in value.split(',')) {
      final trimmed = term.trim().toLowerCase();
      if (trimmed.isNotEmpty) out.add(trimmed);
    }
    return out;
  }

  /// Adds one language's translation rows and indexes it on demand.
  void addTranslations(List<String> lines) {
    final verseIndexByKey = <String, int>{};
    for (var index = 0; index < verses.length; index++) {
      verseIndexByKey[verses[index].key] = index;
    }
    for (final line in lines) {
      if (line.trim().isEmpty) continue;
      final firstTab = line.indexOf('\t');
      if (firstTab < 0) continue;
      final secondTab = line.indexOf('\t', firstTab + 1);
      if (secondTab < 0) continue;
      final language = line.substring(0, firstTab);
      final verseKey = line.substring(firstTab + 1, secondTab);
      final text = line.substring(secondTab + 1);
      _translationText['$language|$verseKey'] = text;
      final verseIndex = verseIndexByKey[verseKey];
      if (verseIndex == null) continue;
      for (final token in latinTokens(text).toSet()) {
        if (token.length < 2) continue;
        for (final form in latinForms(token)) {
          final key = '$language|$form';
          (translationIndex[key] ??= <int>[]).add(verseIndex);
        }
      }
    }
  }

  bool hasTranslationLanguage(String language) => _translationText.containsKey('$language|1:1');

  void _buildIndexes() {
    for (var index = 0; index < verses.length; index++) {
      final verse = verses[index];
      for (final token in verse.tokens.toSet()) {
        for (final form in ArabicText.searchForms(token)) {
          (arabicIndex[form] ??= <int>[]).add(index);
        }
      }
    }
    // The Java app also built a per-language lexicon term list here; it was
    // mis-indexed and never read, so it is deliberately not reproduced.
  }

  /// Looks up a reviewed concept (including entity-* figures) by key.
  Concept? entity(String? key) {
    if (key == null) return null;
    for (final concept in concepts) {
      if (concept.key == key) return concept;
    }
    return null;
  }

  /// Finds a named figure (prophet/person) whose alias appears in the question.
  /// Handles romanised spellings ("Eesa", "Hazrath Essa", "Yaqoob", "Moosa"),
  /// small typos and mixed Arabic/Latin input.
  Concept? findEntity(String? question) {
    if (question == null || question.trim().isEmpty) return null;
    final tokens = mixedTokens(question);
    for (final token in tokens) {
      if (token.length < 3 || Roman.isHonorific(token)) continue;
      final lower = token.toLowerCase();
      for (final concept in concepts) {
        if (!concept.key.startsWith('entity-')) continue;
        for (final vocabulary in concept.byLanguage) {
          for (final term in vocabulary) {
            if (term.length < 3) continue;
            if (term == lower ||
                (Roman.isAscii(term) && Roman.sameRoman(term, lower)) ||
                (Roman.isAscii(term) && Roman.fuzzyMatch(lower, term))) {
              return concept;
            }
          }
        }
        for (final term in concept.arabic) {
          if (ArabicText.normalize(term) == ArabicText.normalize(token)) return concept;
        }
      }
    }
    return null;
  }

  /// Arabic and Latin tokens from mixed-script input (both, when both are present).
  static List<String> mixedTokens(String? text) {
    final tokens = <String>[];
    var arabic = false;
    var latin = false;
    if (text != null) {
      for (final c in text.codeUnits) {
        if (c >= 0x0600 && c <= 0x06FF) {
          arabic = true;
        } else if (c < 128 && _isAsciiLetter(c)) {
          latin = true;
        }
      }
    }
    if (arabic) tokens.addAll(ArabicText.tokens(text));
    if (latin || !arabic) tokens.addAll(latinTokens(text));
    return tokens;
  }

  static bool _isAsciiLetter(int c) => (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A);

  /// Display name for a named figure: the Arabic form for Arabic UI, English otherwise.
  static String entityLabel(Concept? concept, bool arabicUi) {
    if (concept == null) return '';
    if (arabicUi && concept.arabic.isNotEmpty) return concept.arabic.first;
    if (concept.byLanguage.isNotEmpty && concept.byLanguage.first.isNotEmpty) {
      final name = concept.byLanguage.first.first;
      return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    return concept.key;
  }

  static final RegExp _nonWord = RegExp(r"[^\p{L}\p{N}\s'-]+", unicode: true);
  static final RegExp _spaces = RegExp(r'\s+');

  static List<String> latinTokens(String? text) {
    final out = <String>[];
    if (text == null) return out;
    final normalized = text.toLowerCase().replaceAll(_nonWord, ' ');
    for (final token in normalized.split(_spaces)) {
      if (token.isNotEmpty) out.add(token);
    }
    return out;
  }

  static List<String> latinForms(String token) {
    final forms = <String>[token];
    if (token.length > 4 && token.endsWith('ies')) forms.add('${token.substring(0, token.length - 3)}y');
    if (token.length > 3 && token.endsWith('es')) forms.add(token.substring(0, token.length - 2));
    if (token.length > 3 && token.endsWith('s')) forms.add(token.substring(0, token.length - 1));
    if (token.length > 5 && token.endsWith('ing')) forms.add(token.substring(0, token.length - 3));
    if (token.length > 4 && token.endsWith('ed')) forms.add(token.substring(0, token.length - 2));
    return forms;
  }

  void _validate() {
    if (chapters.length != 114) throw StateError('Expected 114 chapters, found ${chapters.length}');
    if (verses.length != 6236) throw StateError('Expected 6236 verses, found ${verses.length}');
    final counts = <int, int>{};
    for (final verse in verses) {
      counts[verse.chapterId] = (counts[verse.chapterId] ?? 0) + 1;
    }
    for (final chapter in chapters.values) {
      final actual = counts[chapter.id] ?? 0;
      if (actual != chapter.verseCount) {
        throw StateError('Chapter ${chapter.id} verse count mismatch: $actual');
      }
    }
  }
}
