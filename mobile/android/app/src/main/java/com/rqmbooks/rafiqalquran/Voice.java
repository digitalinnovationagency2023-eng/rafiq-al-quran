package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.rqmbooks.rafiqalquran.logic.Answer;
import com.rqmbooks.rafiqalquran.logic.Language;
import com.rqmbooks.rafiqalquran.logic.VoiceLanguage;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Native speech input and output, kept strictly separate from recitation.
 *
 * <p>Text-to-speech is initialised lazily and the first "Listen" tap may wait for
 * the engine. The voice always matches the language of the text being spoken; a
 * language without an installed voice gets an actionable message instead of being
 * silently read by an English voice.
 */
public final class Voice {
    private static final String TAG = "RafiqVoice";

    private Voice() {}

    /** BCP-47 locale for an interface-language code. */
    public static String localeFor(String language) {
        return VoiceLanguage.localeTag(language);
    }

    /** Builds a properly parsed Java locale, e.g. "en-US" -> [en, US]. */
    public static Locale ttsLocale(String language) {
        String tag = localeFor(language);
        int dash = tag.indexOf('-');
        if (dash < 0) return new Locale(tag);
        String country = tag.substring(dash + 1);
        return country.length() == 2 ? new Locale(tag.substring(0, dash), country) : new Locale(tag.substring(0, dash));
    }

    public static boolean recognizerAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public static Intent recognitionIntent(String language) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeFor(language));
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return intent;
    }

    /**
     * Creates the TTS engine. {@code onReady} runs on the main thread when the
     * engine is usable; {@code onError} receives a user-facing message otherwise.
     */
    public static TextToSpeech createTts(Context context, Runnable onReady, Consumer<String> onError) {
        return new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (onReady != null) onReady.run();
            } else if (onError != null) {
                onError.accept("Text-to-speech is not available on this device. Enable a speech engine in "
                        + "Android Settings, then try Listen again.");
            }
        });
    }

    /** @return {@code null} when speech started, otherwise a user-facing error message. */
    public static String speak(TextToSpeech tts, String text, String language) {
        if (tts == null) return "The device voice is not ready yet. Try Listen again in a moment.";
        if (text == null || text.trim().isEmpty()) return "There is nothing to read aloud.";
        final Locale locale = ttsLocale(language);
        final String languageName = Language.byCode(language).englishName;
        int available;
        try {
            available = tts.isLanguageAvailable(locale);
        } catch (Exception error) {
            return "The device voice could not be prepared: " + error.getMessage();
        }
        Log.i(TAG, "tts language=" + language + " locale=" + locale + " available=" + available + " chars=" + text.length());
        if (available == TextToSpeech.LANG_MISSING_DATA) {
            Log.w(TAG, "tts voice missing for " + language);
            return missingVoiceMessage(languageName);
        }
        if (available == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "tts language unsupported for " + language);
            return unsupportedVoiceMessage(languageName);
        }
        int result;
        try {
            result = tts.setLanguage(locale);
        } catch (Exception error) {
            return "The device voice could not be prepared: " + error.getMessage();
        }
        if (result == TextToSpeech.LANG_MISSING_DATA) {
            Log.w(TAG, "tts setLanguage missing data for " + language);
            return missingVoiceMessage(languageName);
        }
        if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "tts setLanguage unsupported for " + language);
            return unsupportedVoiceMessage(languageName);
        }
        try {
            int speakResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rafiq-answer");
            if (speakResult == TextToSpeech.ERROR) {
                Log.w(TAG, "tts queue failed for " + language);
                return "The device voice could not start. Please try again.";
            }
            Log.i(TAG, "tts queued language=" + language);
        } catch (Exception error) {
            return "The device voice could not start: " + error.getMessage();
        }
        return null;
    }

    private static String missingVoiceMessage(String languageName) {
        return "The " + languageName + " voice is not installed on this device. Open Android Settings > "
                + "System > Languages & input > Text-to-speech > Install voice data, then try Listen again. "
                + "The translation is still shown on screen.";
    }

    private static String unsupportedVoiceMessage(String languageName) {
        return "This device's text-to-speech engine does not support " + languageName + ". The translation is "
                + "shown on screen; you can install another speech engine or voice in Android Settings.";
    }

    public static boolean isSpeaking(TextToSpeech tts) {
        try {
            return tts != null && tts.isSpeaking();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void stop(TextToSpeech tts) {
        try {
            if (tts != null) tts.stop();
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** Language of the text that will be spoken (never simply the UI setting). */
    public static String spokenLanguage(Answer answer) {
        return VoiceLanguage.spokenLanguage(answer);
    }

    /**
     * Text spoken aloud: translation + references only, never the Arabic verse.
     * For non-English voices only the translation is spoken; see VoiceLanguage.
     */
    public static String spokenText(Answer answer) {
        int max = TextToSpeech.getMaxSpeechInputLength();
        return VoiceLanguage.spokenText(answer, max > 0 ? max : 4000);
    }
}
