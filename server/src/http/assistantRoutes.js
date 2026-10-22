// Public routes for the optional AI assistance layer. Mounted by the public
// router under /api/assistant, so it inherits the app-level helmet, CORS,
// cookie and JSON parsing middleware exactly like every other public route.
import express from "express";
import { config } from "../config.js";
import { asyncHandler, HttpError } from "../middleware/errors.js";
import { rateLimit } from "../middleware/rateLimit.js";
import {
  ASSISTANT_RATE_WINDOW_MS,
  BUDGET_EXHAUSTED_MESSAGE,
  NOT_ENABLED_MESSAGE,
  assistantBudgetAvailable,
  assistantStatus,
  isAssistantConfigured,
  recordAssistantCall,
  runAssistant,
  validateAssistantRequest,
} from "../services/assistant.js";
import {
  SUMMARY_NOT_ENABLED_MESSAGE,
  isSummaryConfigured,
  runAnswer,
  runSummary,
  validateAnswerRequest,
  validateSummaryRequest,
} from "../services/synthesis.js";

// The app is a native client and does not send cookies, so the assistant's
// allowance is keyed by client IP rather than by the anonymous session id.
const perIpKey = (req) => `assistant:${req.ip || "unknown"}`;

export const createAssistantRoutes = ({ db }) => {
  const router = express.Router();

  // Cheap discovery endpoint so the app can decide whether to offer the AI
  // layer at all. It never reveals the provider, model, URL or key.
  router.get("/status", (req, res) => {
    res.json({
      ...assistantStatus(),
      limits: {
        deviceDaily: 10,
        serverDaily: config.ai.dailyAnswerCap,
        monthlyBudgetUsd: config.ai.monthlyBudgetUsd,
      },
      donationUrl: config.donations.url || "",
    });
  });

  router.post(
    "/",
    rateLimit({ max: config.rateLimit.assistantMax, windowMs: ASSISTANT_RATE_WINDOW_MS, key: perIpKey }),
    asyncHandler(async (req, res) => {
      // Disabled is a deployment-wide state: answer 503 before reading the body
      // so the app gets a stable signal to fall back to its local rules. It is
      // an expected condition, not a server error, so it is not logged.
      if (!isAssistantConfigured()) {
        return res.status(503).json({ error: NOT_ENABLED_MESSAGE });
      }

      const request = validateAssistantRequest(req.body);
      if (!assistantBudgetAvailable(db)) throw new HttpError(429, BUDGET_EXHAUSTED_MESSAGE);

      const result = await runAssistant(request);
      recordAssistantCall(db);
      res.json({ enabled: true, ...result });
    })
  );

  // Grounded advisory answer: the model may only use the verses the app already
  // retrieved, and the server rejects any answer citing references outside that set.
  router.post(
    "/summary",
    rateLimit({
      max: config.rateLimit.assistantMax,
      windowMs: ASSISTANT_RATE_WINDOW_MS,
      key: (req) => "assistant-summary:" + (req.ip || "unknown"),
    }),
    asyncHandler(async (req, res) => {
      if (!isSummaryConfigured()) return res.status(503).json({ error: SUMMARY_NOT_ENABLED_MESSAGE });
      const request = validateSummaryRequest(req.body);
      if (!assistantBudgetAvailable(db)) throw new HttpError(429, BUDGET_EXHAUSTED_MESSAGE);
      const result = await runSummary({
        db,
        question: request.question,
        language: request.language,
        verseKeys: request.verseKeys,
        history: request.history,
      });
      recordAssistantCall(db);
      res.json({ enabled: true, ...result });
    })
  );

  // Deeper Quran-only reasoning answer: more supplied verses plus app index facts for
  // counts/lists. Same grounding rules as the summary: citations are whitelisted,
  // Arabic script is rejected, and nothing outside the supplied text is allowed.
  router.post(
    "/answer",
    rateLimit({
      max: config.rateLimit.assistantMax,
      windowMs: ASSISTANT_RATE_WINDOW_MS,
      key: (req) => "assistant-answer:" + (req.ip || "unknown"),
    }),
    asyncHandler(async (req, res) => {
      if (!isSummaryConfigured()) return res.status(503).json({ error: SUMMARY_NOT_ENABLED_MESSAGE });
      const request = validateAnswerRequest(req.body);
      if (!assistantBudgetAvailable(db)) throw new HttpError(429, BUDGET_EXHAUSTED_MESSAGE);
      const result = await runAnswer({ db, ...request });
      recordAssistantCall(db);
      res.json({ enabled: true, ...result });
    })
  );

  return router;
};
