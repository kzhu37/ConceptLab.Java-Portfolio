<p align="center">
  <img src="ConceptLabLogo.svg" alt="ConceptLab logo" width="180">
</p>

<p align="center">
  <strong>A Java study platform I built to turn source material into application-focused practice, feedback, and reusable StudySets.</strong>
</p>

<p align="center">
  Java · Swing/AWT · Java HTTP Client · Groq API · versioned local persistence · CheerpJ · Vercel
</p>

<p align="center">
  Shared with <strong>60+ users and testers</strong> during development · outside use changed practice controls, feedback, and reliability · <a href="docs/USER_TESTING.md">testing context</a>
</p>

<p align="center">
  <a href="https://conceptlab-browser.vercel.app"><strong>Run ConceptLab in Browser</strong></a>
</p>

<table>
  <tr>
    <td width="50%">
      <img src="docs/media/conceptlab-dashboard.png" alt="ConceptLab dashboard showing a Newtonian Mechanics StudySet">
    </td>
    <td width="50%">
      <img src="docs/media/browser-feedback-review.webp" alt="ConceptLab feedback after an incorrect mechanics response, with correction, PhET review material, and related flashcards">
    </td>
  </tr>
  <tr>
    <td align="center"><sub><strong>StudySet:</strong> flashcards, practice, unit assessments, resources, and saved progress in one workflow.</sub></td>
    <td align="center"><sub><strong>Feedback loop:</strong> an incorrect response becomes a correction, a relevant simulation, and related flashcards to review next.</sub></td>
  </tr>
</table>

<p align="center">
  <a href="#product-workflow">Product</a> ·
  <a href="#engineering-highlights">Engineering</a> ·
  <a href="#development-and-user-driven-iteration">Iteration</a> ·
  <a href="#browser-edition">Browser</a> ·
  <a href="#verification">Verification</a> ·
  <a href="#architecture-and-trade-offs">Architecture</a> ·
  <a href="#my-role-and-repository-scope">My role</a>
</p>

## Why I built it

ConceptLab began with a learning problem I kept seeing: reviewing notes and recognizing definitions did not guarantee that someone could apply a concept when the context changed.

I wanted one workflow connecting source material, fresh practice, testing, feedback, resources, and review. The design principle became simple: **application matters more than memorization alone, and a mistake should lead to the next useful learning step.** That also became an engineering rule: repeated prompts and unvalidated model output are not useful StudySet data.

## At a glance

| Area | What ConceptLab demonstrates |
| --- | --- |
| **Learning workflow** | Source material becomes reusable StudySets with flashcards, fresh practice, unit assessments, feedback, and related resources. |
| **Guarded generation** | Structured contracts, validation, batching, retries, deduplication, token control, provider failover, and deterministic fallbacks. |
| **Durable local data** | Versioned `.clab` StudySets with escaping, count checks, corruption handling, backward-compatible loading, and cross-bank duplicate protection. |
| **Real iteration** | Outside use changed practice controls, feedback, workflow, and reliability priorities. |
| **Public engineering proof** | The real Java/Swing app runs in a browser, with separate verification of desktop, browser, persistence, and live generation paths. |
| **My role** | Independently designed and developed the project end to end. |

**Core implementation:** [`Main.java`](Main.java) · [`StudySet.java`](StudySet.java) · [`Question.java`](Question.java) · [`api/conceptlab/ai.js`](api/conceptlab/ai.js)

**Executable proof:** [`ConceptLabSelfTest.java`](tests/ConceptLabSelfTest.java) · [`GenerationPolicySelfTest.java`](tests/GenerationPolicySelfTest.java) · [`QuestionAnswerPolicySelfTest.java`](tests/QuestionAnswerPolicySelfTest.java) · [`browser-api.test.js`](tests/browser-api.test.js) · [`production-browser-smoke.mjs`](tests/production-browser-smoke.mjs)

### How ConceptLab turns generation into trusted StudySet data

![ConceptLab generation pipeline from source material through guarded generation and validated StudySet models](docs/media/generation-pipeline.svg)

Model output is treated as **untrusted external data**. ConceptLab requests task-specific structured output, parses and validates it, rejects malformed or repeated records, converts valid records into domain objects, and only then admits them into a StudySet.

## Product workflow

### 1. Build a StudySet from source material

A StudySet starts with the user's own material and can include learning goals, custom instructions, target difficulty, flashcard count, and challenge-style material.

<table>
  <tr>
    <td width="50%">
      <img src="docs/media/create-study-set.png" alt="ConceptLab StudySet creation form populated with mechanics notes and learning goals">
    </td>
    <td width="50%">
      <img src="docs/media/practice-settings.png" alt="ConceptLab fresh-practice settings showing difficulty, response mix, challenge, seen-question, and uniqueness controls">
    </td>
  </tr>
  <tr>
    <td align="center"><sub>Source material, goals, and instructions define what the StudySet should teach.</sub></td>
    <td align="center"><sub>Practice settings feed directly into generation constraints and duplicate handling.</sub></td>
  </tr>
