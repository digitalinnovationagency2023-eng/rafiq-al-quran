// The conversational screen. Retrieval happens server-side; this component only
// renders what the API verified and keeps the transcript for follow-ups.
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../lib/api.js";
import { translate } from "../lib/i18n.js";
import { FallbackBlock, NoticeBlock } from "../components/Notice.jsx";
import { PassageCard } from "../components/PassageCard.jsx";
import {
  createRecognition,
  recognitionAvailable,
  speak,
  spokenTextFor,
  stopSpeaking,
  synthesisAvailable,
} from "../lib/voice.js";

const STORAGE_KEY = "quran.conversationId";

const EXAMPLES = [
  { label: "Patience in hardship", question: "What does the Quran say about patience in hardship?" },
  { label: "Mercy and forgiveness", question: "What does the Quran say about mercy and forgiveness?" },
  { label: "Parents", question: "What does the Quran say about kindness to parents?" },
  { label: "Reference 2:255", question: "Show me 2:255" },
];

export const ChatPage = ({ settings }) => {
  const { language, voiceEnabled } = settings;
  const t = (key) => translate(language, key);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [conversationId, setConversationId] = useState(() => localStorage.getItem(STORAGE_KEY) || null);
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState("");
  const [speakingIndex, setSpeakingIndex] = useState(null);
  const [voiceHint, setVoiceHint] = useState("");
  const recognitionRef = useRef(null);
  const bottomRef = useRef(null);

  const canRecognise = useMemo(() => recognitionAvailable(), []);
  const canSpeak = useMemo(() => synthesisAvailable(), []);

  useEffect(() => {
    if (!conversationId) return;
    let cancelled = false;
    api
      .conversation(conversationId)
      .then((response) => {
        if (cancelled) return;
        const restored = response.messages.map((message) =>
          message.role === "assistant" && message.payload
            ? { role: "assistant", answer: message.payload }
            : { role: "user", text: message.text }
        );
        setMessages(restored);
      })
      .catch(() => {
        localStorage.removeItem(STORAGE_KEY);
        setConversationId(null);
      });
    return () => {
      cancelled = true;
    };
  }, []); // restore once on mount

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, sending]);

  useEffect(
    () => () => {
      stopSpeaking();
      recognitionRef.current?.stop?.();
    },
    []
  );

  const startNewConversation = () => {
    localStorage.removeItem(STORAGE_KEY);
    setConversationId(null);
    setMessages([]);
    setError("");
    setInput("");
    setInterim("");
  };

  const send = async (rawText) => {
    const question = String(rawText ?? input).trim();
    if (!question || sending) return;
    setError("");
    setSending(true);
    setMessages((current) => [...current, { role: "user", text: question }]);
    setInput("");
    try {
      const response = await api.ask({ question, language, conversationId });
      setConversationId(response.conversationId);
      localStorage.setItem(STORAGE_KEY, response.conversationId);
      setMessages((current) => [...current, { role: "assistant", answer: response.answer }]);
    } catch (requestError) {
      setError(requestError?.message || t("error"));
    } finally {
      setSending(false);
    }
  };

  const toggleListening = () => {
    if (!canRecognise) return;
    if (listening) {
      recognitionRef.current?.stop?.();
      setListening(false);
      return;
    }
    setVoiceHint("");
    const recognition = createRecognition({
      language,
      onStart: () => setListening(true),
      onEnd: () => setListening(false),
      onError: (code) => {
        setListening(false);
        setVoiceHint(
          code === "not-allowed" || code === "service-not-allowed"
            ? "Microphone permission was not granted. You can type instead."
            : "Voice input is unavailable right now. You can type instead."
        );
      },
      onResult: (transcript, isFinal) => {
        setInterim(isFinal ? "" : transcript);
        setInput(transcript);
      },
    });
    if (!recognition) return;
    recognitionRef.current = recognition;
    recognition.start();
  };

  const listenToAnswer = (index, answer) => {
    if (!canSpeak || !voiceEnabled) return;
    if (speakingIndex === index) {
      stopSpeaking();
      setSpeakingIndex(null);
      return;
    }
    setSpeakingIndex(index);
    speak({
      text: spokenTextFor(answer),
      language,
      onEnd: () => setSpeakingIndex(null),
    });
  };

  const renderAssistant = (message, index) => {
    const { answer } = message;
    if (!answer) return null;
    if (answer.kind === "fallback") {
      return <FallbackBlock key={index} answer={answer} t={t} />;
    }
    return (
      <div className="answer" key={index}>
        <p className="connective">{answer.message}</p>
        {answer.passages.map((passage) => (
          <PassageCard key={passage.verseKey} passage={passage} language={language} t={t} />
        ))}
        {answer.translationNotice && <p className="inline-note">{answer.translationNotice}</p>}
        {answer.suggestRefine && (
          <div className="followups">
            <button type="button" className="button ghost" onClick={() => send("more")} disabled={sending}>
              {t("moreVerses")}
            </button>
            {canSpeak && (
              <button
                type="button"
                className="button ghost"
                onClick={() => listenToAnswer(index, answer)}
                disabled={!voiceEnabled}
                title={voiceEnabled ? t("ttsDisclosure") : t("voiceDisabled")}
              >
                {speakingIndex === index ? t("stopSpeaking") : t("speakAnswer")}
              </button>
            )}
            <button type="button" className="button ghost" onClick={startNewConversation}>
              {t("newConversation")}
            </button>
          </div>
        )}
        {canSpeak && voiceEnabled && <p className="inline-note">{t("ttsDisclosure")}</p>}
        <NoticeBlock notice={answer.notice} t={t} />
      </div>
    );
  };

  return (
    <section className="chat" aria-label={t("chat")}>
      <div className="chat-toolbar">
        <h1>{t("appName")}</h1>
        <button type="button" className="button ghost" onClick={startNewConversation}>
          {t("newConversation")}
        </button>
      </div>

      <div className="messages" aria-live="polite">
        {messages.length === 0 && (
          <div className="empty-state">
            <h2>{t("emptyTitle")}</h2>
            <p>{t("emptyBody")}</p>
            <div className="examples">
              {EXAMPLES.map((example) => (
                <button key={example.label} type="button" className="button ghost" onClick={() => send(example.question)}>
                  {example.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message, index) =>
          message.role === "user" ? (
            <div className="user-message" key={index}>
              <p>{message.text}</p>
            </div>
          ) : (
            <div className="assistant-message" key={index}>
              {renderAssistant(message, index)}
            </div>
          )
        )}

        {sending && (
          <div className="assistant-message loading" role="status">
            <span className="dot" />
            <span className="dot" />
            <span className="dot" />
            <span>{t("searchRunning")}</span>
          </div>
        )}

        {error && (
          <div className="banner-error" role="alert">
            {error}
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="composer"
        onSubmit={(event) => {
          event.preventDefault();
          send();
        }}
      >
        {listening && <div className="recording-indicator" role="status">● {t("listening")}</div>}
        {interim && <div className="interim">{interim}</div>}
        <textarea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder={t("composerPlaceholder")}
          aria-label={t("composerPlaceholder")}
          rows={2}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
        />
        <div className="composer-actions">
          <button
            type="button"
            className={listening ? "button mic active" : "button mic"}
            onClick={toggleListening}
            disabled={!canRecognise || !voiceEnabled}
            aria-pressed={listening}
            title={!canRecognise ? "Speech recognition is not available in this browser" : !voiceEnabled ? t("voiceDisabled") : t("micIdle")}
          >
            {listening ? t("stopListening") : t("micIdle")}
          </button>
          <button type="submit" className="button primary" disabled={sending || !input.trim()}>
            {t("send")}
          </button>
        </div>
        {(voiceHint || (input && listening)) && <p className="inline-note">{voiceHint || t("transcriptEditable")}</p>}
        {canRecognise && voiceEnabled && <p className="inline-note">{t("sttDisclosure")}</p>}
      </form>
    </section>
  );
};
