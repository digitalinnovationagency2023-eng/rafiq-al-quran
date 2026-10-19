package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** Native speech input and output, kept strictly separate from recitation. */
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

    public static TextToSpeech createTts(Context context, Runnable onReady) {
        return new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS && onReady != null) onReady.run();
        });
    }

    public static void speak(TextToSpeech tts, String text, String language) {
        if (tts == null || text == null || text.isEmpty()) return;
        tts.setLanguage(new Locale(localeFor(language).replace('-', '_')));
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer");
    }

    /** Text spoken aloud: translation + references only, never the Arabic verse. */
    public static String spokenText(com.rqmbooks.rafiqalquran.logic.Answer answer) {
        if (answer == null) return "";
        if ("fallback".equals(answer.kind)) return answer.message;
        StringBuilder builder = new StringBuilder();
        for (com.rqmbooks.rafiqalquran.logic.Passage passage : answer.passages) {
            builder.append(passage.referenceLabel).append(". ").append(passage.translationText).append(' ');
        }
        builder.append(" ").append(answer.message);
        return builder.toString();
    }
}
