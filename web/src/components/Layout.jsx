// Application shell: identity, navigation, language, text size and voice
// preferences. Everything visual is black/white with restrained gold accents.
import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { api } from "../lib/api.js";
import { LANGUAGES, isUiTranslated, languageMeta, translate } from "../lib/i18n.js";

export const Logo = ({ compact = false }) => (
  <span className="logo" aria-label="Quran Companion">
    <span className="logo-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" width="26" height="26" role="presentation" focusable="false">
        <rect x="1.5" y="3.5" width="29" height="25" rx="4" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M8 3.5v25M24 3.5v25" stroke="currentColor" strokeWidth="1.2" opacity="0.5" />
        <path d="M13 11.5c2.4 1.6 3.6 3 6 3s3.6-1.4 6-3M13 17c2.4 1.6 3.6 3 6 3s3.6-1.4 6-3" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
      </svg>
    </span>
    {!compact && (
      <span className="logo-text">
        <strong>QURAN</strong>
        <small>COMPANION</small>
      </span>
    )}
  </span>
);

const NavItems = ({ t }) => (
  <nav className="nav" aria-label="Main">
    <NavLink to="/" end>{t("chat")}</NavLink>
    <NavLink to="/sources">{t("sources")}</NavLink>
    <NavLink to="/support">{t("donate")}</NavLink>
    <NavLink to="/tools">{t("premium")}</NavLink>
    <NavLink to="/report">{t("report")}</NavLink>
    <NavLink to="/about">{t("about")}</NavLink>
    <NavLink to="/privacy">{t("privacy")}</NavLink>
  </nav>
);

export const Layout = ({ children, settings, onSettingsChange, online }) => {
  const { language, textScale, voiceEnabled } = settings;
  const meta = useMemo(() => languageMeta(language), [language]);
  const t = (key) => translate(language, key);
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [recitation, setRecitation] = useState({ enabled: false, reason: "" });

  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = meta.direction;
  }, [language, meta.direction]);

  useEffect(() => {
    document.documentElement.style.setProperty("--text-scale", String(textScale));
  }, [textScale]);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    api
      .reciters()
      .then((response) => setRecitation({ enabled: Boolean(response?.enabled), reason: response?.reason || "" }))
      .catch(() => setRecitation({ enabled: false, reason: "" }));
  }, []);

  return (
    <div className="shell" dir={meta.direction}>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <Link to="/" className="brand" aria-label={t("appName")}>
          <Logo />
        </Link>
        <div className="topbar-controls">
          <label className="control">
            <span>{t("language")}</span>
            <select
              value={language}
              onChange={(event) => onSettingsChange({ language: event.target.value })}
              aria-label={t("language")}
            >
              {LANGUAGES.map((entry) => (
                <option key={entry.code} value={entry.code}>
                  {entry.nativeName}
                </option>
              ))}
            </select>
          </label>
          <label className="control range-control">
            <span>{t("textSize")}</span>
            <input
              type="range"
              min="0.9"
              max="1.5"
              step="0.05"
              value={textScale}
              onChange={(event) => onSettingsChange({ textScale: Number(event.target.value) })}
              aria-label={t("textSize")}
            />
          </label>
          <label className="control switch-control">
            <span>{t("voiceSettings")}</span>
            <input
              type="checkbox"
              checked={voiceEnabled}
              onChange={(event) => onSettingsChange({ voiceEnabled: event.target.checked })}
              aria-label={t("enableVoice")}
            />
          </label>
          <button
            type="button"
            className="menu-button"
            aria-expanded={menuOpen}
            aria-controls="mobile-nav"
            onClick={() => setMenuOpen((value) => !value)}
          >
            ☰
          </button>
        </div>
      </header>

      <div className={menuOpen ? "nav-panel open" : "nav-panel"} id="mobile-nav">
        <NavItems t={t} />
        {!isUiTranslated(language) && <p className="pending-note">{t("interfacePending")}</p>}
      </div>

      {!online && <div className="banner-warning" role="status">{t("offline")}</div>}

      <main className="main" id="main">
        {children}
      </main>

      <footer className="footer">
        <p>{t("tagline")}</p>
        <p className="footer-note">
          {recitation.enabled ? t("recitation") : t("recitationDisabled")}
        </p>
        <p className="footer-note">{t("isolationNote")}</p>
      </footer>
    </div>
  );
};
