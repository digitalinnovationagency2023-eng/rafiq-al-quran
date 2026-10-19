// Voluntary donations. The interface is complete; live collection stays off until
// the deployment enables it and records its compliance review.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

const PRESETS = [500, 1000, 2500, 5000, 10000];

export const SupportPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [config, setConfig] = useState(null);
  const [amountMinor, setAmountMinor] = useState(1000);
  const [custom, setCustom] = useState("");
  const [recurring, setRecurring] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.donationConfig().then(setConfig).catch((requestError) => setError(requestError.message));
  }, []);

  const currency = config?.currency || "AED";

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const response = await api.donationCheckout({
        amountMinor: custom ? Math.round(Number(custom) * 100) : amountMinor,
        currency,
        recurring,
      });
      setResult(response);
      if (response.live && response.checkoutUrl) window.location.assign(response.checkoutUrl);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1>{t("donationTitle")}</h1>
      <p className="lede">{t("donationIntro")}</p>
      <p className="inline-note strong">{t("donationFree")}</p>

      {config && !config.live && (
        <div className="banner-warning" role="status">
          <strong>{t("donationDisabled")}</strong>
          <p>{t("donationCompliance")}</p>
        </div>
      )}

      <form className="card" onSubmit={submit}>
        <fieldset className="fieldset">
          <legend>{t("donationAmount")} ({currency})</legend>
          <div className="amount-grid">
            {PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={amountMinor === preset && !custom ? "amount active" : "amount"}
                onClick={() => {
                  setAmountMinor(preset);
                  setCustom("");
                }}
              >
                {currency} {(preset / 100).toFixed(0)}
              </button>
            ))}
          </div>
          <label className="field">
            <span>{t("donationCustom")}</span>
            <input
              type="number"
              min="5"
              max="50000"
              step="0.01"
              inputMode="decimal"
              value={custom}
              onChange={(event) => setCustom(event.target.value)}
              placeholder={`${currency} 25.00`}
            />
          </label>
        </fieldset>

        {config?.recurringEnabled && (
          <label className="field checkbox">
            <input type="checkbox" checked={recurring} onChange={(event) => setRecurring(event.target.checked)} />
            <span>{t("donationRecurring")}</span>
          </label>
        )}

        <p className="inline-note">
          {t("donationReceipt")} {t("donationCompliance")}
        </p>

        <button type="submit" className="button primary" disabled={busy}>
          {busy ? t("loading") : t("donationContinue")}
        </button>

        {error && <p className="banner-error" role="alert">{error}</p>}
        {result && (
          <div className="card subtle" role="status">
            <p>{result.message}</p>
            {result.blockedReasons?.length > 0 && (
              <ul className="limits">
                {result.blockedReasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </form>

      <section className="card">
        <h2>How contributions are used</h2>
        <ul className="limits">
          <li>Maintenance and reliability of the service.</li>
          <li>Accessibility and additional language support.</li>
          <li>Improved search quality over the verified dataset.</li>
          <li>Access to authentic recitation under verified rights.</li>
          <li>Development of optional convenience tools.</li>
        </ul>
        <p className="inline-note">
          Donations are not required for any core feature, are not a purchase, and are separate from optional tools. No
          religious reward is promised and no charitable or tax-deductible status is claimed.
        </p>
      </section>
    </div>
  );
};
