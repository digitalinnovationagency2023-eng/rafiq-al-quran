// Small in-process rate limiter. Good enough for a single instance and for
// tests; documentation notes that a shared store is needed when scaling out.
import { config } from "../config.js";

const buckets = new Map();

export const rateLimit = ({ max = config.rateLimit.max, windowMs = config.rateLimit.windowMs } = {}) => (req, res, next) => {
  const key = `${req.ip || "unknown"}:${req.sessionId || "anon"}:${req.path}`;
  const now = Date.now();
  const bucket = buckets.get(key) || { count: 0, resetAt: now + windowMs };
  if (now > bucket.resetAt) {
    bucket.count = 0;
    bucket.resetAt = now + windowMs;
  }
  bucket.count += 1;
  buckets.set(key, bucket);
  if (bucket.count > max) {
    res.setHeader("retry-after", Math.ceil((bucket.resetAt - now) / 1000));
    return res.status(429).json({ error: "Too many requests. Please wait a moment and try again." });
  }
  next();
};

export const resetRateLimits = () => buckets.clear();
