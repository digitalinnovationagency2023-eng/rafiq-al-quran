// Transparency page: where the Quranic text and translations come from, and the
// current licence review status of each source.
import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";

const LicenceBadge = ({ status }) => (
  <span className={`badge badge-${status}`}>
    {status === "verified" ? "verified" : status === "blocked" ? "blocked" : "unverified"}
  </span>
);

export const SourcesPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  const [sources, setSources] = useState(null);
  const [notices, setNotices] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([api.sources(), api.notices()])
      .then(([sourcesResponse, noticesResponse]) => {
        setSources(sourcesResponse);
        setNotices(noticesResponse.notices);
      })
      .catch((requestError) => setError(requestError.message));
  }, []);

  if (error) return <div className="page"><p className="banner-error" role="alert">{error}</p></div>;
  if (!sources) return <div className="page"><p>{t("loading")}</p></div>;

  return (
    <div className="page">
      <h1>{t("sources")}</h1>
      <p className="lede">
        The application serves only the Quranic text and translations configured in its own dataset. No Hadith, Tafsir,
        external commentary or model-generated religious content is used to answer questions.
      </p>

      <section className="card">
        <h2>Quran dataset</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Dataset</th>
              <th>Version</th>
              <th>Source</th>
              <th>Checksum</th>
              <th>Imported</th>
            </tr>
          </thead>
          <tbody>
            {sources.datasets.map((dataset) => (
              <tr key={`${dataset.dataset}-${dataset.checksum}`}>
                <td>{dataset.dataset}</td>
                <td>{dataset.version}</td>
                <td>
                  {dataset.sourceUrl ? (
                    <a href={dataset.sourceUrl} target="_blank" rel="noreferrer noopener">source</a>
                  ) : (
                    "—"
                  )}
                </td>
                <td className="mono small">{dataset.checksum?.slice(0, 16)}…</td>
                <td>{dataset.importedAt?.slice(0, 10)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="inline-note">
          The Arabic text is stored exactly as imported and is validated for completeness (114 surahs, 6236 verses) before
          it is served. Any dataset replacement goes through the same validated import, never through an editing screen.
        </p>
      </section>

      <section className="card">
        <h2>Translations</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Language</th>
              <th>Translation</th>
              <th>Author / publisher</th>
              <th>Licence</th>
            </tr>
          </thead>
          <tbody>
            {sources.translations.map((translation) => (
              <tr key={translation.id}>
                <td>{translation.languageName}</td>
                <td>{translation.title}</td>
                <td>{translation.author || translation.publisher || "—"}</td>
                <td><LicenceBadge status={translation.licenseStatus} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="inline-note">
          Licence status is shown for every translation. Until an operator records the licence review as verified, a
          translation is explicitly marked unverified. Each passage also shows its own source panel with the dataset
          checksum and translation metadata.
        </p>
      </section>

      <section className="card">
        <h2>Recitation</h2>
        <p>
          Authentic recitation is designed as a separate, rights-gated feature. Candidate reciters are recorded in the
          dataset with an unverified rights status and playback disabled. No recording is cloned, synthesised or
          redistributed. Ordinary text-to-speech is used only for translations and short summaries and is never presented
          as Quranic recitation.
        </p>
        <table className="table">
          <thead>
            <tr>
              <th>Reciter</th>
              <th>Style</th>
              <th>Rights</th>
              <th>Playback</th>
            </tr>
          </thead>
          <tbody>
            {sources.reciters.map((reciter) => (
              <tr key={reciter.key}>
                <td>{reciter.name}</td>
                <td>{reciter.style || "—"}</td>
                <td><LicenceBadge status={reciter.rightsStatus} /></td>
                <td>{reciter.enabled ? "enabled" : "disabled"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {notices && (
        <section className="card">
          <h2>Notice review status</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Language</th>
                <th>Required notice</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(notices).map(([code, notice]) => (
                <tr key={code}>
                  <td>{code}</td>
                  <td className="small">{notice.text.slice(0, 90)}…</td>
                  <td>
                    <span className={`badge badge-${notice.reviewStatus === "verified" ? "verified" : "pending"}`}>
                      {notice.reviewStatus}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
};
