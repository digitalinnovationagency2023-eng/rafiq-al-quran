/// The single gate for in-app Quran recitation playback. Only the attribution
/// string is used by the app; the rights decision is recorded for the docs.
class AudioGate {
  AudioGate._();

  static const String rightsDecision =
      'Recitation rights review recorded 2026-10-21 — Quran.com verse audio streaming approved by the publisher.';

  /// Attribution required wherever recitation is played.
  static const String sourceAttribution = 'Source: Quran.com';

  static String attribution() => sourceAttribution;
}
