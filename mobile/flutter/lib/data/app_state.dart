import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'asset_store.dart';
import 'prefs.dart';
import 'study_store.dart';

/// Process-wide services, created once before `runApp`.
class AppState {
  AppState._(this.prefs, this.store, this.packageInfo)
      : dark = ValueNotifier<bool>(prefs.isDark),
        textScale = ValueNotifier<double>(prefs.textScale),
        language = ValueNotifier<String>(prefs.language);

  static late AppState instance;

  final Prefs prefs;
  final StudyStore store;
  final PackageInfo? packageInfo;
  final AssetStore assets = AssetStore.instance;

  /// Theme flag; the MaterialApp rebuilds on change (Android recreated the activity).
  final ValueNotifier<bool> dark;
  final ValueNotifier<double> textScale;
  final ValueNotifier<String> language;

  /// Deep link from Study tools: a saved search to run on the chat screen.
  final ValueNotifier<String?> pendingAsk = ValueNotifier<String?>(null);

  /// The operator screen is unlocked for this process only.
  bool operatorUnlocked = false;

  static Future<AppState> init() async {
    final shared = await SharedPreferences.getInstance();
    PackageInfo? info;
    try {
      info = await PackageInfo.fromPlatform();
    } catch (_) {
      info = null;
    }
    instance = AppState._(Prefs(shared), StudyStore(shared), info);
    return instance;
  }

  /// For widget tests: an in-memory state without platform channels.
  static AppState forTests(SharedPreferences shared) {
    instance = AppState._(Prefs(shared), StudyStore(shared), null);
    return instance;
  }

  String get versionName => packageInfo?.version.isNotEmpty == true ? packageInfo!.version : '1.4.2';

  int get versionCode => int.tryParse(packageInfo?.buildNumber ?? '') ?? 23;

  void toggleTheme() {
    prefs.theme = dark.value ? 'light' : 'dark';
    dark.value = prefs.isDark;
  }

  void setTextScale(double value) {
    prefs.textScale = value;
    textScale.value = value;
  }

  void setLanguage(String code) {
    prefs.language = code;
    language.value = code;
  }
}
