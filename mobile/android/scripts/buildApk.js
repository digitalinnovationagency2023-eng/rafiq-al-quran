#!/usr/bin/env node
/**
 * Builds the signed release APK with the Android SDK command line tools only —
 * no Gradle, no Android Studio, no node modules.
 *
 * Requirements (documented in the repo): JDK 17+, Android SDK with
 * platforms;android-34 and build-tools;34.0.0.
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const build = path.join(root, "build");
const sdkRoot =
  process.env.ANDROID_HOME ||
  process.env.ANDROID_SDK_ROOT ||
  path.join(os.homedir(), "android-sdk");

const buildTools = path.join(sdkRoot, "build-tools", "34.0.0");
const androidJar = path.join(sdkRoot, "platforms", "android-34", "android.jar");
const exe = (name) => {
  const candidates = process.platform === "win32"
    ? [path.join(buildTools, `${name}.exe`), path.join(buildTools, `${name}.bat`)]
    : [path.join(buildTools, name)];
  for (const candidate of candidates) if (fs.existsSync(candidate)) return candidate;
  return candidates[0];
};

const run = (label, command, args, options = {}) => {
  process.stdout.write(`\n▶ ${label}\n`);
  const result = spawnSync(command, args, { cwd: root, stdio: "inherit", ...options });
  if (result.status !== 0) {
    console.error(`\n✖ ${label} failed (exit ${result.status})`);
    process.exit(result.status || 1);
  }
};

const walk = (directory, suffix, out = []) => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(full, suffix, out);
    else if (entry.name.endsWith(suffix)) out.push(full);
  }
  return out;
};

// 0. Preconditions ---------------------------------------------------------
for (const required of [androidJar, exe("aapt2"), exe("d8"), exe("zipalign"), exe("apksigner")]) {
  if (!fs.existsSync(required)) {
    console.error(`Missing Android SDK component: ${required}`);
    console.error(`Install with: sdkmanager "platforms;android-34" "build-tools;34.0.0"`);
    process.exit(1);
  }
}

// 1. Clean -----------------------------------------------------------------
fs.rmSync(build, { recursive: true, force: true });
for (const dir of ["res", "classes", "dex", "gen", "apk"]) fs.mkdirSync(path.join(build, dir), { recursive: true });

// 2. Resources -------------------------------------------------------------
const resFiles = walk(path.join(root, "res"), ".xml");
run("Compile resources (aapt2)", exe("aapt2"), [
  "compile",
  "--dir", path.join(root, "res"),
  "-o", path.join(build, "apk", "compiled.zip"),
]);

run("Link resources and manifest (aapt2)", exe("aapt2"), [
  "link",
  "-o", path.join(build, "apk", "unsigned.apk"),
  "-I", androidJar,
  "--manifest", path.join(root, "AndroidManifest.xml"),
  "-A", path.join(root, "app", "src", "main", "assets"),
  "--java", path.join(build, "gen"),
  "--auto-add-overlay",
  "--min-sdk-version", "24",
  "--target-sdk-version", "36",
  path.join(build, "apk", "compiled.zip"),
]);

// 3. Java sources ----------------------------------------------------------
const sources = walk(path.join(root, "app", "src", "main", "java"), ".java");
const generated = walk(path.join(build, "gen"), ".java");
run("Compile Java sources (javac)", "javac", [
  "-encoding", "UTF-8",
  "-source", "8",
  "-target", "8",
  "-classpath", androidJar,
  "-Xlint:-options",
  "-d", path.join(build, "classes"),
  ...sources,
  ...generated,
]);

// 4. Dex -------------------------------------------------------------------
const classes = walk(path.join(build, "classes"), ".class");
run("Convert bytecode to dex (d8)", "java", [
  "-cp", path.join(buildTools, "lib", "d8.jar"),
  "com.android.tools.r8.D8",
  "--lib", androidJar,
  "--min-api", "24",
  "--output", path.join(build, "dex"),
  ...classes,
]);

// 5. Package ---------------------------------------------------------------
fs.copyFileSync(path.join(build, "apk", "unsigned.apk"), path.join(build, "apk", "with-dex.apk"));
run("Add classes.dex to the APK (jar)", "jar", [
  "--update",
  "--file", path.join(build, "apk", "with-dex.apk"),
  "-C", path.join(build, "dex"), "classes.dex",
]);

run("Align the APK (zipalign)", exe("zipalign"), [
  "-f", "4",
  path.join(build, "apk", "with-dex.apk"),
  path.join(build, "apk", "aligned.apk"),
]);

// 6. Sign ------------------------------------------------------------------
// A stable release/upload key can be supplied through QURAN_KEYSTORE_PATH so
// rebuilding the APK does not change its signature (and so it matches the key
// used to sign the Play upload AAB). Without it a throwaway key is generated.
const keystore = path.join(build, "quran-release.keystore");
const storePass = process.env.QURAN_KEYSTORE_PASSWORD || "quran-companion";
const persistentKeystore = process.env.QURAN_KEYSTORE_PATH
  || path.join(os.homedir(), ".secrets", "quran-release.keystore");
if (persistentKeystore && fs.existsSync(persistentKeystore)) {
  fs.copyFileSync(persistentKeystore, keystore);
  console.log(`   Using the persistent signing key: ${persistentKeystore}`);
}
if (!fs.existsSync(keystore)) {
  console.log("   WARNING: generating a NEW signing key. This build will NOT match the Play upload key;");
  console.log("            keep quran-release.keystore in %USERPROFILE%\\.secrets or set QURAN_KEYSTORE_PATH.");
  run("Create a release keystore (keytool)", "keytool", [
    "-genkeypair",
    "-keystore", keystore,
    "-alias", "quran",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-storepass", storePass,
    "-keypass", storePass,
    "-dname", "CN=Quran Companion, OU=Mobile, O=Quran Companion, L=Dubai, C=AE",
  ]);
}

const apk = path.join(build, "quran-companion.apk");
const apksigner = path.join(buildTools, "lib", "apksigner.jar");
run("Sign the APK (apksigner)", "java", [
  "-jar", apksigner,
  "sign",
  "--ks", keystore,
  "--ks-pass", `pass:${storePass}`,
  "--key-pass", `pass:${storePass}`,
  "--out", apk,
  path.join(build, "apk", "aligned.apk"),
]);

run("Verify the signature (apksigner)", "java", ["-jar", apksigner, "verify", "--print-certs", apk]);
run("Inspect the package (aapt2)", exe("aapt2"), [
  "dump", "badging", apk,
]);

const size = (fs.statSync(apk).size / (1024 * 1024)).toFixed(2);
console.log(`\n✔ APK ready: ${apk} (${size} MB)`);
console.log(`   Resources compiled: ${resFiles.length} · Java sources: ${sources.length}`);
