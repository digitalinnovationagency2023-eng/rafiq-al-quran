import 'dataset.dart';

/// Structural mushaf metadata (juz, Madani page, hizb) for every verse.
///
/// This is layout metadata only: which page of the standard Madani mushaf a verse
/// is printed on. The Arabic text and translations come exclusively from the
/// validated dataset; this table never supplies scripture.
class QuranMeta {
  static const int pages = 604;
  static const int juzCount = 30;
  static const int hizbCount = 240;
  static const int verseCountTotal = 6236;

  final List<int> _page;
  final List<int> _juz;
  final List<int> _hizb;
  final int _count;

  QuranMeta._(this._page, this._juz, this._hizb) : _count = _page.length - 1;

  static final RegExp _verseKey = RegExp(r'^\d+:\d+$');

  /// Parses the bundled TSV. Throws when the table is incomplete or malformed.
  static QuranMeta parse(List<String> lines) {
    final page = List<int>.filled(verseCountTotal + 1, 0);
    final juz = List<int>.filled(verseCountTotal + 1, 0);
    final hizb = List<int>.filled(verseCountTotal + 1, 0);
    var index = 0;
    var lastPage = 0;
    var lastJuz = 0;
    var lastHizb = 0;
    for (final line in lines) {
      final trimmed = line.trim();
      if (trimmed.isEmpty || trimmed.startsWith('#')) continue;
      final parts = trimmed.split('\t');
      if (parts.length < 4) throw StateError('Malformed quran-meta row: $trimmed');
      if (!_verseKey.hasMatch(parts[0])) throw StateError('Bad verse key: ${parts[0]}');
      index++;
      if (index > verseCountTotal) throw StateError('quran-meta has more than $verseCountTotal rows');
      final pageNumber = int.parse(parts[2]);
      final juzNumber = int.parse(parts[1]);
      final hizbNumber = int.parse(parts[3]);
      if (pageNumber < 1 || pageNumber > pages) throw StateError('Bad page $pageNumber');
      if (juzNumber < 1 || juzNumber > juzCount) throw StateError('Bad juz $juzNumber');
      if (hizbNumber < 1 || hizbNumber > hizbCount) throw StateError('Bad hizb $hizbNumber');
      if (pageNumber < lastPage || juzNumber < lastJuz || hizbNumber < lastHizb) {
        throw StateError('Metadata regressed at ${parts[0]}');
      }
      page[index] = pageNumber;
      juz[index] = juzNumber;
      hizb[index] = hizbNumber;
      lastPage = pageNumber;
      lastJuz = juzNumber;
      lastHizb = hizbNumber;
    }
    if (index != verseCountTotal) throw StateError('Expected $verseCountTotal meta rows, found $index');
    if (lastPage != pages) throw StateError('Expected last page $pages, found $lastPage');
    if (lastJuz != juzCount) throw StateError('Expected last juz $juzCount, found $lastJuz');
    return QuranMeta._(page, juz, hizb);
  }

  int verseCount() => _count;

  int page(int verseId) => _bounded(_page, verseId);
  int juz(int verseId) => _bounded(_juz, verseId);
  int hizb(int verseId) => _bounded(_hizb, verseId);

  /// "Juz 3  ·  Page 42": the indicator shown in the reader.
  String indicator(int verseId) => 'Juz ${juz(verseId)}  ·  Page ${page(verseId)}';

  /// Chapter start metadata, using the first verse id of the chapter.
  String chapterIndicator(Dataset dataset, int chapterId) {
    final verses = dataset.chapterVerses(chapterId);
    if (verses.isEmpty) return '';
    final first = verses.first;
    final last = verses.last;
    final firstPage = page(first.id);
    final lastPage = page(last.id);
    final pagesText = firstPage == lastPage ? 'Page $firstPage' : 'Pages $firstPage–$lastPage';
    final juzText = juz(last.id) == juz(first.id) ? '' : '–${juz(last.id)}';
    return 'Juz ${juz(first.id)}$juzText  ·  $pagesText';
  }

  static int _bounded(List<int> values, int verseId) {
    if (verseId < 1 || verseId >= values.length) return 0;
    return values[verseId];
  }
}
