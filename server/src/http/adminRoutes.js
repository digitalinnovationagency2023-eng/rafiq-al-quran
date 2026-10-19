// Operator endpoints. They are disabled entirely when ADMIN_TOKEN is unset, and
// they never expose payment secrets, raw card data or the canonical Quran text
// through an editable content form.
import crypto from "node:crypto";
import express from "express";
import { config } from "../config.js";
import { asyncHandler, HttpError } from "../middleware/errors.js";
import { applyPremiumEvent } from "../services/premium.js";

const audit = (db, actor, action, detail) =>
  db.prepare("INSERT INTO audit_log (at, actor, action, detail_json) VALUES (?, ?, ?, ?)").run(
    new Date().toISOString(),
    actor || "admin",
    action,
    detail ? JSON.stringify(detail).slice(0, 4000) : null
  );

const timingSafeEqual = (a, b) => {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  if (left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
};

export const adminAuth = (req, res, next) => {
  if (!config.admin.token) {
    return res.status(503).json({ error: "Administration is not configured (ADMIN_TOKEN is unset)" });
  }
  const provided = req.headers["x-admin-token"] || req.query.adminToken;
  if (!provided || !timingSafeEqual(provided, config.admin.token)) {
    return res.status(401).json({ error: "Administrator authentication required" });
  }
  req.admin = true;
  next();
};

export const createAdminRoutes = ({ db, index }) => {
  const router = express.Router();
  router.use(adminAuth);

  router.get("/summary", (req, res) => {
    const count = (sql, ...params) => db.prepare(sql).get(...params)?.n || 0;
    res.json({
      generatedAt: new Date().toISOString(),
      dataset: index.dataset,
      counts: {
        chapters: count("SELECT COUNT(*) AS n FROM chapters"),
        verses: count("SELECT COUNT(*) AS n FROM verses"),
        translations: count("SELECT COUNT(*) AS n FROM translations"),
        conversations: count("SELECT COUNT(*) AS n FROM conversations"),
        messages: count("SELECT COUNT(*) AS n FROM messages"),
        feedbackOpen: count("SELECT COUNT(*) AS n FROM feedback WHERE status = 'open'"),
        donationsPaid: count("SELECT COUNT(*) AS n FROM donations WHERE status = 'paid'"),
        donationsTotalMinor: count("SELECT COALESCE(SUM(amount_minor),0) AS n FROM donations WHERE status = 'paid'"),
      },
      flags: {
        donationsLive: Boolean(config.donations.enabled && config.donations.complianceApproved),
        premiumEnabled: config.premium.enabled,
        recitationEnabled: config.audio.enabled,
        aiConfigured: config.ai.provider !== "none",
      },
    });
  });

  router.get("/datasets", (req, res) => {
    res.json({
      datasets: db
        .prepare("SELECT id, dataset, version, source_url AS sourceUrl, checksum, imported_at AS importedAt, validated_at AS validatedAt, validation_ok AS validationOk, verse_count AS verseCount, chapter_count AS chapterCount, notes FROM dataset_versions ORDER BY id DESC")
        .all(),
    });
  });

  router.get("/feedback", (req, res) => {
    const status = req.query.status ? String(req.query.status) : null;
    const rows = status
      ? db.prepare("SELECT * FROM feedback WHERE status = ? ORDER BY created_at DESC LIMIT 200").all(status)
      : db.prepare("SELECT * FROM feedback ORDER BY created_at DESC LIMIT 200").all();
    res.json({
      feedback: rows.map((row) => ({
        id: row.id,
        category: row.category,
        description: row.description,
        language: row.language,
        verseReference: row.verse_reference,
        contact: row.contact,
        consent: Boolean(row.consent),
        diagnostics: row.diagnostics_json ? JSON.parse(row.diagnostics_json) : null,
        status: row.status,
        severity: row.severity,
        createdAt: row.created_at,
        resolvedAt: row.resolved_at,
        resolutionNote: row.resolution_note,
      })),
    });
  });

  router.patch(
    "/feedback/:id",
    asyncHandler(async (req, res) => {
      const allowedStatus = ["open", "triaged", "in_review", "resolved", "rejected"];
      const allowedSeverity = ["low", "normal", "high", "critical"];
      const row = db.prepare("SELECT id FROM feedback WHERE id = ?").get(req.params.id);
      if (!row) throw new HttpError(404, "Feedback not found");
      const status = req.body?.status;
      const severity = req.body?.severity;
      if (status && !allowedStatus.includes(status)) throw new HttpError(400, "Invalid status");
      if (severity && !allowedSeverity.includes(severity)) throw new HttpError(400, "Invalid severity");
      const resolutionNote = req.body?.resolutionNote ? String(req.body.resolutionNote).slice(0, 2000) : null;
      db.prepare(
        `UPDATE feedback SET status = COALESCE(?, status), severity = COALESCE(?, severity),
           resolution_note = COALESCE(?, resolution_note), resolved_at = CASE WHEN ? IN ('resolved','rejected') THEN ? ELSE resolved_at END
         WHERE id = ?`
      ).run(status || null, severity || null, resolutionNote, status || null, new Date().toISOString(), req.params.id);
      audit(db, "admin", "feedback.updated", { id: req.params.id, status, severity });
      res.json({ ok: true });
    })
  );

  router.get("/translations", (req, res) => {
    res.json({
      translations: db
        .prepare("SELECT id, title, author, publisher, language_code AS languageCode, language_name AS languageName, direction, license_status AS licenseStatus, license_note AS licenseNote, enabled, checksum, source_url AS sourceUrl FROM translations ORDER BY language_code")
        .all(),
    });
  });

  router.patch(
    "/translations/:id",
    asyncHandler(async (req, res) => {
      const row = db.prepare("SELECT id FROM translations WHERE id = ?").get(Number(req.params.id));
      if (!row) throw new HttpError(404, "Translation not found");
      const status = req.body?.licenseStatus;
      if (status && !["verified", "unverified", "blocked"].includes(status)) throw new HttpError(400, "Invalid licence status");
      const enabled = req.body?.enabled === undefined ? null : req.body.enabled ? 1 : 0;
      const note = req.body?.licenseNote ? String(req.body.licenseNote).slice(0, 2000) : null;
      db.prepare(
        `UPDATE translations SET license_status = COALESCE(?, license_status), enabled = COALESCE(?, enabled), license_note = COALESCE(?, license_note) WHERE id = ?`
      ).run(status || null, enabled, note, Number(req.params.id));
      audit(db, "admin", "translation.updated", { id: Number(req.params.id), status, enabled });
      res.json({ ok: true });
    })
  );

  router.get("/reciters", (req, res) => {
    res.json({ reciters: db.prepare("SELECT id, key, name, style, provider, audio_url_template AS template, rights_status AS rightsStatus, enabled, notes FROM audio_reciters ORDER BY name").all() });
  });

  router.post(
    "/reciters",
    asyncHandler(async (req, res) => {
      const key = String(req.body?.key || "").trim();
      const name = String(req.body?.name || "").trim();
      if (!key || !name) throw new HttpError(400, "key and name are required");
      db.prepare(
        `INSERT INTO audio_reciters (key, name, style, provider, audio_url_template, rights_status, enabled, notes)
         VALUES (?, ?, ?, ?, ?, 'unverified', 0, ?)`
      ).run(key, name, req.body?.style || null, req.body?.provider || null, req.body?.template || null, req.body?.notes || null);
      audit(db, "admin", "reciter.created", { key });
      res.status(201).json({ ok: true });
    })
  );

  router.patch(
    "/reciters/:id",
    asyncHandler(async (req, res) => {
      const row = db.prepare("SELECT id FROM audio_reciters WHERE id = ?").get(Number(req.params.id));
      if (!row) throw new HttpError(404, "Reciter not found");
      const rights = req.body?.rightsStatus;
      if (rights && !["verified", "unverified", "blocked"].includes(rights)) throw new HttpError(400, "Invalid rights status");
      db.prepare(
        `UPDATE audio_reciters SET rights_status = COALESCE(?, rights_status), enabled = COALESCE(?, enabled),
           audio_url_template = COALESCE(?, audio_url_template), notes = COALESCE(?, notes) WHERE id = ?`
      ).run(
        rights || null,
        req.body?.enabled === undefined ? null : req.body.enabled ? 1 : 0,
        req.body?.template || null,
        req.body?.notes || null,
        Number(req.params.id)
      );
      audit(db, "admin", "reciter.updated", { id: Number(req.params.id), rights });
      res.json({ ok: true });
    })
  );

  router.get("/premium/features", (req, res) => {
    res.json({
      enabled: config.premium.enabled,
      features: db.prepare("SELECT key, name, description, enabled, price_minor AS priceMinor, currency, billing FROM premium_features ORDER BY sort_order").all(),
    });
  });

  router.patch(
    "/premium/features/:key",
    asyncHandler(async (req, res) => {
      const row = db.prepare("SELECT key FROM premium_features WHERE key = ?").get(req.params.key);
      if (!row) throw new HttpError(404, "Feature not found");
      db.prepare("UPDATE premium_features SET enabled = ? WHERE key = ?").run(req.body?.enabled ? 1 : 0, req.params.key);
      audit(db, "admin", "premium.feature.updated", { key: req.params.key, enabled: Boolean(req.body?.enabled) });
      res.json({ ok: true });
    })
  );

  // Manual entitlement repair for support (records the audit trail; does not
  // imply a payment was received).
  router.post(
    "/premium/grant",
    asyncHandler(async (req, res) => {
      const sessionId = String(req.body?.sessionId || "").trim();
      const featureKey = String(req.body?.featureKey || "").trim();
      if (!sessionId || !featureKey) throw new HttpError(400, "sessionId and featureKey are required");
      db.prepare(
        `INSERT INTO entitlements (session_id, feature_key, status, provider_ref, created_at)
         VALUES (?, ?, 'active', 'admin-grant', ?)
         ON CONFLICT(session_id, feature_key) DO UPDATE SET status = 'active', provider_ref = 'admin-grant'`
      ).run(sessionId, featureKey, new Date().toISOString());
      audit(db, "admin", "premium.granted", { sessionId, featureKey });
      res.json({ ok: true });
    })
  );

  router.get("/donations", (req, res) => {
    res.json({
      donations: db
        .prepare("SELECT id, amount_minor AS amountMinor, currency, status, provider, provider_ref AS providerRef, recurring, created_at AS createdAt, updated_at AS updatedAt FROM donations ORDER BY created_at DESC LIMIT 200")
        .all(),
    });
  });

  router.post(
    "/donations/:id/refund",
    asyncHandler(async (req, res) => {
      const row = db.prepare("SELECT id, status FROM donations WHERE id = ?").get(req.params.id);
      if (!row) throw new HttpError(404, "Donation not found");
      db.prepare("UPDATE donations SET status = 'refunded', updated_at = ? WHERE id = ?").run(new Date().toISOString(), req.params.id);
      audit(db, "admin", "donation.refunded", { id: req.params.id, note: req.body?.note || null });
      res.json({ ok: true });
    })
  );

  router.get("/audit", (req, res) => {
    const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 100));
    res.json({
      entries: db
        .prepare("SELECT id, at, actor, action, detail_json AS detail FROM audit_log ORDER BY id DESC LIMIT ?")
        .all(limit)
        .map((row) => ({ ...row, detail: row.detail ? JSON.parse(row.detail) : null })),
    });
  });

  router.post(
    "/purge-conversations",
    asyncHandler(async (req, res) => {
      const days = Math.max(1, Number(req.body?.olderThanDays) || config.privacy.conversationRetentionDays);
      const cutoff = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
      const ids = db.prepare("SELECT id FROM conversations WHERE updated_at < ?").all(cutoff).map((row) => row.id);
      for (const id of ids) {
        db.prepare("DELETE FROM messages WHERE conversation_id = ?").run(id);
        db.prepare("DELETE FROM conversations WHERE id = ?").run(id);
      }
      audit(db, "admin", "conversations.purged", { days, count: ids.length });
      res.json({ ok: true, purged: ids.length });
    })
  );

  router.get("/settings/:key", (req, res) => {
    const row = db.prepare("SELECT key, value_json AS value, updated_at AS updatedAt FROM settings WHERE key = ?").get(req.params.key);
    if (!row) throw new HttpError(404, "Setting not found");
    res.json({ ...row, value: JSON.parse(row.value) });
  });

  router.put(
    "/settings/:key",
    asyncHandler(async (req, res) => {
      const key = String(req.params.key);
      if (!/^[a-z0-9._-]{2,80}$/i.test(key)) throw new HttpError(400, "Invalid setting key");
      db.prepare(
        `INSERT INTO settings (key, value_json, updated_at) VALUES (?, ?, ?)
         ON CONFLICT(key) DO UPDATE SET value_json = excluded.value_json, updated_at = excluded.updated_at`
      ).run(key, JSON.stringify(req.body?.value ?? null), new Date().toISOString());
      audit(db, "admin", "setting.updated", { key });
      res.json({ ok: true });
    })
  );

  // Kept for completeness: provider events can be replayed by an operator when a
  // webhook was missed. The same signature rules apply at the public endpoint.
  router.post(
    "/premium/replay-event",
    asyncHandler(async (req, res) => {
      const result = applyPremiumEvent({ db, event: req.body?.event });
      audit(db, "admin", "premium.event.replayed", { result });
      res.json(result);
    })
  );

  void index;
  return router;
};
