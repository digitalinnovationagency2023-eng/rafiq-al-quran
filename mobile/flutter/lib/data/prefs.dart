import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Device-local preferences. Same keys and defaults as the Android
/// `quran_prefs` file. No personal data is sent anywhere.
class Prefs {
  final SharedPreferences _prefs;

  Prefs(this._prefs);

  static Future<Prefs> load() async => Prefs(await SharedPreferences.getInstance());

  SharedPreferences get raw => _prefs;

  String get language => _prefs.getString('language') ?? 'en';
  set language(String value) => _prefs.setString('language', value);

  double get textScale => _prefs.getDouble('textScale') ?? 1.0;
  set textScale(double value) => _prefs.setDouble('textScale', value);

  bool get voiceEnabled => _prefs.getBool('voiceEnabled') ?? true;
  set voiceEnabled(bool value) => _prefs.setBool('voiceEnabled', value);

  /// Hands-free conversation: listen, answer aloud, then listen again.
  bool get handsFree => _prefs.getBool('handsFree') ?? false;
  set handsFree(bool value) => _prefs.setBool('handsFree', value);

  /// The three-card introduction is shown only once.
  bool get onboardingSeen => _prefs.getBool('onboardingSeen') ?? false;
  set onboardingSeen(bool value) => _prefs.setBool('onboardingSeen', value);

  String get history => _prefs.getString('history') ?? '[]';
  void setHistory(List<dynamic> array) => _prefs.setString('history', jsonEncode(array));
  void clearHistory() => _prefs.setString('history', '[]');

  /// Saved conversations shown on the History screen (newest first).
  List<dynamic> conversations() {
    try {
      final decoded = jsonDecode(_prefs.getString('conversations') ?? '[]');
      return decoded is List ? decoded : <dynamic>[];
    } catch (_) {
      return <dynamic>[];
    }
  }

  void setConversations(List<dynamic>? value) =>
      _prefs.setString('conversations', value == null ? '[]' : jsonEncode(value));

  // ---------------------------------------------------------------- theme
  String get theme => _prefs.getString('theme') ?? 'light';
  bool get isDark => theme == 'dark';
  set theme(String value) => _prefs.setString('theme', value == 'dark' ? 'dark' : 'light');

  // ------------------------------------------------------- reader options
  bool get sideBySide => _prefs.getBool('sideBySide') ?? false;
  set sideBySide(bool value) => _prefs.setBool('sideBySide', value);

  bool get bookMode => _prefs.getBool('bookMode') ?? true;
  set bookMode(bool value) => _prefs.setBool('bookMode', value);

  bool get continuousListen => _prefs.getBool('continuousListen') ?? true;
  set continuousListen(bool value) => _prefs.setBool('continuousListen', value);

  bool get translationVisible => _prefs.getBool('translationVisible') ?? true;
  set translationVisible(bool value) => _prefs.setBool('translationVisible', value);

  // ------------------------------------------------------ operator screen
  String get adminToken => _prefs.getString('adminToken') ?? '';
  set adminToken(String? value) => _prefs.setString('adminToken', value ?? '');

  // ------------------------------------------------------- recitation audio
  int get reciterIndex => _prefs.getInt('reciterIndex') ?? 0;
  set reciterIndex(int value) => _prefs.setInt('reciterIndex', value < 0 ? 0 : value);

  // ---------------------------------------------------------- AI assistance
  bool get aiAssistEnabled => _prefs.getBool('aiAssist') ?? true;
  set aiAssistEnabled(bool value) => _prefs.setBool('aiAssist', value);

  bool get aiConsentShown => _prefs.getBool('aiConsentShown') ?? false;
  set aiConsentShown(bool value) => _prefs.setBool('aiConsentShown', value);

  int get aiCallsToday => _prefs.getInt('aiCallsToday') ?? 0;
  set aiCallsToday(int value) => _prefs.setInt('aiCallsToday', value < 0 ? 0 : value);

  String get aiDay => _prefs.getString('aiDay') ?? '';
  set aiDay(String? value) => _prefs.setString('aiDay', value ?? '');
}
