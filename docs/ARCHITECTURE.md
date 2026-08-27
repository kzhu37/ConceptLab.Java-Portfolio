# ConceptLab Architecture

[Back to the README](../README.md)

This document is the technical companion to the main ConceptLab README. I keep the deeper implementation details here so the top-level project overview can stay focused on the product and its main engineering decisions.

## Engineering priorities

ConceptLab was built around a few practical priorities:

| Priority | How it appears in the implementation |
| --- | --- |
| **Validate at boundaries** | Generated JSON, questions, resources, and saved data are checked before the rest of the application trusts them |
| **Keep the product local-first** | StudySets persist under the user's home directory without requiring a database or hosted backend |
| **Prefer dependable behavior over feature count** | Retry logic, fallback paths, duplicate prevention, and simplified storage were prioritized over adding more infrastructure |
| **Keep long work off the interface thread** | Generation and grading flows run through `SwingWorker` |
| **Be explicit about trade-offs** | The durable model layer is separated, while application and service responsibilities remain concentrated in `Main.java` |

## System overview

ConceptLab is a local-first Java desktop application. The UI, generation orchestration, quiz lifecycle, persistence coordination, and Groq HTTP integration currently live primarily in [`Main.java`](../Main.java), while durable model and persistence rules are separated into smaller classes.

The main runtime flow is:

1. A user creates or loads a `StudySet`.
2. Source material, learning goals, and optional instructions are transformed into generation inputs.
3. ConceptLab attempts AI-backed generation through the Groq OpenAI-compatible API.
4. Model output is required to conform to task-specific JSON contracts.
5. Responses are parsed, normalized, and converted into `Flashcard`, `Question`, and `ResourceLink` objects.
6. Invalid or repeated content is rejected or filtered before it enters a StudySet.
7. If remote generation fails, deterministic local fallbacks can keep core study-set and quiz-generation flows usable.
8. StudySets are persisted locally in a versioned `.clab` format.

The visual version of this flow is in [`media/generation-pipeline.svg`](media/generation-pipeline.svg).

## Browser edition boundary

The browser edition keeps the desktop source files authoritative. [`browser/build-browser.py`](../browser/build-browser.py) applies a small browser-only patch set, compiles Java 17 bytecode, and packages a deterministic JAR for CheerpJ 4.3. The normal desktop application continues to use the local filesystem and direct environment configuration without depending on Vercel or a browser runtime.

In the browser build:

- CheerpJ runs the real Swing application and maps `user.home` to its persistent `/files` filesystem;
- the bundled Newtonian Mechanics StudySet is copied into persistent storage without deleting user-created sets;
- AI calls use a constrained same-origin server endpoint, while provider credentials remain server-side;
- each launch passes a unique token to Java, and Java writes that token only after storage setup and Swing initialization finish;
- CheerpJ's internal Java clipboard avoids browser permission prompts during anonymous startup.

The production smoke workflow verifies the deployed commit metadata and JAR hash before exercising live AI, Java startup, StudySet persistence, refresh behavior, and the scoped demo reset.

## Major components

### [`Main.java`](../Main.java)

`Main` is the application entry point and the largest orchestration component. It owns:

- Swing screen construction and navigation;
- current UI and application state;
- StudySet creation flow;
- practice and unit-test generation;
- quiz-session lifecycle and scoring;
- feedback rendering;
- Groq requests and fallback sequencing;
- resource validation;
- local persistence coordination;
- asynchronous loading flows through `SwingWorker`.

This concentration reflects the project's development history rather than an architecture I would describe as ideal for a larger production system. The domain layer is separated, but the application and service layer could be decomposed further.

### [`StudySet.java`](../StudySet.java)

`StudySet` is the persistent aggregate root for one unit of learning material.

It stores:

- a stable StudySet ID;
- title;
- flashcards;
- practice questions;
- unit-test questions;
- resource links;
- best recorded unit-test percentage.

It also enforces important invariants. Practice and unit-test banks cannot contain duplicate IDs or normalized duplicate prompts, and the two banks must remain disjoint by normalized prompt.

### [`Question.java`](../Question.java)

`Question` supports two response modes:

- `MCQ`
- `SHORT_ANSWER`

The constructor validates the object immediately rather than allowing invalid question state to propagate through the application.

For MCQs, the model requires exactly four non-blank choices, verifies that choices remain distinct after normalization, validates the correct index, and normalizes feedback. Difficulty must remain within `[0, 1]`.

Normalized prompt and answer keys are used by the generation layer for duplicate detection.

### [`Flashcard.java`](../Flashcard.java)

`Flashcard` is an immutable model containing a stable ID, topic, front, and back. Blank required fields are rejected at construction time.

### [`ResourceLink.java`](../ResourceLink.java) and [`ResourceType.java`](../ResourceType.java)

Resource links have stable IDs and semantic categories such as simulation, article, video, practice, reference, or interactive content.

`ResourceLink` validates URL syntax and only permits HTTP/HTTPS URLs with a non-empty host. The application then performs additional direct-link and reachability checks before keeping generated resources.

### [`EscapeUtil.java`](../EscapeUtil.java)

ConceptLab's persistence format is pipe-delimited, but user-authored content can itself contain pipes, backslashes, and newlines.

`EscapeUtil` implements explicit encode/decode rules and a delimiter-aware split routine so arbitrary study content can survive a storage round trip without corrupting record boundaries.

