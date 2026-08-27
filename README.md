<p align="center">
  <img src="ConceptLabLogo.svg" alt="ConceptLab logo" width="180">
</p>

<p align="center">
  <strong>A Java study platform I built to turn learning material into application-focused practice, feedback, and reusable StudySets.</strong>
</p>

<p align="center">
  Java · Swing/AWT · CheerpJ · Java HTTP Client · Groq API · Vercel · local file persistence
</p>

<p align="center">
  <strong>Independently designed and developed</strong> · used or tested by <strong>60+ people</strong> during development
</p>

<p align="center">
  <a href="https://conceptlab-browser.vercel.app"><strong>Run ConceptLab in your browser</strong></a>
</p>

<p align="center">
  <a href="#why-i-built-it">Why</a> ·
  <a href="#engineering-highlights">Engineering</a> ·
  <a href="#browser-edition">Browser</a> ·
  <a href="#product-workflow">Product</a> ·
  <a href="#development-decisions-and-iteration">Iteration</a> ·
  <a href="#testing-and-reproducibility">Testing</a> ·
  <a href="#run-locally">Run locally</a>
</p>

<table>
  <tr>
    <td width="50%">
      <img src="docs/media/conceptlab-dashboard.png" alt="ConceptLab dashboard showing a Newtonian Mechanics StudySet">
    </td>
    <td width="50%">
      <img src="docs/media/browser-generated-quiz.webp" alt="ConceptLab browser edition showing an application-focused Newtonian Mechanics question and a submitted response in progress">
    </td>
  </tr>
  <tr>
    <td align="center"><sub>A loaded StudySet brings flashcards, practice, unit testing, resources, and saved progress into one local workflow.</sub></td>
    <td align="center"><sub>The live browser edition runs the real Swing application and asks users to apply concepts, not only recall them.</sub></td>
  </tr>
</table>

## Why I built it

ConceptLab began with a problem I kept seeing around me: people could spend hours reviewing notes and memorizing definitions, then struggle when a test changed the context and asked them to **apply** what they knew.

I wanted to build around that gap. Instead of stopping at flashcards, ConceptLab connects source material, fresh practice, unit testing, feedback, review resources, and saved progress in one workflow.

> **Design principle:** application matters more than memorization alone, and feedback should be part of learning rather than only the score at the end.

That principle became both a product decision and an engineering requirement. Fresh questions should not simply repeat familiar wording. Generated content should not become application data just because an API returned it. A feature should not remain merely because it is technically possible.

## At a glance

| Area | What ConceptLab demonstrates |
| --- | --- |
| **Product** | Turns notes and learning goals into reusable StudySets with flashcards, fresh practice, unit tests, feedback, and related resources. |
| **Reliability** | Treats generated output as untrusted data through contracts, validation, batching, retries, duplicate filtering, and fallback paths. |
| **Desktop to browser** | Runs the real Java/Swing application through CheerpJ with persistent browser StudySets and a private server-side AI boundary. |
| **Real use** | Used or tested by **60+ people during development**; testing changed practice, feedback, workflow, and reliability decisions. |
| **Verification** | Tests desktop and browser builds, persistence, AI contracts, security boundaries, reproducible UI capture, and the live deployment. |

## Engineering highlights

### 1. Building a reliable system around an unreliable generator

Calling an LLM was the easy part. The harder problem was making generated content dependable enough to become real application data.

ConceptLab requests structured output, parses the response, checks its shape, converts valid records into domain models, filters malformed or repeated content, and only then admits it into a StudySet. Larger question requests are split into bounded batches, and source material is chunked to reduce output-limit failures.

The generation layer also includes primary and fallback models, environment-based credential failover, retry handling, token-aware output budgets, truncated-response detection, duplicate protection, and deterministic local fallback paths.

![ConceptLab generation pipeline from source material through guarded generation and validated StudySet models](docs/media/generation-pipeline.svg)

The implementation is concentrated primarily in [`Main.java`](Main.java), while durable model rules live in [`StudySet.java`](StudySet.java), [`Question.java`](Question.java), [`Flashcard.java`](Flashcard.java), and [`ResourceLink.java`](ResourceLink.java).

**The key lesson was that I was not just calling an API. I was building a reliable system around an unreliable generator.**

### 2. Turning a learning philosophy into data rules

ConceptLab is designed around application rather than repeated recognition. Generation instructions push toward computation, inference, interpretation, method selection, error diagnosis, and explanation.

That philosophy also appears in the data layer. Practice and unit-test banks are kept disjoint by normalized prompt. Generation can block previously seen prompts and, when requested, duplicate correct-answer text. [`Question.java`](Question.java) validates response type, MCQ structure, choice uniqueness, answer indices, difficulty, and stable identity before a question becomes usable application data.

