import 'dart:async';

import 'package:flutter/material.dart';

import '../data/app_state.dart';
import 'main/main_screen.dart';
import 'onboarding_screen.dart';
import 'theme.dart';

/// Emerald-and-gold Bismillah opening. Shown on every cold start for about
/// 2.6 seconds; tapping anywhere skips it. The first run then shows a short
/// three-card introduction.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  static const Color gold = Color(0xFFFFD166);
  static const Color silver = Color(0xFFEAF8FF);
  static const Color muted = Color(0xFFB8C6D9);
  static const Duration hold = Duration(milliseconds: 2600);

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  Timer? _timer;
  bool _launched = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 2000))..forward();
    _timer = Timer(SplashScreen.hold, _advance);
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _advance() {
    if (_launched || !mounted) return;
    _launched = true;
    _timer?.cancel();
    final Widget target = AppState.instance.prefs.onboardingSeen ? const MainScreen() : const OnboardingScreen();
    Navigator.of(context).pushReplacement(PageRouteBuilder<void>(
      pageBuilder: (_, _, _) => target,
      transitionsBuilder: (_, animation, _, child) => FadeTransition(opacity: animation, child: child),
      transitionDuration: const Duration(milliseconds: 350),
    ));
  }

  /// Staggered fade matching the Android start delays and durations (ms).
  Animation<double> _fade(int delay, int duration, {double to = 1}) => Tween<double>(begin: 0, end: to).animate(
        CurvedAnimation(
          parent: _controller,
          curve: Interval(delay / 2000, ((delay + duration) / 2000).clamp(0.0, 1.0), curve: Curves.decelerate),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final bismillahFade = _fade(260, 900);
    final meaningFade = _fade(560, 700, to: 0.86);
    final dividerScale = _fade(760, 760);
    final titleFade = _fade(1050, 600);
    final subtitleFade = _fade(1200, 600);
    final skipFade = _fade(1450, 500);
    return GestureDetector(
      onTap: _advance,
      behavior: HitTestBehavior.opaque,
      child: Scaffold(
        backgroundColor: Palette.splashDark,
        body: Stack(
          fit: StackFit.expand,
          children: [
            Image.asset(Palette.heroArt, fit: BoxFit.cover),
            Center(
              child: Opacity(
                opacity: 0.28,
                child: Image.asset(Palette.calligraphy, width: 260, height: 182, fit: BoxFit.contain),
              ),
            ),
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    AnimatedBuilder(
                      animation: _controller,
                      builder: (context, child) => Opacity(
                        opacity: bismillahFade.value,
                        child: Transform.translate(
                          offset: Offset(0, 14 * (1 - bismillahFade.value)),
                          child: Transform.scale(scale: 0.94 + 0.06 * bismillahFade.value, child: child),
                        ),
                      ),
                      child: Text(
                        'بِسْمِ اللهِ الرَّحْمَٰنِ الرَّحِيمِ',
                        maxLines: 1,
                        textAlign: TextAlign.center,
                        textDirection: TextDirection.rtl,
                        style: Fonts.quran(23.5, SplashScreen.gold, height: 1.35).copyWith(
                          shadows: const [Shadow(color: Color(0x96FFD166), blurRadius: 18, offset: Offset(0, 3))],
                        ),
                      ),
                    ),
                    FadeTransition(
                      opacity: meaningFade,
                      child: Padding(
                        padding: const EdgeInsets.only(top: 10),
                        child: Text('In the name of Allah, the Most Gracious, the Most Merciful',
                            textAlign: TextAlign.center, style: Fonts.body(12, SplashScreen.silver)),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: 24, bottom: 20),
                      child: AnimatedBuilder(
                        animation: dividerScale,
                        builder: (context, child) => Transform.scale(scaleX: dividerScale.value, child: child),
                        child: Container(
                          width: 150,
                          height: 2,
                          decoration: const BoxDecoration(
                            gradient: LinearGradient(colors: [Colors.transparent, SplashScreen.gold, Colors.transparent]),
                          ),
                        ),
                      ),
                    ),
                    AnimatedBuilder(
                      animation: titleFade,
                      builder: (context, child) => Opacity(
                        opacity: titleFade.value,
                        child: Transform.translate(offset: Offset(0, 8 * (1 - titleFade.value)), child: child),
                      ),
                      child: Text('Rafiq Al-Quran',
                          textAlign: TextAlign.center, style: Fonts.medium(25, Colors.white, height: 1.2)),
                    ),
                    FadeTransition(
                      opacity: subtitleFade,
                      child: Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text('Quran-only assistant',
                            textAlign: TextAlign.center, style: Fonts.body(12, SplashScreen.muted)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 26 + MediaQuery.paddingOf(context).bottom,
              child: FadeTransition(
                opacity: skipFade,
                child: Center(
                  child: GestureDetector(
                    onTap: _advance,
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                      child: Text('Tap to skip', style: Fonts.body(11, const Color(0xAADCE6E0))),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
