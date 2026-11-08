import 'package:flutter/material.dart';

/// Design tokens ported from `Ui.java`: jewel tones, pastel light and a rich
/// nocturnal dark theme. Resolve the active palette with [Palette.of].
class Palette {
  final bool dark;
  final Color ink;
  final Color paper;
  final Color card;
  final Color muted;
  final Color line;
  final Color silver;
  final Color gold;
  final Color goldBright;
  final Color goldDeep;
  final Color goldSoft;
  final Color green;
  final Color greenSoft;
  final Color danger;
  final Color bar;
  final Color bubble;
  final Color onBar;
  final Color teal;
  final Color coral;
  final Color purple;
  final Color amber;
  final Color pink;
  final Color sky;
  final Color mint;
  final Color lavender;
  final Color sun;

  const Palette._({
    required this.dark,
    required this.ink,
    required this.paper,
    required this.card,
    required this.muted,
    required this.line,
    required this.silver,
    required this.gold,
    required this.goldBright,
    required this.goldDeep,
    required this.goldSoft,
    required this.green,
    required this.greenSoft,
    required this.danger,
    required this.bar,
    required this.bubble,
    required this.onBar,
    required this.teal,
    required this.coral,
    required this.purple,
    required this.amber,
    required this.pink,
    required this.sky,
    required this.mint,
    required this.lavender,
    required this.sun,
  });

  static const Palette light = Palette._(
    dark: false,
    ink: Color(0xFF182B3A),
    paper: Color(0xFFF8F7FF),
    card: Color(0xFFFFFFFF),
    muted: Color(0xFF62748A),
    line: Color(0xFFDCE6F2),
    silver: Color(0xFFB8C6D9),
    gold: Color(0xFFE76F51),
    goldBright: Color(0xFFFFD166),
    goldDeep: Color(0xFFC94F3D),
    goldSoft: Color(0xFFFDEAE4),
    green: Color(0xFF0E7C86),
    greenSoft: Color(0xFFDDF4F2),
    danger: Color(0xFFD94A4A),
    bar: Color(0xFF0B5568),
    bubble: Color(0xFFE9F4FF),
    onBar: Color(0xFFFFFFFF),
    teal: Color(0xFF0E7C86),
    coral: Color(0xFFE76F51),
    purple: Color(0xFF7B5EA7),
    amber: Color(0xFFF4A261),
    pink: Color(0xFFE5989B),
    sky: Color(0xFF5B8DEF),
    mint: Color(0xFF3DC9B0),
    lavender: Color(0xFFB79CFF),
    sun: Color(0xFFFFD166),
  );

  static const Palette night = Palette._(
    dark: true,
    ink: Color(0xFFF6F8FF),
    paper: Color(0xFF0B1520),
    card: Color(0xFF142234),
    muted: Color(0xFF9CB0C8),
    line: Color(0xFF283A50),
    silver: Color(0xFFC4D2E6),
    gold: Color(0xFFFF8A6B),
    goldBright: Color(0xFFFFD166),
    goldDeep: Color(0xFFFFB09B),
    goldSoft: Color(0xFF35232D),
    green: Color(0xFF4ED6C7),
    greenSoft: Color(0xFF123B3C),
    danger: Color(0xFFFF8E8E),
    bar: Color(0xFF082C3A),
    bubble: Color(0xFF1A2C40),
    onBar: Color(0xFFFFFFFF),
    teal: Color(0xFF4ED6C7),
    coral: Color(0xFFFF8A6B),
    purple: Color(0xFFB79CFF),
    amber: Color(0xFFFFB86B),
    pink: Color(0xFFF5A9B8),
    sky: Color(0xFF7FB2FF),
    mint: Color(0xFF63E6D6),
    lavender: Color(0xFFCDB4FF),
    sun: Color(0xFFFFD166),
  );

