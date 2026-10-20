// Shared test helpers. Tests run against a throw-away copy of the imported
// database, so they never touch the development or production data file.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const serverRoot = path.resolve(here, "..");

export const prepareTestDb = () => {
  const source = path.join(serverRoot, "data", "quran-app.db");
  if (!fs.existsSync(source)) {
    throw new Error("Dataset missing. Run `npm run import:quran -w server` before running tests.");
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "quran-app-test-"));
  const target = path.join(directory, "test.db");
  fs.copyFileSync(source, target);
  for (const suffix of ["-wal", "-shm"]) {
    const companion = `${source}${suffix}`;
    if (fs.existsSync(companion)) fs.copyFileSync(companion, `${target}${suffix}`);
  }
  process.env.QURAN_DB_PATH = target;
  process.env.SESSION_SECRET = "test-session-secret";
  process.env.ADMIN_TOKEN = "test-admin-token";
  process.env.DONATIONS_ENABLED = "false";
  process.env.PREMIUM_ENABLED = "false";
  process.env.RECITATION_ENABLED = "false";
  // AI assistance is optional and must stay off during the shared suites; the
  // assistant tests configure the provider in-process against a local mock.
  process.env.AI_PROVIDER = "none";
  process.env.AI_BASE_URL = "";
  process.env.AI_API_KEY = "";
  process.env.AI_MODEL = "";
  process.env.AI_TIMEOUT_MS = "8000";
  process.env.AI_MAX_MONTHLY_CALLS = "0";
  process.env.RATE_LIMIT_ASSISTANT_MAX = "20";
  return target;
};

export const startTestServer = async () => {
  const { getDb } = await import("../src/db.js");
  const { buildIndex } = await import("../src/services/retrieval.js");
  const { createApp } = await import("../src/app.js");
  const db = getDb();
  const index = buildIndex(db);
  const app = createApp({ db, index });
  const server = app.listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  const request = async (method, url, body, headers = {}) => {
    const response = await fetch(`${baseUrl}${url}`, {
      method,
      headers: {
        ...(body ? { "content-type": "application/json" } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: response.status, json, text, headers: response.headers };
  };

  const close = () =>
    new Promise((resolve) => {
      server.close(() => {
        db.close();
        resolve();
      });
    });

  return { baseUrl, request, close, db, index };
};
