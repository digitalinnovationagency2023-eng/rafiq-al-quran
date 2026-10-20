package com.rqmbooks.rafiqalquran;

import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Optional online services (feedback, donation configuration). The core reading
 * and search experience never calls this class. If no base URL is configured,
 * the app stays fully offline.
 */
public final class Api {
    private Api() {}

    public static String baseUrl(Resources resources) {
        try {
            return resources.getString(R.string.api_base_url).trim();
        } catch (Exception error) {
            return "";
        }
    }

    public static JSONObject get(Resources resources, String path) throws Exception {
        return request(resources, "GET", path, null, null);
    }

    /** Token-authenticated GET used only by the in-app operator screen. */
    public static JSONObject get(Resources resources, String path, String adminToken) throws Exception {
        return request(resources, "GET", path, null, adminToken);
    }

    public static JSONObject post(Resources resources, String path, JSONObject body) throws Exception {
        return request(resources, "POST", path, body, null, 15000);
    }

    /** Optional assistant status check (does not send any user text). */
    public static JSONObject assistantStatus(Resources resources) throws Exception {
        return request(resources, "GET", "/api/assistant/status", null, null, 8000);
    }

    /**
     * Optional AI assistance: the model only returns a short chat reply, search
     * terms and/or OCR text for an attached image. Quran text is never requested
     * from, or accepted from, the model.
     */
    public static JSONObject assistant(Resources resources, JSONObject body) throws Exception {
        return request(resources, "POST", "/api/assistant", body, null, 45000);
    }

    private static JSONObject request(Resources resources, String method, String path, JSONObject body, String adminToken) throws Exception {
        return request(resources, method, path, body, adminToken, 15000);
    }

    private static JSONObject request(Resources resources, String method, String path, JSONObject body, String adminToken, int readTimeoutMs) throws Exception {
        String base = baseUrl(resources);
        if (base.isEmpty()) throw new IllegalStateException("Online services are not configured in this build.");
        URL url = new URL(base.replaceAll("/$", "") + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("Accept", "application/json");
        if (adminToken != null && !adminToken.isEmpty()) {
            connection.setRequestProperty("x-admin-token", adminToken);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder builder = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) builder.append(line);
            }
        }
        String payload = builder.toString();
        if (status >= 400) {
            throw new IllegalStateException(payload.isEmpty() ? "Request failed (" + status + ")" : payload);
        }
        return payload.isEmpty() ? new JSONObject() : new JSONObject(payload);
    }

    public static JSONArray array(JSONObject object, String key) {
        return object.optJSONArray(key) == null ? new JSONArray() : object.optJSONArray(key);
    }
}
