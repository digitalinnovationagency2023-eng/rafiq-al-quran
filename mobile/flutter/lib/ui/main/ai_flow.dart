import 'package:intl/intl.dart';

import '../../data/api.dart';
import '../../data/attachments.dart';
import '../../data/history_codec.dart';
import '../../data/prefs.dart';
import '../../logic/logic.dart';

/// Result of the optional `/api/assistant` call.
class AiResult {
  String intent = '';
  String reply = '';
  String searchText = '';
  String imageText = '';
}

/// The optional server AI calls, the device daily quota and the grounding
/// checks (port of the AI half of `MainActivity`). Verses never come from here.
class AiFlow {
  /// A grounded summary is a second AI request per question, so the device cap
  /// is slightly higher than the old single-call allowance.
  static const int dailyLimit = 10;

  final Prefs prefs;

  /// Set when a call was refused for quota reasons (local or server 429).
  bool quotaBlocked = false;

  AiFlow(this.prefs);

  static String today() => DateFormat('yyyy-MM-dd').format(DateTime.now());

  /// Daily allowance so the free AI tier cannot be exhausted.
  bool consumeAllowance() {
    final day = today();
    var count = prefs.aiCallsToday;
    if (day != prefs.aiDay) {
      count = 0;
      prefs.aiDay = day;
    }
    if (count >= dailyLimit) {
      quotaBlocked = true;
      return false;
    }
    prefs.aiCallsToday = count + 1;
    return true;
  }

  int remainingToday() {
    if (today() != prefs.aiDay) return dailyLimit;
    final left = dailyLimit - prefs.aiCallsToday;
    return left < 0 ? 0 : left;
  }

  /// Calls the optional server AI service; returns an empty result on any failure.
  Future<AiResult> callAi(String? question, AttachmentFile? image, String language) async {
    final result = AiResult();
    if (!consumeAllowance()) return result;
    try {
      final body = <String, dynamic>{'language': language};
      if (question != null) body['text'] = question;
      final bytes = image?.imageBytes;
      if (image != null && bytes != null) {
        body['attachment'] = <String, dynamic>{
          'name': image.name,
          'mime': image.mime.isEmpty ? 'image/jpeg' : image.mime,
          'dataBase64': Attachments.base64(bytes),
        };
      }
      final response = await Api.assistant(body);
      result.intent = Api.optString(response, 'intent');
      result.reply = Api.optString(response, 'reply');
      result.imageText = Api.optString(response, 'imageText');
      final terms = response['searchTerms'];
      if (terms is List) {
        result.searchText = [
          for (final term in terms)
            if (term.toString().trim().isNotEmpty) term.toString().trim()
        ].join(' ');
      } else {
        result.searchText = Api.optString(response, 'searchTerms');
      }
    } catch (error) {
      if (Api.isQuotaError(error)) quotaBlocked = true;
      // Offline, unconfigured AI or rate limited: fall back to local replies.
    }
    return result;
  }

  /// Optional deeper Quran-only answer: the app sends up to 18 retrieved verse
  /// keys plus an optional reviewed index fact. The server reloads the
  /// authoritative translations and rejects any answer that cites outside verses
  /// or writes Arabic script; on any failure the deterministic answer stays.
  Future<Answer> deepenAnswer(Answer answer, String? question, String language, String? indexFact,
      List<Turn> recentHistory, void Function(String) busy) async {
    if (question == null || question.trim().isEmpty) return answer;
    if (answer.passages.isEmpty) return answer;
    // Do not ask AI to explain weak retrieval: weak matches produce confident-sounding but wrong answers.
    if (answer.confidence < 0.35) return answer;
    if (!consumeAllowance()) return answer;
    try {
      final trimmed = question.trim();
      final keys = answer.shownVerseKeys.isNotEmpty
          ? List<String>.from(answer.shownVerseKeys)
          : [for (final passage in answer.passages) passage.verseKey];
      final body = <String, dynamic>{
        'question': trimmed.length > 500 ? trimmed.substring(0, 500) : trimmed,
        'language': language,
        'verseKeys': keys.length > 18 ? keys.sublist(0, 18) : keys,
      };
      if (indexFact != null && indexFact.trim().isNotEmpty) body['facts'] = [indexFact.trim()];
      final history = <Map<String, String>>[];
      for (final turn in recentHistory) {
        if (turn.role == 'user') {
          final text = turn.question?.trim() ?? '';
          if (text.isEmpty || text.startsWith('[')) continue;
          history.add({'role': 'user', 'content': HistoryCodec.clip(text, 400)});
        } else {
          final previous = turn.answer;
          if (previous == null) continue;
          final summary = previous.summary;
          final text = summary != null && summary.trim().isNotEmpty ? summary : previous.message;
          if (text.trim().isEmpty) continue;
          history.add({'role': 'assistant', 'content': HistoryCodec.clip(text, 400)});
        }
      }
      if (history.isNotEmpty) body['history'] = history;
      busy('Thinking through the verses');
      final response = await Api.assistantAnswer(body);
      final text = Api.optString(response, 'answer');
      if (text.trim().isNotEmpty && hasGroundedCitation(text, answer) && hasQuestionOverlap(text, question)) {
        return answer.withSummary(text.trim());
      }
    } catch (error) {
      if (Api.isQuotaError(error)) quotaBlocked = true;
      // The verses are still shown; the reasoning answer is optional.
    }
    return answer;
  }

  /// Reject a fluent summary that never actually addresses the user's question words.
  bool hasQuestionOverlap(String summary, String question) {
    try {
      final tokens = Retrieval.meaningfulTokens(question, prefs.language);
      if (tokens.length < 2) return true;
      final normalized = ArabicText.normalize(summary);
      for (final token in tokens) {
        final needle = ArabicText.normalize(token);
        if (needle.length >= 3 && normalized.contains(needle)) return true;
      }
    } catch (_) {
      return true;
    }
    return false;
  }

  /// Require the AI summary to cite at least one verse the app actually showed.
  static bool hasGroundedCitation(String? text, Answer answer) {
    if (text == null) return false;
    final allowed = <String>{for (final passage in answer.passages) passage.verseKey, ...answer.shownVerseKeys};
    for (final match in RegExp(r'(\d{1,3})\s*:\s*(\d{1,3})').allMatches(text)) {
      if (allowed.contains('${match.group(1)}:${match.group(2)}')) return true;
    }
    return false;
  }
}
