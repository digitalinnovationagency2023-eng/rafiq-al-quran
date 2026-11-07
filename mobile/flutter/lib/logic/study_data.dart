/// Pure helpers behind the study tools: bookmark collections, notes, saved
/// searches, reading plans and CSV exports.
class StudyData {
  StudyData._();

  static const String defaultCollection = 'General';
  static const int maxCollectionLength = 40;
  static const int maxNoteLength = 2000;
  static const int maxSearches = 30;
  static const String presetFull = 'full';
  static const String presetJuz30 = 'juz30';
  static const String presetLast10 = 'last10';

  static final RegExp _spaces = RegExp(r'\s+');
  static final RegExp _verseKey = RegExp(r'^\d{1,3}:\d{1,3}$');

  // ------------------------------------------------------------ collections
  /// Collection names are single-line, pipe-free and bounded.
  static String sanitizeCollectionName(String? raw) {
    if (raw == null) return defaultCollection;
    var cleaned = raw.replaceAll('|', ' ').replaceAll('\n', ' ').replaceAll('\r', ' ').trim();
    cleaned = cleaned.replaceAll(_spaces, ' ');
    if (cleaned.isEmpty) return defaultCollection;
    if (cleaned.length > maxCollectionLength) cleaned = cleaned.substring(0, maxCollectionLength).trim();
    return cleaned;
  }

  /// Adds a collection when it does not exist yet; returns the canonical name.
  static String addCollection(List<String> collections, String? raw) {
    final name = sanitizeCollectionName(raw);
    for (final existing in collections) {
      if (existing.toLowerCase() == name.toLowerCase()) return existing;
    }
    collections.add(name);
    return name;
  }

  static List<String> defaultCollections() => <String>[defaultCollection];

  // ------------------------------------------------------------------ notes
  static String sanitizeNote(String? raw) {
    if (raw == null) return '';
    var cleaned = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n').trim();
    if (cleaned.length > maxNoteLength) cleaned = cleaned.substring(0, maxNoteLength);
    return cleaned;
  }

  // -------------------------------------------------------------- progress
  static int percent(int done, int total) {
    if (total <= 0) return 0;
    final clamped = done.clamp(0, total);
    return (clamped * 100.0 / total).round();
  }

  static String formatProgress(int done, int total) {
    final clampedDone = total < 0 ? 0 : done.clamp(0, total);
    return '$clampedDone of $total  ·  ${percent(done, total)}%';
  }

  // --------------------------------------------------------------- presets
  static List<int> presetChapters(String? preset) {
    switch (preset ?? presetFull) {
      case presetJuz30:
        return [for (var chapter = 78; chapter <= 114; chapter++) chapter];
      case presetLast10:
        return [for (var chapter = 105; chapter <= 114; chapter++) chapter];
      case presetFull:
      default:
        return [for (var chapter = 1; chapter <= 114; chapter++) chapter];
    }
  }

  static String presetTitle(String? preset) {
    switch (preset ?? presetFull) {
      case presetJuz30:
        return 'Juz 30 (surahs 78–114)';
      case presetLast10:
        return 'Last ten surahs (105–114)';
      case presetFull:
      default:
        return 'Full Quran (114 surahs)';
    }
  }

  /// The next chapter in a plan that is not marked read, or 0 when finished.
  static int nextChapter(List<int> chapters, List<int> done) {
    for (final chapter in chapters) {
      if (!done.contains(chapter)) return chapter;
    }
    return 0;
  }

  // ---------------------------------------------------------------- export
  /// RFC 4180-style quoting so exported CSV opens cleanly in spreadsheets.
  static String csv(List<String?> fields) {
    final builder = StringBuffer();
    for (var index = 0; index < fields.length; index++) {
      if (index > 0) builder.write(',');
      final value = fields[index] ?? '';
      final quote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r');
      if (quote) {
        builder.write('"${value.replaceAll('"', '""')}"');
      } else {
        builder.write(value);
      }
    }
    return builder.toString();
  }

  static String csvHeader(List<String> columns) => csv(columns);

  static List<int> parseChapters(String? csvValue) {
    final chapters = <int>[];
    if (csvValue == null || csvValue.trim().isEmpty) return chapters;
    for (final part in csvValue.split(',')) {
      final chapter = int.tryParse(part.trim());
      if (chapter != null && chapter >= 1 && chapter <= 114 && !chapters.contains(chapter)) chapters.add(chapter);
    }
    return chapters;
  }

  static String joinChapters(List<int> chapters) => chapters.join(',');

  static bool validVerseKey(String? key) => key != null && _verseKey.hasMatch(key);

  /// Natural ordering for "2:255" style keys (numeric, not lexicographic).
  static int compareVerseKeys(String left, String right) {
    final a = left.split(':');
    final b = right.split(':');
    final chapterA = int.parse(a[0]);
    final chapterB = int.parse(b[0]);
    if (chapterA != chapterB) return chapterA.compareTo(chapterB);
    return int.parse(a[1]).compareTo(int.parse(b[1]));
  }
}
