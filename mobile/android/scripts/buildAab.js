#!/usr/bin/env node
/**
 * Builds the release AAB with the Android command-line tools and bundletool.
 *
 * Why not Gradle: this project is built with aapt2/javac/d8 directly. bundletool
 * can bundle a proto-format base module produced by aapt2, so no Gradle is needed.
 * Run on a machine (or CI runner) with: JDK 17, Android SDK 34, `zip`, and
 * BUNDLETOOL_JAR pointing at the executable bundletool jar.
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), "android-sdk");
const buildTools = path.join(sdk, "build-tools", "34.0.0");
const androidJar = path.join(sdk, "platforms", "android-34", "android.jar");
const bundletool = process.env.BUNDLETOOL_JAR || path.join(root, "tools", "bundletool-all.jar");
const outDir = path.join(root, "build", "aab");
const apkOut = process.env.AAB_OUTPUT || path.join(root, "build", "rafiq-al-quran.aab");

const run = (label, cmd, args, opts = {}) => {
  console.log(`\n▶ ${label}: ${cmd} ${args.slice(0, 4).join(" ")} …`);
  const result = spawnSync(cmd, args, { stdio: "inherit", ...opts });
  if (result.status !== 0) {
    console.error(`✖ ${label} failed (${result.status})`);
    process.exit(result.status || 1);
  }
};

const walk = (dir, suffix, acc = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, suffix, acc);
    else if (entry.name.endsWith(suffix)) acc.push(full);
  }
  return acc;
};

for (const required of [androidJar, path.join(buildTools, "aapt2"), bundletool]) {
  if (!fs.existsSync(required)) {
    console.error(`Missing required file: ${required}`);
    process.exit(1);
  }
}
const isWin = process.platform === "win32";
const tool = (name) => path.join(buildTools, isWin ? `${name}.exe` : name);
const aapt2 = isWin ? path.join(buildTools, "aapt2.exe") : path.join(buildTools, "aapt2");

fs.rmSync(outDir, { recursive: true, force: true });
for (const dir of ["gen", "classes", "dex", "proto", "module/base"]) {
  fs.mkdirSync(path.join(outDir, dir), { recursive: true });
}

run("Compile resources", aapt2, ["compile", "--dir", path.join(root, "res"), "-o", path.join(outDir, "res.zip")]);
run("Link proto-format resources", aapt2, [
  "link",
  "-o", path.join(outDir, "proto.apk"),
  "-I", androidJar,
  "--manifest", path.join(root, "AndroidManifest.xml"),
  "-A", path.join(root, "app", "src", "main", "assets"),
  "--java", path.join(outDir, "gen"),
  "--auto-add-overlay",
  "--proto-format",
  "--min-sdk-version", "23",
  "--target-sdk-version", "34",
  path.join(outDir, "res.zip"),
]);

run("Compile Java", "javac", [
  "-encoding", "UTF-8", "-source", "8", "-target", "8",
  "-classpath", androidJar, "-Xlint:-options",
  "-d", path.join(outDir, "classes"),
  ...walk(path.join(root, "app", "src", "main", "java"), ".java"),
  ...walk(path.join(outDir, "gen"), ".java"),
]);

run("Dex", "java", [
  "-cp", path.join(buildTools, "lib", "d8.jar"),
  "com.android.tools.r8.D8",
  "--lib", androidJar, "--min-api", "23",
  "--output", path.join(outDir, "dex"),
  ...walk(path.join(outDir, "classes"), ".class"),
]);

// Unpack the proto APK and arrange it as a base module.
const protoDir = path.join(outDir, "proto");
if (isWin) {
  fs.copyFileSync(path.join(outDir, "proto.apk"), path.join(outDir, "proto.zip"));
  run("Unpack proto apk", "powershell", ["-NoProfile", "-Command",
    `Expand-Archive -Force -Path '${path.join(outDir, "proto.zip")}' -DestinationPath '${protoDir}'`]);
} else {
  run("Unpack proto apk", "unzip", ["-q", "-o", path.join(outDir, "proto.apk"), "-d", protoDir]);
}

const base = path.join(outDir, "module", "base");
fs.mkdirSync(path.join(base, "manifest"), { recursive: true });
fs.mkdirSync(path.join(base, "dex"), { recursive: true });
const has = (name) => fs.existsSync(path.join(protoDir, name));
if (has("AndroidManifest.xml")) fs.copyFileSync(path.join(protoDir, "AndroidManifest.xml"), path.join(base, "manifest", "AndroidManifest.xml"));
if (has("resources.pb")) fs.copyFileSync(path.join(protoDir, "resources.pb"), path.join(base, "resources.pb"));
fs.copyFileSync(path.join(outDir, "dex", "classes.dex"), path.join(base, "dex", "classes.dex"));
for (const dir of ["res", "assets"]) {
  if (has(dir)) fs.cpSync(path.join(protoDir, dir), path.join(base, dir), { recursive: true });
}

const moduleZip = path.join(outDir, "base.zip");
if (isWin) {
  run("Zip base module", "powershell", ["-NoProfile", "-Command",
    `Compress-Archive -Force -Path '${path.join(base, "*")}' -DestinationPath '${moduleZip}'`]);
} else {
  run("Zip base module", "zip", ["-q", "-r", moduleZip, "."], { cwd: base });
}

run("bundletool build-bundle", "java", ["-jar", bundletool, "build-bundle", "--modules=" + moduleZip, "--output=" + apkOut]);
run("bundletool validate", "java", ["-jar", bundletool, "validate", "--bundle=" + apkOut]);
console.log(`\n✔ AAB ready: ${apkOut} (${(fs.statSync(apkOut).size / 1024 / 1024).toFixed(2)} MB)`);
void tool;
