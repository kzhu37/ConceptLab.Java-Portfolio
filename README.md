# ConceptLab

**A Java desktop study platform built to turn course material into application-focused practice, feedback, and reusable StudySets.**

**Sole designer & developer:** Kevin Zhu  
**Stack:** Java · Swing/AWT · Groq API · local file persistence  
**Product testing:** 60+ users and testers, with several classmates and friends adopting ConceptLab for their own studying

ConceptLab began with a problem I kept seeing around me: students could spend hours reviewing notes and memorizing definitions, yet still struggle when a test changed the context and asked them to apply what they knew. I built ConceptLab to make that gap the center of the study process.

Instead of acting only as a flashcard deck, ConceptLab turns a student's own material into a structured study environment with generated flashcards, application-oriented practice, unit tests, detailed answer feedback, relevant learning resources, and persistent StudySets that can be saved and revisited.

> **Design principle:** application matters more than memorization alone. Useful feedback should be part of learning, not just the score at the end.

## At a glance

| Area | What ConceptLab does |
| --- | --- |
| **Study-set generation** | Converts user-provided notes, topic goals, and custom instructions into structured study material. |
| **Flashcards** | Builds concept-focused cards with stable identities, topic labels, and persistent storage. |
| **Fresh practice** | Generates new quizzes with configurable length, difficulty, open-response mix, challenge questions, seen-question avoidance, and strict uniqueness. |
| **Unit tests** | Builds broader, higher-difficulty assessments intended to cover the StudySet rather than repeat practice questions. |
| **Feedback** | Evaluates responses, explains mistakes, and surfaces related resources and flashcards after a question. |
| **Reliability** | Validates structured AI output, batches larger requests, retries across model/key combinations, and falls back when generation fails. |
| **Persistence** | Stores StudySets locally in a versioned text format with validation, escaping, and backward-compatible parsing. |

## Why I built it

The original idea was simple: **having notes is not the same as understanding them**.

I wanted a study tool that would do more than make students recognize familiar wording. ConceptLab's question-generation rules therefore prioritize application: interpreting information, choosing methods, identifying errors, making inferences, and explaining reasoning. Definitions can still matter, but they are treated as a foundation rather than the endpoint.

That philosophy shaped the product as it grew. It also changed the way I approached software development. Early on, I tended to treat adding features as progress. Building for other students forced me to ask a better question: **does this actually improve the learning experience?**

## How ConceptLab works

1. **Create a StudySet.** The user provides source material, a title, learning goals, and optional custom instructions.
2. **Build structured study content.** ConceptLab creates flashcards, a unit-test bank, and curated resource links.
3. **Generate fresh practice on demand.** The user can control question count, difficulty, response style, challenge level, and uniqueness settings.
4. **Validate before use.** AI output is parsed into ConceptLab's internal models and filtered against structural and duplication rules before it reaches a StudySet.
5. **Learn from the response.** After answering, the user receives feedback and can jump to related resources or flashcards.
6. **Persist progress locally.** StudySets and best unit-test performance are stored on disk and can be loaded again later.

## Engineering highlights

### 1. Making LLM output reliable enough for application code

Calling an LLM was the easy part. The harder problem was making generated content predictable enough to become real application data.

ConceptLab's generation pipeline requests strict JSON, parses responses with an internal recursive-descent JSON parser, converts valid data into domain models, and rejects or filters malformed output. Larger question requests are split into bounded batches, while source material is chunked to reduce token-pressure failures.

The API layer also includes:

- environment-based credentials instead of hard-coded keys;
- primary and secondary key support;
- primary and fallback model sequencing;
- retry handling for transient failures and rate limits;
- token-aware output budgeting;
- detection of blank, malformed, and truncated responses;
- deterministic local fallback generation when remote generation is unavailable.

This turned the AI integration from a single API call into a fault-tolerant pipeline.

### 2. Designing for application, not repetition

ConceptLab's practice system is deliberately configurable. A student can choose question count, target difficulty, approximate open-response percentage, challenge-style questions, whether previously seen prompts should be avoided, and whether correct-answer text must also remain unique.

At generation time, the system tracks normalized prompt and answer keys so new questions can be filtered against material the student has already encountered. Practice and unit-test banks are also kept disjoint by normalized prompt inside the persisted StudySet.

The generation instructions themselves strongly bias questions toward applying knowledge rather than recalling isolated definitions. This is one of the clearest examples of the project's learning philosophy becoming an engineering requirement.

### 3. Enforcing data integrity in the domain models

The `Question` model supports both multiple-choice and short-answer interaction modes. It validates core invariants at construction time, including:

- difficulty values constrained to the `[0, 1]` range;
- exactly four choices for MCQs;
- non-blank and distinct choices after normalization;
- valid correct-answer indices;
- normalized prompt/answer keys used for matching and deduplication;
- stable UUID-based identity.

`ResourceLink` similarly validates HTTP/HTTPS URLs before they enter a StudySet, helping keep malformed resource data out of persistence and the UI.

### 4. Building a versioned local persistence format

I chose a local-first persistence model instead of introducing a database that the project did not need.

