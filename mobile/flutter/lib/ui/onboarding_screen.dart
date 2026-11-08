import 'package:flutter/material.dart';

import '../data/app_state.dart';
import 'main/main_screen.dart';
import 'theme.dart';

/// Three short cards shown once, immediately after the first Bismillah screen.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  static const Color gold = Color(0xFFE76F51);
  static const Color ink = Color(0xFF182B3A);
  static const Color body = Color(0xFF62748A);
  static const Color muted = Color(0xFF8496AB);

  static const List<String> icons = ['✦', '“', '◉'];
  static const List<String> headings = ['Ask naturally', 'Quran-only answers', 'Talk and listen'];
  static const List<String> bodies = [
    'Ask in English, Roman Urdu or Arabic — even with typos. The app works out the question, then searches the Quran for the most relevant verses.',
    'Every answer is grounded in Quran verses with clear references. No tafsir, hadith or fatwa, and Quran text is never invented.',
    'Tap the voice-chat icon, speak your question, and hear the answer read aloud. It listens again for the next question, hands-free.',
  ];

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  int _page = 0;

  void _finish() {
    AppState.instance.prefs.onboardingSeen = true;
    Navigator.of(context).pushReplacement(PageRouteBuilder<void>(
      pageBuilder: (_, _, _) => const MainScreen(),
      transitionsBuilder: (_, animation, _, child) => FadeTransition(opacity: animation, child: child),
      transitionDuration: const Duration(milliseconds: 350),
    ));
  }

  void _next() {
    if (_page < 2) {
      setState(() => _page++);
    } else {
      _finish();
    }
  }

  void _previous() {
    if (_page > 0) setState(() => _page--);
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.paddingOf(context);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_page > 0) {
          _previous();
        } else {
          _finish();
        }
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFF8F7FF),
        body: Container(
          decoration: const BoxDecoration(
            image: DecorationImage(image: AssetImage('assets/art/bg_page_light.jpg'), fit: BoxFit.cover),
          ),
          child: Column(
            children: [
              Container(height: padding.top, color: Palette.splashBar),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(22, 22, 22, 0),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text('Rafiq Al-Quran', style: Fonts.medium(16, OnboardingScreen.ink, height: 1.2)),
                          ),
                          GestureDetector(
                            onTap: _finish,
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
                              child: Text('Skip', style: Fonts.body(13, OnboardingScreen.muted)),
                            ),
                          ),
                        ],
                      ),
                      const Spacer(),
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 260),
                        transitionBuilder: (child, animation) => FadeTransition(
                          opacity: animation,
                          child: SlideTransition(
                            position: Tween<Offset>(begin: const Offset(0.04, 0), end: Offset.zero).animate(animation),
                            child: child,
                          ),
                        ),
                        child: Container(
                          key: ValueKey<int>(_page),
                          width: double.infinity,
                          padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(24),
                            border: Border.all(color: const Color(0xFFDCE6F2)),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(OnboardingScreen.icons[_page],
                                  style: Fonts.body(38, OnboardingScreen.gold, weight: FontWeight.w300, height: 1.1)),
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Text(OnboardingScreen.headings[_page],
                                    style: Fonts.medium(22, OnboardingScreen.ink, height: 1.2)),
                              ),
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Text(OnboardingScreen.bodies[_page],
                                    style: Fonts.body(14, OnboardingScreen.body, height: 1.3)),
                              ),
                              Padding(
                                padding: const EdgeInsets.only(top: 12),
                                child: Text('${_page + 1} of 3',
                                    style: Fonts.body(11, OnboardingScreen.muted, letterSpacing: 0.9)),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 168),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        bottomSheet: Container(
          color: Colors.transparent,
          padding: EdgeInsets.fromLTRB(22, 8, 22, 22 + padding.bottom),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    for (var i = 0; i < 3; i++)
                      Container(
                        width: 7,
                        height: 7,
                        margin: const EdgeInsets.symmetric(horizontal: 4),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: i == _page ? OnboardingScreen.gold : const Color(0x508496AB),
                        ),
                      ),
                  ],
                ),
              ),
              Row(
                children: [
                  Visibility(
                    visible: _page > 0,
                    maintainSize: true,
                    maintainAnimation: true,
                    maintainState: true,
                    child: GestureDetector(
                      onTap: _previous,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(8, 12, 18, 12),
                        child: Text('Back', style: Fonts.medium(14, OnboardingScreen.muted, height: 1.2)),
                      ),
                    ),
                  ),
                  const Spacer(),
                  GestureDetector(
                    onTap: _next,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 34, vertical: 14),
                      decoration: BoxDecoration(color: const Color(0xFF0E7C86), borderRadius: BorderRadius.circular(24)),
                      child: Text(_page == 2 ? 'Start' : 'Next', style: Fonts.medium(15, Colors.white, height: 1.2)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
