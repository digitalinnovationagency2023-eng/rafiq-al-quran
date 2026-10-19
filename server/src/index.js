import { config, isProduction } from "./config.js";
import { createApp, purgeExpiredConversations } from "./app.js";
import { getDb } from "./db.js";
import { seedReciters } from "./services/seed.js";
import { buildIndex } from "./services/retrieval.js";
import { seedPremiumFeatures } from "./services/premium.js";

const db = getDb();
seedReciters(db);
seedPremiumFeatures(db);
const index = buildIndex(db);

if (index.size === 0) {
  console.error(
    "The Quran dataset is empty. Run `npm run import:quran` (workspace server) before starting the API."
  );
  process.exit(1);
}

const purged = purgeExpiredConversations(db);
if (purged) console.log(`[privacy] removed ${purged} conversation(s) past the retention window`);

const app = createApp({ db, index });
const server = app.listen(config.port, () => {
  console.log(`Quran app API listening on http://localhost:${config.port}`);
  console.log(`Dataset: ${index.size} verses, ${index.chapters.size} chapters, ${index.translationsByLanguage.size} translations`);
  if (isProduction) {
    if (!config.session.secret) console.warn("[warn] SESSION_SECRET is not set");
    if (!config.admin.token) console.warn("[warn] ADMIN_TOKEN is not set; /api/admin is disabled");
  }
});

const shutdown = (signal) => {
  console.log(`${signal} received, shutting down`);
  server.close(() => {
    db.close();
    process.exit(0);
  });
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
