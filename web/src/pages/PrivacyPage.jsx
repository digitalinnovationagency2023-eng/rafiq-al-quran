// Privacy page: what is collected, how long it is kept, and what is never done.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

export const PrivacyPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [retention, setRetention] = useState(null);

  useEffect(() => {
    api.meta().then((meta) => setRetention(meta.privacy)).catch(() => {});
  }, []);

  return (
    <div className="page">
      <h1>{t("privacyTitle")}</h1>

      <section className="card">
        <h2>What this application stores</h2>
        <ul className="limits">
          <li>An anonymous, signed session identifier in a cookie. No account is required for the core experience.</li>
          <li>Your questions and the answers shown, so you can continue a conversation and revisit it on this device.</li>
          <li>Interface preferences (language, text size, voice setting) in your browser's local storage.</li>
          <li>Reports you submit, with the details you choose to include.</li>
          <li>Donation records without card data: amount, currency, status and the payment provider's reference.</li>
        </ul>
        {retention && (
          <p className="inline-note">
            Conversation retention: {retention.conversationRetentionDays} days. Feedback retention:{" "}
            {retention.feedbackRetentionDays} days. Expired conversations are deleted automatically.
          </p>
        )}
      </section>

      <section className="card">
        <h2>What is never done</h2>
        <ul className="limits">
          <li>No raw payment card details are stored. Payment is hosted by the payment provider.</li>
          <li>Conversation text is never attached to a report without your explicit consent.</li>
          <li>Nothing is forwarded to WhatsApp or another third party automatically.</li>
          <li>This application does not read RQM Books accounting, customer, invoice or payroll data.</li>
        </ul>
      </section>

      <section className="card">
        <h2>Voice features</h2>
        <p>{t("sttDisclosure")}</p>
        <p>{t("ttsDisclosure")}</p>
        <p>Voice features can be disabled entirely in the settings bar at the top of the application.</p>
      </section>

      <section className="card">
        <h2>Deleting your data</h2>
        <p>
          Each conversation can be deleted from the chat screen, which removes its messages from the server. Anonymous
          sessions also expire automatically. Reports can be corrected or removed by contacting the team through the
          Report page.
        </p>
      </section>

      <section className="card">
        <h2>Isolation from RQM Books</h2>
        <p>{t("isolationNote")}</p>
      </section>
    </div>
  );
};