  static Palette of(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? night : light;

  static Palette forDark(bool dark) => dark ? night : light;

  /// Rich teal → indigo → violet header, replacing the old black bar.
  static const LinearGradient headerGradient = LinearGradient(
    colors: [Color(0xFF0B6E7A), Color(0xFF3A5FCD), Color(0xFF6C4AA8)],
  );

  /// Soft colour wash used behind the hero and listening banners.
  static const LinearGradient heroGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF0B6E7A), Color(0xFF2C5BC4), Color(0xFF6C4AA8), Color(0xFFD95D4E)],
  );

  /// Thin multicolour rule used under the header and between sections.
  LinearGradient get ruleGradient => LinearGradient(colors: [coral, amber, mint, sky, lavender]);

  /// Splash colours (fixed, independent of the theme).
  static const Color splashDark = Color(0xFF081824);
  static const Color splashBar = Color(0xFF0B5568);
  static const Color heroText = Color(0xFFEAF8FF);

  String get pageBackground => dark ? 'assets/art/bg_page_dark.jpg' : 'assets/art/bg_page_light.jpg';
  static const String heroArt = 'assets/art/bg_hero.jpg';
  static const String calligraphy = 'assets/art/calligraphy.png';
}

/// Typography helpers mirroring the Android `Ui` text factories.
class Fonts {
  Fonts._();

  static const String arabic = 'Amiri';
  static const String serif = 'Georgia';
  static const List<String> serifFallback = ['Times New Roman', 'Noto Serif', 'serif'];

  /// Body text (`Ui.text`): sans, 1.45 line height.
  static TextStyle body(double size, Color color, {FontWeight? weight, double? letterSpacing, double height = 1.45}) =>
      TextStyle(fontSize: size, color: color, fontWeight: weight, letterSpacing: letterSpacing, height: height);

  /// `sans-serif-medium`.
  static TextStyle medium(double size, Color color, {double? letterSpacing, double height = 1.45}) =>
      body(size, color, weight: FontWeight.w500, letterSpacing: letterSpacing, height: height);

  /// Editorial display face used for screen titles and hero headings (`Ui.display`).
  static TextStyle display(double size, Color color) => TextStyle(
        fontSize: size,
        color: color,
        fontWeight: FontWeight.bold,
        fontFamily: serif,
        fontFamilyFallback: serifFallback,
        height: 1.05,
        letterSpacing: -0.025 * size,
      );

  /// Amiri gives the Arabic text a proper mushaf voice (`Ui.arabic`).
  static TextStyle quran(double size, Color color, {double height = 1.55}) =>
      TextStyle(fontSize: size, color: color, fontFamily: arabic, height: height);

  /// Small uppercase-ish label (`Ui.label`): 12.5, muted, letter-spaced.
  static TextStyle label(Color color) => medium(12.5, color, letterSpacing: 0.08 * 12.5);

  /// `Ui.sectionLabel`: 14.5 medium ink.
  static TextStyle section(Color color) => medium(14.5, color, letterSpacing: 0.01 * 14.5);
}

ThemeData buildTheme(bool dark) {
  final palette = Palette.forDark(dark);
  final base = dark ? ThemeData.dark(useMaterial3: true) : ThemeData.light(useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: palette.paper,
    colorScheme: base.colorScheme.copyWith(
      primary: palette.green,
      secondary: palette.gold,
      surface: palette.card,
      error: palette.danger,
      brightness: dark ? Brightness.dark : Brightness.light,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: palette.card,
      titleTextStyle: Fonts.medium(19, palette.ink, height: 1.25),
      contentTextStyle: Fonts.body(14, palette.ink),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: palette.ink,
      contentTextStyle: Fonts.body(13.5, palette.paper, height: 1.3),
      behavior: SnackBarBehavior.floating,
    ),
    textTheme: base.textTheme.apply(bodyColor: palette.ink, displayColor: palette.ink),
    inputDecorationTheme: InputDecorationTheme(
      hintStyle: Fonts.body(14, palette.muted),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: palette.line)),
      enabledBorder:
          OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: palette.line)),
      focusedBorder:
          OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: palette.green)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      fillColor: palette.paper,
      filled: true,
    ),
    dividerColor: palette.line,
  );
}
