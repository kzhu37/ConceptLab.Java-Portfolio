# User Testing and Iteration

[Back to the README](../README.md)

This document records how outside use shaped ConceptLab and defines exactly what the **60+ users and testers** figure means.

## What the 60+ figure means

During development, I shared ConceptLab with **60+ users and testers**, including friends, peers, and other users, so I could see whether the product made sense outside my own workflow. Several people went beyond a one-time test and used it to support their own learning.

The number represents people who used or tested ConceptLab during development and iteration. It is not a monthly-active-user count, retention metric, or controlled study population.

ConceptLab did not include production telemetry for:

- monthly or daily active users;
- retention rates;
- number of study sessions;
- quantified grade improvement;
- statistically measured learning outcomes.

The useful evidence is therefore not a growth metric. It is that outside use repeatedly exposed product problems and changed what I built.

## What I was trying to learn

ConceptLab began with a recurring learning problem: having notes and recognizing definitions does not guarantee that someone can apply a concept when the context changes.

Testing focused on practical questions:

- Can another user understand the workflow without me explaining every step?
- Does practice feel different from simply rereading notes or reviewing definitions?
- Are generated questions varied enough to remain useful?
- Does feedback help a user decide what to review next?
- Are the controls worth the complexity they add?
- Does the application remain usable when generation or integration behavior is imperfect?

This changed how I evaluated progress. A feature was no longer automatically valuable because it existed.

## How testing changed the product

| Observation or product question | Resulting direction |
| --- | --- |
| Recognition alone was not the learning problem I wanted to solve | Emphasize application, inference, method selection, error diagnosis, and explanation in generated practice |
| Repetition reduced the value of generated practice | Add normalized prompt tracking, seen-question avoidance, answer-uniqueness controls, and separation between practice and assessment banks |
| Different users wanted different kinds of practice | Make quiz length, difficulty, response mix, and challenge level configurable |
| A wrong answer should lead to a next step | Connect question feedback with related resources and flashcards |
| More controls and features could make the tool harder to use | Shift from feature-first development toward utility-first design |
| Framework and integration friction slowed iteration | Simplify the desktop stack and move from earlier JavaFX work to Swing |
| Real generation failures were not isolated edge cases | Add validation, batching, retry behavior, token budgeting, duplicate filtering, and local fallback paths |

These changes matter more than the raw tester count because they altered the product's workflow, data rules, and reliability priorities.

## Evidence boundary

I do not claim production adoption, retention, grade improvement, or controlled learning outcomes because ConceptLab did not collect evidence that would support those claims.

The defensible claim is narrower: **60+ people used or tested ConceptLab during development, and repeated outside use materially influenced the final product.**

## What I learned from the process

The most important transition was from building something that worked for me to building something other people could understand and choose to use.

Early in development, adding functionality often felt like progress. Testing pushed me toward a harder standard: **does this make studying clearer, more useful, or more dependable?**

That shift is visible throughout the final project, from configurable practice and feedback to duplicate prevention, guarded generation, local persistence, and deliberate simplification.
