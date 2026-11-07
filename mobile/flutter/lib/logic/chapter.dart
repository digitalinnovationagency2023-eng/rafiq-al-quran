/// A surah of the Quran as read from `chapters.tsv`.
class Chapter {
  final int id;
  final String nameArabic;

  /// `name_simple` from Quran.com, e.g. "Al-Baqarah".
  final String nameEnglish;
  final int verseCount;
  final String revelation;

  const Chapter(this.id, this.nameArabic, this.nameEnglish, this.verseCount, this.revelation);

  static Chapter parse(String line) {
    final parts = line.split('\t');
    return Chapter(
      int.parse(parts[0]),
      parts[1],
      parts[2],
      int.parse(parts[3]),
      parts.length > 4 ? parts[4] : '',
    );
  }
}
