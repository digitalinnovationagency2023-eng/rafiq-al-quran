#!/usr/bin/env node
/**
 * Verifies the packaged APK without a device: signature, manifest identity,
 * version, bundled dataset assets and the compiled app. Exits non-zero on any
 * missing requirement. Usage: node scripts/verifyApk.js [path-to-apk]
 *
 * When no path is given, the default is dist/rafiq-al-quran-<versionName>.apk
 * (versionName read from AndroidManifest.xml).
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const manifestPath = path.join(root, "AndroidManifest.xml");
const manifest = fs.readFileSync(manifestPath, "utf8");
const versionCode = (manifest.match(/android:versionCode="(\d+)"/) || [])[1] || "";
const versionName = (manifest.match(/android:versionName="([^"]+)"/) || [])[1] || "";
const defaultApk = path.join(root, "..", "..", "dist", `rafiq-al-quran-${versionName || "1.0.0"}.apk`);
const apk = path.resolve(process.argv[2] || defaultApk);
const sdkRoot = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), "android-sdk");
const buildTools = path.join(sdkRoot, "build-tools", "34.0.0");
const PACKAGE = "com.rqmbooks.rafiqalquran";

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

const aapt2 = path.join(buildTools, process.platform === "win32" ? "aapt2.exe" : "aapt2");
const badging = capture(aapt2, ["dump", "badging", apk]);
const badgingText = badging.output;
check(`package is ${PACKAGE}`, badgingText.includes(`package: name='${PACKAGE}'`));
check("launchable activity present", badgingText.includes(`launchable-activity: name='${PACKAGE}.MainActivity'`));
check("microphone permission declared", /uses-permission: name='android\.permission\.RECORD_AUDIO'/.test(badgingText));
check("internet permission declared", /uses-permission: name='android\.permission\.INTERNET'/.test(badgingText));
check("minimum Android 6 (sdk 23)", /sdkVersion:'23'/.test(badgingText));
check("targets Android 14 (sdk 34)", /targetSdkVersion:'34'/.test(badgingText));
check("versionCode matches AndroidManifest.xml", Boolean(versionCode) && badgingText.includes(`versionCode='${versionCode}'`), `manifest ${versionCode}`);
check("versionName matches AndroidManifest.xml", Boolean(versionName) && badgingText.includes(`versionName='${versionName}'`), `manifest ${versionName}`);

const listing = capture("jar", ["tf", apk]).output.split(/\r?\n/);
check("classes.dex bundled", listing.includes("classes.dex"));
for (const asset of ["assets/quran.tsv", "assets/translations.tsv", "assets/chapters.tsv", "assets/translation-meta.tsv", "assets/lexicon.tsv", "assets/provenance.tsv", "assets/quran-meta.tsv"]) {
  check(`bundled ${asset}`, listing.includes(asset));
}
check("launcher icon packaged", listing.some((entry) => /^res\/drawable.*\/ic_launcher\.(png|xml)$/.test(entry)));

const appLabel = /application-label:'([^']+)'/.exec(badgingText);
check("app label is Rafiq Al-Quran", Boolean(appLabel && appLabel[1] === "Rafiq Al-Quran"), appLabel ? appLabel[1] : "missing");

const sizeMb = (fs.statSync(apk).size / (1024 * 1024)).toFixed(2);
console.log(`\nAPK: ${apk}`);
console.log(`Size: ${sizeMb} MB`);
if (failures > 0) {
  console.error(`\n${failures} APK verification failure(s)`);
  process.exit(1);
}
console.log("APK verification passed.");
