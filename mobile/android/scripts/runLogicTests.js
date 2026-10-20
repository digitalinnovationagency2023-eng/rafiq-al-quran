#!/usr/bin/env node
/** Compiles and runs the pure-logic JVM tests against the real bundled assets. */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const out = path.join(root, "build", "logic-tests");
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });

const walk = (dir, suffix, acc = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, suffix, acc);
    else if (entry.name.endsWith(suffix)) acc.push(full);
  }
  return acc;
};

const sources = walk(path.join(root, "app", "src", "main", "java", "com", "rqmbooks", "rafiqalquran", "logic"), ".java");
const run = (command, args) => {
  const result = spawnSync(command, args, { cwd: root, stdio: "inherit" });
  if (result.status !== 0) process.exit(result.status || 1);
};

run("javac", ["-encoding", "UTF-8", "-d", out, ...sources, path.join(root, "tools", "LogicTest.java")]);
run("java", ["-cp", out, "LogicTest", path.join(root, "app", "src", "main", "assets")]);
