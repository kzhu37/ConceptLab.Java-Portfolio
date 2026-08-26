const WINDOW_MS = 10 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 30;
const MAX_PROMPT_CHARS = 24_000;
const MAX_OUTPUT_TOKENS = 4096;
const PROVIDER_TIMEOUT_MS = 40_000;

const requestsByClient = new Map();

const SYSTEM_TASKS = new Map([
  ["You are a helpful education assistant that generates study materials. Return ONLY valid JSON.", "flashcards"],
  ["You are a helpful education assistant that curates high-quality study resources. Return ONLY valid JSON.", "resources"],
  ["You are a helpful education assistant. Return ONLY valid JSON.", "questions"],
  ["You are a strict but supportive tutor. Return ONLY valid JSON.", "grading"],
]);

const ALLOWED_MODELS = new Set(["openai/gpt-oss-20b", "openai/gpt-oss-120b"]);
const ALLOWED_BODY_KEYS = new Set([
  "model",
  "temperature",
  "max_tokens",
  "reasoning_effort",
  "response_format",
  "messages",
]);

function safeError(res, status, category, retryable, retryAfterSeconds = 0) {
  res.setHeader("Cache-Control", "no-store");
  if (retryAfterSeconds > 0) {
    res.setHeader("Retry-After", String(retryAfterSeconds));
  }
  return res.status(status).json({
    error: {
      category,
      retryable,
      message: retryable
        ? "ConceptLab's AI service is temporarily unavailable. Please retry shortly."
        : "ConceptLab could not process this AI request.",
    },
  });
}

function getBody(req) {
  if (req.body && typeof req.body === "object") return req.body;
  if (typeof req.body === "string") {
    try { return JSON.parse(req.body); } catch { return null; }
  }
  return null;
}

function sameOrigin(req) {
  const origin = req.headers.origin;
  if (!origin) return true;
  const host = req.headers["x-forwarded-host"] || req.headers.host;
  if (!host) return false;
  try {
    return new URL(origin).host === String(host).split(",")[0].trim();
  } catch {
    return false;
  }
}

function clientKey(req) {
  const forwarded = req.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.length > 0) {
    return forwarded.split(",")[0].trim();
  }
  return req.socket?.remoteAddress || "unknown";
}

function allowRate(req) {
  const now = Date.now();
  const key = clientKey(req);
  const existing = requestsByClient.get(key) || [];
  const recent = existing.filter((time) => now - time < WINDOW_MS);
  if (recent.length >= MAX_REQUESTS_PER_WINDOW) {
    requestsByClient.set(key, recent);
    return false;
  }
  recent.push(now);
  requestsByClient.set(key, recent);
  if (requestsByClient.size > 1000) {
    for (const [client, times] of requestsByClient) {
      if (!times.some((time) => now - time < WINDOW_MS)) requestsByClient.delete(client);
    }
  }
  return true;
}

function commonObject(properties, required) {
  return { type: "object", additionalProperties: false, properties, required };
}

function flashcardSchema(prompt) {
  const match = prompt.match(/(?:^|;)fcN=(\d+)/);
  const requested = match ? Math.max(1, Math.min(30, Number(match[1]))) : 12;
  const card = commonObject(
    { topic: { type: "string" }, front: { type: "string" }, back: { type: "string" } },
    ["topic", "front", "back"],
  );
  return commonObject(
    {
      title: { type: "string" },
      topic: { type: "string" },
      flashcards: { type: "array", minItems: requested, maxItems: requested, items: card },
      practice_questions: { type: "array", maxItems: 0, items: {} },
      unit_test_questions: { type: "array", maxItems: 0, items: {} },
      resources: { type: "array", maxItems: 0, items: {} },
    },
    ["title", "topic", "flashcards", "practice_questions", "unit_test_questions", "resources"],
  );
}

function questionSchema(prompt) {
  const match = prompt.match(/HARD COUNT: questions\.len=(\d+) EXACT/);
  const requested = match ? Math.max(1, Math.min(6, Number(match[1]))) : 6;
  const item = commonObject(
    {
      topic: { type: "string" },
      response_type: { type: "string", enum: ["MCQ", "SHORT_ANSWER"] },
      prompt: { type: "string" },
      choices: { type: "array", minItems: 0, maxItems: 4, items: { type: "string" } },
      correct_index: { type: "integer", minimum: -1, maximum: 3 },
      difficulty: { type: "number", minimum: 0, maximum: 1 },
      challenge: { type: "boolean" },
    },
    ["topic", "response_type", "prompt", "choices", "correct_index", "difficulty", "challenge"],
  );
  return commonObject(
    { questions: { type: "array", minItems: requested, maxItems: requested, items: item } },
    ["questions"],
  );
}

function resourceSchema() {
  const item = commonObject(
    {
      topic: { type: "string" },
      title: { type: "string" },
      type: { type: "string", enum: ["SIMULATION", "REFERENCE", "ARTICLE", "VIDEO", "INTERACTIVE", "PRACTICE", "OTHER"] },
      url: { type: "string" },
    },
    ["topic", "title", "type", "url"],
  );
  return commonObject(
    { resources: { type: "array", minItems: 0, maxItems: 20, items: item } },
    ["resources"],
  );
}

function gradingSchema() {
  return commonObject(
    { correct: { type: "boolean" }, feedback: { type: "string" } },
    ["correct", "feedback"],
  );
}

