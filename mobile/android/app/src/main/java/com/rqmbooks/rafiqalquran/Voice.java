package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Native speech input and output, kept strictly separate from recitation.
 *
 * <p>Text-to-speech is initialised lazily and the first "Listen" tap may wait for
 * the engine; unsupported languages fall back to English and a clear message is
 * returned when no usable TTS engine/voice exists instead of failing silently.
 */
public final class Voice {
    private Voice() {}

    public static final String[] LOCALES = {
            "ar-SA", "en-US", "ur-PK", "id-ID", "bn-BD", "tr-TR", "fa-IR", "ms-MY", "sw-KE", "fr-FR"
    };

    public static String localeFor(String language) {
        String[] codes = {"ar", "en", "ur", "id", "bn", "tr", "fa", "ms", "sw", "fr"};
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(language)) return LOCALES[i];
        return "en-US";
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
        int result;
        try {
            result = tts.setLanguage(ttsLocale(language));
        } catch (Exception error) {
            return "The device voice could not be prepared: " + error.getMessage();
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            try {
                result = tts.setLanguage(Locale.US);
            } catch (Exception error) {
                return "The device voice could not be prepared: " + error.getMessage();
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return "This device's text-to-speech engine has no usable voice. Install a speech voice in "
                        + "Android Settings, then try Listen again.";
            }
        }
        try {
            int speakResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rafiq-answer");
            if (speakResult == TextToSpeech.ERROR) {
                return "The device voice could not start. Please try again.";
            }
        } catch (Exception error) {
            return "The device voice could not start: " + error.getMessage();
        }
        return null;
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

    /** Text spoken aloud: translation + references only, never the Arabic verse. */
    public static String spokenText(com.rqmbooks.rafiqalquran.logic.Answer answer) {
        if (answer == null) return "";
        if ("fallback".equals(answer.kind) || "conversation".equals(answer.kind)) return answer.message;
        StringBuilder builder = new StringBuilder();
        for (com.rqmbooks.rafiqalquran.logic.Passage passage : answer.passages) {
            builder.append(passage.referenceLabel).append(". ").append(passage.translationText).append(' ');
        }
        builder.append(" ").append(answer.message);
        return builder.toString();
    }
}
