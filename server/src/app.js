// Express application factory. Separated from the listener so tests can build an
// app against an in-memory database without opening a port.
import fs from "node:fs";
import path from "node:path";
import cookieParser from "cookie-parser";
import cors from "cors";
import express from "express";
import helmet from "helmet";
import { config, repoRoot } from "./config.js";
import { createAdminRoutes } from "./http/adminRoutes.js";
import { createPublicRoutes } from "./http/publicRoutes.js";
import { errorHandler, notFound } from "./middleware/errors.js";
import { ensureSession } from "./services/session.js";

export const createApp = ({ db, index }) => {
  const app = express();
  app.disable("x-powered-by");
  if (config.trustProxy) app.set("trust proxy", 1);

  app.use(
    helmet({
      contentSecurityPolicy: false, // the SPA sets its own policy in the HTML shell
      crossOriginEmbedderPolicy: false,
    })
  );
  app.use(
    cors({
      origin: (origin, callback) => {
        if (!origin) return callback(null, true); // same-origin or server-to-server
        if (config.corsOrigins.includes(origin) || config.corsOrigins.includes("*")) {
          return callback(null, true);
        }
        return callback(new Error("Origin is not allowed"));
      },
      credentials: true,
    })
  );
  app.use(cookieParser());
  app.use(express.json({ limit: "128kb" }));

  // Every request gets a signed anonymous session id; the cookie is HttpOnly.
  app.use((req, res, next) => {
    req.sessionId = ensureSession(req, res);
    next();
  });

  app.use("/api/admin", createAdminRoutes({ db, index }));
  app.use("/api", createPublicRoutes({ db, index }));

  // Serve the built single-page app when it exists (production single process).
  const webDist = path.join(repoRoot, "web", "dist");
  if (fs.existsSync(webDist)) {
    app.use(express.static(webDist, { index: false, maxAge: "1h" }));
    app.get("*", (req, res, next) => {
      if (req.path.startsWith("/api/")) return next();
      res.sendFile(path.join(webDist, "index.html"));
    });
  }

  app.use(notFound);
  app.use(errorHandler);
  return app;
};

/** Deletes conversations older than the configured retention window. */
export const purgeExpiredConversations = (db, days = config.privacy.conversationRetentionDays) => {
  const cutoff = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
  const ids = db.prepare("SELECT id FROM conversations WHERE updated_at < ?").all(cutoff).map((row) => row.id);
  for (const id of ids) {
    db.prepare("DELETE FROM messages WHERE conversation_id = ?").run(id);
    db.prepare("DELETE FROM conversations WHERE id = ?").run(id);
  }
  return ids.length;
};