</table>

### 2. Practice, adapt, and review

Users can change question count, difficulty, response style, challenge level, seen-question avoidance, and answer uniqueness. Those settings feed into generation constraints, duplicate filtering, and which `Question` objects enter the StudySet.

After a response, ConceptLab can show correctness, an explanation, a related resource, and related flashcards. Questions with known answer keys retain conservative deterministic checking when remote grading is unavailable, so a mistake becomes the next review step rather than only a score.

<p align="center">
  <img src="docs/media/browser-generated-quiz.webp" alt="ConceptLab running an application-focused Newtonian Mechanics practice question in the browser edition" width="86%">
</p>
<p align="center">
  <sub><strong>Practice proof:</strong> the public edition runs the Java/Swing workflow directly, including generated questions, saved StudySets, and the same validation rules used by the desktop application.</sub>
</p>

## Engineering highlights

### 1. Turning the learning goal into data rules

ConceptLab biases practice toward computation, inference, interpretation, method selection, error diagnosis, and explanation. The goal is enforced below the prompt layer: `StudySet` rejects duplicate IDs and normalized prompts across banks, while `Question` validates response type, MCQ structure, choice uniqueness, answer indices, difficulty, and identity at construction time.

### 2. Reliability beyond a successful API call

Large requests are batched, source material is chunked, output budgets account for prompt size, and truncated responses are rejected. Transient failures can trigger retries, model or credential failover where configured, duplicate filtering, and deterministic local fallbacks. **Successful transport is not the same thing as trustworthy application data.**

### 3. Choosing local persistence instead of unnecessary infrastructure

Desktop StudySets use a versioned `.clab` format under the user's home directory. Files store learning content and the best assessment score, declare expected collection counts, reject count mismatches, and retain compatibility with older question records. [`EscapeUtil.java`](EscapeUtil.java) escapes pipes, backslashes, and newlines so user content round-trips without corrupting record boundaries.

I chose local persistence because the product did not need a hosted database. The simpler infrastructure still required versioning, validation, backward compatibility, and corruption handling.

### 4. Keeping long-running work off the Swing event thread

Generation and grading can involve network calls, retries, parsing, and fallback work. ConceptLab runs slow flows through `SwingWorker` behind a cancellable loading experience instead of blocking Swing's event-dispatch thread.

## Development and user-driven iteration

ConceptLab was shared with **60+ users and testers during development**. Some changes came from outside use, while others came from concrete engineering failures observed during development and deployment. Both pushed the project away from feature count and toward utility, clarity, and reliability.

<p align="center">
  <img src="docs/media/development-evolution.svg" alt="ConceptLab development evolution from framework simplification and feature reduction to guarded generation, validated persistence, and a public browser adaptation" width="100%">
</p>

| Signal | Decision | Result |
| --- | --- | --- |
| **Development constraint:** JavaFX and embedded-UI integration created build and dependency friction | Move the desktop interface to Swing and simplify the project structure | Faster iteration and a more dependable local build |
| **Product evaluation:** early development rewarded feature count | Shift from feature-first to utility-first design | A clearer learning loop with less unnecessary interface complexity |
| **Outside use:** repetition reduced the value of generated practice | Track normalized prompts, separate practice and assessment banks, and add seen-question and answer-uniqueness controls | More varied practice with explicit duplicate protection |
| **Outside use:** different users wanted different kinds of practice | Make question count, difficulty, response mix, and challenge level configurable | Practice could adapt without creating separate workflows |
| **Outside use:** a wrong answer alone did not tell the learner what to do next | Connect feedback to explanation, related resources, and related flashcards | Mistakes became a route back into review |
| **Generation failures:** responses could be malformed, repetitive, rate-limited, unavailable, or cut off at an output limit | Add structured contracts, validation, batching, retries, token budgeting, deduplication, and fallbacks | Generation became a guarded pipeline rather than a single API call |
| **Scope decision:** detailed analytics would add storage and UI work without solving the core problem | Keep only the best unit-assessment score | Useful progress feedback without infrastructure for its own sake |
| **Public release constraint:** a demo could expose credentials or drift into a second implementation | Keep the Java/Swing sources authoritative, then add explicit browser runtime, storage, and AI boundaries | Immediate browser access without replacing the original project |

The tester count is context, not a growth metric. I did not collect production analytics or a controlled learning-outcomes dataset, so I do not claim active-user counts, retention, grade improvement, or measured educational effect. [`docs/USER_TESTING.md`](docs/USER_TESTING.md) documents the evidence boundary and connects representative observations to final repository evidence.

## Browser edition

ConceptLab began as a Java/Swing desktop application. I added the public browser edition later so the project could be opened immediately without a local Java installation or a visitor-provided API key.

![ConceptLab system architecture showing the canonical Java sources branching into desktop and browser execution paths](docs/media/system-architecture.svg)

