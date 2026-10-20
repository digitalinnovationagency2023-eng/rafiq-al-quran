package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Operator-only screen for the token-protected read-only admin API.
 *
 * <p>This screen is deliberately read-only: it shows server health, feature flags,
 * the imported dataset, recent feedback metadata, recent audit entries and
 * recitation rights status. It never shows contact details from feedback, never
 * logs the token, and never displays the token after connecting. The token is
 * stored on this device only, in the app's private preferences.
 */
public final class AdminActivity extends Activity {
    private Prefs prefs;
    private EditText tokenInput;
    private TextView statusText;
    private LinearLayout results;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);

        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.bar(this, "Operator", "Read-only diagnostics", this::finish);
        bar.addView(Ui.themeToggle(this));
        root.addView(bar);
        root.addView(Ui.goldRule(this));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 28));
        content.setBackgroundColor(Ui.PAPER);

        // Purpose explanation first: this screen is operator-only and read-only,
        // and the token is never bundled with the app.
        LinearLayout intro = Ui.cardBox(this);
        intro.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        intro.addView(Ui.sectionLabel(this, "What this screen is for"));
        TextView introBody = Ui.text(this,
                "• Operator-only, read-only diagnostics for the Rafiq API. It shows service status: "
                        + "dataset counts, feature flags, the latest feedback reports, the audit log and "
                        + "recitation rights status.\n"
                        + "• It never changes records and never shows user data beyond report metadata "
                        + "(no contact details from feedback).\n"
                        + "• It needs the ADMIN_TOKEN configured on the Rafiq API server by the operator. "
                        + "Ask the operator for the x-admin-token value; it is never bundled with the app.\n"
                        + "• The token is stored on this device only and can be removed with Forget token.",
                12f, Ui.MUTED);
        introBody.setPadding(0, Ui.dp(this, 8), 0, 0);
        intro.addView(introBody);
        content.addView(intro);

        TextView tokenLabel = Ui.label(this, "Operator token");
        tokenLabel.setPadding(0, Ui.dp(this, 18), 0, 0);
        content.addView(tokenLabel);
        tokenInput = new EditText(this);
        tokenInput.setHint("x-admin-token");
        tokenInput.setTextColor(Ui.INK);
        tokenInput.setHintTextColor(Ui.MUTED);
        tokenInput.setSingleLine(true);
        tokenInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 8, this));
        tokenInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        tokenInput.setText(prefs.adminToken());
        LinearLayout.LayoutParams tokenParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tokenParams.topMargin = Ui.dp(this, 6);
        tokenInput.setLayoutParams(tokenParams);
        content.addView(tokenInput);

        TextView tokenHint = Ui.text(this,
                "Ask the operator for the x-admin-token value; it is never bundled with the app.",
                11f, Ui.MUTED);
        tokenHint.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(tokenHint);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        Button connect = Ui.button(this, "Connect", true);
        connect.setOnClickListener(view -> connect());
        Button forget = Ui.button(this, "Forget token", false);
        forget.setOnClickListener(view -> forgetToken());
        LinearLayout.LayoutParams forgetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        forgetParams.leftMargin = Ui.dp(this, 8);
        actions.addView(connect);
        actions.addView(forget, forgetParams);
        content.addView(actions);

        statusText = Ui.text(this, "Not connected yet. Enter the operator token and choose Connect.", 12f, Ui.INK);
        statusText.setPadding(0, Ui.dp(this, 10), 0, 0);
        content.addView(statusText);

        TextView note = Ui.text(this,
                "The token is stored on this device only. Requests are read-only and use the "
                        + "configured data API; the token is never logged or shown after connecting.",
                11f, Ui.MUTED);
        note.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        content.addView(note);

        results = Ui.column(this);
        content.addView(results);

        scroll.addView(content);
        root.addView(scroll);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------- connecting
    private void connect() {
        String entered = tokenInput.getText().toString().trim();
        String token = entered.isEmpty() ? prefs.adminToken() : entered;
        if (token.isEmpty()) {
            statusText.setText("Enter the operator token to connect.");
            return;
        }
        prefs.setAdminToken(token);
        // The token is never displayed after it has been stored.
        tokenInput.setText("");
        load(token);
    }

    private void forgetToken() {
        prefs.setAdminToken("");
        tokenInput.setText("");
        results.removeAllViews();
        statusText.setText("Token forgotten on this device. Enter the token to reconnect.");
        Toast.makeText(this, "Operator token removed from this device", Toast.LENGTH_SHORT).show();
    }

    /** Reads every operator endpoint; the summary decides whether the token works. */
    private void load(final String token) {
        statusText.setText("Connecting to " + Api.baseUrl(getResources()) + "\u2026");
        results.removeAllViews();
        results.addView(Ui.text(this, "Reading the operator overview\u2026", 12f, Ui.MUTED));

        new Thread(() -> {
            AdminData data = new AdminData();
            String fatal = null;
            try {
                data.summary = Api.get(getResources(), "/api/admin/summary", token);
            } catch (Exception error) {
                fatal = describeError(error);
            }
            if (fatal == null) {
                try {
                    data.datasets = Api.get(getResources(), "/api/admin/datasets", token);
                } catch (Exception error) {
                    data.datasetsError = describeError(error);
                }
                try {
                    data.feedback = Api.get(getResources(), "/api/admin/feedback?limit=10", token);
                } catch (Exception error) {
                    data.feedbackError = describeError(error);
                }
                try {
                    data.audit = Api.get(getResources(), "/api/admin/audit?limit=10", token);
                } catch (Exception error) {
                    data.auditError = describeError(error);
                }
                try {
                    data.premium = Api.get(getResources(), "/api/admin/premium/features", token);
                } catch (Exception error) {
                    data.premiumError = describeError(error);
                }
                try {
                    data.reciters = Api.get(getResources(), "/api/admin/reciters", token);
                } catch (Exception error) {
                    data.recitersError = describeError(error);
                }
            }
            final AdminData loaded = data;
            final String failure = fatal;
            runOnUiThread(() -> {
                if (failure == null) renderConnected(loaded);
                else renderError(failure);
            });
        }).start();
    }

    // ------------------------------------------------------------- rendering
    private void renderError(String message) {
        statusText.setText("The operator data could not be loaded.");
        results.removeAllViews();
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        card.setLayoutParams(params);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.chip(this, "CONNECTION FAILED", Ui.DANGER, Ui.PAPER, Ui.DANGER));
        head.addView(Ui.spacer(this));
        head.addView(Ui.text(this, Api.baseUrl(getResources()), 10f, Ui.MUTED));
        card.addView(head);
        TextView detail = Ui.text(this, message, 13f, Ui.INK);
        detail.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 12));
        card.addView(detail);
        Button retry = Ui.button(this, "Retry", true);
        retry.setOnClickListener(view -> connect());
        card.addView(retry);
        TextView help = Ui.text(this,
                "A 401 means the token does not match the server's ADMIN_TOKEN. A 503 means "
                        + "administration is not enabled on the server at all.",
                11f, Ui.MUTED);
        help.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(help);
        results.addView(card);
    }

    private void renderConnected(AdminData data) {
        statusText.setText("Connected. This screen only reads operator data; it never changes records.");
        results.removeAllViews();

        LinearLayout statusCard = Ui.cardBox(this);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.dp(this, 14);
        statusCard.setLayoutParams(statusParams);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.chip(this, "CONNECTED", Ui.GREEN, Ui.GREEN_SOFT, Ui.GREEN));
        head.addView(Ui.spacer(this));
        head.addView(Ui.chip(this, "READ-ONLY", Ui.GOLD_DEEP, Ui.GOLD_SOFT, Ui.LINE));
        statusCard.addView(head);
        addKeyValue(statusCard, "Data API", Api.baseUrl(getResources()));
        addKeyValue(statusCard, "Generated", formatTime(data.summary.optString("generatedAt", "")));
        results.addView(statusCard);

        renderFlags(data.summary.optJSONObject("flags"));
        renderDataset(data);
        renderFeedback(data);
        renderAudit(data);
        renderReciters(data);
        renderPremium(data);
    }

    private void renderFlags(JSONObject flags) {
        LinearLayout card = section("Feature flags");
        addFlagRow(card, flags, "donationsLive", "Donation collection");
        addFlagRow(card, flags, "premiumEnabled", "Premium tools");
        addFlagRow(card, flags, "recitationEnabled", "Recitation audio");
        addFlagRow(card, flags, "aiConfigured", "AI query interpretation");
        results.addView(card);
    }

    private void renderDataset(AdminData data) {
        LinearLayout card = section("Imported dataset");
        JSONObject summaryDataset = data.summary.optJSONObject("dataset");
        JSONObject counts = data.summary.optJSONObject("counts");
        JSONArray datasets = data.datasets == null ? new JSONArray() : Api.array(data.datasets, "datasets");
        JSONObject first = datasets.length() > 0 ? datasets.optJSONObject(0) : null;

        String name = first == null ? "" : first.optString("dataset", "");
        if (name.isEmpty() && summaryDataset != null) name = summaryDataset.optString("dataset", "");
        String version = first == null ? "" : first.optString("version", "");
        if (version.isEmpty() && summaryDataset != null) version = summaryDataset.optString("version", "");
        String checksum = first == null ? "" : first.optString("checksum", "");
        if (checksum.isEmpty() && summaryDataset != null) checksum = summaryDataset.optString("checksum", "");
        String importedAt = first == null ? "" : first.optString("importedAt", "");
        if (importedAt.isEmpty() && summaryDataset != null) {
            importedAt = summaryDataset.optString("imported_at", summaryDataset.optString("importedAt", ""));
        }
        String verses = first == null ? "" : first.optString("verseCount", "");
        if (!verses.matches("\\d+") && counts != null) verses = String.valueOf(counts.optInt("verses", 0));
        String chapters = first == null ? "" : first.optString("chapterCount", "");
        if (!chapters.matches("\\d+") && counts != null) chapters = String.valueOf(counts.optInt("chapters", 0));
        String translations = counts == null ? "" : String.valueOf(counts.optInt("translations", 0));

        addKeyValue(card, "Dataset", name);
        addKeyValue(card, "Version", version);
        addKeyValue(card, "Verses", verses);
        addKeyValue(card, "Chapters", chapters);
        addKeyValue(card, "Translations", translations);
        addKeyValue(card, "Checksum", checksum.length() > 16 ? checksum.substring(0, 16) + "\u2026" : checksum);
        addKeyValue(card, "Imported", formatTime(importedAt));
        if (data.datasetsError != null) addSectionError(card, data.datasetsError);
        results.addView(card);
    }

    private void renderFeedback(AdminData data) {
        LinearLayout card = section("Latest feedback");
        if (data.feedbackError != null) {
            addSectionError(card, data.feedbackError);
            results.addView(card);
            return;
        }
        JSONArray rows = data.feedback == null ? new JSONArray() : Api.array(data.feedback, "feedback");
        int shown = 0;
        for (int i = 0; i < rows.length() && shown < 10; i++, shown++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            if (shown > 0) card.addView(Ui.divider(this));
            String status = row.optString("status", "open");
            LinearLayout line = Ui.row(this);
            line.addView(Ui.text(this, "#" + row.optString("id", "?") + "  \u00B7  "
                    + row.optString("category", "uncategorised"), 12f, Ui.INK),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(Ui.chip(this, status.toUpperCase(), statusColor(status),
                    statusFill(status), statusColor(status)));
            card.addView(line);
            TextView time = Ui.text(this, formatTime(row.optString("createdAt", "")), 10f, Ui.MUTED);
            time.setPadding(0, Ui.dp(this, 2), 0, 0);
            card.addView(time);
        }
        if (shown == 0) card.addView(emptyRow("No feedback rows returned."));
        results.addView(card);
    }

    private void renderAudit(AdminData data) {
        LinearLayout card = section("Recent audit entries");
        if (data.auditError != null) {
            addSectionError(card, data.auditError);
            results.addView(card);
            return;
        }
        JSONArray entries = data.audit == null ? new JSONArray() : Api.array(data.audit, "entries");
        int shown = 0;
        for (int i = 0; i < entries.length() && shown < 10; i++, shown++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            if (shown > 0) card.addView(Ui.divider(this));
            TextView action = Ui.text(this, entry.optString("action", "unknown action"), 12f, Ui.INK);
            card.addView(action);
            TextView meta = Ui.text(this, entry.optString("actor", "admin") + "  \u00B7  "
                    + formatTime(entry.optString("at", "")), 10f, Ui.MUTED);
            meta.setPadding(0, Ui.dp(this, 2), 0, 0);
            card.addView(meta);
        }
        if (shown == 0) card.addView(emptyRow("No audit entries returned."));
        results.addView(card);
    }

    private void renderReciters(AdminData data) {
        LinearLayout card = section("Recitation reciters");
        if (data.recitersError != null) {
            addSectionError(card, data.recitersError);
            results.addView(card);
            return;
        }
        JSONArray reciters = data.reciters == null ? new JSONArray() : Api.array(data.reciters, "reciters");
        int shown = 0;
        for (int i = 0; i < reciters.length() && shown < 10; i++, shown++) {
            JSONObject reciter = reciters.optJSONObject(i);
            if (reciter == null) continue;
            if (shown > 0) card.addView(Ui.divider(this));
            String rights = reciter.optString("rightsStatus", "unverified");
            boolean enabled = reciter.optBoolean("enabled", false);
            LinearLayout line = Ui.row(this);
            line.addView(Ui.text(this, reciter.optString("name", "Reciter"), 12f, Ui.INK),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(Ui.chip(this, rights.toUpperCase(), rightsColor(rights),
                    rightsFill(rights), rightsColor(rights)));
            card.addView(line);
            TextView detail = Ui.text(this, reciter.optString("style", "Recitation") + "  \u00B7  "
                    + (enabled ? "enabled on server" : "disabled on server"), 10f, Ui.MUTED);
            detail.setPadding(0, Ui.dp(this, 2), 0, 0);
            card.addView(detail);
        }
        if (shown == 0) card.addView(emptyRow("No reciter rows returned."));
        TextView note = Ui.text(this,
                "Playback inside the app stays disabled until the recitation rights review is recorded; "
                        + "enabling a reciter on the server is not enough on its own.",
                10f, Ui.MUTED);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(note);
        results.addView(card);
    }

    private void renderPremium(AdminData data) {
        LinearLayout card = section("Premium tools");
        if (data.premiumError != null) {
            addSectionError(card, data.premiumError);
            results.addView(card);
            return;
        }
        JSONObject premium = data.premium == null ? new JSONObject() : data.premium;
        boolean enabled = premium.optBoolean("enabled", false);
        LinearLayout line = Ui.row(this);
        line.addView(Ui.text(this, "Premium features", 12f, Ui.INK),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(Ui.chip(this, enabled ? "ENABLED" : "DISABLED",
                enabled ? Ui.GREEN : Ui.MUTED, enabled ? Ui.GREEN_SOFT : Ui.PAPER,
                enabled ? Ui.GREEN : Ui.LINE));
        card.addView(line);
        JSONArray features = Api.array(premium, "features");
        int shown = 0;
        for (int i = 0; i < features.length() && shown < 10; i++, shown++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) continue;
            if (shown > 0) card.addView(Ui.divider(this));
            LinearLayout row = Ui.row(this);
            row.addView(Ui.text(this, feature.optString("name", feature.optString("key", "Feature")),
                    12f, Ui.INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.chip(this, feature.optBoolean("enabled", false) ? "ON" : "OFF",
                    Ui.MUTED, Ui.PAPER, Ui.LINE));
            card.addView(row);
            String price = feature.optString("currency", "") + " "
                    + (feature.optInt("priceMinor", 0) / 100.0) + " \u00B7 "
                    + feature.optString("billing", "one-off");
            TextView detail = Ui.text(this, price, 10f, Ui.MUTED);
            detail.setPadding(0, Ui.dp(this, 2), 0, 0);
            card.addView(detail);
        }
        if (shown == 0) card.addView(emptyRow("No premium features returned."));
        results.addView(card);
    }

    // ---------------------------------------------------------------- helpers
    private LinearLayout section(String title) {
        LinearLayout card = Ui.cardBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        card.setLayoutParams(params);
        TextView heading = Ui.sectionLabel(this, title);
        card.addView(heading);
        return card;
    }

    private void addFlagRow(LinearLayout card, JSONObject flags, String key, String title) {
        boolean enabled = flags != null && flags.optBoolean(key, false);
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.text(this, title, 12f, Ui.INK),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.chip(this, enabled ? "ENABLED" : "DISABLED",
                enabled ? Ui.GREEN : Ui.MUTED, enabled ? Ui.GREEN_SOFT : Ui.PAPER,
                enabled ? Ui.GREEN : Ui.LINE));
        card.addView(row);
    }

    private void addKeyValue(LinearLayout card, String key, String value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        row.addView(Ui.text(this, key, 12f, Ui.MUTED),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView content = Ui.text(this, value == null || value.isEmpty() ? "\u2014" : value, 12f, Ui.INK);
        content.setGravity(Gravity.RIGHT);
        content.setMaxWidth(Ui.dp(this, 240));
        row.addView(content);
        card.addView(row);
    }

    private void addSectionError(LinearLayout card, String message) {
        TextView error = Ui.text(this, message, 11f, Ui.DANGER);
        error.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(error);
    }

    private View emptyRow(String message) {
        TextView view = Ui.text(this, message, 11f, Ui.MUTED);
        view.setPadding(0, Ui.dp(this, 8), 0, 0);
        return view;
    }

    private int statusColor(String status) {
        if ("resolved".equals(status)) return Ui.GREEN;
        if ("rejected".equals(status)) return Ui.DANGER;
        if ("open".equals(status)) return Ui.GOLD_DEEP;
        return Ui.MUTED;
    }

    private int statusFill(String status) {
        if ("resolved".equals(status)) return Ui.GREEN_SOFT;
        if ("open".equals(status)) return Ui.GOLD_SOFT;
        return Ui.PAPER;
    }

    private int rightsColor(String rights) {
        if ("verified".equals(rights)) return Ui.GREEN;
        if ("blocked".equals(rights)) return Ui.DANGER;
        return Ui.GOLD_DEEP;
    }

    private int rightsFill(String rights) {
        if ("verified".equals(rights)) return Ui.GREEN_SOFT;
        if ("blocked".equals(rights)) return Ui.PAPER;
        return Ui.GOLD_SOFT;
    }

    /** Friendly, status-aware reading of Api errors; the token is never included. */
    private String describeError(Exception error) {
        String raw = error == null ? "" : String.valueOf(error.getMessage());
        if (raw.contains("Online services are not configured in this build")) {
            return "Online services are not configured in this build.";
        }
        if (raw.contains("Administrator authentication required") || raw.contains("(401)")) {
            return "Token rejected (401)";
        }
        if (raw.contains("ADMIN_TOKEN is unset") || raw.contains("Administration is not configured")) {
            return "Administration is not enabled on the server (ADMIN_TOKEN unset)";
        }
        try {
            JSONObject payload = new JSONObject(raw);
            String message = payload.optString("error", "");
            if (!message.isEmpty()) return shorten(message);
        } catch (Exception ignored) {
            // The payload is not JSON; fall through to the raw message.
        }
        return shorten(raw.isEmpty() ? "The request could not be completed." : raw);
    }

    private String shorten(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 160) return trimmed;
        return trimmed.substring(0, 157) + "\u2026";
    }

    private String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "\u2014";
        String value = iso.replace('T', ' ');
        if (value.length() > 19) value = value.substring(0, 19);
        return value;
    }

    /** Holds one operator snapshot so the UI renders on the main thread. */
    private static final class AdminData {
        JSONObject summary;
        JSONObject datasets;
        JSONObject feedback;
        JSONObject audit;
        JSONObject premium;
        JSONObject reciters;
        String datasetsError;
        String feedbackError;
        String auditError;
        String premiumError;
        String recitersError;
    }
}
