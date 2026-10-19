package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

/** Device-local preferences. No personal data is sent anywhere. */
public final class Prefs {
    private static final String FILE = "quran_prefs";
    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String language() { return prefs.getString("language", "en"); }
    public void setLanguage(String value) { prefs.edit().putString("language", value).apply(); }

    public float textScale() { return prefs.getFloat("textScale", 1f); }
    public void setTextScale(float value) { prefs.edit().putFloat("textScale", value).apply(); }

    public boolean voiceEnabled() { return prefs.getBoolean("voiceEnabled", true); }
    public void setVoiceEnabled(boolean value) { prefs.edit().putBoolean("voiceEnabled", value).apply(); }

    public String history() { return prefs.getString("history", "[]"); }
    public void setHistory(JSONArray array) { prefs.edit().putString("history", array.toString()).apply(); }
    public void clearHistory() { prefs.edit().putString("history", "[]").apply(); }
}
