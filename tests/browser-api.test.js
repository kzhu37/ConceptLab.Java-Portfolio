import test from "node:test";
import assert from "node:assert/strict";
import { __test } from "../api/conceptlab/ai.js";

function request(headers = {}) {
  return {
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
