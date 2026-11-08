import 'package:flutter/material.dart';

import '../../data/app_state.dart';
import '../../logic/logic.dart';
import '../admin_screen.dart';
import '../listen_screen.dart';
import '../pages_screen.dart';
import '../study_screen.dart';
import '../surah_screen.dart';
import '../theme.dart';
import '../widgets.dart';
import 'main_controller.dart';

/// Navigation drawer (a dialog sliding in from the left, 88% width up to
/// 420), the Settings sheet and the About & legal sheet of the chat screen.
class MainMenus {
  final BuildContext context;
  final MainController controller;
  final Future<void> Function() openHistory;
  final VoidCallback checkForUpdates;

  const MainMenus({required this.context, required this.controller, required this.openHistory, required this.checkForUpdates});

  AppState get app => controller.app;

  Future<void> showMenu() => showGeneralDialog<void>(
        context: context,
        barrierDismissible: true,
        barrierLabel: 'Menu',
        barrierColor: Colors.black45,
        transitionDuration: const Duration(milliseconds: 220),
        transitionBuilder: (context, animation, _, child) => SlideTransition(
          position: Tween<Offset>(begin: const Offset(-1, 0), end: Offset.zero)
              .animate(CurvedAnimation(parent: animation, curve: Curves.easeOut)),
          child: child,
        ),
        pageBuilder: (context, _, _) {
          final palette = Palette.of(context);
          final width = (MediaQuery.sizeOf(context).width * 0.88).clamp(0.0, 420.0);
          return Align(
            alignment: Alignment.centerLeft,
            child: Material(
              color: Colors.transparent,
              child: Container(
                width: width,
                height: double.infinity,
                decoration: BoxDecoration(
                  color: palette.paper,
                  image: DecorationImage(image: AssetImage(palette.pageBackground), fit: BoxFit.cover),
                ),
                child: SingleChildScrollView(
                  padding: const EdgeInsets.only(bottom: 24),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: _drawerBody(context, palette)),
                ),
              ),
            ),
          );
        },
      );

  List<Widget> _drawerBody(BuildContext dialog, Palette palette) {
    void go(VoidCallback action) {
      Navigator.pop(dialog);
      action();
    }

    void push(Widget screen) => go(() => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen)));

    return [
      Container(
        decoration: const BoxDecoration(gradient: Palette.heroGradient),
        padding: EdgeInsets.only(top: MediaQuery.paddingOf(context).top),
        child: Stack(
          children: [
            Positioned(
              top: 2,
              right: 2,
              child: Opacity(opacity: 0.24, child: Image.asset(Palette.calligraphy, width: 220, height: 155, fit: BoxFit.contain)),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(22, 20, 22, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('QUR’AN COMPANION', style: Fonts.medium(10.5, palette.sun, letterSpacing: 1.47, height: 1.2)),
                  Padding(padding: const EdgeInsets.only(top: 4), child: Text('Rafiq', style: Fonts.display(32, Colors.white))),
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text('Ask. Read. Listen.', style: Fonts.body(13.5, Palette.heroText)),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _menuSection(palette, 'EXPLORE'),
            drawerRow(palette, '♫', 'Listen to Qur’an', 'Car & home', palette.coral, () => push(const ListenScreen())),
            drawerRow(palette, '▤', 'Read the Qur’an', '114 surahs · offline', palette.teal, () => push(const SurahScreen())),
            drawerRow(palette, '＋', 'New chat', 'Fresh conversation', palette.purple, () => go(controller.newConversation)),
            _menuSection(palette, 'YOUR SPACE'),
            drawerRow(palette, '◷', 'History', 'Recent history', palette.amber, () => go(openHistory)),
            drawerRow(palette, '◉', 'Voice chat', controller.handsFree ? 'Hands-free · On' : 'Hands-free · Off',
                palette.sky, () => go(controller.toggleHandsFree)),
            drawerRow(palette, '⚙', 'Settings', 'Language & privacy', palette.lavender, () => go(showSettings)),
            _menuSection(palette, 'ABOUT'),
            drawerRow(palette, '↻', 'Update app', 'Check for updates', palette.mint, () => go(checkForUpdates)),
            drawerRow(palette, 'ⓘ', 'About & legal', 'Sources & licences', palette.pink, () => go(showLegal)),
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Text('Rafiq Al-Quran  ·  v${app.versionName}',
                  textAlign: TextAlign.center, style: Fonts.body(11, palette.muted)),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _menuSection(Palette palette, String label) => Padding(
        padding: const EdgeInsets.fromLTRB(6, 14, 6, 6),
        child: Text(label, style: Fonts.label(palette.goldDeep)),
      );

  static Widget drawerRow(Palette palette, String glyph, String label, String? detail, Color accent, VoidCallback action) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 5),
        child: Material(
          color: palette.card,
          elevation: 1,
          borderRadius: BorderRadius.circular(16),
          child: InkWell(
            onTap: action,
            borderRadius: BorderRadius.circular(16),
            child: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16), border: Border.all(color: palette.line.withAlpha(220))),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: accent, borderRadius: BorderRadius.circular(20)),
                    child: Text(glyph, style: Fonts.body(20, Colors.white, height: 1.1)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: Fonts.medium(16, palette.ink, height: 1.2)),
                        if (detail != null && detail.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 2),
                            child: Text(detail, maxLines: 1, overflow: TextOverflow.ellipsis, style: Fonts.body(12, palette.muted, height: 1.2)),
                          ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(left: 6, right: 2),
                    child: Text('›', style: Fonts.body(24, accent.withAlpha(180), height: 1.1)),
                  ),
                ],
              ),
            ),
          ),
        ),
      );

  // -------------------------------------------------------------- settings
  Future<void> showSettings() => _showSheet((dialog, palette) {
        final prefs = app.prefs;
        void act(VoidCallback action) {
          Navigator.pop(dialog);
          action();
        }

        void push(Widget screen) => act(() => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen)));
        final aiValue = prefs.aiAssistEnabled ? 'On · ${controller.ai.remainingToday()} of 10 left today' : 'Off';
        return [
          Text('Settings', style: Fonts.medium(24, palette.ink, height: 1.2)),
          _settingsSection(palette, 'Assistant'),
          _settingsRow(palette, 'Language', Language.byCode(prefs.language).englishName, () => act(showLanguageDialog)),
          _settingsRow(palette, 'AI answers', aiValue, () => act(() {
                prefs.aiAssistEnabled = !prefs.aiAssistEnabled;
                controller.hooks.toast(prefs.aiAssistEnabled
                    ? (controller.aiAvailable
                        ? 'AI answers on'
                        : 'AI answers on — server AI is not configured yet; local replies are used')
                    : 'AI answers off — Quran verses and index facts only');
              })),
          _settingsRow(palette, 'Voice chat', controller.handsFree ? 'On' : 'Off', () => act(controller.toggleHandsFree)),
          _settingsSection(palette, 'Voice & reading'),
          _settingsRow(palette, 'Voice features', prefs.voiceEnabled ? 'On' : 'Off', () => act(() {
                prefs.voiceEnabled = !prefs.voiceEnabled;
                if (prefs.voiceEnabled) {
                  controller.voice.ensureTts();
                } else {
                  controller.voice.stop();
                }
                controller.hooks.toast(prefs.voiceEnabled ? 'Voice features on' : 'Voice features off');
              })),
          _settingsRow(palette, 'Text size', '${(prefs.textScale * 100).round()}%',
              () => act(() => controller.hooks.toast('Use the A− and A+ rows below.'))),
          _settingsRow(palette, 'Text size smaller', 'A−', () => act(() => controller.changeTextScale(-0.05))),
          _settingsRow(palette, 'Text size larger', 'A+', () => act(() => controller.changeTextScale(0.05))),
          _settingsRow(palette, 'Theme', palette.dark ? 'Dark' : 'Light', () => act(app.toggleTheme)),
          _settingsRow(palette, 'Study tools', 'Bookmarks', () => push(const StudyScreen())),
          _settingsRow(palette, 'Browse surahs', '114 surahs', () => push(const SurahScreen())),
          _settingsSection(palette, 'Support & legal'),
          _settingsRow(palette, 'Sources & licences', null, () => act(() => PagesScreen.open(context, 'sources'))),
          _settingsRow(palette, 'Report an issue', null, () => act(() => PagesScreen.open(context, 'report'))),
          _settingsRow(palette, 'Privacy', 'Data handling', () => act(() => PagesScreen.open(context, 'privacy'))),
          _settingsRow(palette, 'Support the app', "No donations — du'a and feedback",
              () => act(() => PagesScreen.open(context, 'support'))),
          _settingsRow(palette, 'Check for updates', 'v${app.versionName}', () => act(checkForUpdates)),
          if (app.operatorUnlocked) _settingsRow(palette, 'Operator admin', 'Restricted', () => push(const AdminScreen())),
          Padding(
            padding: const EdgeInsets.only(top: 18),
            child: RafiqButton('Done', primary: true, expand: true, onPressed: () => Navigator.pop(dialog)),
          ),
        ];
      });

  Widget _settingsSection(Palette palette, String label) => Padding(
        padding: const EdgeInsets.only(top: 18, bottom: 4),
        child: Text(label.toUpperCase(), style: Fonts.medium(12, palette.green, letterSpacing: 0.96, height: 1.2)),
      );

  Widget _settingsRow(Palette palette, String label, String? value, VoidCallback action) => Column(
        children: [
          InkWell(
            onTap: action,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(2, 13, 2, 13),
              child: Row(
                children: [
                  Expanded(child: Text(label, style: Fonts.medium(17, palette.ink, height: 1.2))),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    child: Text(value ?? '', style: Fonts.body(14, palette.muted, height: 1.2)),
                  ),
                  Text('›', style: Fonts.body(21, palette.muted, height: 1.1)),
                ],
              ),
            ),
          ),
          Container(height: 1, color: palette.line),
        ],
      );

  /// Large, readable interface-language chooser.
  Future<void> showLanguageDialog() async {
    final languages = Language.all;
    final current = app.prefs.language;
    var selected = 0;
    final labels = <String>[];
    for (var i = 0; i < languages.length; i++) {
      final language = languages[i];
      labels.add('${language.englishName}  —  ${language.nativeName}  (${language.code})');
      if (language.code == current) selected = i;
    }
    final which = await chooseOne(context, 'Interface language', labels, selected: selected);
    if (which != null) await controller.applyLanguage(languages[which].code);
  }

  // ------------------------------------------------------------- about & legal
  Future<void> showLegal() => _showSheet((dialog, palette) {
        void open(String page) {
          Navigator.pop(dialog);
          PagesScreen.open(context, page);
        }

        return [
          Text('About & legal', style: Fonts.medium(24, palette.ink, height: 1.2)),
          Padding(
            padding: const EdgeInsets.only(top: 2, bottom: 12),
            child: Text('Transparency, sources and support', style: Fonts.body(13.5, palette.muted)),
          ),
          drawerRow(palette, 'ⓘ', 'About', 'What Rafiq Al-Quran is', palette.teal, () => open('about')),
          drawerRow(palette, '⚿', 'Privacy', 'How data is handled', palette.teal, () => open('privacy')),
          drawerRow(palette, '✓', 'Sources & licences', 'Dataset provenance and checksums', palette.teal, () => open('sources')),
          drawerRow(palette, '✉', 'Report an issue', 'Send feedback to the team', palette.teal, () => open('report')),
          drawerRow(palette, '♡', 'Support the app', "No donations — just du'a and feedback", palette.teal, () => open('support')),
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: RafiqButton('Done', primary: true, expand: true, onPressed: () => Navigator.pop(dialog)),
          ),
        ];
      });

  Future<void> _showSheet(List<Widget> Function(BuildContext dialog, Palette palette) body) => showDialog<void>(
        context: context,
        barrierColor: Colors.black45,
        builder: (dialog) {
          final palette = Palette.of(dialog);
          final width = MediaQuery.sizeOf(dialog).width * 0.92;
          return Dialog(
            backgroundColor: palette.paper,
            insetPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 24),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            child: SizedBox(
              width: width,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: body(dialog, palette)),
              ),
            ),
          );
        },
      );
}
