// About: what the application is, what it refuses to do, and its limitations.
import { translate } from "../lib/i18n.js";

export const AboutPage = ({ settings }) => {
  const t = (key) => translate(settings.language, key);
  return (
    <div className="page">
      <h1>{t("about")}</h1>
      <p className="lede">
        {t("appName")} is a free, Quran-focused conversational experience. It helps users locate and read verses from the
        Quran, with the Arabic text and an identified translation shown separately.
      </p>

      <section className="card">
        <h2>What it does</h2>
        <ul className="limits">
          <li>Retrieves verses from a validated local Quran dataset and identified translations.</li>
          <li>Shows the exact Arabic text, the surah and verse reference, and the source metadata for each passage.</li>
          <li>Lets users refine a search, request more passages, or open a specific reference.</li>
          <li>Supports text and voice input, and can read the translation and summary aloud.</li>
        </ul>
      </section>

      <section className="card">
        <h2>What it does not do</h2>
        <ul className="limits">
          <li>It does not use Hadith, Tafsir, external religious websites or a model's own religious knowledge.</li>
          <li>It does not write or paraphrase Quranic verses: every displayed verse is read from the dataset.</li>
          <li>It does not issue fatwas, rulings, or authoritative interpretation.</li>
          <li>It does not claim that its search results are exhaustive.</li>
          <li>It does not present a translation as the Arabic text, or imitated speech as a Quranic recitation.</li>
        </ul>
      </section>

      <section className="card">
        <h2>Limitations</h2>
        <p>
          Retrieval is keyword and concept based over the configured dataset. It may misunderstand a question, miss
          relevant verses, or rank a less relevant passage highly. When it cannot identify sufficiently relevant verses it
          says so explicitly and never concludes that the Quran does not address a topic. Please consult qualified scholars
          where religious interpretation is required.
        </p>
        <p className="inline-note">
          The required notice is shown with every substantive answer. The English and Arabic notices are the approved
          wording; other languages show the English notice and are marked as pending professional review.
        </p>
      </section>

      <section className="card">
        <h2>Sources and independence</h2>
        <p>
          The Quranic text and translations are imported from the configured dataset and recorded with their source,
          checksum and import date. Translation licence status is explicit and visible. The application runs on its own
          database and infrastructure and does not read RQM Books accounting, customer or financial data.
        </p>
      </section>
    </div>
  );
};
