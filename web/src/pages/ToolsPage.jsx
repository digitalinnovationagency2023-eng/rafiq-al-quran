// Optional premium tools. Disabled by default; the page always states that the
// core experience is free and that payment never changes Quranic content.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

export const ToolsPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [data, setData] = useState(null);
  const [entitlements, setEntitlements] = useState([]);
  const [busyKey, setBusyKey] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([api.premiumFeatures(), api.premiumEntitlements()])
      .then(([features, entitlementsResponse]) => {
        setData(features);
        setEntitlements(entitlementsResponse.entitlements || []);
      })
      .catch((requestError) => setError(requestError.message));
  }, []);

  const startCheckout = async (featureKey) => {
    setError("");
    setBusyKey(featureKey);
    try {
      const response = await api.premiumCheckout(featureKey);
      if (response.checkoutUrl) window.location.assign(response.checkoutUrl);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusyKey("");
    }
  };

  if (!data) return <div className="page"><p>{t("loading")}</p></div>;

  return (
    <div className="page">
      <h1>{t("premiumTitle")}</h1>
      <p className="lede">{t("premiumIntro")}</p>
      <p className="inline-note strong">{t("premiumCore")}</p>

      {!data.enabled && (
        <div className="banner-warning" role="status">
          {t("premiumDisabled")}
        </div>
      )}

      <div className="cards">
        {data.features.map((feature) => {
          const owned = entitlements.some((entitlement) => entitlement.featureKey === feature.key && entitlement.status === "active");
          return (
            <section className="card" key={feature.key}>
              <h2>{feature.name}</h2>
              <p>{feature.description}</p>
              <p className="price">
                {feature.currency} {(feature.priceMinor / 100).toFixed(0)} / {feature.billing}
              </p>
              <button
                type="button"
                className="button primary"
                disabled={!data.live || !feature.available || busyKey === feature.key}
                onClick={() => startCheckout(feature.key)}
                title={!data.live ? data.blockedReasons?.join("; ") : undefined}
              >
                {owned ? "Active" : busyKey === feature.key ? t("loading") : data.live && feature.available ? "Choose" : "Not available"}
              </button>
            </section>
          );
        })}
      </div>

      {data.blockedReasons?.length > 0 && (
        <ul className="limits">
          {data.blockedReasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      )}
      {error && <p className="banner-error" role="alert">{error}</p>}
    </div>
  );
};
