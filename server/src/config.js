// Central configuration. Every external capability is off unless explicitly
// configured, so a fresh checkout never calls a paid service or publishes
// content whose licence has not been reviewed.
import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const here = path.dirname(fileURLToPath(import.meta.url));
export const serverRoot = path.resolve(here, "..");
export const repoRoot = path.resolve(serverRoot, "..");

dotenv.config({ path: path.join(repoRoot, ".env") });
dotenv.config({ path: path.join(serverRoot, ".env") });

const bool = (value, fallback = false) => {
  if (value === undefined || value === null || value === "") return fallback;
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
};
const int = (value, fallback) => {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const config = {
  env: process.env.NODE_ENV || "development",
  port: int(process.env.PORT, 8787),
  // The app keeps its own database. It never points at RQM Books data.
  dbPath: process.env.QURAN_DB_PATH || path.join(serverRoot, "data", "quran-app.db"),
  rawDataDir: process.env.QURAN_RAW_DIR || path.join(serverRoot, "data", "raw"),
  corsOrigins: (process.env.CORS_ORIGINS || "http://localhost:5173")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean),
  trustProxy: bool(process.env.TRUST_PROXY, false),
  logLevel: process.env.LOG_LEVEL || "info",

  session: {
    cookieName: "quran_session",
    secret: process.env.SESSION_SECRET || "",
    maxAgeDays: int(process.env.SESSION_MAX_AGE_DAYS, 30),
  },

  admin: {
    token: process.env.ADMIN_TOKEN || "",
  },

  rateLimit: {
    windowMs: int(process.env.RATE_LIMIT_WINDOW_MS, 60_000),
    max: int(process.env.RATE_LIMIT_MAX, 60),
    askMax: int(process.env.RATE_LIMIT_ASK_MAX, 20),
    feedbackMax: int(process.env.RATE_LIMIT_FEEDBACK_MAX, 10),
    // AI assistance is more expensive per call, so it gets its own allowance
    // over a 10-minute window (per client IP; see middleware/rateLimit.js).
    assistantMax: int(process.env.RATE_LIMIT_ASSISTANT_MAX, 20),
  },

  retrieval: {
    maxVerses: int(process.env.RETRIEVAL_MAX_VERSES, 8),
    minScore: Number(process.env.RETRIEVAL_MIN_SCORE || 1),
    // A question is only answered with verses when the best candidate clears
    // this confidence floor; otherwise the safe fallback is shown.
    confidenceFloor: Number(process.env.RETRIEVAL_CONFIDENCE_FLOOR || 0.42),
  },

  // Translation publishing policy:
  //  "label-unverified" (default) shows a translation with an explicit licence
  //  status label and a source panel. "verified-only" hides any translation
  //  whose licence has not been marked verified by an operator.
  licenses: {
    mode: process.env.TRANSLATION_LICENSE_MODE || "label-unverified",
  },

  audio: {
    // Authentic recitation. Off until a reciter's recording rights are verified
    // and a reciter row is enabled in the database.
    enabled: bool(process.env.RECITATION_ENABLED, false),
  },

  ai: {
    // Optional. Used only to interpret the question into search terms and an
    // intent. It never supplies religious text: every quoted verse and every
    // translation shown comes from the local, validated dataset.
    provider: String(process.env.AI_PROVIDER || "none").trim().toLowerCase(), // none | openai-compatible
    baseUrl: (process.env.AI_BASE_URL || "").trim(),
    apiKey: (process.env.AI_API_KEY || "").trim(),
    // The free Gemini tier works through its OpenAI-compatible endpoint, so a
    // model default keeps the feature usable with only AI_PROVIDER/AI_API_KEY.
    model: (process.env.AI_MODEL || "gemini-flash-lite-latest").trim(),
    // Optional second model for questions that need deeper reasoning.
    deepModel: (process.env.AI_DEEP_MODEL || process.env.AI_MODEL || "gemini-flash-lite-latest").trim(),
    // Optional second OpenAI-compatible provider used when the primary fails.
    fallbackBaseUrl: (process.env.AI_FALLBACK_BASE_URL || "").trim(),
    fallbackApiKey: (process.env.AI_FALLBACK_API_KEY || "").trim(),
    fallbackModel: (process.env.AI_FALLBACK_MODEL || "").trim(),
    timeoutMs: int(process.env.AI_TIMEOUT_MS, 8000),
    maxMonthlyCalls: Math.max(0, int(process.env.AI_MAX_MONTHLY_CALLS, 0)),
    // Cost guards: hard daily cap and a monthly estimated-spend ceiling. Both
    // fall back to deterministic answers instead of spending without a bound.
    dailyAnswerCap: Math.max(1, int(process.env.AI_DAILY_ANSWER_CAP, 300)),
    monthlyBudgetUsd: Math.max(0, Number(process.env.AI_MONTHLY_BUDGET_USD || 25)),
  },

  donations: {
    // Voluntary donations only; never required for the core experience.
    enabled: bool(process.env.DONATIONS_ENABLED, false),
    provider: process.env.DONATIONS_PROVIDER || "stripe",
    stripeSecretKey: process.env.STRIPE_SECRET_KEY || "",
    stripeWebhookSecret: process.env.STRIPE_WEBHOOK_SECRET || "",
    currency: process.env.DONATIONS_CURRENCY || "AED",
    minAmountMinor: int(process.env.DONATIONS_MIN_MINOR, 500),
    maxAmountMinor: int(process.env.DONATIONS_MAX_MINOR, 5_000_000),
    recurringEnabled: bool(process.env.DONATIONS_RECURRING_ENABLED, false),
    // Set true only after legal/fundraising review for the operating entity.
    complianceApproved: bool(process.env.DONATIONS_COMPLIANCE_APPROVED, false),
    successUrl: process.env.DONATIONS_SUCCESS_URL || "",
    cancelUrl: process.env.DONATIONS_CANCEL_URL || "",
    // Optional public donation page shown on the app's Support screen (leave
    // empty until the operator has reviewed Play's payments policy for links).
    url: (process.env.DONATION_URL || "").trim(),
  },

  premium: {
    enabled: bool(process.env.PREMIUM_ENABLED, false),
    provider: process.env.PREMIUM_PROVIDER || "stripe",
    stripeSecretKey: process.env.PREMIUM_STRIPE_SECRET_KEY || process.env.STRIPE_SECRET_KEY || "",
    webhookSecret: process.env.PREMIUM_WEBHOOK_SECRET || process.env.STRIPE_WEBHOOK_SECRET || "",
    currency: process.env.PREMIUM_CURRENCY || "AED",
  },

  feedback: {
    whatsappNumber: process.env.WHATSAPP_BUSINESS_NUMBER || "",
    whatsappEnabled: bool(process.env.WHATSAPP_ENABLED, false),
    retentionDays: int(process.env.FEEDBACK_RETENTION_DAYS, 365),
  },

  privacy: {
    conversationRetentionDays: int(process.env.CONVERSATION_RETENTION_DAYS, 90),
  },

  sources: {
    quranApiBase: process.env.QURAN_API_BASE || "https://api.quran.com/api/v4",
    // Optional local mirror directory with pre-downloaded JSON files.
    localMirror: process.env.QURAN_LOCAL_MIRROR || "",
  },
};

export const isProduction = config.env === "production";