## AI generation pipeline

### 1. Input shaping

Study-set generation begins with:

- title;
- source material;
- topic or learning goals;
- custom instructions;
- flashcard target count;
- target difficulty;
- challenge preference.

Source, goals, and instructions can also be reduced into normalized content facets used by deterministic fallback generation.

### 2. Strict output contracts

ConceptLab asks the model for JSON-only output with explicit schemas rather than accepting free-form prose.

Different generation tasks use different contracts:

- flashcards;
- resources;
- questions;
- answer evaluation.

This makes the model behave more like an unreliable external service whose responses must be validated than like a trusted internal function.

### 3. Application-focused question design

The question-generation prompt intentionally biases assessments toward application rather than isolated definition recall. It asks for a mix of behaviors such as:

- computation;
- inference;
- interpretation;
- method selection;
- error diagnosis;
- open response where reasoning is better tested without choices.

Users can tune the resulting practice set through difficulty, open-response mix, challenge questions, seen-question avoidance, and strict uniqueness settings.

### 4. Batching and token control

Large requests are not sent as one unbounded prompt.

ConceptLab:

- splits source material into bounded character chunks;
- generates questions in small batches;
- calculates an output-token allowance from estimated prompt size;
- reserves a safety margin against configured token budgets;
- detects responses truncated at output limits.

This logic was added after real generation failures made it clear that a successful API integration needs resource-aware request planning, not only correct endpoint syntax.

### 5. Retry and fallback sequence

Credentials are loaded from environment variables:

- `GROQ_API_KEY_PRIMARY`
- `GROQ_API_KEY_SECONDARY` (optional)

The API layer can try a primary and fallback model across available keys. Each combination can be retried, with special handling for transient HTTP failures, rate limits, and server-provided retry timing.

Real secrets are intentionally kept outside the repository.

### 6. Parse and validate

A successful HTTP status is not enough.

ConceptLab also checks for:

- blank response content;
- invalid response envelopes;
- malformed JSON;
- truncated completions;
- question objects that cannot satisfy model invariants;
- duplicate prompts;
- duplicate answer text where strict uniqueness is active.

Only content that survives those checks becomes application data.

### 7. Local fallback behavior

If remote generation fails, ConceptLab can create deterministic flashcards, questions, and fallback resources from extracted facets.

For answer checking, MCQs and short answers with known answer keys can also fall back to local checking. AI-generated short-answer questions that intentionally omit a stored answer key require the remote evaluator for full automatic grading. When that evaluator is unavailable, the application explains the limitation rather than pretending to know correctness.

## Persistence design

ConceptLab stores StudySets under the user's home directory rather than requiring a server or database.

The current file signature is:

```text
CONCEPTLAB_STUDYSET|v4
```

A file contains sections for:

```text
META
FLASHCARDS
PRACTICE
UNITTEST
RESOURCES
```

Each collection section declares an expected count. Loading validates those counts against the number of records actually parsed.

The loader also recognizes older question-record formats, allowing StudySets written by earlier versions of the project to remain readable after the question model evolved to support multiple response types.

## UI and concurrency

ConceptLab uses Swing and AWT for the desktop interface.

Important screens include:

- start/create/load;
- StudySet dashboard;
- flashcard review;
- practice launcher;
- resources;
- shared quiz and unit-test interface.

Potentially slow work runs through `SwingWorker` behind a modal loading experience rather than blocking the event-dispatch thread. The loading flow supports cancellation and rotates lightweight educational facts while work is in progress.

After a submitted quiz response, the interface can show:

- correctness and feedback;
- related external resources;
- related flashcards selected by token overlap.

## Major design evolution

### JavaFX to Swing

The earlier interface used JavaFX. Build and integration friction became significant enough that I migrated the application to Swing and simplified the project layout.

The decision was not based on Swing being universally better. It was based on what made this project more stable, easier to run, and faster to continue developing.

### Feature-first to utility-first

Earlier development made it easy to treat added functionality as progress. The project became more focused when I started asking whether each feature improved the actual learning workflow.

One example is progress tracking. Rather than building detailed analytics infrastructure, ConceptLab stores the best unit-test percentage. That kept a useful signal while avoiding storage and interface complexity that did not solve the central product problem.

### Free-form AI to guarded AI

The generation system similarly evolved from simply asking for content to treating model output as untrusted external data.

Batching, schema rules, retries, token budgeting, parsing, structural validation, and deduplication were consequences of encountering concrete failure modes during development rather than additions made only for technical appearance.

## Current architecture trade-offs

The largest remaining architectural trade-off is the size and responsibility of [`Main.java`](../Main.java). It currently contains both UI responsibilities and a substantial amount of service and integration logic.

If I were continuing the project as a longer-term production system, the clearest refactor would extract components such as:

- `GroqClient` for HTTP and retry logic;
- `GenerationService` for prompt construction and parsing;
- `StorageService` for StudySet discovery, save, and load behavior;
- `QuizService` for assessment generation and answer evaluation;
- the JSON parser into its own utility class.

`Main.java` is still larger than I would want long-term. For this version, stabilizing the complete working product and validating the learning workflow took priority over a late structural rewrite.

That trade-off is part of the project's development history: it explains what I chose to stabilize first and where I would split the system next.