The connection is deliberate: **if the learning goal is transfer, near-duplicate questions are not good enough.**

### 3. Building local persistence instead of adding infrastructure I did not need

I chose a local-first persistence model rather than adding a database simply to make the stack longer.

Each desktop StudySet is stored under the user's home directory in a versioned `.clab` format. In the browser, the same persistence model is mapped into CheerpJ's persistent filesystem. [`StudySet.java`](StudySet.java) stores metadata, flashcards, practice questions, unit-test questions, resources, and the best unit-test score. The loader verifies declared section counts and recognizes older question-record formats.

User-authored content can contain pipes, backslashes, and newlines, so [`EscapeUtil.java`](EscapeUtil.java) explicitly escapes and restores reserved characters so content can round-trip without corrupting the file format.

For a deeper technical walkthrough, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Browser edition

ConceptLab was originally built as a Java/Swing desktop application. I wanted a public version that could be opened immediately without installing Java or entering an API key, while preserving the desktop program instead of replacing it with a look-alike web rewrite.

The browser edition therefore runs the Java/Swing application through **CheerpJ Core 4.3**. The desktop sources remain authoritative. A reproducible build script compiles them into a Java 17 browser artifact and applies controlled browser-specific adaptations at explicit boundaries instead of maintaining a second application codebase.

The browser architecture adds three boundaries around the original application:

1. **Java runtime:** CheerpJ executes the real Swing application in the browser.
2. **Persistence:** browser StudySets live under CheerpJ's persistent `/files` filesystem, with a resettable Newtonian Mechanics demo seeded for first-time use.
3. **AI security:** browser AI requests go to a constrained same-origin Vercel function. Groq credentials remain server-side and are never entered by the user or embedded in the public browser JAR.

The server bridge accepts only ConceptLab's known task contracts, constrains structured output, bounds request size, rate-limits public use, validates provider responses, and can fail over across private server-side credentials. The application also surfaces generation, retry, validation, partial-success, and failure states so an external-service problem does not silently overwrite a StudySet.

