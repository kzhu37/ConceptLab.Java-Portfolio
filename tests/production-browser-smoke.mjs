import assert from "node:assert/strict";
import fs from "node:fs";
import { chromium } from "playwright";

const BASE_URL = (process.env.CONCEPTLAB_BASE_URL || "https://conceptlab-browser-xiangseanzhu-7370.vercel.app").replace(/\/$/, "");
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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

async function verifyStaticSurface() {
  const index = await fetchWithRetry("/");
  const html = await index.text();
  assertPublicConceptLabPage(html);
  assertNoCredentialMarker(Buffer.from(html), "index.html");

  const browserJs = await fetchWithRetry("/browser.js");
  const browserText = await browserJs.text();
  assert.match(browserText, /cheerpjInit/);
  assert.match(browserText, /conceptlab\.api\.url/);
  assertNoCredentialMarker(Buffer.from(browserText), "browser.js");

  const demo = await fetchWithRetry("/demo/newtonian-mechanics.clab");
  const demoText = await demo.text();
  assert.match(demoText, /^CONCEPTLAB_STUDYSET\|v4/m);
  assert.match(demoText, /title=Newtonian Mechanics/);

  const jar = await fetchWithRetry("/ConceptLab-browser.jar");
  const jarBytes = new Uint8Array(await jar.arrayBuffer());
  assert.ok(jarBytes.byteLength > 50_000, `browser JAR unexpectedly small: ${jarBytes.byteLength}`);
  assertNoCredentialMarker(jarBytes, "browser JAR");
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
  await page.waitForFunction(() => document.querySelectorAll("canvas").length > 0, null, { timeout: 30_000 });
}

async function readSeededSet(page) {
  return page.evaluate(async () => {
    if (typeof cjFileBlob !== "function") throw new Error("cjFileBlob is unavailable");
    const blob = await cjFileBlob("/files/.conceptlab/sets/Newtonian_Mechanics.clab");
    return blob.text();
  });
}

async function verifyBrowserRuntime() {
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const page = await context.newPage();
    const consoleErrors = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });

    await page.goto(BASE_URL, { waitUntil: "domcontentloaded", timeout: 90_000 });
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
    fs.writeFileSync("conceptlab-browser-console-errors.txt", consoleErrors.join("\n"));
    console.log(`Browser runtime smoke passed; captured ${consoleErrors.length} console error message(s) for review.`);
  } finally {
    await browser.close();
  }
}

await verifyStaticSurface();
await verifyProductionAi();
await verifyBrowserRuntime();
console.log("ConceptLab production browser smoke checks passed.");
