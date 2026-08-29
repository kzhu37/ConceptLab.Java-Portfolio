# User Testing and Iteration

[Back to the README](../README.md)

ConceptLab was shared with **60+ users and testers during development**, including friends, peers, and other users. This document defines that figure carefully and shows how outside use connects to changes visible in the final repository.

## What the 60+ figure means

The figure counts people who used or tested ConceptLab while I was developing and iterating on it. Several people used it beyond a one-time walkthrough to support their own studying.

It is not a monthly-active-user count, retention metric, formal survey sample, or controlled study population. I did not collect production telemetry or preserve a formal per-tester research dataset, so I do not present quotes, percentages, or learning outcomes that I cannot support.

The defensible claim is narrower: **60+ people used or tested ConceptLab during development, and outside use materially influenced the product.**

## What I was trying to learn

The central product question was whether ConceptLab could help with a problem that notes alone did not solve: recognizing material is different from applying it when the context changes.

Testing focused on practical questions:

- Can another user understand the workflow without me explaining every step?
- Does practice feel meaningfully different from rereading notes or definitions?
- Are generated questions varied enough to remain useful?
- Do users need different difficulty, response, or challenge settings?
- Does feedback tell a learner what to review next?
- Are added controls worth the complexity they introduce?

That changed my definition of progress. A feature was not automatically valuable because it existed.

## Representative iteration chains

The strongest evidence is not the raw tester count. It is that observations led to specific decisions that remain visible and testable in the final project.

| Observation from outside use | Product decision | Repository evidence |
| --- | --- | --- |
| Repetition made fresh practice less useful | Track normalized prompts, keep practice and assessment banks disjoint, add seen-question avoidance, and optionally require unique correct-answer text | [`StudySet.java`](../StudySet.java), [`Question.java`](../Question.java), [`GenerationPolicySelfTest.java`](../tests/GenerationPolicySelfTest.java), [practice settings](media/practice-settings.png) |
| Different users wanted different kinds of practice | Make question count, difficulty, response mix, and challenge level configurable instead of creating separate workflows | [`Main.java`](../Main.java), [practice settings](media/practice-settings.png) |
| A wrong answer alone did not give enough direction | Connect response feedback to an explanation, related resources, and related flashcards | [feedback example](media/browser-feedback-review.png), [`Main.java`](../Main.java) |
| More controls and features could make the product harder to use | Shift from feature-first development toward utility-first design and keep only the best unit-assessment score rather than building detailed analytics | [`StudySet.java`](../StudySet.java), [development evolution](media/development-evolution.svg) |

These are representative examples, not a claim that every engineering decision came directly from user feedback.

## Engineering observations that reinforced the same priorities

Some major changes came from development and deployment failures rather than from users. I separate them because they demonstrate a different kind of iteration.

| Engineering observation | Response |
| --- | --- |
| JavaFX and embedded-UI integration created build and dependency friction | Move to Swing and simplify the desktop stack |
| Model responses could be malformed, repetitive, rate-limited, unavailable, or truncated | Add structured contracts, parsing, validation, batching, retries, token budgeting, duplicate filtering, and deterministic fallbacks |
| The `Question` model evolved to support multiple response types | Add versioned persistence and backward-compatible question loading |
| A public demo could expose credentials or drift into a second implementation | Keep the Java/Swing sources authoritative and add explicit browser runtime, persistence, and server-side AI boundaries |

This separation matters. Outside use shaped the learning workflow and controls; engineering failures shaped reliability and deployment architecture.

## Evidence boundary

ConceptLab did not collect evidence that would support claims about:

- monthly or daily active users;
- retention rates;
- number of study sessions;
- quantified grade improvement;
- statistically measured learning outcomes.

The repository therefore treats the 60+ figure as **development and iteration context**, not as growth or educational-outcome proof. Current screenshots, source files, tests, and verification workflows can demonstrate the resulting product behavior, but they do not reconstruct a formal historical experiment that never existed.

## What I learned

The most important transition was from building something that worked for me to building something other people could understand and choose to use.

Early in development, adding functionality often felt like progress. Testing pushed me toward a harder standard: **does this make studying clearer, more useful, or more dependable?** That shift is visible in configurable practice, feedback that points toward review, duplicate prevention, guarded generation, local persistence, and deliberate simplification.
