// Anonymous, signed sessions. The app deliberately does not require an account
// for the core experience: a random id in a signed cookie identifies a visitor so
// conversations and entitlements can be scoped without collecting personal data.
import crypto from "node:crypto";
import { config } from "../config.js";

const fallbackSecret = crypto.randomBytes(32).toString("hex");
let warnedAboutSecret = false;

const secret = () => {
  if (config.session.secret) return config.session.secret;
  if (!warnedAboutSecret) {
    warnedAboutSecret = true;
    // Loud in production, harmless in development: sessions simply do not
    // survive a restart when no secret is configured.
    console.warn(
      "[session] SESSION_SECRET is not set. Using an ephemeral secret; set SESSION_SECRET before deploying."
    );
  }
  return fallbackSecret;
};

const sign = (value) => crypto.createHmac("sha256", secret()).update(value).digest("base64url");

export const createSessionValue = () => {
  const id = crypto.randomUUID();
  return `${id}.${sign(id)}`;
};

export const readSession = (value) => {
  if (!value || typeof value !== "string") return null;
  const [id, signature] = value.split(".");
  if (!id || !signature) return null;
  const expected = sign(id);
  const provided = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);
  if (provided.length !== expectedBuffer.length) return null;
  if (!crypto.timingSafeEqual(provided, expectedBuffer)) return null;
  return id;
};

export const ensureSession = (req, res) => {
  const existing = readSession(req.cookies?.[config.session.cookieName]);
  if (existing) return existing;
  const value = createSessionValue();
  res.cookie(config.session.cookieName, value, {
    httpOnly: true,
    sameSite: "lax",
    secure: config.env === "production",
    maxAge: config.session.maxAgeDays * 24 * 60 * 60 * 1000,
  });
  return readSession(value);
};
