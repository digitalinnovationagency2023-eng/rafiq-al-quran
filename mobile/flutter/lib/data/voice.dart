import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_recognition_error.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../logic/logic.dart';

/// Native speech input and output, kept strictly separate from recitation.
///
/// Text-to-speech is initialised lazily and the first "Listen" tap may wait for
/// the engine. The voice always matches the language of the text being spoken; a
/// language without an installed voice gets an actionable message instead of
/// being silently read by an English voice. Port of `Voice.java` on
/// `flutter_tts` + `speech_to_text`.
class VoiceService {
  VoiceService._();

  static final VoiceService instance = VoiceService._();

  /// The platform speech limit (Android's TextToSpeech.getMaxSpeechInputLength).
  static const int maxSpeechChars = 4000;

  FlutterTts? _tts;
  bool _ttsReady = false;
  bool _speaking = false;
  Set<String>? _ttsLanguages;

  final SpeechToText _speech = SpeechToText();
  bool _speechInitialised = false;
  bool _speechAvailable = false;

  /// Invoked when an utterance finishes (hands-free loop continues here).
  VoidCallback? onSpeechDone;

  /// Invoked with a user-facing message when synthesis fails after queuing.
  ValueChanged<String>? onSpeechError;

  bool get isSpeaking => _speaking;
  bool get listening => _speech.isListening;

  /// BCP-47 locale for an interface-language code.
  static String localeFor(String language) => VoiceLanguage.localeTag(language);

  /// Language of the text that will be spoken (never simply the UI setting).
  static String spokenLanguage(Answer answer) => VoiceLanguage.spokenLanguage(answer);

  /// Text spoken aloud: translation + references only, never the Arabic verse.
  static String spokenText(Answer answer) => VoiceLanguage.spokenText(answer, maxSpeechChars);

  // ------------------------------------------------------------------ TTS
  /// Creates the engine once. Returns an error message when it is unusable.
  Future<String?> ensureTts() async {
    if (_ttsReady) return null;
    try {
      final tts = FlutterTts();
      await tts.setSharedInstance(true);
      await tts.setIosAudioCategory(IosTextToSpeechAudioCategory.playback,
          [IosTextToSpeechAudioCategoryOptions.duckOthers, IosTextToSpeechAudioCategoryOptions.mixWithOthers]);
      await tts.awaitSpeakCompletion(false);
      tts.setStartHandler(() => _speaking = true);
      tts.setCompletionHandler(() {
        _speaking = false;
        onSpeechDone?.call();
      });
      tts.setCancelHandler(() => _speaking = false);
      tts.setErrorHandler((message) {
        _speaking = false;
        onSpeechError?.call('The device voice could not read this text aloud. Read it on screen, or add a '
            'voice for this language in iOS Settings > Accessibility > Spoken Content > Voices.');
      });
      try {
        final languages = await tts.getLanguages;
        if (languages is List) _ttsLanguages = {for (final tag in languages) tag.toString().toLowerCase()};
      } catch (_) {
        _ttsLanguages = null;
      }
      _tts = tts;
      _ttsReady = true;
      return null;
    } catch (_) {
      _tts = null;
      _ttsReady = false;
      return 'Text-to-speech is not available on this device. Check Spoken Content in iOS Settings, then try Listen again.';
    }
  }

  /// Returns null when speech started, otherwise a user-facing error message.
  Future<String?> speak(String? text, String language) async {
    final tts = _tts;
    if (tts == null || !_ttsReady) return 'The device voice is not ready yet. Try Listen again in a moment.';
    if (text == null || text.trim().isEmpty) return 'There is nothing to read aloud.';
    final locale = localeFor(language);
    final languageName = Language.byCode(language).englishName;
    bool available;
    try {
      final result = await tts.isLanguageAvailable(locale);
      available = result == true || result == 1;
      if (!available && _ttsLanguages != null) {
        final code = language.toLowerCase();
        available = _ttsLanguages!.any((tag) => tag == locale.toLowerCase() || tag.startsWith('$code-') || tag == code);
      }
    } catch (error) {
      return 'The device voice could not be prepared: $error';
    }
    if (!available) return missingVoiceMessage(languageName);
    try {
      await tts.setLanguage(locale);
      await tts.setSpeechRate(0.5);
      await tts.setPitch(1.0);
      final result = await tts.speak(text);
      if (result != 1 && result != true) return 'The device voice could not start. Please try again.';
      _speaking = true;
    } catch (error) {
      return 'The device voice could not start: $error';
    }
    return null;
  }