The desktop Java sources remain authoritative. [`browser/build-browser.py`](browser/build-browser.py) applies a small exact-match patch set, compiles a Java 17 artifact, and packages it for **CheerpJ Core 4.3**. The browser adaptation adds three explicit boundaries:

1. **Runtime:** CheerpJ executes the Java/Swing application in the browser.
2. **Persistence:** StudySets use CheerpJ's persistent `/files` filesystem, with a resettable Newtonian Mechanics demo seeded for first-time use.
3. **AI security:** browser generation goes through [`api/conceptlab/ai.js`](api/conceptlab/ai.js), which keeps Groq credentials server-side and accepts only ConceptLab's constrained request contracts.

> **Browser demo note:** This is a public adaptation of the desktop application, not a separate web rewrite. Browser-specific transport and storage behavior are added only at explicit boundaries.

<p align="center">
  <a href="https://conceptlab-browser.vercel.app"><strong>Open the live browser edition</strong></a>
</p>

## Verification

A successful compile is not enough evidence for the claims this project makes. The repository verifies the desktop core, answer policy, generation policy, browser boundary, and deployed demo separately.

| Layer | What is verified |
| --- | --- |
| **Desktop core** | JDK 21 compilation, model invariants, escaping, StudySet round-trips, duplicate protection, corrupted counts, legacy question loading, malformed persisted records, and resource validation |
| **Answer policy** | Normalized exact matches, longer responses containing a complete known answer key, rejection of partial fragments, blank-key behavior, and MCQ determinism |
| **Generation policy** | Source chunking, token-budget bounds, token/quota failure classification, malformed generated-question filtering, deterministic fallback validity, uniqueness behavior, and offline grading paths |
| **Browser boundary** | Allowed task shapes and models, prompt-size limits, same-origin behavior, response schemas, provider failure handling, key failover, error redaction, and static credential checks |
| **Production browser** | Deployed commit or artifact equivalence, credential markers, live structured generation, CheerpJ startup in Chromium, seeded StudySet loading, persistence across refresh, Reset Demo behavior, and screenshot evidence |

The main workflows are [`.github/workflows/verify-and-capture.yml`](.github/workflows/verify-and-capture.yml) and [`.github/workflows/production-browser-smoke.yml`](.github/workflows/production-browser-smoke.yml). [`tools/PortfolioCapture.java`](tools/PortfolioCapture.java) drives the real Swing interface to reproduce the desktop captures used in this showcase.

## Architecture and trade-offs

ConceptLab remains a local-first Java application. The durable domain layer is separated, but UI construction, generation orchestration, API communication, quiz lifecycle, and several service responsibilities remain concentrated in [`Main.java`](Main.java).

I do not present that concentration as ideal. A larger production version should extract networking, generation, storage, quiz, and parsing services. For this release, I prioritized a stable complete product and validated learning workflow over a late refactor performed mainly for appearance. The component breakdown and next refactor boundary are documented in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Run locally

**JDK 21 is recommended.** A Groq API key enables full model-backed generation and AI feedback, but the interface can launch without one and several flows include local fallbacks.

<details>
<summary><strong>Local setup and verification</strong></summary>

### Configure Groq

ConceptLab reads credentials from environment variables. Real keys are never stored in this public repository.

```text
GROQ_API_KEY_PRIMARY=your_key_here
GROQ_API_KEY_SECONDARY=your_optional_secondary_key_here
```

[`.env.example`](.env.example) documents the variable names. The public browser deployment does not require users to configure them because its credentials remain private Vercel environment variables behind the same-origin server bridge.

### Compile and run

```bash
javac *.java
java Main
```

Desktop StudySets are stored under:

```text
~/.conceptlab/sets/
```

### Run the Java self-tests

macOS/Linux:

```bash
javac *.java
javac -cp . tests/ConceptLabSelfTest.java tests/GenerationPolicySelfTest.java tests/QuestionAnswerPolicySelfTest.java
java -ea -cp .:tests ConceptLabSelfTest
java -ea -cp .:tests GenerationPolicySelfTest
java -ea -cp .:tests QuestionAnswerPolicySelfTest
```

On Windows, replace the classpath separator `:` with `;`.

### Run the browser boundary checks

```bash
npm test
npm run verify:static
```

### Rebuild the browser JAR

```bash
python3 browser/build-browser.py --output ConceptLab-browser.jar
python3 browser/normalize-jar.py ConceptLab-browser.jar
```

</details>

## My role and repository scope

**ConceptLab was independently designed and developed by Kevin Zhu.**

I owned the project end to end: the learning problem and product direction, StudySet workflow, Swing interface, flashcards, practice and unit assessments, feedback, resource linking, persistence format, domain validation, Groq integration, reliability work, debugging, user testing, browser adaptation, public deployment, verification, and documentation.

> **Repository history:** This is a curated public release of ConceptLab. Its visible Git history primarily reflects public cleanup, verification, browser deployment work, screenshot preparation, and documentation rather than the complete development timeline.

---

**Technical deep dive:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)  
**User testing context:** [`docs/USER_TESTING.md`](docs/USER_TESTING.md)
