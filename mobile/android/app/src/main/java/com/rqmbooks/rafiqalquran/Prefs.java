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

    // ---------------------------------------------------------------- theme
    public String theme() { return prefs.getString("theme", "light"); }
    public boolean isDark() { return "dark".equals(theme()); }
    public void setTheme(String value) { prefs.edit().putString("theme", "dark".equals(value) ? "dark" : "light").apply(); }

    // ------------------------------------------------------- reader options
    /** Side-by-side Arabic/translation layout in the reader. */
    public boolean sideBySide() { return prefs.getBoolean("sideBySide", false); }
    public void setSideBySide(boolean value) { prefs.edit().putBoolean("sideBySide", value).apply(); }

    public boolean translationVisible() { return prefs.getBoolean("translationVisible", true); }
    public void setTranslationVisible(boolean value) { prefs.edit().putBoolean("translationVisible", value).apply(); }

    // ------------------------------------------------------ operator screen
    /** Operator token for the in-app admin screen; stored locally only. */
    public String adminToken() { return prefs.getString("adminToken", ""); }
    public void setAdminToken(String value) { prefs.edit().putString("adminToken", value == null ? "" : value).apply(); }
}