function schemaFor(task, prompt) {
  if (task === "flashcards") return flashcardSchema(prompt);
  if (task === "questions") return questionSchema(prompt);
  if (task === "resources") return resourceSchema();
  return gradingSchema();
}

function validateContract(body, req) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return { error: "invalid_request" };
  if (!Object.keys(body).every((key) => ALLOWED_BODY_KEYS.has(key))) return { error: "invalid_request" };
  if (!ALLOWED_MODELS.has(body.model)) return { error: "invalid_model" };
  if (!Array.isArray(body.messages) || body.messages.length !== 2) return { error: "invalid_request" };
  const [system, user] = body.messages;
  if (system?.role !== "system" || user?.role !== "user") return { error: "invalid_request" };
  if (typeof system.content !== "string" || typeof user.content !== "string") return { error: "invalid_request" };
  if (user.content.length < 1 || user.content.length > MAX_PROMPT_CHARS) return { error: "request_too_large" };
  const task = SYSTEM_TASKS.get(system.content.trim());
  if (!task) return { error: "unsupported_task" };
  const temperature = Number(body.temperature);
  if (!Number.isFinite(temperature) || temperature < 0 || temperature > 0.6) return { error: "invalid_request" };
  const maxTokens = Number(body.max_tokens);
  if (!Number.isInteger(maxTokens) || maxTokens < 128 || maxTokens > MAX_OUTPUT_TOKENS) return { error: "invalid_request" };
  if (req.headers["x-conceptlab-client"] !== "browser-v1") return { error: "invalid_client" };
  return { task, temperature, maxTokens, system, user };
}

function parseRetryAfter(value) {
  if (!value) return 0;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, Math.min(60, Math.ceil(seconds)));
  return 0;
}

async function callProvider(apiKey, providerBody) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROVIDER_TIMEOUT_MS);
  try {
    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(providerBody),
      signal: controller.signal,
    });
    const text = await response.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* handled below */ }
    return { response, json };
  } finally {
    clearTimeout(timer);
  }
}

function providerSuccessIsUsable(json) {
  const content = json?.choices?.[0]?.message?.content;
  if (typeof content !== "string" || content.trim() === "") return false;
  try {
    const parsed = JSON.parse(content);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed);
  } catch {
    return false;
  }
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");

  if (req.method !== "POST") return safeError(res, 405, "method_not_allowed", false);
  if (!sameOrigin(req)) return safeError(res, 403, "origin_rejected", false);
  if (!allowRate(req)) return safeError(res, 429, "rate_limited", true, 60);

  const body = getBody(req);
  const contract = validateContract(body, req);
  if (contract.error) {
    const status = contract.error === "request_too_large" ? 413 : 400;
    return safeError(res, status, contract.error, false);
  }

  const primary = process.env.GROQ_API_KEY_PRIMARY?.trim();
  const secondary = process.env.GROQ_API_KEY_SECONDARY?.trim();
  const keys = [...new Set([primary, secondary].filter(Boolean))];
  if (keys.length === 0) return safeError(res, 503, "service_not_configured", true, 30);

  const providerBody = {
    model: body.model,
    temperature: contract.temperature,
    max_completion_tokens: contract.maxTokens,
    reasoning_effort: "low",
    response_format: {
      type: "json_schema",
      json_schema: {
        name: `conceptlab_${contract.task}`,
        strict: true,
        schema: schemaFor(contract.task, contract.user.content),
      },
    },
    messages: [contract.system, contract.user],
  };

  let lastStatus = 502;
  let lastRetryAfter = 0;
  for (let index = 0; index < keys.length; index += 1) {
    try {
      const { response, json } = await callProvider(keys[index], providerBody);
      lastStatus = response.status;
      lastRetryAfter = parseRetryAfter(response.headers.get("retry-after"));

      if (response.ok) {
        if (!providerSuccessIsUsable(json)) {
          if (index + 1 < keys.length) continue;
          return safeError(res, 502, "invalid_generated_response", true, 1);
        }
        return res.status(200).json(json);
      }

      if ((response.status === 408 || response.status === 429 || response.status >= 500) && index + 1 < keys.length) {
        continue;
      }

      if (response.status === 429) return safeError(res, 429, "rate_limited", true, lastRetryAfter || 2);
      if (response.status === 408) return safeError(res, 408, "provider_timeout", true, 1);
      if (response.status === 413) return safeError(res, 413, "request_too_large", false);
      if (response.status >= 500) return safeError(res, 503, "provider_unavailable", true, lastRetryAfter || 1);
      return safeError(res, 502, "provider_rejected_request", false);
    } catch (error) {
      lastStatus = error?.name === "AbortError" ? 408 : 502;
      if (index + 1 < keys.length) continue;
      if (lastStatus === 408) return safeError(res, 408, "provider_timeout", true, 1);
      return safeError(res, 502, "network_unavailable", true, 1);
    }
  }

  return safeError(
    res,
    lastStatus === 429 ? 429 : 503,
    lastStatus === 429 ? "rate_limited" : "provider_unavailable",
    true,
    lastRetryAfter || 1,
  );
}

export const __test = {
  validateContract,
  schemaFor,
  sameOrigin,
  providerSuccessIsUsable,
  ALLOWED_MODELS,
  MAX_PROMPT_CHARS,
};
