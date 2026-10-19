// Presentation of a single retrieved passage: Quranic Arabic, the identified
// translation, the reference, and a source panel. They are deliberately separate
// blocks so the Arabic text is never confused with a translation or commentary.
import { useState } from "react";

const LicenseBadge = ({ status }) => (
  <span className={`badge badge-${status}`}>
    {status === "verified" ? "Licence verified" : status === "blocked" ? "Licence blocked" : "Licence status: unverified"}
  </span>
);

export const PassageCard = ({ passage, language, t }) => {
  const [open, setOpen] = useState(false);
  const translation = passage.translation;
  return (
    <article className="passage" aria-label={`${passage.referenceLabel}`}>
      <header className="passage-head">
        <span className="passage-ref">{passage.referenceLabel}</span>
        <span className="passage-chapter">{passage.chapter.nameArabic}</span>
      </header>

      <div className="quran-text" dir="rtl" lang="ar">
        {passage.arabic}
      </div>

      <div className="translation-block">
        <div className="label-row">
          <span className="label">{t("translationLabel")}</span>
          {translation.title && (
            <span className="translation-source">
              {translation.title}
              {translation.author ? ` — ${translation.author}` : ""}
            </span>
          )}
        </div>
        <p className="translation-text" dir={translation.direction || "ltr"} lang={translation.language}>
          {translation.text}
        </p>
        {translation.fallbackToEnglish && (
          <p className="inline-note">A reviewed translation is not available for this interface language, so the English translation is shown.</p>
        )}
      </div>

      <footer className="passage-actions">
        <button type="button" className="button ghost" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
          {t("showSource")}
        </button>
        <button type="button" className="button ghost" disabled title={t("recitationDisabled")}>
          {t("recitation")} — disabled
        </button>
      </footer>

      {open && (
        <section className="source-panel" aria-label={t("sourceAndLicence")}>
          <h4>{t("sourceAndLicence")}</h4>
          <dl>
            <div>
              <dt>Quran dataset</dt>
              <dd>
                {passage.source.dataset} · {passage.source.version}
              </dd>
            </div>
            <div>
              <dt>Source URL</dt>
              <dd>
                {passage.source.sourceUrl ? (
                  <a href={passage.source.sourceUrl} target="_blank" rel="noreferrer noopener">
                    {passage.source.sourceUrl}
                  </a>
                ) : (
                  "recorded in dataset_versions"
                )}
              </dd>
            </div>
            <div>
              <dt>Checksum (SHA-256)</dt>
              <dd className="mono">{passage.source.checksum}</dd>
            </div>
            <div>
              <dt>Imported</dt>
              <dd>{passage.source.importedAt}</dd>
            </div>
            <div>
              <dt>Translation</dt>
              <dd>
                {translation.title || "—"} {translation.author ? `— ${translation.author}` : ""}
              </dd>
            </div>
            <div>
              <dt>Licence status</dt>
              <dd><LicenseBadge status={translation.licenseStatus} /></dd>
            </div>
          </dl>
          <p className="inline-note">
            The Arabic line is the exact dataset text for this verse. The translation is a human translation published
            separately and is not the Arabic text. Source metadata for every translation is listed on the Sources page.
          </p>
        </section>
      )}
    </article>
  );
};
