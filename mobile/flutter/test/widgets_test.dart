// Widget smoke tests for screens that need no platform channels.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rafiq_al_quran/data/app_state.dart';
import 'package:rafiq_al_quran/ui/history_screen.dart';
import 'package:rafiq_al_quran/ui/onboarding_screen.dart';
import 'package:rafiq_al_quran/ui/pages_screen.dart';
import 'package:rafiq_al_quran/ui/theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

Widget _app(Widget home, {bool dark = false}) =>
    MaterialApp(theme: buildTheme(false), darkTheme: buildTheme(true), themeMode: dark ? ThemeMode.dark : ThemeMode.light, home: home);

void main() {
  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    AppState.forTests(await SharedPreferences.getInstance());
  });

  testWidgets('onboarding walks through three cards and marks itself seen', (tester) async {
    await tester.pumpWidget(_app(const OnboardingScreen()));
    expect(find.text('Ask naturally'), findsOneWidget);
    expect(find.text('1 of 3'), findsOneWidget);
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
    expect(find.text('Quran-only answers'), findsOneWidget);
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
    expect(find.text('Talk and listen'), findsOneWidget);
    expect(find.text('Start'), findsOneWidget);
    expect(AppState.instance.prefs.onboardingSeen, isFalse);
    await tester.tap(find.text('Skip'));
    await tester.pump();
    expect(AppState.instance.prefs.onboardingSeen, isTrue);
  });

  testWidgets('history screen shows the empty state and lists archives', (tester) async {
    await tester.pumpWidget(_app(const HistoryScreen()));
    expect(find.text('No saved conversations yet'), findsOneWidget);
    AppState.instance.prefs.setConversations([
      {'id': 'a', 'title': 'What does the Quran say about patience?', 'savedAt': 1700000000000, 'turns': [{}, {}]},
    ]);
    await tester.pumpWidget(_app(HistoryScreen(key: UniqueKey()), dark: true));
    expect(find.text('What does the Quran say about patience?'), findsOneWidget);
    expect(find.textContaining('2 messages'), findsOneWidget);
    expect(find.text('Clear history'), findsOneWidget);
  });

  testWidgets('support page keeps the no-donations wording and contact details', (tester) async {
    await tester.pumpWidget(_app(const PagesScreen(page: 'support')));
    expect(find.text('We do not need money'), findsOneWidget);
    final footnote = find.text('No purchase, subscription, donation or payment is requested in this app.');
    await tester.scrollUntilVisible(footnote, 200, scrollable: find.byType(Scrollable).first);
    expect(footnote, findsOneWidget);
    await tester.pumpWidget(_app(const PagesScreen(page: 'about')));
    final contact = find.textContaining('info@rqmbooks.com');
    await tester.scrollUntilVisible(contact, 200, scrollable: find.byType(Scrollable).first);
    expect(contact, findsOneWidget);
    expect(find.textContaining('+971 9 501 0254'), findsOneWidget);
  });
}
