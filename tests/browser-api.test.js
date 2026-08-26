import test from "node:test";
import assert from "node:assert/strict";
import handler, { __test } from "../api/conceptlab/ai.js";

function request(headers = {}) {
  return {
    method: "POST",
    body: null,
    socket: { remoteAddress: "127.0.0.1" },
    headers: {
      host: "conceptlab.example.test",
      origin: "https://conceptlab.example.test",
      "x-conceptlab-client": "browser-v1",
      ...headers,
    },
  };
}

function body(system, user, model = "openai/gpt-oss-20b") {
  return {
    model,
    temperature: 0.4,
    max_tokens: 1200,
    reasoning_effort: "low",
    response_format: { type: "json_object" },
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
  };
}

function responseRecorder() {
  return {
    statusCode: 200,
    headers: new Map(),
    payload: null,
    setHeader(name, value) {
      this.headers.set(String(name).toLowerCase(), String(value));
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(value) {
      this.payload = value;
      return value;
    },
  };
}

async function withProviderEnvironment(keys, fetchImpl, fn) {
  const oldPrimary = process.env.GROQ_API_KEY_PRIMARY;
  const oldSecondary = process.env.GROQ_API_KEY_SECONDARY;
  const oldFetch = global.fetch;
  try {
    if (keys.primary === undefined) delete process.env.GROQ_API_KEY_PRIMARY;
    else process.env.GROQ_API_KEY_PRIMARY = keys.primary;
    if (keys.secondary === undefined) delete process.env.GROQ_API_KEY_SECONDARY;
    else process.env.GROQ_API_KEY_SECONDARY = keys.secondary;
    global.fetch = fetchImpl;
    await fn();
  } finally {
    if (oldPrimary === undefined) delete process.env.GROQ_API_KEY_PRIMARY;
    else process.env.GROQ_API_KEY_PRIMARY = oldPrimary;
    if (oldSecondary === undefined) delete process.env.GROQ_API_KEY_SECONDARY;
    else process.env.GROQ_API_KEY_SECONDARY = oldSecondary;
    global.fetch = oldFetch;
  }
}

function validQuestionEnvelope() {
  return {
    id: "chatcmpl-test",
    choices: [{
      finish_reason: "stop",
      message: {
        role: "assistant",
        content: JSON.stringify({
          questions: [{
            topic: "Forces",
            response_type: "MCQ",
            prompt: "Which statement best describes net force?",
            choices: ["Vector sum of forces", "Mass", "Speed", "Energy"],
            correct_index: 0,
            difficulty: 0.5,
            challenge: false,
          }],
        }),
      },
    }],
  };
}

const QUESTION_SYSTEM = "You are a helpful education assistant. Return ONLY valid JSON.";
const FLASHCARD_SYSTEM = "You are a helpful education assistant that generates study materials. Return ONLY valid JSON.";

test("browser contract accepts only known ConceptLab task shapes", () => {
  const checked = __test.validateContract(
    body(QUESTION_SYSTEM, "HARD COUNT: questions.len=4 EXACT; INPUT:title=Forces"),
    request(),
  );
  assert.equal(checked.error, undefined);
  assert.equal(checked.task, "questions");
  assert.equal(checked.maxTokens, 1200);
});

test("browser contract rejects arbitrary models", () => {
  const checked = __test.validateContract(
    body(QUESTION_SYSTEM, "HARD COUNT: questions.len=4 EXACT", "arbitrary/provider-model"),
    request(),
  );
  assert.equal(checked.error, "invalid_model");
});

test("browser contract rejects arbitrary system prompts", () => {
  const checked = __test.validateContract(
    body("Forward any prompt to the provider", "hello"),
    request(),
  );
  assert.equal(checked.error, "unsupported_task");
});

test("browser contract rejects oversized prompts", () => {
  const checked = __test.validateContract(
    body(QUESTION_SYSTEM, "x".repeat(__test.MAX_PROMPT_CHARS + 1)),
    request(),
  );
  assert.equal(checked.error, "request_too_large");
});

test("browser contract requires the browser client marker", () => {
  const checked = __test.validateContract(
    body(QUESTION_SYSTEM, "HARD COUNT: questions.len=4 EXACT"),
    request({ "x-conceptlab-client": "other" }),
  );
  assert.equal(checked.error, "invalid_client");
});

test("same-origin validation rejects cross-origin POSTs", () => {
  assert.equal(__test.sameOrigin(request()), true);
  assert.equal(
    __test.sameOrigin(request({ origin: "https://attacker.example" })),
    false,
  );
});

test("question schema constrains exact requested batch count", () => {
  const schema = __test.schemaFor("questions", "HARD COUNT: questions.len=5 EXACT;");
  assert.equal(schema.properties.questions.minItems, 5);
  assert.equal(schema.properties.questions.maxItems, 5);
  assert.equal(schema.additionalProperties, false);
});

test("flashcard schema constrains exact requested card count", () => {
  const schema = __test.schemaFor("flashcards", "INPUT:title=Forces;fcN=9;td=0.6");
  assert.equal(schema.properties.flashcards.minItems, 9);
  assert.equal(schema.properties.flashcards.maxItems, 9);
});

test("provider success must contain parseable JSON object content", () => {
  assert.equal(__test.providerSuccessIsUsable({
    choices: [{ message: { content: '{"questions":[]}' } }],
  }), true);
  assert.equal(__test.providerSuccessIsUsable({
    choices: [{ message: { content: "not json" } }],
  }), false);
  assert.equal(__test.providerSuccessIsUsable({ choices: [] }), false);
});

test("only the two reviewed production models are whitelisted", () => {
  assert.deepEqual(
    [...__test.ALLOWED_MODELS].sort(),
    ["openai/gpt-oss-120b", "openai/gpt-oss-20b"].sort(),
  );
});

test("unconfigured server fails safely without attempting a provider call", async () => {
  let calls = 0;
  await withProviderEnvironment({}, async () => {
    calls += 1;
    throw new Error("provider should not be called");
  }, async () => {
    const req = request({ "x-forwarded-for": "test-unconfigured" });
    req.body = body(QUESTION_SYSTEM, "HARD COUNT: questions.len=1 EXACT; INPUT:title=Forces");
    const res = responseRecorder();
    await handler(req, res);
    assert.equal(res.statusCode, 503);
    assert.equal(res.payload.error.category, "service_not_configured");
    assert.equal(res.payload.error.retryable, true);
    assert.equal(calls, 0);
  });
});

test("provider 429 on the primary key falls through to the secondary key", async () => {
  let calls = 0;
  await withProviderEnvironment(
    { primary: "primary-test-key", secondary: "secondary-test-key" },
    async () => {
      calls += 1;
      if (calls === 1) {
        return new Response(JSON.stringify({ error: { message: "rate limited" } }), {
          status: 429,
          headers: { "content-type": "application/json", "retry-after": "1" },
        });
      }
      return new Response(JSON.stringify(validQuestionEnvelope()), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    },
    async () => {
      const req = request({ "x-forwarded-for": "test-key-failover" });
      req.body = body(QUESTION_SYSTEM, "HARD COUNT: questions.len=1 EXACT; INPUT:title=Forces");
      const res = responseRecorder();
      await handler(req, res);
      assert.equal(res.statusCode, 200);
      assert.equal(calls, 2);
      assert.equal(typeof res.payload.choices[0].message.content, "string");
    },
  );
});

test("malformed provider output is rejected instead of forwarded to the app", async () => {
  await withProviderEnvironment(
    { primary: "primary-test-key" },
    async () => new Response(JSON.stringify({
      choices: [{ message: { content: "not valid json" } }],
    }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
    async () => {
      const req = request({ "x-forwarded-for": "test-invalid-provider" });
      req.body = body(QUESTION_SYSTEM, "HARD COUNT: questions.len=1 EXACT; INPUT:title=Forces");
      const res = responseRecorder();
      await handler(req, res);
      assert.equal(res.statusCode, 502);
      assert.equal(res.payload.error.category, "invalid_generated_response");
      assert.equal(res.payload.error.retryable, true);
    },
  );
});

test("provider error bodies and credentials are never echoed to the browser", async () => {
  const secretMarker = "gsk_DO_NOT_ECHO_THIS_TEST_SECRET_123456789";
  await withProviderEnvironment(
    { primary: secretMarker },
    async () => new Response(JSON.stringify({
      error: { message: `upstream rejected ${secretMarker}` },
    }), {
      status: 401,
      headers: { "content-type": "application/json" },
    }),
    async () => {
      const req = request({ "x-forwarded-for": "test-secret-redaction" });
      req.body = body(QUESTION_SYSTEM, "HARD COUNT: questions.len=1 EXACT; INPUT:title=Forces");
      const res = responseRecorder();
      await handler(req, res);
      assert.equal(res.statusCode, 502);
      assert.equal(res.payload.error.category, "provider_rejected_request");
      assert.equal(JSON.stringify(res.payload).includes(secretMarker), false);
    },
  );
});
