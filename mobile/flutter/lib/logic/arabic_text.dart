/// Arabic text helpers. The canonical Quranic text is never modified.
class ArabicText {
  ArabicText._();

  static const Set<int> _diacritics = {
    0x0610, 0x0611, 0x0612, 0x0613, 0x0614, 0x0615, 0x0616, 0x0617, 0x0618, 0x0619, 0x061A, //
    0x064B, 0x064C, 0x064D, 0x064E, 0x064F, 0x0650, 0x0651, 0x0652, 0x0653, 0x0654, 0x0655, //
    0x0656, 0x0657, 0x0658, 0x0659, 0x065A, 0x065B, 0x065C, 0x065D, 0x065E, 0x065F, 0x0670, 0x0640,
  };

  static final RegExp _spaces = RegExp(r'\s+');
  static final RegExp _letterOrDigit = RegExp(r'^[\p{L}\p{N}]$', unicode: true);
  static final RegExp _whitespace = RegExp(r'^\s$', unicode: true);
  static final Map<int, bool> _letterCache = <int, bool>{};

  static String canonical(String? text) {
    if (text == null) return '';
    return text.replaceAll('﻿', '').replaceAll('​', '').replaceAll('‌', '').replaceAll('‍', '').trim();
  }

  /// Readable form without diacritics (used for display fallback and word counts).
  static String plain(String? text) {
    final value = canonical(text);
    final builder = StringBuffer();
    for (final c in value.codeUnits) {
      if (isDiacritic(c)) continue;
      builder.writeCharCode(c);
    }
    return builder.toString().replaceAll(_spaces, ' ').trim();
  }

  static bool isDiacritic(int c) {
    if (_diacritics.contains(c)) return true;
    return c >= 0x06D6 && c <= 0x06ED;
  }

  static bool _isLetterOrDigit(int c) {
    if (c < 128) {
      return (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A);
    }
    return _letterCache.putIfAbsent(c, () => _letterOrDigit.hasMatch(String.fromCharCode(c)));
  }

  static bool _isWhitespace(int c) {
    if (c == 0x20 || (c >= 0x09 && c <= 0x0D)) return true;
    if (c < 128) return false;
    return _whitespace.hasMatch(String.fromCharCode(c));
  }

  /// Search normalisation: unify alef/hamza forms, ya/alef-maqsura, ta-marbuta,
  /// remove diacritics and punctuation. Never shown to users.
  static String normalize(String? text) {
    final value = plain(text).toLowerCase();
    final builder = StringBuffer();
    for (var c in value.codeUnits) {
      if (c == 0x0622 || c == 0x0623 || c == 0x0625 || c == 0x0671) {
        c = 0x0627;
      } else if (c == 0x0649) {
        c = 0x064A;
      } else if (c == 0x0629) {
        c = 0x0647;
      } else if (c == 0x0624) {
        c = 0x0648;
      } else if (c == 0x0626) {
        c = 0x064A;
      } else if (isDiacritic(c)) {
        continue;
      }
      if (_isLetterOrDigit(c) || _isWhitespace(c)) {
        builder.writeCharCode(c);
      } else {
        builder.write(' ');
      }
    }
    return builder.toString().replaceAll(_spaces, ' ').trim();
  }

  static List<String> tokens(String? text) {
    final normalized = normalize(text);
    final out = <String>[];
    if (normalized.isEmpty) return out;
    for (final token in normalized.split(' ')) {
      if (token.isNotEmpty) out.add(token);
    }
    return out;
  }

  /// Light stemming for search only: definite article and common affixes.
  static String stem(String? token) {
    if (token == null) return '';
    var value = token;
    if (value.length < 3) return value;
    for (var pass = 0; pass < 2; pass++) {
      if (value.length > 5 &&
          (value.startsWith('وال') ||
              value.startsWith('بال') ||
              value.startsWith('كال') ||
              value.startsWith('فال'))) {
        value = value.substring(3);
      } else if (value.length > 3 && value.startsWith('ال')) {
        value = value.substring(2);
      } else if (value.length > 4 && 'وفبكل'.contains(value[0])) {
        value = value.substring(1);
      } else {
        break;
      }
    }
    const suffixes = [
      'ها', 'هم', 'هن', 'كم', 'نا', //
      'ات', 'ون', 'ين', 'ان',
    ];
    for (final suffix in suffixes) {
      if (value.endsWith(suffix) && value.length - suffix.length >= 3) {
        value = value.substring(0, value.length - suffix.length);
        break;
      }
    }
    if (value.length > 4 && (value.endsWith('ي') || value.endsWith('ه'))) {
      value = value.substring(0, value.length - 1);
    }
    return value;
  }

  static List<String> searchForms(String token) {
    final forms = <String>[token];
    final stemmed = stem(token);
    if (stemmed != token && stemmed.isNotEmpty) forms.add(stemmed);
    return forms;
  }

  static bool isArabic(String? text) {
    if (text == null) return false;
    for (final c in text.codeUnits) {
      if (c >= 0x0600 && c <= 0x06FF) return true;
    }
    return false;
  }
}
