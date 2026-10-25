import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

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
assert.match(browser, /clipboardMode:\s*"java"/);
assert.match(browser, /user\.home=\/files/);
assert.match(browser, /conceptlab\.api\.url=/);
assert.match(browser, /conceptlab\.launchToken=/);
assert.match(browser, /browser-ready\.txt/);
assert.match(browser, /Java_Main_browserApiFetch/);
assert.match(browser, /natives:\s*\{/);
assert.match(browser, /X-ConceptLab-Client/);
assert.match(browser, /endpoint\.origin !== window\.location\.origin/);
assert.match(browser, /endpoint\.pathname !== AI_BRIDGE_PATH/);
assert.match(api, /openai\/gpt-oss-20b/);
assert.match(api, /openai\/gpt-oss-120b/);
assert.match(api, /type:\s*"json_schema"/);
assert.match(api, /strict:\s*true/);
assert.match(build, /javac.*--release/si);
assert.match(build, /Manifest-Version: 1\.0/);
assert.doesNotMatch(build, /--main-class/);
assert.match(build, /appHome\.resolve\("browser-ready\.txt"\)/);
assert.match(build, /native String browserApiFetch/);
assert.match(build, /browserApiFetch\(GROQ_API_URL, reqBody\)/);
assert.match(build, /skip Java HttpClient initialization in browser/);

// Reset Demo must only replace the bundled demo file. It must never sweep all
// persisted .clab files because users may have created or imported their own sets.
assert.match(build, /Path demoTarget = setsDir\.resolve\("Newtonian_Mechanics\.clab"\)/);
assert.match(build, /Files\.deleteIfExists\(demoTarget\)/);
assert.doesNotMatch(build, /if \(resetRequested\)[\s\S]{0,700}newDirectoryStream\(setsDir, "\*\.clab"\)/);

assert.doesNotMatch(index + browser + api + build, /gsk_[A-Za-z0-9_-]{20,}/);
assert.doesNotMatch(index + browser, /GROQ_API_KEY/);

const jarPath = path.join(root, "ConceptLab-browser.jar");
if (fs.existsSync(jarPath)) {
  const jarBytes = fs.readFileSync(jarPath);
  assert.equal(jarBytes[0], 0x50, "browser JAR must start with ZIP/JAR signature P");
  assert.equal(jarBytes[1], 0x4b, "browser JAR must start with ZIP/JAR signature K");
  assert.equal(jarBytes.length > 100_000, true, "browser JAR is unexpectedly small");
  assert.equal(jarBytes.includes(Buffer.from("gsk_")), false, "browser JAR contains a Groq-style secret marker");
}

console.log("Browser static checks passed.");
