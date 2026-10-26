package com.rqmbooks.rafiqalquran;

import android.graphics.Color;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.rqmbooks.rafiqalquran.logic.Dataset;
import com.rqmbooks.rafiqalquran.logic.DonationRules;
import com.rqmbooks.rafiqalquran.logic.TranslationMeta;

import org.json.JSONObject;

import java.util.Map;

/**
 * Static information pages plus the optional online forms.
 *
 * <p>The pages are user-facing: no build configuration, environment variable or
 * raw server response is ever shown. Optional online services are only rendered
 * when the server reports that they are actually live; otherwise the page shows
 * a short plain-language statement and no form.
 */
public final class PagesActivity extends Activity {
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);
        String page = getIntent().getStringExtra("page");
        if (page == null) page = "about";

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        content.setBackgroundColor(Color.TRANSPARENT);
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
        infoCard(parent, title, body);
    }

    /** Silver hairline card matching the rest of the app. */
    private LinearLayout cardShell(LinearLayout parent, String title) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.PAPER, Ui.LINE, 14, this));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setLayoutParams(Ui.withTop(this, 12));
        TextView heading = Ui.text(this, title, 16f, Ui.INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading);
        parent.addView(card);
        return card;
    }

    private void infoCard(LinearLayout parent, String title, String body) {
        LinearLayout card = cardShell(parent, title);
        TextView text = Ui.text(this, body, 13f, Ui.MUTED);
        text.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(text);
    }

    private void renderAbout(LinearLayout parent) {
        heading(parent, "About Rafiq Al-Quran",
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
        section(parent, "Contact", "Digital Innovative Agency FZ LLC\nEmail: info@rqmbooks.com\nPhone / WhatsApp: +971 9 501 0254\nWhatsApp chat: https://wa.me/97195010254");
        section(parent, "Notices",
                "The English and Arabic notices are the approved wording. Other interface languages show the English notice and are marked as pending professional review.");
    }

    private void renderPrivacy(LinearLayout parent) {
        heading(parent, "Privacy", "What the app stores and what it never does.");
        section(parent, "Stored on this device",
                "• Your questions and the answers shown, so the conversation can be restored.\n"
                + "• Interface preferences: language, text size and voice setting.\n"
                + "• Study data you create: bookmark collections, personal notes, saved searches and reading plans.\n"
                + "• Nothing else. There is no account and no advertising identifier.");
        section(parent, "Never done",
                "• No raw payment card data is stored; payment is hosted by the provider.\n"
                + "• Conversation text is never attached to a report without your explicit consent.\n"
                + "• Nothing is sent to WhatsApp or another third party automatically.\n"
                + "• The app does not read RQM Books accounting, customer, invoice or payroll data.");
        section(parent, "Voice features",
                "Voice input uses the device's speech recognition; audio may be processed by the platform provider. Spoken answers use the device's speech synthesis for the translation and summary only and are never presented as Quranic recitation. Both can be disabled in the menu.");
        section(parent, "Optional AI assistance",
                "AI assistance is off unless the operator enables a free AI service. When it is used, your question and any attached image are sent to this app's own server, which processes them with an AI provider only to understand the wording or to read the image. Nothing is stored and no account or identifier is attached; the feature can be turned off in the menu. The AI never supplies Quran text or interpretation — every verse comes from the bundled dataset. Without AI the app works locally.");
        section(parent, "Attachments",
                "Text files (.txt/.md) and .docx documents are read on this device and are never uploaded. Images are sent only to the optional AI service when it is enabled. PDF attachments are not supported yet. Do not attach sensitive personal documents.");
        section(parent, "Recitation",
                "Verse recitation streams from Quran.com's public CDN over the internet, shows the reciter name and 'Source: Quran.com' attribution, and is not stored on the device. The recitation rights review for this source was recorded on 21 October 2026. Text-to-speech is used only for translations and summaries and is never presented as Quranic recitation.");
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
                "Verse recitation is enabled and streams from Quran.com's public CDN over the internet, with the reciter name and \u201CSource: Quran.com\u201D attribution shown in the player. Audio is not stored on the device. The recitation rights review for this source was recorded by the publisher on 21 October 2026. Text-to-speech is used only for translations and summaries and is never presented as Quranic recitation.");
        section(parent, "Required notices", "English and Arabic: verified wording.\nOther languages: the English notice is shown and marked as pending review.");
    }

    // ----------------------------------------------------------------- support
    private void renderSupport(LinearLayout parent) {
        heading(parent, "Support the app",
                "Rafiq Al-Quran is free to read, search and use. There is no account, and nothing on this page is required to use the app.");
        section(parent, "Voluntary only",
                "Donating is entirely optional and never affects access to reading, search or the conversational experience. No religious reward is promised and no charitable or tax-deductible status is claimed.");
        section(parent, "What it costs to run",
                "The Quran, translations, search, recitation and study tools are free. The optional AI layer costs money per "
                        + "answer: roughly USD 40-60 a month at 1,000 active users and USD 300-400 a month at 10,000. "
                        + "AI questions are limited to 10 a day per device so this stays bounded. There are no ads and no "
                        + "data selling; anything given here goes to server and AI costs only.");

        // The donation form is only ever built when the server confirms that
        // collection is live. When it is not, this page shows plain copy only.
        final LinearLayout donations = Ui.column(this);
        donations.setPadding(0, Ui.dp(this, 12), 0, 0);
        parent.addView(donations);
        donations.addView(Ui.text(this, "Checking donation availability…", 12f, Ui.MUTED));

        new Thread(() -> {
            String donationUrl = "";
            try {
                donationUrl = Api.assistantStatus(getResources()).optString("donationUrl", "");
            } catch (Exception ignored) {
                // donation support is optional; offline devices just see the static copy
            }
            final String link = donationUrl == null ? "" : donationUrl.trim();
            try {
                JSONObject config = Api.get(getResources(), "/api/donations/config");
                final boolean live = config.optBoolean("live");
                final boolean recurringEnabled = config.optBoolean("recurringEnabled");
                final String currency = config.optString("currency", "AED").toUpperCase();
                final int minMinor = config.optInt("minAmountMinor", DonationRules.MIN_MINOR);
                final int maxMinor = config.optInt("maxAmountMinor", DonationRules.MAX_MINOR);
                runOnUiThread(() -> {
                    donations.removeAllViews();
                    if (live) {
                        renderDonationForm(donations, currency, minMinor, maxMinor, recurringEnabled);
                    } else {
                        renderDonationsDisabled(donations);
                        if (!link.isEmpty()) {
                            Button donate = Ui.button(this, "Donate / support hosting", true);
                            donate.setLayoutParams(Ui.withTop(this, 8));
                            donate.setOnClickListener(view -> openDonationLink(link));
                            donations.addView(donate);
                        }
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    donations.removeAllViews();
                    infoCard(donations, "Donation collection is unavailable",
                            "Donations are not enabled in this release. No payment is requested.");
                });
            }
        }).start();

        section(parent, "Other ways to help",
                "You can support the app by sharing it with others and sending feedback.");
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 8), 0, 0);
        Button share = Ui.button(this, "Share the app", false);
        share.setOnClickListener(view -> shareApp());
        Button feedback = Ui.button(this, "Send feedback", true);
        feedback.setOnClickListener(view -> openReport());
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        feedbackParams.leftMargin = Ui.dp(this, 8);
        actions.addView(share);
        actions.addView(feedback, feedbackParams);
        parent.addView(actions);

        TextView footnote = Ui.text(this,
                "No purchase is offered in this app. Reading, search, study tools and a limited number of AI answers stay free.",
                11f, Ui.MUTED);
        footnote.setPadding(0, Ui.dp(this, 18), 0, 0);
        parent.addView(footnote);
    }

    private void openDonationLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "No browser is available to open the donation page.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderDonationsDisabled(LinearLayout parent) {
        infoCard(parent, "Donations are not enabled in this release",
                "No payment is requested. No checkout form is shown, and the app never collects card details.");
    }

    private void renderDonationForm(LinearLayout parent, String currency, int minMinor, int maxMinor, boolean recurringEnabled) {
        LinearLayout card = cardShell(parent, "Make a voluntary donation");
        TextView description = Ui.text(this,
                "Donation collection is enabled. You will be taken to the payment provider's secure checkout; "
                        + "card details are entered on the provider's page, never in this app.",
                13f, Ui.MUTED);
        description.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        card.addView(description);

        final EditText amount = new EditText(this);
        amount.setHint("Amount in " + currency + " (for example " + Math.max(1, minMinor / 100) + ")");
        amount.setTextColor(Ui.INK);
        amount.setHintTextColor(Ui.MUTED);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(amount);

        final CheckBox recurring = new CheckBox(this);
        recurring.setText("Make this a monthly donation");
        recurring.setTextColor(Ui.INK);
        recurring.setVisibility(recurringEnabled ? View.VISIBLE : View.GONE);
        card.addView(recurring);

        final TextView status = Ui.text(this, "", 12f, Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);

        Button donate = Ui.button(this, "Continue to secure checkout", true);
        donate.setOnClickListener(view -> submitDonation(amount, recurring, status, currency, minMinor, maxMinor));
        card.addView(donate, Ui.withTop(this, 8));
        card.addView(status);
    }

    private void submitDonation(EditText amount, CheckBox recurring, TextView status,
                                String currency, int minMinor, int maxMinor) {
        String raw = amount.getText().toString().trim();
        int minor;
        try {
            minor = Math.round(Float.parseFloat(raw) * 100);
        } catch (Exception error) {
            status.setText("Enter a valid amount.");
            return;
        }
        if (minor < minMinor) {
            status.setText("Enter at least " + (minMinor / 100) + " " + currency + ".");
            return;
        }
        if (minor > maxMinor) {
            status.setText("Enter at most " + (maxMinor / 100) + " " + currency + ".");
            return;
        }
        String invalid = DonationRules.validate(minor, currency);
        if (invalid != null) {
            status.setText(invalid);
            return;
        }
        status.setText("Preparing checkout…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("amountMinor", minor);
                body.put("currency", currency);
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
                runOnUiThread(() -> status.setText(
                        "Checkout could not be started. No payment has been requested."));
            }
        }).start();
    }

    // ------------------------------------------------------------- study tools
    private void renderTools(LinearLayout parent) {
        heading(parent, "Study tools are included free",
                "Every study tool is included at no cost. Reading the Quran, search and these tools never require an account or a payment.");
        section(parent, "What is included",
                "• Bookmark collections: group saved verses into named collections.\n"
                + "• Personal notes: attach a private note to any verse.\n"
                + "• Saved searches: keep the searches you run and repeat them later.\n"
                + "• Reading plans: follow a plan and track your progress.\n"
                + "• Cross-language comparison: read the Arabic and a translation side by side in the reader.\n"
                + "• Export: share bookmarks and notes as CSV or plain text through the share sheet.");
        section(parent, "Stored on this device",
                "Collections, notes, saved searches and reading plans are stored on this device only. Reading the Quran remains free and offline.");

        Button open = Ui.button(this, "Open study tools", true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 18);
        open.setOnClickListener(view -> startActivity(new Intent(this, StudyActivity.class)));
        parent.addView(open, params);

        TextView footnote = Ui.text(this,
                "No plans, subscriptions or add-ons are sold in this app.",
                11f, Ui.MUTED);
        footnote.setPadding(0, Ui.dp(this, 14), 0, 0);
        parent.addView(footnote);
    }

    // -------------------------------------------------------- report an issue
    private void renderReport(LinearLayout parent) {
        heading(parent, "Report an issue",
                "Tell us about a missing verse, a wrong reference, a translation display problem, an accessibility issue or a bug. Reports are reviewed before anything is treated as a correction.");
        final Spinner category = new Spinner(this);
        final String[] keys = {"missing_verse", "incorrect_reference", "translation_display", "search_language", "audio_recitation", "accessibility", "bug", "suggestion"};
        final String[] labels = {"Potentially missing verses", "Incorrect verse reference", "Translation display", "Search or language", "Audio or recitation", "Accessibility", "Bug", "Suggestion"};
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        final EditText description = new EditText(this);
        description.setHint("What happened?");
        description.setTextColor(Ui.INK);
        description.setHintTextColor(Ui.MUTED);
        description.setMinLines(3);
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText verse = new EditText(this);
        verse.setHint("Verse reference (optional), e.g. 2:255");
        verse.setTextColor(Ui.INK);
        verse.setHintTextColor(Ui.MUTED);
        final EditText contact = new EditText(this);
        contact.setHint("Contact detail (optional)");
        contact.setTextColor(Ui.INK);
        contact.setHintTextColor(Ui.MUTED);
        final CheckBox consent = new CheckBox(this);
        consent.setText("Include basic diagnostics (interface language and platform) with this report");
        consent.setTextColor(Ui.INK);
        final TextView status = Ui.text(this,
                "Reports are reviewed before anything is treated as a correction. Nothing is sent to WhatsApp or another third party automatically.",
                11f, Ui.MUTED);
        final Button submit = Ui.button(this, "Send report", true);
        submit.setOnClickListener(view -> {
            String text = description.getText().toString().trim();
            if (text.length() < 5) {
                status.setText("Please describe the issue in at least a few words.");
                return;
            }
            if (text.length() > 4000) {
                status.setText("Please keep the description under 4000 characters.");
                return;
            }
            submit.setEnabled(false);
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
                    if (consent.isChecked()) {
                        JSONObject diagnostics = new JSONObject();
                        diagnostics.put("platform", "android");
                        diagnostics.put("language", prefs.language());
                        body.put("diagnostics", diagnostics);
                    }
                    JSONObject response = Api.post(getResources(), "/api/feedback", body);
                    final String message = response.optString("message",
                            "Thank you. Your report has been recorded for review.");
                    runOnUiThread(() -> {
                        status.setText(message);
                        submit.setEnabled(true);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        status.setText("The report could not be sent. Check your connection and try again.");
                        submit.setEnabled(true);
                    });
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
        form.addView(submit, Ui.withTop(this, 8));
        form.addView(status);
        parent.addView(form);
    }

    // ----------------------------------------------------------------- actions
    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Rafiq Al-Quran");
        send.putExtra(Intent.EXTRA_TEXT,
                "Rafiq Al-Quran is a free app for reading and searching the Quran, with the Arabic text "
                        + "and identified translations. It works offline.");
        try {
            startActivity(Intent.createChooser(send, "Share Rafiq Al-Quran"));
        } catch (Exception error) {
            Toast.makeText(this, "No app is available to share with.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openReport() {
        startActivity(new Intent(this, PagesActivity.class).putExtra("page", "report"));
    }
}
