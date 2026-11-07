import 'arabic_text.dart';
import 'chapter.dart';

class Verse {
  /// 1-based mushaf position (1..6236).
  final int id;
  final String key;
  final int chapterId;
  final int number;
  final String arabic;
  final String plain;
  final String normalized;
  final List<String> tokens;
  final Chapter chapter;

  Verse(this.id, this.key, this.chapterId, this.number, this.arabic, this.chapter)
      : plain = ArabicText.plain(arabic),
        normalized = ArabicText.normalize(arabic),
        tokens = ArabicText.tokens(arabic);
}
