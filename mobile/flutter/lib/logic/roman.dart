/// Romanised-input normalisation and fuzzy matching for the chat box.
///
/// People type Islamic terms in English letters ("sabr", "namaz", "gunah"),
/// romanised names with spelling variants ("Hazrath Eesa", "Yaqoob", "Moosa") and
/// often mix English with Roman Urdu/Arabic in one sentence. This helper
/// normalises such tokens and provides a small bounded edit-distance match so
/// small typos still resolve. It is a search aid only: every verse shown still
/// comes from the validated dataset.
class Roman {
  Roman._();

  static const Set<String> _honorifics = {
    'hazrat', 'hazrath', 'hadrat', 'hadrath', 'hazrate', 'hazrat-e', 'hzt', 'hz', //
    'prophet', 'prophets', 'nabi', 'nabe', 'paighambar', 'paigambar', 'payghambar', //
    'peer', 'pir', 'saint', 'imam', 'maulana', 'moulana', 'maulvi', 'shaikh', 'sheikh', //
    'alaihi', 'alaihis', 'alaihissalam', 'salam', 'rahimahullah', 'hafiz', 'qari',
  };

  static final RegExp _nonAlnum = RegExp(r'[^a-z0-9]');

  static bool isHonorific(String? token) {
    if (token == null) return false;
    final value = token.toLowerCase().replaceAll('’', "'").trim();
    return _honorifics.contains(value);
  }

  /// Search normal form: handles the common transliteration variants people type.
  static String canonical(String? token) {
    if (token == null) return '';
    var value = token.toLowerCase().replaceAll('’', "'").replaceAll('‘', "'");
    value = value.replaceAll(_nonAlnum, '');
    if (value.isEmpty) return '';
    value = value
        .replaceAll('aa', 'a')
        .replaceAll('ee', 'i')
        .replaceAll('ii', 'i')
        .replaceAll('oo', 'u')
        .replaceAll('uu', 'u')
        .replaceAll('ou', 'u')
        .replaceAll('kh', 'k')
        .replaceAll('gh', 'g')
        .replaceAll('dh', 'd')
        .replaceAll('th', 't')
        .replaceAll('ph', 'f')
        .replaceAll('q', 'k')
        .replaceAll('v', 'w');
    final builder = StringBuffer();
    var previous = -1;
    for (final current in value.codeUnits) {
      if (current != previous) builder.writeCharCode(current);
      previous = current;
    }
    return builder.toString();
  }

  static bool sameRoman(String? left, String? right) {
    final a = canonical(left);
    return a.isNotEmpty && a == canonical(right);
  }

  /// True when a romanised word is close enough to a known alias/term. Short
  /// words need an exact canonical match; 4-5 letters allow one typo, 6-7 allow
  /// one and 8+ allow two, always with the same first letter to avoid noise.
  static bool fuzzyMatch(String? token, String? term) {
    final a = canonical(token);
    final b = canonical(term);
    if (a.isEmpty || b.isEmpty) return false;
    if (a == b) return true;
    if (a.length < 4 || b.length < 4) return false;
    if (a.codeUnitAt(0) != b.codeUnitAt(0)) return false;
    if ((a.length - b.length).abs() > 2) return false;
    final limit = (a.length < b.length ? a.length : b.length) >= 8 ? 2 : 1;
    return _distance(a, b, limit) <= limit;
  }

  static bool isAscii(String? value) {
    if (value == null) return false;
    for (final c in value.codeUnits) {
      if (c > 127) return false;
    }
    return value.isNotEmpty;
  }

  static int _distance(String left, String right, int limit) {
    var previous = List<int>.generate(right.length + 1, (j) => j);
    var current = List<int>.filled(right.length + 1, 0);
    for (var i = 1; i <= left.length; i++) {
      current[0] = i;
      var rowMin = current[0];
      for (var j = 1; j <= right.length; j++) {
        final cost = left.codeUnitAt(i - 1) == right.codeUnitAt(j - 1) ? 0 : 1;
        var best = current[j - 1] + 1;
        if (previous[j] + 1 < best) best = previous[j] + 1;
        if (previous[j - 1] + cost < best) best = previous[j - 1] + cost;
        current[j] = best;
        if (best < rowMin) rowMin = best;
      }
      if (rowMin > limit) return limit + 1;
      final swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length];
  }
}
