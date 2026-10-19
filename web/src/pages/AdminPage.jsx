// Minimal operator console. The token is kept in sessionStorage only and sent in
// a header; it is never placed in a URL or a cookie, and no payment secret is ever
// displayed.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

const TOKEN_KEY = "quran.adminToken";

export const AdminPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) || "");
  const [summary, setSummary] = useState(null);
  const [feedback, setFeedback] = useState([]);
  const [translations, setTranslations] = useState([]);
  const [audit, setAudit] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = async () => {
    if (!token) return;
    setBusy(true);
    setError("");
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
      const [summaryResponse, feedbackResponse, translationsResponse, auditResponse] = await Promise.all([
        api.admin.summary(token),
        api.admin.feedback(token),
        api.admin.translations(token),
        api.admin.audit(token),
      ]);
      setSummary(summaryResponse);
      setFeedback(feedbackResponse.feedback || []);
      setTranslations(translationsResponse.translations || []);
      setAudit(auditResponse.entries || []);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (token) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setFeedbackStatus = async (id, status) => {
    try {
      await api.admin.updateFeedback(token, id, { status, resolutionNote: status === "resolved" ? "Reviewed by the team." : undefined });
      await load();
    } catch (requestError) {
      setError(requestError.message);
    }
  };

  const setTranslationStatus = async (id, licenseStatus) => {
    try {
      await api.admin.updateTranslation(token, id, { licenseStatus });
      await load();
    } catch (requestError) {
      setError(requestError.message);
    }
  };

  return (
    <div className="page">
      <h1>{t("admin")}</h1>
      <p className="inline-note">
        Administration is protected by the operator token configured on the server. Administrative credentials are never
        exposed to the browser bundle.
      </p>

      <form
        className="card"
        onSubmit={(event) => {
          event.preventDefault();
          load();
        }}
      >
        <label className="field">
          <span>{t("adminToken")}</span>
          <input type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" />
        </label>
        <button type="submit" className="button primary" disabled={busy || !token}>
          {busy ? t("loading") : "Load"}
        </button>
        {error && <p className="banner-error" role="alert">{error}</p>}
      </form>

      {summary && (
        <section className="card">
          <h2>{t("adminSummary")}</h2>
          <table className="table">
            <tbody>
              {Object.entries(summary.counts).map(([key, value]) => (
                <tr key={key}>
                  <td>{key}</td>
                  <td>{value}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="inline-note">
            Dataset checksum: <span className="mono small">{summary.dataset?.checksum}</span>
          </p>
        </section>
      )}

      {feedback.length > 0 && (
        <section className="card">
          <h2>{t("adminFeedback")}</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Created</th>
                <th>Category</th>
                <th>Description</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {feedback.map((row) => (
                <tr key={row.id}>
                  <td className="small">{row.createdAt?.slice(0, 16).replace("T", " ")}</td>
                  <td className="small">{row.category}</td>
                  <td className="small">{row.description}</td>
                  <td><span className="badge badge-pending">{row.status}</span></td>
                  <td>
                    <select
                      value={row.status}
                      onChange={(event) => setFeedbackStatus(row.id, event.target.value)}
                      aria-label={`Status for ${row.id}`}
                    >
                      {["open", "triaged", "in_review", "resolved", "rejected"].map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {translations.length > 0 && (
        <section className="card">
          <h2>{t("adminTranslations")}</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Language</th>
                <th>Title</th>
                <th>Licence</th>
                <th>Set</th>
              </tr>
            </thead>
            <tbody>
              {translations.map((translation) => (
                <tr key={translation.id}>
                  <td>{translation.languageCode}</td>
                  <td className="small">{translation.title}</td>
                  <td>
                    <span className={`badge badge-${translation.licenseStatus}`}>{translation.licenseStatus}</span>
                  </td>
                  <td>
                    <select
                      value={translation.licenseStatus}
                      onChange={(event) => setTranslationStatus(translation.id, event.target.value)}
                      aria-label={`Licence status for ${translation.title}`}
                    >
                      {["unverified", "verified", "blocked"].map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="inline-note">
            Marking a translation verified records an operator decision in the audit log. It does not change the Quranic
            text.
          </p>
        </section>
      )}

      {audit.length > 0 && (
        <section className="card">
          <h2>Audit log</h2>
          <ul className="limits">
            {audit.map((entry) => (
              <li key={entry.id} className="small">
                {entry.at.slice(0, 19).replace("T", " ")} · {entry.actor} · {entry.action}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
};