Each StudySet is stored in a versioned text format with separate sections for metadata, flashcards, practice questions, unit-test questions, and resources. The loader verifies declared section counts, validates parsed objects, and supports older question-record formats for backward compatibility.

Because user content can contain pipes, backslashes, and newlines, I also built custom escaping and unescaping logic so content can safely round-trip through the file format.

## Product decisions and iteration

### JavaFX → Swing

One of the most important decisions in the project was not adding a feature at all. My earlier interface work used JavaFX, but framework and build-integration issues repeatedly slowed development. I eventually migrated the application to Swing, flattened the project structure, and stabilized the build rather than continuing to invest in an approach that was creating friction.

That experience reinforced a principle that became central to ConceptLab: **more complexity does not automatically produce a better product.**

### From "generate something" to "generate something dependable"

The AI pipeline evolved for the same reason. Early generation could fail because of malformed responses, output-size limits, rate limits, repetitive questions, or unavailable models. Instead of treating those as one-off bugs, I redesigned the pipeline around validation, retry/fallback behavior, batching, token budgeting, and uniqueness safeguards.

### Building around feedback

The quiz flow is designed so a submitted answer leads somewhere useful. ConceptLab can provide detailed feedback, surface relevant study resources, and identify related flashcards that the student can revisit immediately. The intent is to make the error part of the learning path rather than simply mark it wrong and move on.

## User testing and adoption

ConceptLab was tested and iterated with **60+ users and testers** during development. Several friends and peers moved beyond simply trying the application and began using it to support their own studying.

I do not present that figure as an "active-user" metric; ConceptLab did not use product analytics to measure retention or monthly activity. What mattered to me was seeing a project I had built for a problem around me become useful enough that other students chose to use it themselves.

More detail on how I frame this testing evidence is available in [`docs/USER_TESTING.md`](docs/USER_TESTING.md).

## Technology

| Technology | Role in the project |
| --- | --- |
| **Java** | Core application, models, persistence, networking, generation logic, and quiz flow. |
| **Swing / AWT** | Desktop interface and interaction layer. |
| **Java HTTP Client** | Direct requests to the Groq OpenAI-compatible endpoint and resource reachability checks. |
| **Groq API** | LLM-backed flashcard generation, question generation, resource curation, and answer feedback. |
| **Custom JSON parser** | Dependency-free parsing of structured model responses. |
| **Local file storage** | Versioned `.clab` StudySet persistence under the user's home directory. |
| **Git / GitHub** | Source control and the curated public portfolio version of the project. |

## Core source structure

The project is intentionally small enough that the important pieces are easy to trace:

- **`Main.java`** - UI construction, navigation, generation pipeline, quiz lifecycle, API calls, and application orchestration.
- **`StudySet.java`** - aggregate model, duplicate prevention, versioned persistence, and backwards-compatible loading.
- **`Question.java`** - MCQ/short-answer model, invariants, normalized keys, feedback, and answer checking.
- **`Flashcard.java`** - immutable flashcard model with stable identity.
- **`ResourceLink.java` / `ResourceType.java`** - validated external learning resources and categories.
- **`EscapeUtil.java`** - escaping helpers for the custom pipe-delimited persistence format.
- **`LoadingScreenFacts.java`** - lightweight loading-state content used while background generation runs.

For a deeper technical walkthrough, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Running ConceptLab locally

### Requirements

- **JDK 17 or newer**
- A Groq API key for LLM-backed generation and AI feedback

The interface can launch without an API key, and several generation paths include deterministic local fallbacks. Full AI-backed generation and open-response feedback require Groq configuration.

### API configuration

Set a primary Groq key through the environment variable:

```text
GROQ_API_KEY_PRIMARY=your_key_here
```

An optional secondary key can be provided through:

```text
GROQ_API_KEY_SECONDARY=your_secondary_key_here
```

A placeholder configuration example is provided in [`.env.example`](.env.example). Real credentials should never be committed.

### Compile and run

From the directory containing the Java source files:

```bash
javac *.java
java Main
```

ConceptLab stores StudySets under:

```text
~/.conceptlab/sets/
```

If you use VS Code, a local environment file can be stored outside the repository at:

```text
~/.conceptlab/groq.env
```

## My role

**ConceptLab was independently designed and developed by Kevin Zhu.**

I owned the project end to end: identifying the learning problem, planning the product, designing the data models and interface, implementing persistence and quiz behavior, integrating the LLM pipeline, debugging API and UI failures, iterating on the product, and testing it with other students.

Development tools, including AI-assisted coding tools, were used as part of the programming workflow, but the product decisions, requirements, integration work, testing, debugging, and final project ownership were mine.

## What I learned

ConceptLab changed the way I think about engineering. The most useful lesson was not a particular Java API or framework. It was learning to separate **technical possibility** from **product value**.

I learned to break large problems into smaller systems, replace an approach when it was creating more friction than value, build guardrails around unreliable external behavior, and pay attention to what people actually found useful. The project became as much an exercise in product thinking and iteration as it was in programming.

---

*ConceptLab is presented here as a curated technical portfolio project rather than a raw development archive.*
