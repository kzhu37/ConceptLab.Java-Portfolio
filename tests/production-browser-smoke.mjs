import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import { chromium } from "playwright";

const BASE_URL = (process.env.CONCEPTLAB_BASE_URL || "https://conceptlab-browser.vercel.app").replace(/\/$/, "");
const EXPECTED_COMMIT = process.env.GITHUB_SHA || "";
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const PRODUCTION_FILES = new Set([
  "Main.java",
  "EscapeUtil.java",
  "Flashcard.java",
  "LoadingScreenFacts.java",
  "Question.java",
  "ResourceLink.java",
  "ResourceType.java",
  "StudySet.java",
  "ConceptLabLogo.png",
  "ConceptLabLogo.svg",
  "ConceptLab-browser.jar",
  "browser.css",
  "browser.js",
  "index.html",
  "package.json",
  "vercel.json",
]);
const PRODUCTION_PREFIXES = ["api/", "browser/", "demo/"];

async function fetchWithRetry(path, options = {}, attempts = 60) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(`${BASE_URL}${path}`, { redirect: "follow", ...options });
      if (response.ok || (options.acceptStatuses || []).includes(response.status)) return response;
      lastError = new Error(`${path} returned HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(10_000);
  }
  throw lastError || new Error(`Could not fetch ${path}`);
}

function isProductionRelevant(file) {
  return PRODUCTION_FILES.has(file) || PRODUCTION_PREFIXES.some((prefix) => file.startsWith(prefix));
}

function changedFilesBetween(baseCommit, headCommit) {
  const output = execFileSync(
    "git",
    ["diff", "--name-only", `${baseCommit}..${headCommit}`],
    { encoding: "utf8" },
  ).trim();
  return output ? output.split(/\r?\n/).filter(Boolean) : [];
}

async function waitForCompatibleDeployment() {
  if (!EXPECTED_COMMIT) return;
  let lastSeen = "none";
  let lastRelevant = [];

  for (let attempt = 1; attempt <= 60; attempt += 1) {
    try {
      const response = await fetch(
        `${BASE_URL}/build-meta.json?expected=${encodeURIComponent(EXPECTED_COMMIT)}`,
        { cache: "no-store", redirect: "follow" },
      );
      if (response.ok) {
        const meta = await response.json();
        lastSeen = typeof meta?.gitCommit === "string" ? meta.gitCommit : "invalid metadata";
        if (lastSeen === EXPECTED_COMMIT) {
          console.log(`Production deployment is current at ${EXPECTED_COMMIT}.`);
          return;
        }

        if (/^[0-9a-f]{40}$/i.test(lastSeen)) {
          try {
            const changed = changedFilesBetween(lastSeen, EXPECTED_COMMIT);
            lastRelevant = changed.filter(isProductionRelevant);
            if (lastRelevant.length === 0) {
              console.log(
                `Production deployment ${lastSeen} is artifact-equivalent to ${EXPECTED_COMMIT}; `
                + `${changed.length} changed file(s) are outside the production surface.`,
              );
              return;
            }
          } catch (error) {
            lastRelevant = [`comparison unavailable: ${error instanceof Error ? error.message : String(error)}`];
          }
        }
      } else {
        lastSeen = `HTTP ${response.status}`;
      }
    } catch (error) {
      lastSeen = error instanceof Error ? error.message : String(error);
    }

    if (attempt < 60) await sleep(10_000);
  }

  const detail = lastRelevant.length > 0
    ? `; production-relevant changes: ${lastRelevant.join(", ")}`
    : "";
  throw new Error(`Production did not become compatible with ${EXPECTED_COMMIT}; last seen ${lastSeen}${detail}`);
}

function assertNoCredentialMarker(bytes, label) {
  const text = Buffer.from(bytes).toString("latin1");
  assert.equal(/gsk_[A-Za-z0-9_-]{20,}/.test(text), false, `${label} contains a Groq-style credential marker`);
  assert.equal(text.includes("GROQ_API_KEY_PRIMARY="), false, `${label} contains an environment assignment`);
}

function assertPublicConceptLabPage(html) {
  if (!/ConceptLab Browser Edition/.test(html) && /_next\/static/.test(html) && /Vercel|zeit-theme|KPSDK/.test(html)) {
    throw new Error(
      "The production URL is serving Vercel's protected access page instead of ConceptLab. Disable Vercel Authentication for the conceptlab-browser production deployment, then rerun this smoke test.",
    );
  }
  assert.match(html, /ConceptLab Browser Edition/);
  assert.match(html, /Java\/Swing browser edition/);
}

function assertTextParity(deployedText, repositoryPath, label) {
  const repositoryText = fs.readFileSync(repositoryPath, "utf8");
  assert.equal(deployedText, repositoryText, `${label} does not match the current repository`);
}

async function verifyStaticSurface() {
  const index = await fetchWithRetry("/");
  const html = await index.text();
  assertPublicConceptLabPage(html);
  assertNoCredentialMarker(Buffer.from(html), "index.html");
  assertTextParity(html, "index.html", "production index.html");

  const browserJs = await fetchWithRetry("/browser.js");
  const browserText = await browserJs.text();
  assert.match(browserText, /cheerpjInit/);
  assert.match(browserText, /conceptlab\.api\.url/);
  assert.match(browserText, /Java_Main_browserApiFetch/);
  assert.match(browserText, /endpoint\.origin !== window\.location\.origin/);
  assertNoCredentialMarker(Buffer.from(browserText), "browser.js");
  assertTextParity(browserText, "browser.js", "production browser.js");

  const browserCss = await fetchWithRetry("/browser.css");
  const browserCssText = await browserCss.text();
  assertTextParity(browserCssText, "browser.css", "production browser.css");

  const demo = await fetchWithRetry("/demo/newtonian-mechanics.clab");
  const demoText = await demo.text();
  assert.match(demoText, /^CONCEPTLAB_STUDYSET\|v4/m);
  assert.match(demoText, /title=Newtonian Mechanics/);
  assertTextParity(demoText, "demo/newtonian-mechanics.clab", "production demo StudySet");

  const jar = await fetchWithRetry("/ConceptLab-browser.jar");
  const jarBytes = new Uint8Array(await jar.arrayBuffer());
  assert.ok(jarBytes.byteLength > 50_000, `browser JAR unexpectedly small: ${jarBytes.byteLength}`);
  assertNoCredentialMarker(jarBytes, "browser JAR");
  const repositoryJar = fs.readFileSync("ConceptLab-browser.jar");
  const deployedHash = createHash("sha256").update(jarBytes).digest("hex");
  const repositoryHash = createHash("sha256").update(repositoryJar).digest("hex");
  assert.equal(deployedHash, repositoryHash, "production JAR does not match the current GitHub revision");
  console.log(`Production static assets and JAR match the current repository; JAR SHA-256 ${deployedHash}.`);
}

async function verifyProductionAi() {
  const system = "You are a helpful education assistant. Return ONLY valid JSON.";
  const prompt = [
    "HARD COUNT: questions.len=1 EXACT;",
    "INPUT:title=Newtonian Mechanics;",
    "topic=Newton's Second Law;",
    "Create one concise application-focused mechanics question suitable for a high-school physics StudySet.",
  ].join(" ");

  const makeBody = (model) => ({
    model,
    temperature: 0.2,
    max_tokens: 1000,
    reasoning_effort: "low",
    response_format: { type: "json_object" },
    messages: [
      { role: "system", content: system },
      { role: "user", content: prompt },
    ],
  });

  let response;
  let usedModel;
  for (const model of ["openai/gpt-oss-20b", "openai/gpt-oss-120b"]) {
    usedModel = model;
    response = await fetch(`${BASE_URL}/api/conceptlab/ai`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-ConceptLab-Client": "browser-v1",
        Origin: BASE_URL,
      },
      body: JSON.stringify(makeBody(model)),
    });
    if (response.ok) break;
    const failure = await response.text();
    console.warn(`AI smoke attempt with ${model} returned ${response.status}: ${failure.slice(0, 300)}`);
  }

  assert.ok(response?.ok, `production AI bridge did not succeed (last HTTP ${response?.status ?? "none"})`);
  const envelope = await response.json();
  const content = envelope?.choices?.[0]?.message?.content;
  assert.equal(typeof content, "string", "provider envelope did not contain message content");
  const parsed = JSON.parse(content);
  assert.equal(Array.isArray(parsed.questions), true, "generated JSON did not contain questions[]");
  assert.equal(parsed.questions.length, 1, "generated JSON did not contain exactly one question");
  console.log(`Production AI smoke passed with ${usedModel}.`);
}

async function waitForRuntime(page) {
  await page.waitForFunction(() => {
    const status = document.querySelector("#runtime-status");
    return status && /ConceptLab Java is running/i.test(status.textContent || "");
  }, null, { timeout: 150_000 });
  await page.waitForFunction(() => {
    const host = document.querySelector("#conceptlab-display");
    return Boolean(
      host
      && host.dataset.javaReady === "true"
      && host.children.length > 0
      && host.getBoundingClientRect().height > 0
    );
  }, null, { timeout: 30_000 });
}

async function readSeededSet(page, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const text = await page.evaluate(async () => {
        if (typeof cjFileBlob !== "function") return null;
        const blob = await cjFileBlob("/files/.conceptlab/sets/Newtonian_Mechanics.clab");
        return blob ? blob.text() : null;
      });
      if (typeof text === "string" && text.length > 0) return text;
    } catch (error) {
      lastError = error;
    }
    await sleep(500);
  }
  throw lastError || new Error("seeded Newtonian Mechanics StudySet did not become available");
}

async function verifyBrowserRuntime() {
  const browser = await chromium.launch({ headless: true });
  let page;
  const consoleErrors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    page = await context.newPage();
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });

    await page.goto(`${BASE_URL}/?build=${encodeURIComponent(EXPECTED_COMMIT || "current")}`, { waitUntil: "domcontentloaded", timeout: 90_000 });
    await waitForRuntime(page);
    const firstSeed = await readSeededSet(page);
    assert.match(firstSeed, /^CONCEPTLAB_STUDYSET\|v4/m);
    assert.match(firstSeed, /title=Newtonian Mechanics/);

    await page.reload({ waitUntil: "domcontentloaded", timeout: 90_000 });
    await waitForRuntime(page);
    const afterRefresh = await readSeededSet(page);
    assert.equal(afterRefresh, firstSeed, "seeded StudySet did not persist across refresh");

    await page.click("#reset-demo");
    await page.waitForURL(/reset=1/, { timeout: 30_000 }).catch(() => {});
    await waitForRuntime(page);
    const afterReset = await readSeededSet(page);
    assert.match(afterReset, /title=Newtonian Mechanics/);

    await page.screenshot({ path: "conceptlab-production.png", fullPage: true });
    const appFailures = consoleErrors.filter((message) => (
      /ConceptLab browser launch failed|Unhandled browser runtime rejection/i.test(message)
    ));
    assert.deepEqual(appFailures, [], `application startup logged errors: ${appFailures.join(" | ")}`);
    console.log(`Browser runtime smoke passed; captured ${consoleErrors.length} console error message(s) for review.`);
  } catch (error) {
    if (page) {
      await page.screenshot({ path: "conceptlab-production-failure.png", fullPage: true }).catch(() => {});
      const dom = await page.content().catch(() => "");
      fs.writeFileSync("conceptlab-browser-failure-dom.html", dom);
    }
    throw error;
  } finally {
    fs.writeFileSync("conceptlab-browser-console-errors.txt", consoleErrors.join("\n"));
    await browser.close();
  }
}

await waitForCompatibleDeployment();
await verifyStaticSurface();
await verifyProductionAi();
await verifyBrowserRuntime();
console.log("ConceptLab production browser smoke checks passed.");