  static String missingVoiceMessage(String languageName) =>
      'The $languageName voice is not installed on this device. Open iOS Settings > Accessibility > '
      'Spoken Content > Voices and add a $languageName voice, then try Listen again. '
      'The translation is still shown on screen.';

  Future<void> stop() async {
    try {
      await _tts?.stop();
    } catch (_) {
      // best effort
    }
    _speaking = false;
  }

  // --------------------------------------------------------------- speech
  /// Whether speech recognition is available (and permitted) on this device.
  Future<bool> recognizerAvailable({ValueChanged<String>? onError, ValueChanged<String>? onStatus}) async {
    if (_speechInitialised) return _speechAvailable;
    try {
      _speechAvailable = await _speech.initialize(
        onError: (SpeechRecognitionError error) => onError?.call(error.errorMsg),
        onStatus: (status) => onStatus?.call(status),
      );
    } catch (_) {
      _speechAvailable = false;
    }
    _speechInitialised = true;
    return _speechAvailable;
  }

  /// Whether the recogniser has a locale for this interface language.
  Future<bool> supportsLanguage(String language) async {
    try {
      final locales = await _speech.locales();
      final code = language.toLowerCase();
      return locales.any((locale) {
        final id = locale.localeId.toLowerCase().replaceAll('_', '-');
        return id == localeFor(language).toLowerCase() || id.startsWith('$code-') || id == code;
      });
    } catch (_) {
      return true;
    }
  }

  /// Starts dictation in the interface language. [onResult] receives partial
  /// and final transcripts. Returns false when listening could not start.
  Future<bool> startListening(String language,
      {required void Function(String text, bool isFinal) onResult}) async {
    if (!_speechAvailable) return false;
    try {
      await _speech.listen(
        onResult: (result) => onResult(result.recognizedWords, result.finalResult),
        listenOptions: SpeechListenOptions(
          partialResults: true,
          listenMode: ListenMode.dictation,
          cancelOnError: true,
          localeId: localeFor(language).replaceAll('-', '_'),
          pauseFor: const Duration(seconds: 3),
          listenFor: const Duration(seconds: 30),
        ),
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> stopListening() async {
    try {
      await _speech.stop();
    } catch (_) {
      // recogniser already gone
    }
  }

  Future<void> cancelListening() async {
    try {
      await _speech.cancel();
    } catch (_) {
      // recogniser already gone
    }
  }

  static String unsupportedLanguageMessage(String language) {
    final name = Language.byCode(language).englishName;
    return "Voice input for $name isn't available on this device. Add the $name keyboard with dictation in iOS "
        'Settings > General > Keyboard, or type instead.';
  }

  /// Friendly wording for the speech_to_text error codes.
  static String errorMessage(String code, String language) {
    switch (code) {
      case 'error_speech_timeout':
      case 'error_no_match':
        return "I didn't hear anything. Tap Voice and speak again, or type instead.";
      case 'error_network':
      case 'error_network_timeout':
        return 'Voice input needs a network connection right now. You can type instead.';
      case 'error_audio':
        return 'The microphone could not be used. Check the device microphone and try again.';
      case 'error_permission':
      case 'error_insufficient_permissions':
        return 'Microphone and speech recognition permission are needed for voice input.';
      case 'error_busy':
        return 'Voice input is still finishing. Try again in a moment.';
      case 'error_language_not_supported':
      case 'error_language_unavailable':
        return unsupportedLanguageMessage(language);
      default:
        return 'Voice input did not complete. You can type instead.';
    }
  }
}
