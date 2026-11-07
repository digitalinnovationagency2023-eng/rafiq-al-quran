import 'dataset.dart';

/// Loads the bundled TSV assets into a Dataset.
class DatasetLoader {
  DatasetLoader._();

  static Dataset load(List<String> chapters, List<String> quran, List<String> translations,
          List<String> translationMeta, List<String> lexicon, List<String> provenance) =>
      Dataset.build(chapters, quran, translations, translationMeta, lexicon, provenance);

  /// Splits file content into non-empty lines (CRLF tolerant).
  static List<String> readLines(String content) {
    final lines = <String>[];
    var start = 0;
    while (start < content.length) {
      var end = content.indexOf('\n', start);
      if (end < 0) end = content.length;
      var line = content.substring(start, end);
      if (line.endsWith('\r')) line = line.substring(0, line.length - 1);
      if (line.isNotEmpty) lines.add(line);
      start = end + 1;
    }
    return lines;
  }
}
