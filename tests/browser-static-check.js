import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const root = path.resolve(import.meta.dirname, "..");
const read = (name) => fs.readFileSync(path.join(root, name), "utf8");

const index = read("index.html");
const browser = read("browser.js");
const api = read("api/conceptlab/ai.js");
const build = read("browser/build-browser.py");

assert.match(index, /https:\/\/cjrtnc\.leaningtech\.com\/4\.3\/loader\.js/);
assert.match(index, /Browser edition/);
assert.match(index, /CheerpJ Community License/);
assert.match(browser, /version:\s*17/);
assert.match(browser, /user\.home=\/files/);
assert.match(browser, /conceptlab\.api\.url=/);
assert.match(api, /openai\/gpt-oss-20b/);
assert.match(api, /openai\/gpt-oss-120b/);
assert.match(api, /type:\s*"json_schema"/);
assert.match(api, /strict:\s*true/);
assert.match(build, /javac.*--release/si);
assert.doesNotMatch(index + browser + api + build, /gsk_[A-Za-z0-9_-]{20,}/);
assert.doesNotMatch(index + browser, /GROQ_API_KEY/);

const jarPath = path.join(root, "ConceptLab-browser.jar");
if (fs.existsSync(jarPath)) {
  const listing = execFileSync("jar", ["--list", "--file", jarPath], { encoding: "utf8" });
  assert.match(listing, /^Main\.class$/m);
  const jarBytes = fs.readFileSync(jarPath);
  assert.equal(jarBytes.includes(Buffer.from("gsk_")), false, "browser JAR contains a Groq-style secret marker");
}

console.log("Browser static checks passed.");
