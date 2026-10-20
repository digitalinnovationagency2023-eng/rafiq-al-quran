// Public API. Everything here reads the local dataset; external calls happen only
// for a configured AI query interpreter and a configured payment provider, both
// of which are optional and disabled by default.
import crypto from "node:crypto";
import express from "express";
import { config } from "../config.js";
import { asyncHandler, HttpError } from "../middleware/errors.js";
import { rateLimit } from "../middleware/rateLimit.js";
import { answerQuestion, verifyPassages } from "../services/answer.js";
import { applyDonationEvent, createDonationCheckout, donationConfig, verifyStripeSignature } from "../services/donations.js";
import { LANGUAGES, NOTICES, FALLBACKS, isSupportedLanguage, languageMeta } from "../services/languages.js";
import { applyPremiumEvent, createPremiumCheckout, hasEntitlement, listEntitlements, listFeatures, premiumConfig, seedPremiumFeatures } from "../services/premium.js";
import { detectIntent, detectLanguage, interpretWithModel, parseReference } from "../services/query.js";
import { getVerseByKey, search, translationFor } from "../services/retrieval.js";
import { ensureSession } from "../services/session.js";
import { createAssistantRoutes } from "./assistantRoutes.js";

export const FEEDBACK_CATEGORIES = [
  { key: "missing_verse", label: "Potentially missing relevant verses" },
  { key: "incorrect_reference", label: "Incorrect verse reference" },
  { key: "translation_display", label: "Translation display issue" },
  { key: "search_language", label: "Search or language problem" },
  { key: "audio_recitation", label: "Audio or recitation problem" },
  { key: "accessibility", label: "Accessibility issue" },
  { key: "bug", label: "Bug" },
  { key: "suggestion", label: "Feature suggestion" },
];

const FEEDBACK_KEYS = new Set(FEEDBACK_CATEGORIES.map((category) => category.key));

const audit = (db, actor, action, detail) =>
  db.prepare("INSERT INTO audit_log (at, actor, action, detail_json) VALUES (?, ?, ?, ?)").run(
    new Date().toISOString(),
    actor || "public",
    action,
    detail ? JSON.stringify(detail).slice(0, 4000) : null
  );

const requireConversation = (db, conversationId, sessionId) => {
  const row = db.prepare("SELECT * FROM conversations WHERE id = ? AND session_id = ?").get(conversationId, sessionId);
  if (!row) throw new HttpError(404, "Conversation not found");
  return row;
};

const messagePayload = (row) => {
  if (!row) return null;
  let payload = null;
  try {
    payload = row.payload_json ? JSON.parse(row.payload_json) : null;
  } catch {
    payload = null;
  }
  return { id: row.id, role: row.role, text: row.text, language: row.language, createdAt: row.created_at, payload };
};

const aiBudgetAvailable = (db) => {
  if (config.ai.provider === "none" || !config.ai.apiKey || config.ai.maxMonthlyCalls <= 0) return true;
  const monthKey = `ai_calls:${new Date().toISOString().slice(0, 7)}`;
  const row = db.prepare("SELECT value_json FROM settings WHERE key = ?").get(monthKey);
  const used = row ? Number(JSON.parse(row.value_json)) : 0;
  return used < config.ai.maxMonthlyCalls;
};

const recordAiCall = (db) => {
  if (config.ai.provider === "none" || !config.ai.apiKey) return;
  const monthKey = `ai_calls:${new Date().toISOString().slice(0, 7)}`;
  const row = db.prepare("SELECT value_json FROM settings WHERE key = ?").get(monthKey);
  const used = row ? Number(JSON.parse(row.value_json)) : 0;
  db.prepare(
    `INSERT INTO settings (key, value_json, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET value_json = excluded.value_json, updated_at = excluded.updated_at`
  ).run(monthKey, JSON.stringify(used + 1), new Date().toISOString());
};

