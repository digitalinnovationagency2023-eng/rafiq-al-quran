package com.qurancompanion.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.qurancompanion.app.logic.Dataset;
import com.qurancompanion.app.logic.Language;
import com.qurancompanion.app.logic.Notices;
import com.qurancompanion.app.logic.TranslationMeta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Static information pages and the optional online forms. */
public final class PagesActivity extends Activity {
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        String page = getIntent().getStringExtra("page");
        if (page == null) page = "about";

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        content.setBackgroundColor(Ui.PAPER);
        scroll.addView(content);
        setContentView(scroll);

        switch (page) {
            case "sources": renderSources(content); break;
            case "support": renderSupport(content); break;
            case "tools": renderTools(content); break;
            case "report": renderReport(content); break;
            case "privacy": renderPrivacy(content); break;
            default: renderAbout(content); break;
        }
    }

    private void heading(LinearLayout parent, String title, String lede) {
        TextView heading = Ui.text(this, title, 24f, Ui.INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(heading);
        if (lede != null) {
            TextView text = Ui.text(this, lede, 13f, Ui.MUTED);
            text.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            parent.addView(text);
        }
    }

    private void section(LinearLayout parent, String title, String body) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 14, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setLayoutParams(Ui.withTop(this, 12));
        TextView heading = Ui.text(this, title, 16f, Ui.INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading);
        TextView text = Ui.text(this, body, 13f, Ui.MUTED);
        text.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(text);
        parent.addView(card);
    }

    private void renderAbout(LinearLayout parent) {
        heading(parent, "About Noor Al-Quran",
                "A free, Quran-focused conversational app. It helps users locate and read verses, with the Arabic text and an identified translation shown separately.");
        section(parent, "What it does",
                "• Retrieves verses from a validated local Quran dataset and identified translations.\n"
                + "• Shows the exact Arabic text, the surah and verse reference, and the source metadata for each passage.\n"
                + "• Lets users request more passages, open a specific reference, and listen to the translation read aloud.\n"
                + "• Works offline: reading, search and answers use the bundled dataset.");
        section(parent, "What it does not do",
                "• It does not use Hadith, Tafsir, external religious websites or a model's own religious knowledge.\n"
                + "• It does not write or paraphrase Quranic verses.\n"
                + "• It does not issue fatwas, rulings or authoritative interpretation.\n"
                + "• It does not claim its search results are exhaustive.\n"
                + "• It does not present a translation as the Arabic text, or imitated speech as Quranic recitation.");
        section(parent, "Limitations",
                "Retrieval is keyword and concept based over the bundled dataset. It may misunderstand a question, miss relevant verses, or rank a less relevant passage highly. When it cannot identify sufficiently relevant verses it says so explicitly and never concludes that the Quran does not address a topic. Please consult qualified scholars where religious interpretation is required.");
        section(parent, "Notices",
                "The English and Arabic notices are the approved wording. Other interface languages show the English notice and are marked as pending professional review.");
    }

    private void renderPrivacy(LinearLayout parent) {
        heading(parent, "Privacy", "What the app stores and what it never does.");
        section(parent, "Stored on this device",
                "• Your questions and the answers shown, so the conversation can be restored.\n"
                + "• Interface preferences: language, text size and voice setting.\n"
                + "• Nothing else. There is no account and no advertising identifier.");
        section(parent, "Never done",
                "• No raw payment card data is stored; payment is hosted by the provider.\n"
                + "• Conversation text is never attached to a report without your explicit consent.\n"
                + "• Nothing is sent to WhatsApp or another third party automatically.\n"
                + "• The app does not read RQM Books accounting, customer, invoice or payroll data.");
        section(parent, "Voice features",
                "Voice input uses the device's speech recognition; audio may be processed by the platform provider. Spoken answers use the device's speech synthesis for the translation and summary only and are never presented as Quranic recitation. Both can be disabled in the menu.");
        section(parent, "Isolation",
                "This app keeps its own database and never reads RQM Books accounting data.");
    }

    private void renderSources(LinearLayout parent) {
        Dataset dataset = Assets.dataset(this);
        heading(parent, "Sources & licences",
                "The app serves only the Quranic text and translations bundled in its own dataset. No Hadith, Tafsir or model-generated religious content is used.");
        section(parent, "Quran dataset",
                "Dataset: " + dataset.provenance().dataset + "\n"
                + "Version: " + dataset.provenance().version + "\n"
                + "Source: " + dataset.provenance().sourceUrl + "\n"
                + "Imported: " + dataset.provenance().importedAt + "\n"
                + "Verses: " + dataset.verseCount() + " · Chapters: " + dataset.chapterCount() + "\n"
                + "Checksum SHA-256:\n" + dataset.provenance().checksum);
        StringBuilder translations = new StringBuilder();
        for (Map.Entry<String, TranslationMeta> entry : dataset.translations().entrySet()) {
            TranslationMeta meta = entry.getValue();
            translations.append("• ").append(entry.getKey().toUpperCase()).append(": ").append(meta.title);
            if (meta.author != null && !meta.author.isEmpty()) translations.append(" — ").append(meta.author);
            translations.append("\n   Licence status: ").append(meta.licenseStatus).append("\n");
        }
        section(parent, "Translations", translations.toString());
        section(parent, "Recitation",
                "Authentic recitation is a separate, rights-gated feature. Candidate reciters are recorded with an unverified rights status and playback is disabled. No recording is cloned, synthesised or redistributed. Text-to-speech is used only for translations and summaries and is never presented as Quranic recitation.");
        section(parent, "Required notices", "English and Arabic: verified wording.\nOther languages: the English notice is shown and marked as pending review.");
    }

    private void renderSupport(LinearLayout parent) {
        heading(parent, "Support the app",
                "Help keep this free Quran-focused app reliable and improving. Voluntary contributions may support maintenance, accessibility, extra language support, better search and verified recitation access.");
        section(parent, "Voluntary only", "Donating is entirely optional and never affects access to reading, search or the conversational experience. No religious reward is promised and no charitable or tax-deductible status is claimed.");

        LinearLayout form = Ui.column(this);
        form.setPadding(0, Ui.dp(this, 12), 0, 0);
        final TextView status = Ui.text(this, "Checking donation availability…", 12f, Ui.MUTED);
        final EditText amount = new EditText(this);
        amount.setHint("Amount in " + "AED" + " (e.g. 25)");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final CheckBox recurring = new CheckBox(this);
        recurring.setText("Make this a monthly donation");
        recurring.setVisibility(View.GONE);
        android.widget.Button donate = Ui.button(this, "Continue to secure checkout", true);
        donate.setEnabled(false);
        donate.setOnClickListener(view -> submitDonation(amount, recurring, status));
        form.addView(status);
        form.addView(amount);
        form.addView(recurring);
        form.addView(donate);
        parent.addView(form);

        new Thread(() -> {
            try {
                JSONObject config = Api.get(getResources(), "/api/donations/config");
                final boolean live = config.optBoolean("live");
                final String message = live
                        ? "Donation collection is enabled. You will be taken to the payment provider's secure checkout."
                        : "Donation collection is not enabled in this deployment. No payment is requested.\n"
                        + joinReasons(config.optJSONArray("blockedReasons"));
                runOnUiThread(() -> {
                    status.setText(message);
                    donate.setEnabled(live);
                    recurring.setVisibility(config.optBoolean("recurringEnabled") ? View.VISIBLE : View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Online services are not configured in this build, so donation collection is unavailable. No payment is requested."));
            }
        }).start();
    }

    private void submitDonation(EditText amount, CheckBox recurring, TextView status) {
        String raw = amount.getText().toString().trim();
        int minor;
        try {
            minor = Math.round(Float.parseFloat(raw) * 100);
        } catch (Exception error) {
            status.setText("Enter a valid amount.");
            return;
        }
        String invalid = com.qurancompanion.app.logic.DonationRules.validate(minor, "AED");
        if (invalid != null) {
            status.setText(invalid);
            return;
        }
        status.setText("Preparing checkout…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("amountMinor", minor);
                body.put("currency", "AED");
                body.put("recurring", recurring.isChecked());
                JSONObject response = Api.post(getResources(), "/api/donations/checkout", body);
                final String url = response.optString("checkoutUrl", "");
                final String message = response.optString("message",
                        "Donation recorded. Follow the provider's checkout to complete the payment.");
                runOnUiThread(() -> {
                    status.setText(message);
                    if (!url.isEmpty()) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Checkout could not be started: " + error.getMessage()));
            }
        }).start();
    }

    private void renderTools(LinearLayout parent) {
        heading(parent, "Optional tools",
                "These are optional conveniences. Core reading, verse lookup and the conversational experience are free and never depend on payment.");
        TextView status = Ui.text(this, "Loading…", 12f, Ui.MUTED);
        parent.addView(status);
        new Thread(() -> {
            try {
                JSONObject response = Api.get(getResources(), "/api/premium/features");
                final boolean enabled = response.optBoolean("enabled");
                final JSONArray features = Api.array(response, "features");
                runOnUiThread(() -> {
                    if (!enabled) {
                        status.setText("Paid tools are not enabled in this deployment. Nothing here changes the Quranic text, its translation sources, or the accuracy of an answer.");
                    } else {
                        status.setText("Choose a tool to continue to the provider's secure checkout.");
                    }
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        if (feature == null) continue;
                        section(parent, feature.optString("name"),
                                feature.optString("description") + "\nPrice: " + feature.optString("currency") + " "
                                        + (feature.optInt("priceMinor") / 100.0) + " / " + feature.optString("billing")
                                        + (enabled ? "" : "\nNot available in this deployment."));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Online services are not configured in this build. Optional tools are unavailable; core features remain free."));
            }
        }).start();
    }

    private void renderReport(LinearLayout parent) {
        heading(parent, "Report an issue",
                "Tell us about a missing verse, a wrong reference, a translation display problem, an accessibility issue or a bug. Reports are reviewed before anything is treated as a correction.");
        final Spinner category = new Spinner(this);
        final String[] keys = {"missing_verse", "incorrect_reference", "translation_display", "search_language", "audio_recitation", "accessibility", "bug", "suggestion"};
        final String[] labels = {"Potentially missing verses", "Incorrect verse reference", "Translation display", "Search or language", "Audio or recitation", "Accessibility", "Bug", "Suggestion"};
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        final EditText description = new EditText(this);
        description.setHint("What happened?");
        description.setMinLines(3);
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText verse = new EditText(this);
        verse.setHint("Verse reference (optional), e.g. 2:255");
        final EditText contact = new EditText(this);
        contact.setHint("Contact detail (optional)");
        final CheckBox consent = new CheckBox(this);
        consent.setText("Include the question text and basic diagnostics with this report");
        final TextView status = Ui.text(this, "Reports are stored in the app's own database and are never forwarded automatically. Set the server URL at build time to submit online.", 11f, Ui.MUTED);
        android.widget.Button submit = Ui.button(this, "Send report", true);
        submit.setOnClickListener(view -> {
            String text = description.getText().toString().trim();
            if (text.length() < 5) {
                status.setText("Please describe the issue in at least a few words.");
                return;
            }
            status.setText("Sending…");
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("category", keys[category.getSelectedItemPosition()]);
                    body.put("description", text);
                    body.put("language", prefs.language());
                    if (!verse.getText().toString().trim().isEmpty()) body.put("verseReference", verse.getText().toString().trim());
                    if (!contact.getText().toString().trim().isEmpty()) body.put("contact", contact.getText().toString().trim());
                    body.put("consent", consent.isChecked());
                    JSONObject response = Api.post(getResources(), "/api/feedback", body);
                    runOnUiThread(() -> status.setText(response.optString("message", "Report recorded.")));
                } catch (Exception error) {
                    runOnUiThread(() -> status.setText("The report could not be sent: " + error.getMessage()));
                }
            }).start();
        });
        LinearLayout form = Ui.column(this);
        form.setPadding(0, Ui.dp(this, 12), 0, 0);
        form.addView(category);
        form.addView(description);
        form.addView(verse);
        form.addView(contact);
        form.addView(consent);
        form.addView(submit);
        form.addView(status);
        parent.addView(form);
    }

    private String joinReasons(JSONArray reasons) {
        if (reasons == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < reasons.length(); i++) builder.append("• ").append(reasons.optString(i)).append('\n');
        return builder.toString();
    }
}