**Live browser:** [conceptlab-browser.vercel.app](https://conceptlab-browser.vercel.app)

## Product workflow

### From notes to a learning path

A StudySet begins with the user's own source material. The user can also specify learning goals, custom instructions, target difficulty, flashcard count, and whether challenge-style material should be included.

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
    <td align="center"><sub>Practice can be tuned without reducing the learning loop to one fixed question bank.</sub></td>
  </tr>
</table>

The system can build flashcards, a broader unit-test bank, and related learning resources. Fresh practice is generated on demand so the user is not limited to one fixed question set.

### Practice is configurable, not fixed

Users can change question count, difficulty, response style, challenge level, whether previously seen prompts should be avoided, and whether correct-answer text should remain unique.

Those settings are not only interface options. They feed directly into prompt construction, generation constraints, duplicate filtering, and the resulting `Question` objects.

### Feedback is part of the loop

A submitted response can lead to correctness feedback, an explanation, a related resource, and related flashcards. For questions with known answer keys, deterministic checking keeps part of the feedback flow usable when remote grading is unavailable.

```text
answer
  -> evaluate
  -> explain
  -> revisit related concept or resource
  -> try again
```

The point is not simply to mark an answer right or wrong. The error should lead somewhere useful.

<p align="center">
  <img src="docs/media/browser-feedback-review.webp" alt="ConceptLab feedback flow linking an incorrect mechanics response to a relevant PhET simulation and related flashcards" width="650">
</p>
<p align="center">
  <sub>After an incorrect response, the live application connects the correction to a relevant simulation and related flashcards so the mistake becomes the next review step.</sub>
</p>

## Development decisions and iteration

ConceptLab became stronger when I stopped treating added functionality as the same thing as progress.

| Challenge or observation | Decision | What it changed |
| --- | --- | --- |
| JavaFX and embedded-UI integration created dependency and build friction | Move the desktop interface to Swing and simplify the project structure | Reduced framework friction and made the application easier to compile and run |
| Early development rewarded adding features | Shift from feature-first to utility-first design | Kept the product focused on the learning loop instead of accumulating unnecessary controls |
| Detailed analytics would add storage and interface complexity without solving the core problem | Keep only the best unit-test score | Preserved useful progress feedback while keeping the persistence model simple |
| Model responses could be malformed, truncated, repetitive, rate-limited, or unavailable | Add structured contracts, validation, batching, retries, token budgeting, duplicate filtering, and fallback paths | Turned generation from a single API call into a guarded pipeline |
| A public browser demo could have exposed a provider credential or required a second UI implementation | Keep secrets behind a narrow server bridge and run the original Swing application through CheerpJ | Preserved the real application while making it accessible without an API key |
| Testing with other users made clarity and usefulness more important than the feature checklist | Make practice configurable and connect mistakes back to learning material | Shifted the product toward a clearer repeatable study workflow |

The most important design lesson was **more complexity does not automatically make a better product**.

## User testing and adoption

ConceptLab was used or tested by **60+ people during development**, including friends, peers, and other users. Several people moved beyond a one-time test and began using it to support their own learning.

What mattered most was what testing changed. Repeated use reinforced the need for less repetitive practice, configurable quizzes, useful feedback after mistakes, a clearer workflow, and greater reliability when generation failed.

I did not collect production telemetry for monthly active users, retention, session counts, or measured grade improvements, so I do not claim those metrics. The testing notes and claim boundaries are documented in [`docs/USER_TESTING.md`](docs/USER_TESTING.md).

## Testing and reproducibility

The project uses several independent testing layers rather than treating a successful compile as sufficient.

The core workflow in [`.github/workflows/verify-and-capture.yml`](.github/workflows/verify-and-capture.yml):

1. compiles the actual desktop application on **JDK 21**;
2. runs the dependency-free core self-tests in [`tests/ConceptLabSelfTest.java`](tests/ConceptLabSelfTest.java);
3. validates the bundled Newtonian Mechanics browser StudySet;
4. builds a **Java 17** CheerpJ artifact from the canonical Java sources;
5. runs browser AI contract tests and static security checks;
6. rejects Groq-style credential markers from the public tree and browser artifact;
7. launches the real Swing application inside a virtual display;
8. drives the interface using an isolated demo StudySet and uploads fresh UI captures as workflow artifacts.

The browser API tests cover accepted ConceptLab task shapes, model whitelisting, prompt-size limits, same-origin behavior, strict schema construction, provider-response validation, transient provider failure handling, credential failover, and safe error redaction.

[`tests/production-browser-smoke.mjs`](tests/production-browser-smoke.mjs) and [`.github/workflows/production-browser-smoke.yml`](.github/workflows/production-browser-smoke.yml) add a public deployment gate. The smoke test checks the deployed static surface and JAR for credential markers, exercises a real structured AI request through the production bridge, starts the CheerpJ Java runtime in Chromium, verifies the seeded StudySet, checks persistence across refresh, exercises Reset Demo, and captures production browser evidence.

The desktop self-tests cover escaping round-trips, question invariants, resource URL validation, StudySet save/load round-trips, duplicate protection across practice and unit-test banks, declared-count corruption, legacy question loading, and malformed persisted records.

The desktop product captures in this README are generated through [`tools/PortfolioCapture.java`](tools/PortfolioCapture.java). CI verifies that the real application can still produce fresh captures, but it does not automatically commit binary screenshot changes back to the repository. The browser quiz and feedback images above are captures from the public production deployment; the separate Playwright smoke workflow also records production browser screenshots as CI artifacts.

These checks exist for the same reason as the generation safeguards: **a technical claim is stronger when the repository can reproduce the behavior behind it.**

## Architecture and trade-offs

ConceptLab remains fundamentally a local-first Java application. The desktop build runs directly on the JDK, while the browser build places runtime and security boundaries around the same source.

The durable model layer is separated into focused classes, but application and service responsibilities remain concentrated in [`Main.java`](Main.java). `Main` currently handles Swing screen construction, navigation, generation orchestration, API communication, quiz lifecycle, persistence coordination, and several utility concerns.

I do not present that as an ideal final architecture. If this became a longer-term production system, the clearest next step would be to separate networking, generation, storage, and quiz services. For this version, I prioritized stabilizing the complete product and validating the learning workflow over performing a late refactor only to make the repository look more modular.

The browser build deliberately avoids turning that architecture into a web framework. [`browser/build-browser.py`](browser/build-browser.py) performs a controlled browser-specific adaptation of the authoritative Java sources at explicit source boundaries, while [`api/conceptlab/ai.js`](api/conceptlab/ai.js) handles the privileged capability that must stay outside the public browser runtime: server-side AI credentials. The source transformations use exact-match checks so code drift fails the browser build instead of silently producing a mismatched artifact.

That trade-off is documented in more detail in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## My role

**ConceptLab was independently designed and developed by Kevin Zhu.**

I owned the project end to end:

- identifying the learning problem and product direction;
- designing the StudySet workflow and desktop interface;
- implementing flashcards, practice, unit tests, feedback, and resource linking;
- building the local persistence format and domain validation;
- integrating and hardening the Groq generation pipeline;
- debugging API, output, persistence, and interface failures;
- testing the product with other users and changing priorities based on what was useful;
- adapting the real Java/Swing application for a secure browser deployment;
- preparing the reproducible public browser release and documentation.

> **Repository history:** This repository is a curated public release of ConceptLab. Its visible Git history primarily reflects public cleanup, testing, browser deployment work, and documentation rather than the project's complete development timeline.

## Technology

| Area | Technology and role |
| --- | --- |
| Desktop application | Java 21 |
| Interface | Swing and AWT |
| Browser runtime | CheerpJ Core 4.3 running a Java 17 artifact built from the canonical sources |
| Networking | Java HTTP Client |
| Generative system | Groq OpenAI-compatible API |
| Browser AI boundary | Vercel Function with private server-side credentials and strict task contracts |
| Structured output | Custom dependency-free JSON parser, strict provider schemas, and domain validation |
| Persistence | Versioned local `.clab` files, mapped to CheerpJ persistent storage in browser mode |
| Concurrency | `SwingWorker` for background generation and grading flows |
| Verification | GitHub Actions, Java assertions, Node contract tests, Playwright, reproducible UI capture |
| Hosting | Vercel |
| Version control | Git and GitHub |

## Core source structure

- **[`Main.java`](Main.java):** UI construction, navigation, generation pipeline, quiz lifecycle, API calls, and orchestration.
- **[`StudySet.java`](StudySet.java):** aggregate model, duplicate prevention, versioned persistence, and backward-compatible loading.
- **[`Question.java`](Question.java):** MCQ and short-answer model, invariants, normalized keys, feedback, and answer checking.
- **[`Flashcard.java`](Flashcard.java):** immutable flashcard model with stable identity.
- **[`ResourceLink.java`](ResourceLink.java) and [`ResourceType.java`](ResourceType.java):** validated external learning resources and categories.
- **[`EscapeUtil.java`](EscapeUtil.java):** escaping helpers for the custom persistence format.
- **[`LoadingScreenFacts.java`](LoadingScreenFacts.java):** lightweight educational content displayed during background work.
- **[`browser/build-browser.py`](browser/build-browser.py):** reproducibly adapts the canonical desktop sources into the Java 17 CheerpJ artifact without maintaining a second application implementation.
- **[`browser/build-site.py`](browser/build-site.py):** assembles the static browser deployment.
- **[`api/conceptlab/ai.js`](api/conceptlab/ai.js):** constrained server-side browser bridge for Groq generation.
- **[`tests/ConceptLabSelfTest.java`](tests/ConceptLabSelfTest.java):** dependency-free regression and smoke checks for the core model and persistence layer.
- **[`tests/browser-api.test.js`](tests/browser-api.test.js):** browser bridge contract, schema, failover, and failure-safety checks.
- **[`tests/production-browser-smoke.mjs`](tests/production-browser-smoke.mjs):** public deployment smoke test for Java startup, persistence, reset, AI, and credential exposure.
- **[`tools/PortfolioCapture.java`](tools/PortfolioCapture.java):** reproducible UI driver that launches and navigates the real desktop application to produce portfolio captures.

## Run locally

### Requirements

- **JDK 21 recommended**
- A Groq API key for full LLM-backed generation and AI feedback

The interface can launch without an API key, and several generation and checking paths include local fallbacks.

### Configure Groq

ConceptLab reads credentials from environment variables. Real keys are never stored in this public repository.

```text
GROQ_API_KEY_PRIMARY=your_key_here
GROQ_API_KEY_SECONDARY=your_optional_secondary_key_here
```

[`.env.example`](.env.example) documents the variable names. The Java application reads values from the process environment, so set them in your shell or IDE before launching.

The public browser deployment does not require users to configure these variables. Its credentials are private Vercel environment variables and are only used by the same-origin server bridge.

### Compile and run

```bash
javac *.java
java Main
```

Desktop StudySets are stored under:

```text
~/.conceptlab/sets/
```

### Run the core self-tests

macOS/Linux:

```bash
javac *.java
javac -cp . tests/ConceptLabSelfTest.java
java -ea -cp .:tests ConceptLabSelfTest
```

On Windows, replace the classpath separator `:` with `;`.

## What I learned

ConceptLab changed the way I think about engineering.

**Build for the problem, not the feature list.** Adding something is only progress if it makes the product more useful.

**Treat external behavior defensively.** Model output, saved files, and external resources should be validated before the rest of the application trusts them.

**Connect technical choices to the user experience.** Simplifying the stack, preventing duplicate practice, keeping background work responsive, and connecting errors back to review material all matter because reliability and usability are part of the same product.

---

**Technical deep dive:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)  
**User-testing context:** [`docs/USER_TESTING.md`](docs/USER_TESTING.md)
