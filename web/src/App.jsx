// Routes and shared preferences. Preferences stay on the device.
import { useEffect, useState } from "react";
import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout.jsx";
import { LANGUAGES } from "./lib/i18n.js";
import { AboutPage } from "./pages/AboutPage.jsx";
import { AdminPage } from "./pages/AdminPage.jsx";
import { ChatPage } from "./pages/ChatPage.jsx";
import { PrivacyPage } from "./pages/PrivacyPage.jsx";
import { ReportPage } from "./pages/ReportPage.jsx";
import { SourcesPage } from "./pages/SourcesPage.jsx";
import { SupportPage } from "./pages/SupportPage.jsx";
import { ToolsPage } from "./pages/ToolsPage.jsx";

const SETTINGS_KEY = "quran.settings";

const readSettings = () => {
  const stored = localStorage.getItem(SETTINGS_KEY);
  const defaults = { language: "en", textScale: 1, voiceEnabled: true };
  if (!stored) return defaults;
  try {
    const parsed = JSON.parse(stored);
    return {
      language: LANGUAGES.some((entry) => entry.code === parsed.language) ? parsed.language : "en",
      textScale: Number(parsed.textScale) >= 0.9 && Number(parsed.textScale) <= 1.5 ? Number(parsed.textScale) : 1,
      voiceEnabled: parsed.voiceEnabled !== false,
    };
  } catch {
    return defaults;
  }
};

export default function App() {
  const [settings, setSettings] = useState(readSettings);
  const [online, setOnline] = useState(navigator.onLine);

  useEffect(() => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  }, [settings]);

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  const updateSettings = (patch) => setSettings((current) => ({ ...current, ...patch }));

  return (
    <Layout settings={settings} onSettingsChange={updateSettings} online={online}>
      <Routes>
        <Route path="/" element={<ChatPage settings={settings} />} />
        <Route path="/sources" element={<SourcesPage settings={settings} />} />
        <Route path="/support" element={<SupportPage settings={settings} />} />
        <Route path="/tools" element={<ToolsPage settings={settings} />} />
        <Route path="/report" element={<ReportPage settings={settings} />} />
        <Route path="/about" element={<AboutPage settings={settings} />} />
        <Route path="/privacy" element={<PrivacyPage settings={settings} />} />
        <Route path="/admin" element={<AdminPage settings={settings} />} />
        <Route path="*" element={<ChatPage settings={settings} />} />
      </Routes>
    </Layout>
  );
}
