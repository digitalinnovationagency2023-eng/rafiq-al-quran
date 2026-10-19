// Thin API client. Cookies carry the anonymous session; no tokens are stored in
// localStorage for the public experience.
const BASE = import.meta.env.VITE_API_BASE || "/api";

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload || null;
  }
}

const request = async (path, { method = "GET", body, headers = {}, timeoutMs = 25_000 } = {}) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${BASE}${path}`, {
      method,
      credentials: "include",
      signal: controller.signal,
      headers: {
        ...(body ? { "content-type": "application/json" } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    let payload = null;
    try {
      payload = text ? JSON.parse(text) : null;
    } catch {
      payload = { error: text };
    }
    if (!response.ok) {
      throw new ApiError(payload?.error || `Request failed (${response.status})`, response.status, payload);
    }
    return payload;
  } catch (error) {
    if (error.name === "AbortError") throw new ApiError("The request timed out. Please try again.", 0, null);
    throw error;
  } finally {
    clearTimeout(timer);
  }
};

export const api = {
  meta: () => request("/meta"),
  notices: () => request("/notices"),
  sources: () => request("/sources"),
  chapters: () => request("/chapters"),
  chapter: (id, language) => request(`/chapters/${id}?language=${encodeURIComponent(language)}`),
  verse: (key, language) => request(`/verses/${encodeURIComponent(key)}?language=${encodeURIComponent(language)}`),
  ask: ({ question, language, conversationId }) =>
    request("/ask", { method: "POST", body: { question, language, conversationId } }),
  conversations: () => request("/conversations"),
  conversation: (id) => request(`/conversations/${id}`),
  deleteConversation: (id) => request(`/conversations/${id}`, { method: "DELETE" }),
  feedbackCategories: () => request("/feedback/categories"),
  feedback: (payload) => request("/feedback", { method: "POST", body: payload }),
  donationConfig: () => request("/donations/config"),
  donationCheckout: (payload) => request("/donations/checkout", { method: "POST", body: payload }),
  premiumFeatures: () => request("/premium/features"),
  premiumEntitlements: () => request("/premium/entitlements"),
  premiumCheckout: (featureKey) => request("/premium/checkout", { method: "POST", body: { featureKey } }),
  reciters: () => request("/reciters"),
  recitation: (verseKey) => request(`/recitations/${encodeURIComponent(verseKey)}`),
  admin: {
    summary: (token) => request("/admin/summary", { headers: { "x-admin-token": token } }),
    feedback: (token, status) =>
      request(`/admin/feedback${status ? `?status=${encodeURIComponent(status)}` : ""}`, {
        headers: { "x-admin-token": token },
      }),
    updateFeedback: (token, id, payload) =>
      request(`/admin/feedback/${id}`, { method: "PATCH", body: payload, headers: { "x-admin-token": token } }),
    translations: (token) => request("/admin/translations", { headers: { "x-admin-token": token } }),
    updateTranslation: (token, id, payload) =>
      request(`/admin/translations/${id}`, { method: "PATCH", body: payload, headers: { "x-admin-token": token } }),
    audit: (token) => request("/admin/audit?limit=100", { headers: { "x-admin-token": token } }),
  },
};
