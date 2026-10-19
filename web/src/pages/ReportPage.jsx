// Feedback and issue reporting. Conversation text and diagnostics are only
// included with explicit consent, and nothing is forwarded to a third party
// automatically.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

export const ReportPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [categories, setCategories] = useState([]);
  const [whatsapp, setWhatsapp] = useState(false);
  const [form, setForm] = useState({
    category: "missing_verse",
    description: "",
    verseReference: "",
    contact: "",
    consent: false,
  });
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .feedbackCategories()
      .then((response) => {
        setCategories(response.categories || []);
        setWhatsapp(Boolean(response.whatsappConfigured));
      })
      .catch((requestError) => setError(requestError.message));
  }, []);

  const update = (key) => (event) => {
    const value = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    setForm((current) => ({ ...current, [key]: value }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const response = await api.feedback({
        category: form.category,
        description: form.description,
        language: settings.language,
        verseReference: form.verseReference || undefined,
        contact: form.contact || undefined,
        consent: form.consent,
      });
      setResult(response);
      setForm((current) => ({ ...current, description: "", verseReference: "", contact: "", consent: false }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1>{t("feedbackTitle")}</h1>
      <p className="lede">{t("feedbackIntro")}</p>

      <form className="card" onSubmit={submit}>
        <label className="field">
          <span>{t("feedbackCategory")}</span>
          <select value={form.category} onChange={update("category")}>
            {categories.map((category) => (
              <option key={category.key} value={category.key}>{category.label}</option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>{t("feedbackDescription")}</span>
          <textarea value={form.description} onChange={update("description")} rows={5} required minLength={5} maxLength={4000} />
        </label>

        <label className="field">
          <span>{t("feedbackVerse")}</span>
          <input value={form.verseReference} onChange={update("verseReference")} placeholder="2:255" />
        </label>

        <label className="field">
          <span>{t("feedbackContact")}</span>
          <input value={form.contact} onChange={update("contact")} autoComplete="email" />
        </label>

        <label className="field checkbox">
          <input type="checkbox" checked={form.consent} onChange={update("consent")} />
          <span>{t("feedbackConsent")}</span>
        </label>
        <p className="inline-note">{t("feedbackConsentNote")}</p>

        <button type="submit" className="button primary" disabled={busy || form.description.trim().length < 5}>
          {busy ? t("loading") : t("feedbackSubmit")}
        </button>

        {error && <p className="banner-error" role="alert">{error}</p>}
        {result && (
          <div className="card subtle" role="status">
            <p>{result.message}</p>
            {result.whatsapp && (
              <p className="inline-note">
                {t("feedbackWhatsApp")} <a href={`https://wa.me/${result.whatsapp.number.replace(/[^\d]/g, "")}`} target="_blank" rel="noreferrer noopener">{result.whatsapp.number}</a>
              </p>
            )}
          </div>
        )}
      </form>

      <section className="card">
        <h2>What happens to a report</h2>
        <ul className="limits">
          <li>It is stored in the application's own database and reviewed by the team.</li>
          <li>It is not treated as a verified correction until it has been checked against the dataset.</li>
          <li>Conversation text or diagnostics are included only when you tick the consent box.</li>
          <li>Nothing is sent to WhatsApp or another third party automatically.</li>
        </ul>
      </section>
    </div>
  );
};
