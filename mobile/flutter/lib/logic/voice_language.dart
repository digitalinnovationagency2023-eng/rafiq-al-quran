import 'answer.dart';

/// Locale and spoken-text selection for the voice features.
///
/// Two principles: the TTS voice must match the language of the text that is
/// actually spoken, and other languages' translations must never be read by an
/// English fallback voice.
class VoiceLanguage {
  VoiceLanguage._();

  static const String defaultLocale = 'en-US';

  static const List<String> _codes = ['ar', 'en', 'ur', 'id', 'bn', 'tr', 'fa', 'ms', 'sw', 'fr'];
  static const List<String> _tags = [
    'ar-SA', 'en-US', 'ur-PK', 'id-ID', 'bn-BD', 'tr-TR', 'fa-IR', 'ms-MY', 'sw-KE', 'fr-FR', //
  ];

  /// BCP-47 recognition/TTS locale for an interface-language code.
  static String localeTag(String? language) {
    if (language != null) {
      for (var i = 0; i < _codes.length; i++) {
        if (_codes[i] == language) return _tags[i];
      }
    }
    return defaultLocale;
  }

  /// The language actually spoken: the language of the spoken text, not the UI
  /// setting. Passage translations win; English is spoken for the Arabic notice
  /// fallback (Arabic has no translation rows) and for non-Arabic fallbacks.
  static String spokenLanguage(Answer? answer) {
    if (answer == null) return 'en';
    if (answer.kind == 'conversation') return 'en';
    if (answer.kind == 'fallback') return answer.language == 'ar' ? 'ar' : 'en';
    for (final passage in answer.passages) {
      if (passage.translationLanguage.isNotEmpty) return passage.translationLanguage;
    }
    return 'en';
  }

  /// The exact text spoken for an answer. For a non-English voice only the
  /// translated passages are spoken: the English reference labels and connective
  /// sentences stay on screen, because another language's voice mispronounces them.
  static String spokenText(Answer? answer, int maxChars) {
    if (answer == null) return '';
    String text;
    if (answer.kind == 'fallback' || answer.kind == 'conversation') {
      text = answer.message;
    } else {
      final language = spokenLanguage(answer);
      final builder = StringBuffer();
      for (final passage in answer.passages) {
        if (builder.isNotEmpty) builder.write(' ');
        if (language == 'en') builder.write('${passage.referenceLabel}. ');
        builder.write(passage.translationText);
      }
      final messageLanguage = answer.language == 'ar' ? 'ar' : 'en';
      final messageMatchesVoice = messageLanguage == language;
      if (messageMatchesVoice && answer.message.trim().isNotEmpty) {
        builder.write(' ${answer.message}');
      }
      text = builder.toString();
    }
    return truncate(text.trim(), maxChars);
  }

  /// Caps spoken input at the platform limit without cutting a word in half.
  static String truncate(String? value, int maxChars) {
    if (value == null || value.isEmpty) return '';
    if (maxChars <= 0) return '';
    if (value.length <= maxChars) return value;
    var cut = value.substring(0, maxChars);
    final lastSpace = cut.lastIndexOf(' ');
    if (lastSpace > maxChars ~/ 2) cut = cut.substring(0, lastSpace);
    return cut.trim();
  }
}
