import 'dart:convert';

import '../logic/logic.dart';

/// One chat turn.
class Turn {
  /// "user" | "assistant".
  final String role;
  final String? question;
  final Answer? answer;

  const Turn(this.role, this.question, this.answer);
}

/// Conversation persistence for the device. Stored answers keep the exact text
/// that was verified against the dataset; on restore each passage is re-checked
/// against the bundled Arabic text and dropped if it does not match.
class HistoryCodec {
  HistoryCodec._();

  static const int maxArchives = 20;
  static const int maxTitleLength = 90;

  static List<Map<String, dynamic>> encode(List<Turn> turns) {
    final array = <Map<String, dynamic>>[];
    for (final turn in turns) {
      final object = <String, dynamic>{'role': turn.role, 'question': turn.question ?? ''};
      final answer = turn.answer;
      if (answer != null) object['answer'] = encodeAnswer(answer);
      array.add(object);
    }
    return array;
  }

  static String encodeString(List<Turn> turns) => jsonEncode(encode(turns));

  static List<Turn> decode(String? json) {
    try {
      final decoded = jsonDecode(json ?? '[]');
      return decoded is List ? decodeList(decoded) : <Turn>[];
    } catch (_) {
      return <Turn>[];
    }
  }

  static List<Turn> decodeList(List<dynamic> array) {
    final turns = <Turn>[];
    for (final item in array) {
      if (item is! Map) continue;
      final role = item['role']?.toString() ?? 'user';
      final question = item['question']?.toString() ?? '';
      final answer = item['answer'] is Map ? decodeAnswer(Map<String, dynamic>.from(item['answer'] as Map)) : null;
      turns.add(Turn(role, question, answer));
    }
    return turns;
  }

  static Map<String, dynamic> encodeAnswer(Answer answer) => <String, dynamic>{
        'kind': answer.kind,
        'language': answer.language,
        'message': answer.message,
        'confidence': answer.confidence,
        'notice': answer.notice,
        'noticeVerified': answer.noticeVerified,
        'translationNotice': answer.translationNotice,
        'limits': answer.limits,
        'passages': [for (final passage in answer.passages) encodePassage(passage)],
        'shownVerseKeys': answer.shownVerseKeys,
      };

  static Map<String, dynamic> encodePassage(Passage passage) => <String, dynamic>{
        'verseKey': passage.verseKey,
        'chapterNameEnglish': passage.chapterNameEnglish,
        'chapterNameArabic': passage.chapterNameArabic,
        'arabic': passage.arabic,
        'translationText': passage.translationText,
        'translationLanguage': passage.translationLanguage,
        'translationTitle': passage.translationTitle,
        'translationAuthor': passage.translationAuthor,
        'translationDirection': passage.translationDirection,
        'translationLicenseStatus': passage.translationLicenseStatus,
        'fallbackToEnglish': passage.fallbackToEnglish,
        'referenceLabel': passage.referenceLabel,
        'dataset': passage.dataset,
        'datasetVersion': passage.datasetVersion,
        'datasetSourceUrl': passage.datasetSourceUrl,
        'datasetChecksum': passage.datasetChecksum,
      };

  static String _string(Map<String, dynamic> object, String key, [String fallback = '']) {
    final value = object[key];
    return value == null ? fallback : value.toString();
  }

  static Answer? decodeAnswer(Map<String, dynamic>? object) {
    if (object == null) return null;
    try {
      final passages = <Passage>[];
      final array = object['passages'];
      if (array is List) {
        for (final item in array) {
          if (item is! Map) continue;
          final row = Map<String, dynamic>.from(item);
          final chapter = Chapter(0, _string(row, 'chapterNameArabic'), _string(row, 'chapterNameEnglish'), 0, '');
          final meta = TranslationMeta(
            _string(row, 'translationLanguage'),
            _string(row, 'translationTitle'),
            _string(row, 'translationAuthor'),
            _string(row, 'translationDirection', 'ltr'),
            _string(row, 'translationLicenseStatus', 'unverified'),
          );
          final provenance = Provenance()
            ..dataset = _string(row, 'dataset', 'quran-uthmani')
            ..version = _string(row, 'datasetVersion')
            ..sourceUrl = _string(row, 'datasetSourceUrl')
            ..checksum = _string(row, 'datasetChecksum');
          passages.add(Passage(_string(row, 'verseKey'), chapter, _string(row, 'arabic'),
              _string(row, 'translationText'), _string(row, 'translationLanguage'), meta,
              row['fallbackToEnglish'] == true, provenance));
        }
      }
      final limits = <String>[
        if (object['limits'] is List) for (final limit in object['limits'] as List) limit.toString()
      ];
      final keys = <String>[
        if (object['shownVerseKeys'] is List) for (final key in object['shownVerseKeys'] as List) key.toString()
      ];
      final confidence = object['confidence'];
      return Answer(
        _string(object, 'kind', 'answer'),
        _string(object, 'language', 'en'),
        _string(object, 'message'),
        passages,
        confidence is num ? confidence.toDouble() : 0,
        _string(object, 'notice'),
        object['noticeVerified'] == true,
        object['translationNotice']?.toString(),
        limits,
        true,
        keys,
      );
    } catch (_) {
      return null;
    }
  }

  // ---------------------------------------------------------------- archives
  /// Title for an archived conversation: the first real user question.
  static String archiveTitle(List<Turn> turns) {
    for (final turn in turns) {
      final question = turn.question?.trim() ?? '';
      if (turn.role == 'user' && question.isNotEmpty && !question.startsWith('[')) {
        return clip(question, maxTitleLength);
      }
    }
    return 'Attachment conversation';
  }

  /// Inserts (or replaces) the archive for [conversationId] at the top of the
  /// list and keeps at most [maxArchives] entries.
  static List<dynamic> archive(List<dynamic> existing, String conversationId, List<Turn> turns, int savedAt) {
    final next = <dynamic>[
      <String, dynamic>{
        'id': conversationId,
        'title': archiveTitle(turns),
        'savedAt': savedAt,
        'turns': encode(turns),
      }
    ];
    for (final item in existing) {
      if (next.length >= maxArchives) break;
      if (item is! Map) continue;
      // one entry per conversation: the current chat replaces its previous archive
      if (item['id']?.toString() == conversationId) continue;
      next.add(item);
    }
    return next;
  }

  /// Keeps only turns whose passages still match the dataset's Arabic text.
  static List<Turn> verified(List<Turn> restored, Dataset? dataset) {
    final turns = <Turn>[];
    for (final turn in restored) {
      final answer = turn.answer;
      if (answer == null || answer.kind == 'conversation') {
        turns.add(turn);
        continue;
      }
      var valid = true;
      for (final passage in answer.passages) {
        final verse = dataset?.verse(passage.verseKey);
        if (verse == null || verse.arabic != passage.arabic) valid = false;
      }
      if (valid) turns.add(turn);
    }
    return turns;
  }

  static String clip(String? value, int max) {
    if (value == null) return '';
    final trimmed = value.trim();
    return trimmed.length <= max ? trimmed : trimmed.substring(0, max);
  }
}
