// App Store screenshots of the real app, captured on an iOS simulator by
// .github/workflows/ios-screenshots.yml:
//
//   flutter drive --driver=test_driver/screenshots_driver.dart \
//     --target=integration_test/screenshots_test.dart -d <simulator udid>
//
// The test seeds device-local data through the app's own stores (preferences,
// bookmarks, notes, saved searches, reading plans), launches the app through
// `main()`, walks to each screen and captures it with
// `IntegrationTestWidgetsFlutterBinding.takeScreenshot`. The driver writes the
// PNGs to $SCREENSHOT_DIR. Nothing here changes app behaviour.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:rafiq_al_quran/data/app_state.dart';
import 'package:rafiq_al_quran/data/study_store.dart';
import 'package:rafiq_al_quran/logic/logic.dart';
import 'package:rafiq_al_quran/main.dart' as app;
import 'package:rafiq_al_quran/ui/main/answer_view.dart';
import 'package:rafiq_al_quran/ui/main/main_screen.dart';
import 'package:rafiq_al_quran/ui/player_screen.dart';
import 'package:rafiq_al_quran/ui/reader_screen.dart';
import 'package:rafiq_al_quran/ui/study_screen.dart';
import 'package:rafiq_al_quran/ui/surah_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const String question = 'What does the Quran say about patience?';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.framePolicy = LiveTestWidgetsFlutterBindingFramePolicy.fullyLive;
  // No DEBUG ribbon even though `flutter drive` runs a debug build.
  WidgetsApp.debugAllowBannerOverride = false;

  testWidgets('App Store screenshots', (tester) async {
    await _seed();
    await app.main();

    final shooter = _Shooter(binding, tester);
    // Splash (about 2.6 s) then the chat home with the dataset loaded.
    await shooter.until(find.byType(MainScreen), const Duration(seconds: 40));
    await shooter.until(find.text('CONTINUE READING'), const Duration(seconds: 60));
    await shooter.until(find.textContaining('Juz 15'), const Duration(seconds: 30));

    // 07: the home screen (quick strip, hero, continue reading).
    await shooter.shot('07-home');

    // 01: a question answered from the bundled Quran, with matched verses.
    AppState.instance.pendingAsk.value = question;
    await shooter.until(find.byType(PassageCard), const Duration(seconds: 30));
    await shooter.wait(const Duration(milliseconds: 1200));
    final chatList = find.descendant(of: find.byType(MainScreen), matching: find.byType(Scrollable)).first;
    tester.state<ScrollableState>(chatList).position.jumpTo(0);
    await shooter.shot('01-ask');

    final navigator = tester.state<NavigatorState>(find.byType(Navigator).first);

    // 03: the surah list.
    unawaited(navigator.push(MaterialPageRoute<void>(builder: (_) => const SurahScreen())));
    await shooter.until(find.text('Al-Fatihah'), const Duration(seconds: 20));
    // The list pads its top by the safe-area inset; scroll past that empty band.
    await shooter.scrollIntoView(find.text('Al-Fatihah'),
        find.descendant(of: find.byType(SurahScreen), matching: find.byType(ListView)), 12);
    await shooter.shot('03-surahs');
    navigator.popUntil((route) => route.isFirst);
    await shooter.wait(const Duration(milliseconds: 600));

    // 02: the reader, Al-Fatihah in book view (Arabic with the translation).
    unawaited(navigator.push(MaterialPageRoute<void>(builder: (_) => const ReaderScreen(chapterId: 1))));
    await shooter.until(find.text('Al-Fatihah · 1:1'), const Duration(seconds: 20));
    await shooter.shot('02-reader');
    navigator.popUntil((route) => route.isFirst);
    await shooter.wait(const Duration(milliseconds: 600));

    // 04: the recitation player. Try to stream the verse; if the network is not
    // available, reopen the player so no error line is on screen.
    await _player(shooter, navigator);
    navigator.popUntil((route) => route.isFirst);
    await shooter.wait(const Duration(milliseconds: 800));

    // 05: study tools with the seeded collections, notes and plans.
    unawaited(navigator.push(MaterialPageRoute<void>(builder: (_) => const StudyScreen())));
    await shooter.until(find.text('Bookmark collections'), const Duration(seconds: 20));
    await shooter.shot('05-study');
    navigator.popUntil((route) => route.isFirst);
    await shooter.wait(const Duration(milliseconds: 600));

    // 06: the reader in dark theme, scrolled to the verses of Ya-Sin.
    AppState.instance.toggleTheme();
    await shooter.wait(const Duration(milliseconds: 800));
    unawaited(navigator.push(MaterialPageRoute<void>(builder: (_) => const ReaderScreen(chapterId: 36))));
    await shooter.until(find.text('Ya-Sin · 36:1'), const Duration(seconds: 20));
    await shooter.wait(const Duration(milliseconds: 600));
    await shooter.scrollIntoView(find.text('Ya-Sin · 36:1'),
        find.descendant(of: find.byType(ReaderScreen), matching: find.byType(SingleChildScrollView)), 14);
    await shooter.shot('06-reader-dark');
    navigator.popUntil((route) => route.isFirst);
    AppState.instance.toggleTheme();
    await shooter.wait(const Duration(milliseconds: 600));
  }, timeout: const Timeout(Duration(minutes: 12)));
}

