package com.rqmbooks.rafiqalquran;

import com.rqmbooks.rafiqalquran.logic.Answer;
import com.rqmbooks.rafiqalquran.logic.Chapter;
import com.rqmbooks.rafiqalquran.logic.Passage;
import com.rqmbooks.rafiqalquran.logic.Provenance;
import com.rqmbooks.rafiqalquran.logic.TranslationMeta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation persistence for the device. Stored answers keep the exact text that
 * was verified against the dataset; on restore each passage is re-checked against
 * the bundled Arabic text and dropped if it does not match.
 */
public final class HistoryCodec {
    private HistoryCodec() {}

    public static final class Turn {
        public final String role; // "user" | "assistant"
        public final String question;
        public final Answer answer;

        public Turn(String role, String question, Answer answer) {
            this.role = role;
            this.question = question;
            this.answer = answer;
        }
    }

    public static JSONArray encode(List<Turn> turns) {
        JSONArray array = new JSONArray();
        for (Turn turn : turns) {
            try {
                JSONObject object = new JSONObject();
                object.put("role", turn.role);
                object.put("question", turn.question == null ? "" : turn.question);
                if (turn.answer != null) object.put("answer", encodeAnswer(turn.answer));
                array.put(object);
            } catch (Exception ignored) {
                // a single bad row never breaks the saved history
            }
        }
        return array;
    }

    public static List<Turn> decode(String json) {
        List<Turn> turns = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String role = object.optString("role", "user");
                String question = object.optString("question", "");
                Answer answer = object.has("answer") ? decodeAnswer(object.optJSONObject("answer")) : null;
                turns.add(new Turn(role, question, answer));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return turns;
    }

    private static JSONObject encodeAnswer(Answer answer) throws Exception {
        JSONObject object = new JSONObject();
        object.put("kind", answer.kind);
        object.put("language", answer.language);
        object.put("message", answer.message);
        object.put("confidence", answer.confidence);
        object.put("notice", answer.notice);
        object.put("noticeVerified", answer.noticeVerified);
        object.put("translationNotice", answer.translationNotice == null ? JSONObject.NULL : answer.translationNotice);
        JSONArray limits = new JSONArray();
        for (String limit : answer.limits) limits.put(limit);
        object.put("limits", limits);
        JSONArray passages = new JSONArray();
        for (Passage passage : answer.passages) passages.put(encodePassage(passage));
        object.put("passages", passages);
        JSONArray keys = new JSONArray();
        for (String key : answer.shownVerseKeys) keys.put(key);
        object.put("shownVerseKeys", keys);
        return object;
    }

    private static JSONObject encodePassage(Passage passage) throws Exception {
        JSONObject object = new JSONObject();
        object.put("verseKey", passage.verseKey);
        object.put("chapterNameEnglish", passage.chapterNameEnglish);
        object.put("chapterNameArabic", passage.chapterNameArabic);
        object.put("arabic", passage.arabic);
        object.put("translationText", passage.translationText);
        object.put("translationLanguage", passage.translationLanguage);
        object.put("translationTitle", passage.translationTitle == null ? JSONObject.NULL : passage.translationTitle);
        object.put("translationAuthor", passage.translationAuthor == null ? JSONObject.NULL : passage.translationAuthor);
        object.put("translationDirection", passage.translationDirection);
        object.put("translationLicenseStatus", passage.translationLicenseStatus);
        object.put("fallbackToEnglish", passage.fallbackToEnglish);
        object.put("referenceLabel", passage.referenceLabel);
        object.put("dataset", passage.dataset);
        object.put("datasetVersion", passage.datasetVersion);
        object.put("datasetSourceUrl", passage.datasetSourceUrl == null ? JSONObject.NULL : passage.datasetSourceUrl);
        object.put("datasetChecksum", passage.datasetChecksum);
        return object;
    }

    private static Answer decodeAnswer(JSONObject object) {
        if (object == null) return null;
        try {
            List<Passage> passages = new ArrayList<>();
            JSONArray array = object.optJSONArray("passages");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject row = array.optJSONObject(i);
                    if (row == null) continue;
                    Chapter chapter = new Chapter(0, row.optString("chapterNameArabic"),
                            row.optString("chapterNameEnglish"), 0, "");
                    TranslationMeta meta = new TranslationMeta(row.optString("translationLanguage"),
                            row.optString("translationTitle"), row.optString("translationAuthor"),
                            row.optString("translationDirection", "ltr"),
                            row.optString("translationLicenseStatus", "unverified"));
                    Provenance provenance = new Provenance();
                    provenance.dataset = row.optString("dataset", "quran-uthmani");
                    provenance.version = row.optString("datasetVersion");
                    provenance.sourceUrl = row.optString("datasetSourceUrl");
                    provenance.checksum = row.optString("datasetChecksum");
                    passages.add(new Passage(row.optString("verseKey"), chapter, row.optString("arabic"),
                            row.optString("translationText"), row.optString("translationLanguage"), meta,
                            row.optBoolean("fallbackToEnglish"), provenance));
                }
            }
            List<String> limits = new ArrayList<>();
            JSONArray limitsArray = object.optJSONArray("limits");
            if (limitsArray != null) {
                for (int i = 0; i < limitsArray.length(); i++) limits.add(limitsArray.optString(i));
            }
            List<String> keys = new ArrayList<>();
            JSONArray keysArray = object.optJSONArray("shownVerseKeys");
            if (keysArray != null) {
                for (int i = 0; i < keysArray.length(); i++) keys.add(keysArray.optString(i));
            }
            return new Answer(object.optString("kind", "answer"), object.optString("language", "en"),
                    object.optString("message", ""), passages, object.optDouble("confidence", 0),
                    object.optString("notice", ""), object.optBoolean("noticeVerified", false),
                    object.isNull("translationNotice") ? null : object.optString("translationNotice"),
                    limits, true, keys);
        } catch (Exception error) {
            return null;
        }
    }
}