export const createPublicRoutes = ({ db, index }) => {
  const router = express.Router();
  seedPremiumFeatures(db);

  // Optional AI chat/OCR layer. Disabled (503) unless an OpenAI-compatible
  // provider is configured; it can only produce intent metadata, a neutral
  // small-talk reply, ASCII search terms and OCR text — never religious content.
  router.use("/assistant", createAssistantRoutes({ db }));

  router.get("/health", (req, res) => {
    res.json({
      ok: true,
      dataset: {
        verses: index.size,
        chapters: index.chapters.size,
        checksum: index.dataset?.checksum || null,
        importedAt: index.dataset?.imported_at || null,
      },
      translations: index.translationsByLanguage.size,
      features: {
        aiQueryInterpretation: config.ai.provider !== "none",
        voice: "browser",
        recitation: config.audio.enabled,
        donations: donationConfig().live,
        premium: premiumConfig().enabled,
      },
    });
  });

  router.get("/meta", (req, res) => {
    res.json({
      languages: LANGUAGES,
      translations: [...index.translationsByLanguage.values()].map((translation) => ({
        id: translation.id,
        language: translation.language,
        title: translation.title,
        author: translation.author,
        direction: translation.direction,
        licenseStatus: translation.licenseStatus,
      })),
      notices: NOTICES,
      fallbacks: FALLBACKS,
      feedbackCategories: FEEDBACK_CATEGORIES,
      flags: {
        donations: donationConfig(),
        premium: premiumConfig(),
        recitation: { enabled: config.audio.enabled },
        aiQueryInterpretation: config.ai.provider !== "none",
        whatsappConfigured: Boolean(config.feedback.whatsappEnabled && config.feedback.whatsappNumber),
      },
      privacy: {
        conversationRetentionDays: config.privacy.conversationRetentionDays,
        feedbackRetentionDays: config.feedback.retentionDays,
      },
      sourcesNotice:
        "Arabic text, chapter metadata and translations are imported from the configured dataset. Licence status for each translation is shown in the Sources page and must be verified by the operator before public launch.",
    });
  });

  router.get("/notices", (req, res) => {
    res.json({ notices: NOTICES, fallbacks: FALLBACKS, languages: LANGUAGES });
  });

  router.get("/sources", (req, res) => {
    const datasets = db
      .prepare("SELECT dataset, version, source_url AS sourceUrl, checksum, imported_at AS importedAt, notes FROM dataset_versions ORDER BY id DESC")
      .all();
    const translations = db
      .prepare(
        `SELECT id, provider, slug, title, author, publisher, language_code AS languageCode, language_name AS languageName,
                direction, license_status AS licenseStatus, license_note AS licenseNote, source_url AS sourceUrl,
                imported_at AS importedAt, checksum, enabled
         FROM translations ORDER BY language_code`
      )
      .all();
    const reciters = db
      .prepare("SELECT key, name, style, provider, rights_status AS rightsStatus, enabled, notes FROM audio_reciters ORDER BY name")
      .all();
    res.json({ datasets, translations, reciters, notices: NOTICES });
  });

  router.get("/chapters", (req, res) => {
    const chapters = db
      .prepare("SELECT id, name_arabic AS nameArabic, name_simple AS nameEnglish, name_english AS translatedName, verses_count AS versesCount, revelation_place AS revelationPlace FROM chapters ORDER BY id")
      .all();
    res.json({ chapters });
  });

  router.get("/chapters/:id", (req, res) => {
    const chapterId = Number(req.params.id);
    const chapter = index.chapters.get(chapterId);
    if (!chapter) throw new HttpError(404, "Chapter not found");
    const language = isSupportedLanguage(req.query.language) ? req.query.language : "en";
    const limit = Math.min(300, Math.max(1, Number(req.query.limit) || chapter.verses_count));
    const verses = index.verses
      .filter((verse) => verse.chapter_id === chapterId)
      .slice(0, limit)
      .map((verse) => {
        const translation = translationFor(index, language, verse.id);
        return {
          verseKey: verse.verse_key,
          verseNumber: verse.verse_number,
          arabic: verse.text_uthmani,
          translation: { text: translation.text, language: translation.language },
        };
      });
    res.json({
      chapter: {
        id: chapter.id,
        nameArabic: chapter.name_arabic,
        nameEnglish: chapter.name_simple,
        versesCount: chapter.verses_count,
        revelationPlace: chapter.revelation_place,
      },
      language,
      verses,
    });
  });

  router.get("/verses/:verseKey", (req, res) => {
    const verse = getVerseByKey(index, req.params.verseKey);
    if (!verse) throw new HttpError(404, "Verse not found");
    const language = isSupportedLanguage(req.query.language) ? req.query.language : "en";
    const translation = translationFor(index, language, verse.id);
    const meta = index.translationsByLanguage.get(translation.language);
    res.json({
      verseKey: verse.verse_key,
      chapter: {
        id: verse.chapter.id,
        nameArabic: verse.chapter.name_arabic,
        nameEnglish: verse.chapter.name_simple,
      },
      arabic: verse.text_uthmani,
      arabicPlain: verse.text_plain,
      translation: {
        text: translation.text,
        language: translation.language,
        direction: languageMeta(translation.language).direction,
        title: meta?.title || null,
        author: meta?.author || null,
        licenseStatus: meta?.licenseStatus || "unverified",
        fallbackToEnglish: translation.pendingEnglishFallback,
      },
      source: {
        dataset: index.dataset?.dataset,
        version: index.dataset?.version,
        sourceUrl: index.dataset?.source_url,
        checksum: index.dataset?.checksum,
      },
    });
  });

  // Ask ----------------------------------------------------------------
  router.post(
    "/ask",
    rateLimit({ max: config.rateLimit.askMax }),
    asyncHandler(async (req, res) => {
      const question = String(req.body?.question || "").trim();
      if (question.length < 2 || question.length > 1000) {
        throw new HttpError(400, "Question must be between 2 and 1000 characters");
      }

      const sessionId = req.sessionId || ensureSession(req, res);
      const requestedLanguage = isSupportedLanguage(req.body?.language) ? req.body.language : null;
      const language = requestedLanguage || detectLanguage(question, "en");

      let conversationId = req.body?.conversationId ? String(req.body.conversationId) : null;
      if (conversationId) {
        requireConversation(db, conversationId, sessionId);
      } else {
        conversationId = crypto.randomUUID();
        const now = new Date().toISOString();
        db.prepare("INSERT INTO conversations (id, session_id, language, created_at, updated_at) VALUES (?, ?, ?, ?, ?)").run(
          conversationId,
          sessionId,
          language,
          now,
          now
        );
      }

      const priorMessages = db
        .prepare("SELECT role, text, payload_json FROM messages WHERE conversation_id = ? ORDER BY created_at")
        .all(conversationId);
      const lastUserMessage = [...priorMessages].reverse().find((message) => message.role === "user");
      const shownVerseKeys = new Set();
      for (const message of priorMessages) {
        if (message.role !== "assistant" || !message.payload_json) continue;
        try {
          const payload = JSON.parse(message.payload_json);
          for (const passage of payload.passages || []) shownVerseKeys.add(passage.verseKey);
        } catch {
          // ignore malformed history rows
        }
      }

      const intent = detectIntent(question);
      const reference = parseReference(question, index.chapters);

      // Optional model assistance: search terms only, never religious text.
      let extraTerms = [];
      if (aiBudgetAvailable(db) && question.length >= 8) {
        const interpretation = await interpretWithModel({ question, language });
        if (interpretation?.searchTerms?.length) {
          extraTerms = interpretation.searchTerms;
          recordAiCall(db);
        }
      }

      const askingQuestion = intent === "more" && lastUserMessage ? lastUserMessage.text : question;
      const answer = answerQuestion(index, {
        question: askingQuestion,
        language,
        intent,
        reference: intent === "more" ? null : reference,
        extraTerms,
        excludeVerseKeys: intent === "more" ? [...shownVerseKeys] : [],
      });
      verifyPassages(index, answer.passages);

      const now = new Date().toISOString();
      db.prepare(
        "INSERT INTO messages (id, conversation_id, role, text, language, payload_json, created_at) VALUES (?, ?, 'user', ?, ?, NULL, ?)"
      ).run(crypto.randomUUID(), conversationId, question, language, now);
      db.prepare(
        "INSERT INTO messages (id, conversation_id, role, text, language, payload_json, created_at) VALUES (?, ?, 'assistant', ?, ?, ?, ?)"
      ).run(
        crypto.randomUUID(),
        conversationId,
        answer.kind === "fallback" ? answer.message : answer.message,
        language,
        JSON.stringify(answer),
        new Date().toISOString()
      );
      db.prepare("UPDATE conversations SET updated_at = ?, language = ? WHERE id = ?").run(now, language, conversationId);

      res.json({ conversationId, answer });
    })
  );

  // Conversations ------------------------------------------------------
  router.get("/conversations", (req, res) => {
    const sessionId = req.sessionId || ensureSession(req, res);
    const conversations = db
      .prepare(
        `SELECT c.id, c.language, c.created_at AS createdAt, c.updated_at AS updatedAt,
                (SELECT text FROM messages m WHERE m.conversation_id = c.id AND m.role = 'user' ORDER BY m.created_at LIMIT 1) AS firstQuestion,
                (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS messageCount
         FROM conversations c WHERE c.session_id = ? ORDER BY c.updated_at DESC LIMIT 100`
      )
      .all(sessionId);
    res.json({ conversations });
  });

  router.get("/conversations/:id", (req, res) => {
    const sessionId = req.sessionId || ensureSession(req, res);
    const conversation = requireConversation(db, req.params.id, sessionId);
    const messages = db
      .prepare("SELECT id, role, text, language, payload_json, created_at FROM messages WHERE conversation_id = ? ORDER BY created_at")
      .all(conversation.id)
      .map(messagePayload);
    res.json({
      conversation: { id: conversation.id, language: conversation.language, createdAt: conversation.created_at, updatedAt: conversation.updated_at },
      messages,
    });
  });

  router.delete("/conversations/:id", (req, res) => {
    const sessionId = req.sessionId || ensureSession(req, res);
    const conversation = requireConversation(db, req.params.id, sessionId);
    db.prepare("DELETE FROM messages WHERE conversation_id = ?").run(conversation.id);
    db.prepare("DELETE FROM conversations WHERE id = ?").run(conversation.id);
    audit(db, sessionId, "conversation.deleted", { conversationId: conversation.id });
    res.json({ ok: true });
  });

  // Feedback -----------------------------------------------------------
  router.post(
    "/feedback",
    rateLimit({ max: config.rateLimit.feedbackMax }),
    asyncHandler(async (req, res) => {
      const category = String(req.body?.category || "");
      if (!FEEDBACK_KEYS.has(category)) throw new HttpError(400, "Unknown feedback category");
      const description = String(req.body?.description || "").trim();
      if (description.length < 5 || description.length > 4000) {
        throw new HttpError(400, "Description must be between 5 and 4000 characters");
      }
      const consent = req.body?.consent === true;
      const diagnostics = consent && req.body?.diagnostics ? JSON.stringify(req.body.diagnostics).slice(0, 4000) : null;
      const contact = req.body?.contact ? String(req.body.contact).trim().slice(0, 200) : null;
      const id = crypto.randomUUID();
      db.prepare(
        `INSERT INTO feedback (id, category, description, language, verse_reference, contact, consent, diagnostics_json, status, severity, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'open', 'normal', ?)`
      ).run(
        id,
        category,
        description,
        isSupportedLanguage(req.body?.language) ? req.body.language : null,
        req.body?.verseReference ? String(req.body.verseReference).slice(0, 40) : null,
        contact,
        consent ? 1 : 0,
        diagnostics,
        new Date().toISOString()
      );
      audit(db, req.sessionId || "public", "feedback.created", { id, category });
      const whatsapp =
        config.feedback.whatsappEnabled && config.feedback.whatsappNumber
          ? {
              number: config.feedback.whatsappNumber,
              note: "You may also message us on WhatsApp yourself. Nothing is sent automatically.",
            }
          : null;
      res.status(201).json({
        id,
        status: "open",
        message:
          "Thank you. Your report has been recorded for review. It is not treated as a verified correction until our team checks it.",
        whatsapp,
      });
    })
  );

  router.get("/feedback/categories", (req, res) => {
    res.json({
      categories: FEEDBACK_CATEGORIES,
      whatsappConfigured: Boolean(config.feedback.whatsappEnabled && config.feedback.whatsappNumber),
      note: "Reports are reviewed by the team. Conversation text is only included when you explicitly consent.",
    });
  });

  // Recitation ---------------------------------------------------------
  router.get("/reciters", (req, res) => {
    const reciters = db
      .prepare("SELECT key, name, style, provider, rights_status AS rightsStatus FROM audio_reciters WHERE enabled = 1 AND rights_status = 'verified'")
      .all();
    res.json({
      enabled: Boolean(config.audio.enabled) && reciters.length > 0,
      reason: config.audio.enabled ? null : "Recitation playback is disabled until recording rights are verified.",
      reciters,
    });
  });

  router.get("/recitations/:verseKey", (req, res) => {
    if (!config.audio.enabled) {
      throw new HttpError(403, "Recitation playback is disabled until recording rights are verified");
    }
    const verse = getVerseByKey(index, req.params.verseKey);
    if (!verse) throw new HttpError(404, "Verse not found");
    const reciter = db
      .prepare("SELECT key, name, style, audio_url_template AS template FROM audio_reciters WHERE enabled = 1 AND rights_status = 'verified' LIMIT 1")
      .get();
    if (!reciter) throw new HttpError(403, "No verified recitation source is configured");
    const url = String(reciter.template || "")
      .replaceAll("{surah}", String(verse.chapter_id).padStart(3, "0"))
      .replaceAll("{ayah}", String(verse.verse_number).padStart(3, "0"))
      .replaceAll("{verseKey}", verse.verse_key);
    res.json({ verseKey: verse.verse_key, reciter: { key: reciter.key, name: reciter.name, style: reciter.style }, url });
  });

  // Donations ----------------------------------------------------------
  router.get("/donations/config", (req, res) => {
    res.json(donationConfig());
  });

  router.post(
    "/donations/checkout",
    rateLimit({ max: 10 }),
    asyncHandler(async (req, res) => {
      const sessionId = req.sessionId || ensureSession(req, res);
      const result = await createDonationCheckout({
        db,
        amountMinor: req.body?.amountMinor,
        currency: req.body?.currency || config.donations.currency,
        recurring: req.body?.recurring === true,
        email: req.body?.email,
        sessionId,
      });
      audit(db, sessionId, "donation.checkout", { donationId: result.donationId, live: result.live });
      res.status(result.live ? 201 : 200).json(result);
    })
  );

  router.post(
    "/webhooks/donations",
    express.raw({ type: "application/json" }),
    asyncHandler(async (req, res) => {
      const payload = Buffer.isBuffer(req.body) ? req.body.toString("utf8") : "";
      const valid = verifyStripeSignature({
        payload,
        header: req.headers["stripe-signature"],
        secret: config.donations.stripeWebhookSecret,
      });
      if (!valid) throw new HttpError(400, "Invalid webhook signature");
      const event = JSON.parse(payload);
      const result = applyDonationEvent({ db, event });
      audit(db, "provider", "donation.event", { type: event.type, result });
      res.json({ received: true, ...result });
    })
  );

  // Premium ------------------------------------------------------------
  router.get("/premium/features", (req, res) => {
    res.json({ ...premiumConfig(), features: listFeatures(db) });
  });

  router.get("/premium/entitlements", (req, res) => {
    const sessionId = req.sessionId || ensureSession(req, res);
    res.json({ entitlements: listEntitlements(db, sessionId) });
  });

  router.post(
    "/premium/checkout",
    rateLimit({ max: 10 }),
    asyncHandler(async (req, res) => {
      const sessionId = req.sessionId || ensureSession(req, res);
      const featureKey = String(req.body?.featureKey || "");
      if (!featureKey) throw new HttpError(400, "featureKey is required");
      const result = await createPremiumCheckout({ db, sessionId, featureKey });
      audit(db, sessionId, "premium.checkout", { featureKey });
      res.status(201).json(result);
    })
  );

  router.post(
    "/webhooks/premium",
    express.raw({ type: "application/json" }),
    asyncHandler(async (req, res) => {
      const payload = Buffer.isBuffer(req.body) ? req.body.toString("utf8") : "";
      const valid = verifyStripeSignature({
        payload,
        header: req.headers["stripe-signature"],
        secret: config.premium.webhookSecret,
      });
      if (!valid) throw new HttpError(400, "Invalid webhook signature");
      const event = JSON.parse(payload);
      const result = applyPremiumEvent({ db, event });
      audit(db, "provider", "premium.event", { type: event.type, result });
      res.json({ received: true, ...result });
    })
  );

  return router;
};
