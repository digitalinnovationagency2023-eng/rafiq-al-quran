import 'notices.dart';
import 'passage.dart';

/// The composed answer shown to the user.
class Answer {
  /// "answer" | "fallback" | "conversation".
  final String kind;
  final String language;
  final String message;
  final List<Passage> passages;
  final double confidence;
  final String notice;
  final bool noticeVerified;
  final String? translationNotice;
  final List<String> limits;
  final bool suggestRefine;
  final List<String> shownVerseKeys;

  /// Optional AI summary synthesised only from these passages (never Quran text).
  final String? summary;

  const Answer(this.kind, this.language, this.message, this.passages, this.confidence, this.notice,
      this.noticeVerified, this.translationNotice, this.limits, this.suggestRefine, this.shownVerseKeys,
      [this.summary]);

  /// Copy of this answer carrying an AI summary (or null to clear it).
  Answer withSummary(String? value) => Answer(kind, language, message, passages, confidence, notice,
      noticeVerified, translationNotice, limits, suggestRefine, shownVerseKeys, value);

  static Answer fallback(String language, List<String> limits) => Answer(
      'fallback',
      language,
      Notices.fallbackFor(language),
      <Passage>[],
      0,
      Notices.noticeFor(language),
      Notices.noticeVerified(language),
      null,
      limits,
      true,
      <String>[]);
}
