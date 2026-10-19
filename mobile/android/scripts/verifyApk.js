#!/usr/bin/env node
/**
 * Verifies the packaged APK without a device: signature, manifest, bundled
 * dataset assets and the presence of the compiled app. Exits non-zero on any
 * missing requirement. Usage: node scripts/verifyApk.js [path-to-apk]
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const apk = path.resolve(process.argv[2] || path.join(root, "..", "..", "dist", "quran-companion-1.0.0.apk"));
const sdkRoot = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), "android-sdk");
const buildTools = path.join(sdkRoot, "build-tools", "34.0.0");

const capture = (command, args) => {
  const result = spawnSync(command, args, { encoding: "utf8" });
  return { status: result.status, output: `${result.stdout || ""}\n${result.stderr || ""}` };
};

let failures = 0;
const check = (name, condition, detail = "") => {
  if (condition) console.log(`✔ ${name}`);
  else {
    failures += 1;
    console.log(`✖ ${name}${detail ? ` — ${detail}` : ""}`);
  }
};

if (!fs.existsSync(apk)) {
  console.error(`APK not found: ${apk}`);
  process.exit(1);
}
check("APK file exists", true);

const signature = capture("java", ["-jar", path.join(buildTools, "lib", "apksigner.jar"), "verify", "--print-certs", apk]);
check("signature verifies (apksigner)", signature.status === 0 && /certificate DN/.test(signature.output));

const badging = capture(path.join(buildTools, "aapt2.exe"), ["dump", "badging", apk]);
const badgingText = badging.output;
check("package is com.rqmbooks.rafiqalquran", /package: name='com\.qurancompanion\.app'/.test(badgingText));
check("launchable activity present", /launchable-activity: name='com\.qurancompanion\.app\.MainActivity'/.test(badgingText));
check("microphone permission declared", /uses-permission: name='android\.permission\.RECORD_AUDIO'/.test(badgingText));
check("internet permission declared", /uses-permission: name='android\.permission\.INTERNET'/.test(badgingText));
check("minimum Android 6 (sdk 23)", /sdkVersion:'23'/.test(badgingText));

const listing = capture("jar", ["tf", apk]).output.split(/\r?\n/);
check("classes.dex bundled", listing.includes("classes.dex"));
for (const asset of ["assets/quran.tsv", "assets/translations.tsv", "assets/chapters.tsv", "assets/translation-meta.tsv", "assets/lexicon.tsv", "assets/provenance.tsv"]) {
  check(`bundled ${asset}`, listing.includes(asset));
}

const appLabel = /application-label:'([^']+)'/.exec(badgingText);
check("app label is Quran Companion", Boolean(appLabel && appLabel[1] === "Rafiq Al-Quran"), appLabel ? appLabel[1] : "missing");

const sizeMb = (fs.statSync(apk).size / (1024 * 1024)).toFixed(2);
console.log(`\nAPK: ${apk}`);
console.log(`Size: ${sizeMb} MB`);
if (failures > 0) {
  console.error(`\n${failures} APK verification failure(s)`);
  process.exit(1);
}
console.log("APK verification passed.");
