// Provider router for the optional AI layer.
//
// A single OpenAI-compatible chat call with:
//  - model selection (fast model for most answers, deep model for reasoning cues),
//  - an optional fallback provider when the primary is rate-limited/failing,
//  - token/cost estimation for the budget guard.
//
// It never supplies Quran text; callers only send the verses they already have.
import { config } from "../config.js";

export const PROVIDER_ERROR_MESSAGE = "AI assistance is temporarily unavailable. Please try again.";
export const PROVIDER_TIMEOUT_MESSAGE = "AI assistance timed out. Please try again.";

const DEEP_CUES = [
  "alive", "dead", "death", "return", "will he", "will she", "why", "compare", "difference",
  "meaning", "mean", "explain", "reason", "logic", "contradiction", "alive or dead",
  "afterlife", "hereafter", "predestination", "free will", "soul"
];

export const providerConfigured = () =>
  config.ai.provider === "openai-compatible" &&
  Boolean(config.ai.baseUrl) &&
  Boolean(config.ai.apiKey) &&
  Boolean(config.ai.model);

export const isDeepQuestion = (question) => {
  const q = String(question || "").toLowerCase();
  return DEEP_CUES.some((cue) => q.includes(cue));
};

export const pickModel = (question) => (isDeepQuestion(question) ? config.ai.deepModel : config.ai.model);

const timeoutMs = () =>
  Number.isFinite(config.ai.timeoutMs) && config.ai.timeoutMs > 0 ? config.ai.timeoutMs : 8000;

const providers = () => {
  const list = [{
    baseUrl: config.ai.baseUrl,
    apiKey: config.ai.apiKey,
    model: config.ai.model,
  }];
  if (config.ai.fallbackBaseUrl && config.ai.fallbackApiKey && config.ai.fallbackModel) {
    list.push({
      baseUrl: config.ai.fallbackBaseUrl,
      apiKey: config.ai.fallbackApiKey,
      model: config.ai.fallbackModel,
    });
  }
  return list;
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Calls an OpenAI-compatible /chat/completions endpoint. Returns
 * { content, usage, provider, model }. Retries the fallback provider on
 * 429/5xx/timeout/network failures; throws the last mapped error otherwise.
 */
export const chatCompletion = async ({ messages, model, maxTokens = 900, temperature = 0 }) => {
  let lastError = null;
  const list = providers();
  for (let index = 0; index < list.length; index++) {
    const provider = list[index];
    const chosenModel = model || provider.model;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs());
    try {
      const response = await fetch(provider.baseUrl.replace(/\/+$/, "") + "/chat/completions", {
        method: "POST",
        signal: controller.signal,
        headers: { "content-type": "application/json", authorization: "Bearer " + provider.apiKey },
        body: JSON.stringify({ model: chosenModel, temperature, max_tokens: maxTokens, messages }),
      });
      if (!response.ok) {
        const retryable = response.status === 429 || response.status >= 500;
        console.warn("[provider] " + (index === 0 ? "primary" : "fallback") + " responded " + response.status);
        lastError = response.status === 429
          ? { status: 429, message: PROVIDER_ERROR_MESSAGE }
          : { status: 502, message: PROVIDER_ERROR_MESSAGE };
        if (retryable && index < list.length - 1) continue;
        throw Object.assign(new Error(PROVIDER_ERROR_MESSAGE), lastError);
      }
      const payload = await response.json().catch(() => null);
      const raw = payload && payload.choices && payload.choices[0] && payload.choices[0].message
        ? payload.choices[0].message.content
        : null;
      const content = Array.isArray(raw)
        ? raw.map((part) => (typeof part === "string" ? part : (part && part.text) || "")).join("")
        : raw;
      if (typeof content !== "string" || !content.trim()) {
        lastError = { status: 502, message: PROVIDER_ERROR_MESSAGE };
        if (index < list.length - 1) continue;
        throw Object.assign(new Error(PROVIDER_ERROR_MESSAGE), lastError);
      }
      return { content, usage: (payload && payload.usage) || null, provider: index, model: chosenModel };
    } catch (error) {
      if (error && error.name === "AbortError") {
        console.warn("[provider] " + (index === 0 ? "primary" : "fallback") + " timed out");
        lastError = { status: 504, message: PROVIDER_TIMEOUT_MESSAGE };
        if (index < list.length - 1) { await sleep(250); continue; }
        throw Object.assign(new Error(PROVIDER_TIMEOUT_MESSAGE), lastError);
      }
      if (error && error.status) throw error;
      console.warn("[provider] request failed (" + ((error && error.name) || "Error") + ")");
      lastError = { status: 502, message: PROVIDER_ERROR_MESSAGE };
      if (index < list.length - 1) { await sleep(250); continue; }
      throw Object.assign(new Error(PROVIDER_ERROR_MESSAGE), lastError);
    } finally {
      clearTimeout(timer);
    }
  }
  throw Object.assign(new Error((lastError && lastError.message) || PROVIDER_ERROR_MESSAGE),
    lastError || { status: 502, message: PROVIDER_ERROR_MESSAGE });
};

// Conservative list-price estimates in USD micros (1e-6). Unknown models use
// the Flash band so the budget guard errs on the safe side.
const PRICES = {
  flash: { input: 0.30 / 1e6, output: 2.50 / 1e6 },
  pro: { input: 1.25 / 1e6, output: 10.0 / 1e6 },
};

export const estimateCostMicros = ({ usage, model, inputChars, outputChars }) => {
  const tier = /pro|sonnet|opus|o3|o4|deepseek-r/i.test(String(model || "")) ? "pro" : "flash";
  const price = PRICES[tier];
  const inputTokens = (usage && (usage.prompt_tokens || usage.input_tokens)) || Math.ceil((inputChars || 0) / 4);
  const outputTokens = (usage && (usage.completion_tokens || usage.output_tokens)) || Math.ceil((outputChars || 0) / 4);
  const usd = inputTokens * price.input + outputTokens * price.output;
  return Math.max(1, Math.round(usd * 1e6));
};
