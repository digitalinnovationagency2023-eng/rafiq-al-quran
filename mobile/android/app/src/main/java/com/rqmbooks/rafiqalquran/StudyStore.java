package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.content.SharedPreferences;

import com.rqmbooks.rafiqalquran.logic.StudyData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Device-local study data: bookmark collections, personal notes, saved searches,
 * reading plans and the last reading position. Everything stays on the device.
 */
public final class StudyStore {
    private static final String FILE = "rafiq_study";
    private static final String KEY_COLLECTIONS = "collections";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_SEARCHES = "searches";
    private static final String KEY_PLANS = "plans";
    private static final String KEY_LAST_VERSE = "lastVerse";
    private static final String KEY_LAST_CHAPTER = "lastChapter";
    private static final String KEY_LAST_AT = "lastAt";
    private static final String KEY_HISTORY = "historyChapters";

    private final SharedPreferences prefs;

    public StudyStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ collections
    public List<String> collections() {
        List<String> names = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_COLLECTIONS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "");
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        } catch (Exception ignored) {
            // fall through to the default collection
        }
        if (!containsIgnoreCase(names, StudyData.DEFAULT_COLLECTION)) {
            names.add(0, StudyData.DEFAULT_COLLECTION);
            saveCollections(names);
        }
        return names;
    }

    public String addCollection(String raw) {
        List<String> names = collections();
        String canonical = StudyData.addCollection(names, raw);
        saveCollections(names);
        return canonical;
    }

    public void removeCollection(String raw) {
        List<String> names = collections();
        String name = StudyData.sanitizeCollectionName(raw);
        if (StudyData.DEFAULT_COLLECTION.equalsIgnoreCase(name)) return;
        names.removeIf(existing -> existing.equalsIgnoreCase(name));
        saveCollections(names);
        // Reassign the collection's bookmarks to the default collection.
        try {
            JSONObject bookmarks = bookmarks();
            List<String> keys = new ArrayList<>();
            java.util.Iterator<String> iterator = bookmarks.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            for (String key : keys) {
                if (bookmarks.optString(key, StudyData.DEFAULT_COLLECTION).equals(name)) {
                    bookmarks.put(key, StudyData.DEFAULT_COLLECTION);
                }
            }
            prefs.edit().putString(KEY_BOOKMARKS, bookmarks.toString()).apply();
        } catch (Exception ignored) {
            // best effort; the collection itself is already gone
        }
    }

    private void saveCollections(List<String> names) {
        JSONArray array = new JSONArray();
        for (String name : names) array.put(name);
        prefs.edit().putString(KEY_COLLECTIONS, array.toString()).apply();
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        for (String value : values) if (value.equalsIgnoreCase(needle)) return true;
        return false;
    }

    // -------------------------------------------------------------- bookmarks
    private JSONObject bookmarks() {
        try {
            return new JSONObject(prefs.getString(KEY_BOOKMARKS, "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private void saveBookmarks(JSONObject object) {
        prefs.edit().putString(KEY_BOOKMARKS, object.toString()).apply();
    }

    public boolean isBookmarked(String verseKey) {
        return bookmarks().has(verseKey);
    }

    public String collectionOf(String verseKey) {
        JSONObject object = bookmarks();
        return object.has(verseKey) ? object.optString(verseKey, StudyData.DEFAULT_COLLECTION) : null;
    }

    public void bookmark(String verseKey, String collection) {
        try {
            JSONObject object = bookmarks();
            String name = StudyData.sanitizeCollectionName(collection);
            if (!containsIgnoreCase(collections(), name)) {
                addCollection(name);
                name = StudyData.sanitizeCollectionName(name);
            }
            object.put(verseKey, name);
            saveBookmarks(object);
        } catch (Exception ignored) {
            // never let a single bad key break bookmarking
        }
    }

    public void removeBookmark(String verseKey) {
        JSONObject object = bookmarks();
        object.remove(verseKey);
        saveBookmarks(object);
    }

    /** Toggles the default collection. Returns true when the verse is now saved. */
    public boolean toggleBookmark(String verseKey) {
        if (isBookmarked(verseKey)) {
            removeBookmark(verseKey);
            return false;
        }
        bookmark(verseKey, StudyData.DEFAULT_COLLECTION);
        return true;
    }

    public List<String> bookmarkedKeys(String collection) {
        List<String> keys = new ArrayList<>();
        JSONObject object = bookmarks();
        java.util.Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (collection == null || object.optString(key, StudyData.DEFAULT_COLLECTION).equalsIgnoreCase(collection)) {
                keys.add(key);
            }
        }
        keys.sort(StudyData::compareVerseKeys);
        return keys;
    }

    public int bookmarkCount(String collection) {
        return bookmarkedKeys(collection).size();
    }

    public int bookmarkTotal() {
        return bookmarkedKeys(null).size();
    }

    // ------------------------------------------------------------------ notes
    public String note(String verseKey) {
        try {
            JSONObject notes = new JSONObject(prefs.getString(KEY_NOTES, "{}"));
            JSONObject entry = notes.optJSONObject(verseKey);
            return entry == null ? "" : entry.optString("text", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public void setNote(String verseKey, String text) {
        try {
            JSONObject notes = new JSONObject(prefs.getString(KEY_NOTES, "{}"));
            String cleaned = StudyData.sanitizeNote(text);
            if (cleaned.isEmpty()) notes.remove(verseKey);
            else {
                JSONObject entry = new JSONObject();
                entry.put("text", cleaned);
                entry.put("at", System.currentTimeMillis());
                notes.put(verseKey, entry);
            }
            prefs.edit().putString(KEY_NOTES, notes.toString()).apply();
        } catch (Exception ignored) {
            // notes are best-effort local data
        }
    }

    public List<String> noteKeys() {
        List<String> keys = new ArrayList<>();
        try {
            JSONObject notes = new JSONObject(prefs.getString(KEY_NOTES, "{}"));
            java.util.Iterator<String> iterator = notes.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
        } catch (Exception ignored) {
            // empty
        }
        keys.sort(StudyData::compareVerseKeys);
        return keys;
    }

    public int noteCount() {
        return noteKeys().size();
    }

    // --------------------------------------------------------- saved searches
    public void addSearch(String query) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) return;
        try {
            JSONArray existing = new JSONArray(prefs.getString(KEY_SEARCHES, "[]"));
            List<String> queries = new ArrayList<>();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject entry = existing.optJSONObject(i);
                String value = entry == null ? "" : entry.optString("q", "");
                if (!value.isEmpty() && !value.equalsIgnoreCase(cleaned) && !queries.contains(value)) queries.add(value);
            }
            queries.add(0, cleaned);
            while (queries.size() > StudyData.MAX_SEARCHES) queries.remove(queries.size() - 1);
            JSONArray array = new JSONArray();
            for (String value : queries) {
                JSONObject entry = new JSONObject();
                entry.put("q", value);
                entry.put("at", System.currentTimeMillis());
                array.put(entry);
            }
            prefs.edit().putString(KEY_SEARCHES, array.toString()).apply();
        } catch (Exception ignored) {
            // best effort
        }
    }

    public List<String> searches() {
        List<String> queries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_SEARCHES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                String value = entry == null ? "" : entry.optString("q", "");
                if (!value.isEmpty()) queries.add(value);
            }
        } catch (Exception ignored) {
            // empty
        }
        return queries;
    }

    public void clearSearches() {
        prefs.edit().putString(KEY_SEARCHES, "[]").apply();
    }

    // ----------------------------------------------------------- reading plans
    public static final class Plan {
        public final String id;
        public final String name;
        public final String preset;
        public final int days;
        public final List<Integer> chapters;
        public final List<Integer> done;
        public final long createdAt;

        Plan(String id, String name, String preset, int days, List<Integer> chapters, List<Integer> done, long createdAt) {
            this.id = id;
            this.name = name;
            this.preset = preset;
            this.days = days;
            this.chapters = chapters;
            this.done = done;
            this.createdAt = createdAt;
        }

        public int percent() { return StudyData.percent(done.size(), chapters.size()); }
        public String progressText() { return StudyData.formatProgress(done.size(), chapters.size()); }
        public boolean isComplete() { return !chapters.isEmpty() && done.size() >= chapters.size(); }
        public int nextChapter() { return StudyData.nextChapter(chapters, done); }
    }

    private JSONArray plansArray() {
        try {
            return new JSONArray(prefs.getString(KEY_PLANS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void savePlans(JSONArray array) {
        prefs.edit().putString(KEY_PLANS, array.toString()).apply();
    }

    public List<Plan> plans() {
        List<Plan> plans = new ArrayList<>();
        JSONArray array = plansArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            try {
                List<Integer> chapters = StudyData.parseChapters(object.optString("chapters", ""));
                List<Integer> done = StudyData.parseChapters(object.optString("done", ""));
                plans.add(new Plan(object.optString("id", "plan-" + i), object.optString("name", "Reading plan"),
                        object.optString("preset", StudyData.PRESET_FULL), object.optInt("days", 30),
                        chapters, done, object.optLong("createdAt", 0)));
            } catch (Exception ignored) {
                // skip malformed plan
            }
        }
        return plans;
    }

    public Plan createPlan(String name, String preset, int days) {
        List<Integer> chapters = StudyData.presetChapters(preset);
        String id = "plan-" + System.currentTimeMillis();
        String cleaned = StudyData.sanitizeCollectionName(name);
        if (!cleaned.equalsIgnoreCase(StudyData.DEFAULT_COLLECTION) || preset != null) {
            // Use the provided name, but never an empty one.
        }
        if (name == null || name.trim().isEmpty()) cleaned = StudyData.presetTitle(preset);
        JSONArray array = plansArray();
        try {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", cleaned);
            object.put("preset", preset);
            object.put("days", Math.max(1, days));
            object.put("chapters", StudyData.joinChapters(chapters));
            object.put("done", "");
            object.put("createdAt", System.currentTimeMillis());
            array.put(object);
            savePlans(array);
        } catch (Exception ignored) {
            // ignore
        }
        return new Plan(id, cleaned, preset, Math.max(1, days), chapters, new ArrayList<>(), System.currentTimeMillis());
    }

    public void markChapterDone(String planId, int chapterId) {
        JSONArray array = plansArray();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null || !object.optString("id", "").equals(planId)) continue;
                List<Integer> chapters = StudyData.parseChapters(object.optString("chapters", ""));
                if (!chapters.contains(chapterId)) return;
                List<Integer> done = StudyData.parseChapters(object.optString("done", ""));
                if (!done.contains(chapterId)) done.add(chapterId);
                object.put("done", StudyData.joinChapters(done));
            }
            savePlans(array);
        } catch (Exception ignored) {
            // ignore
        }
    }

    public void deletePlan(String planId) {
        JSONArray array = plansArray();
        JSONArray next = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null && !object.optString("id", "").equals(planId)) next.put(object);
        }
        savePlans(next);
    }

    /** Replaces the whole plan list; used when the app changes plan structure. */
    public void clearPlans() {
        prefs.edit().putString(KEY_PLANS, "[]").apply();
    }

    // -------------------------------------------------------- reading position
    public void recordProgress(int chapterId, String verseKey) {
        prefs.edit()
                .putInt(KEY_LAST_CHAPTER, chapterId)
                .putString(KEY_LAST_VERSE, verseKey == null ? "" : verseKey)
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .apply();
    }

    public boolean hasProgress() {
        return prefs.getInt(KEY_LAST_CHAPTER, 0) > 0;
    }

    public int lastChapter() { return prefs.getInt(KEY_LAST_CHAPTER, 0); }

    public String lastVerseKey() { return prefs.getString(KEY_LAST_VERSE, ""); }

    public long lastAt() { return prefs.getLong(KEY_LAST_AT, 0); }

    // ------------------------------------------------------- reading history
    /** Most-recent-first list of chapter ids, capped at 30. */
    public void recordHistoryChapter(int chapterId) {
        List<Integer> chapters = historyChapters();
        chapters.remove(Integer.valueOf(chapterId));
        chapters.add(0, chapterId);
        while (chapters.size() > 30) chapters.remove(chapters.size() - 1);
        JSONArray array = new JSONArray();
        for (Integer chapter : chapters) array.put(chapter);
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public List<Integer> historyChapters() {
        List<Integer> chapters = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                int chapter = array.optInt(i, 0);
                if (chapter >= 1 && chapter <= 114 && !chapters.contains(chapter)) chapters.add(chapter);
            }
        } catch (Exception ignored) {
            // empty history
        }
        return chapters;
    }

    public void clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
    }
}