Future<void> _player(_Shooter shooter, NavigatorState navigator) async {
  Route<void> route() => MaterialPageRoute<void>(builder: (_) => const PlayerScreen(chapterId: 55, verseKey: '55:13'));
  unawaited(navigator.push(route()));
  await shooter.until(find.text('▶ Play'), const Duration(seconds: 20));
  await shooter.wait(const Duration(milliseconds: 500));
  await shooter.tester.tap(find.text('▶ Play'));
  final playing = await shooter.until(find.text('⏸ Pause'), const Duration(seconds: 15), required: false);
  final failed = find.textContaining('Could not play').evaluate().isNotEmpty;
  if (playing && !failed) {
    await shooter.wait(const Duration(milliseconds: 1500));
    await shooter.shot('04-listen');
    return;
  }
  // Offline fallback: a fresh player shows the idle "Tap play" state.
  navigator.pop();
  await shooter.wait(const Duration(milliseconds: 800));
  unawaited(navigator.push(route()));
  await shooter.until(find.text('▶ Play'), const Duration(seconds: 20));
  await shooter.shot('04-listen');
}

/// Device-local data, written through the app's own stores before launch.
Future<void> _seed() async {
  final shared = await SharedPreferences.getInstance();
  await shared.clear();
  await shared.setBool('onboardingSeen', true);
  await shared.setString('theme', 'light');
  await shared.setString('language', 'en');
  await shared.setDouble('textScale', 1.0);
  await shared.setBool('bookMode', true);
  await shared.setBool('translationVisible', true);
  // Offline answers only: deterministic, and no calls to the AI service.
  await shared.setBool('aiAssist', false);
  await shared.setBool('aiConsentShown', true);

  final store = StudyStore(shared);
  for (final name in ['Patience & hope', 'Duas to memorise', 'Family']) {
    store.addCollection(name);
  }
  const bookmarks = {
    '2:153': 'Patience & hope',
    '2:286': 'Patience & hope',
    '94:5': 'Patience & hope',
    '94:6': 'Patience & hope',
    '20:114': 'Duas to memorise',
    '2:201': 'Duas to memorise',
    '25:74': 'Family',
    '17:24': 'Family',
    '2:255': StudyData.defaultCollection,
  };
  bookmarks.forEach(store.bookmark);
  store.setNote('94:5', 'With hardship comes ease, and the next verse repeats it. Read this on hard days.');
  store.setNote('2:286', 'No soul is burdened beyond what it can bear. Memorise the closing dua.');
  store.setNote('20:114', 'Rabbi zidni ilma: ask for more knowledge before every study session.');
  for (final search in ['Who is Prophet Musa?', 'Verses about gratitude', 'Show me 2:255']) {
    store.addSearch(search);
  }
  final juzAmma = store.createPlan('Juz Amma this month', StudyData.presetJuz30, 30);
  for (var chapter = 78; chapter <= 95; chapter++) {
    store.markChapterDone(juzAmma.id, chapter);
  }
  await Future<void>.delayed(const Duration(milliseconds: 5));
  final khatm = store.createPlan('Ramadan khatm', StudyData.presetFull, 30);
  for (var chapter = 1; chapter <= 18; chapter++) {
    store.markChapterDone(khatm.id, chapter);
  }
  // A saved reading position for the "Continue reading" card.
  store.recordHistoryChapter(18);
  store.recordProgress(18, '18:10');
}

class _Shooter {
  final IntegrationTestWidgetsFlutterBinding binding;
  final WidgetTester tester;
  bool _surfaceConverted = false;

  _Shooter(this.binding, this.tester);

  /// Pumps real frames for [duration].
  Future<void> wait(Duration duration) async {
    final end = DateTime.now().add(duration);
    while (DateTime.now().isBefore(end)) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Pumps until [finder] matches something. Fails the test when [required].
  Future<bool> until(Finder finder, Duration timeout, {bool required = true}) async {
    final end = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(end)) {
      await tester.pump(const Duration(milliseconds: 200));
      if (finder.evaluate().isNotEmpty) return true;
    }
    if (required) fail('Timed out after ${timeout.inSeconds} s waiting for $finder');
    return false;
  }

  /// Scrolls [scrollView] so that [target] sits [gap] logical pixels below its top edge.
  Future<void> scrollIntoView(Finder target, Finder scrollView, double gap) async {
    final view = scrollView.first;
    final scrollable = find.descendant(of: view, matching: find.byType(Scrollable)).first;
    final position = tester.state<ScrollableState>(scrollable).position;
    final delta = tester.getTopLeft(target.first).dy - tester.getTopLeft(view).dy - gap;
    position.jumpTo((position.pixels + delta).clamp(0.0, position.maxScrollExtent));
    await wait(const Duration(milliseconds: 400));
  }

  /// Lets fonts, images and animations settle, then captures the screen.
  Future<void> shot(String name) async {
    if (Platform.isAndroid && !_surfaceConverted) {
      await binding.convertFlutterSurfaceToImage();
      _surfaceConverted = true;
    }
    try {
      await tester.pumpAndSettle(const Duration(milliseconds: 100), EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 8));
    } catch (_) {
      // A live position stream (the player) never settles; the fixed wait below covers it.
    }
    await wait(const Duration(milliseconds: 1500));
    await binding.takeScreenshot(name);
    // ignore: avoid_print
    print('screenshot $name captured');
  }
}
