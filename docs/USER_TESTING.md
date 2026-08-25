# User Testing and Adoption

ConceptLab was not developed only as a private coding exercise. During development, I shared it with **60+ users and testers**, including friends, peers, and other users, so I could see whether the product made sense outside my own workflow.

Several people went beyond a one-time test and began using ConceptLab to support their own learning.

## What the 60+ figure means

I use the wording **"60+ users and testers"** deliberately.

The number represents people who used or tested ConceptLab during its development and iteration. It is not a monthly-active-user count, retention metric, or controlled study population.

ConceptLab did not include production telemetry that measured:

- monthly or daily active users;
- retention rates;
- number of study sessions;
- quantified grade improvement;
- statistically measured learning outcomes.

I therefore do not claim those metrics.

The strongest outcome I can support is simpler: some friends, peers, and other users found the project useful enough to continue using it for their own learning after being introduced to it.

## What I was trying to learn from testing

Early ConceptLab development focused on a recurring learning problem: having notes and recognizing definitions does not guarantee that someone can apply a concept when the context changes.

Testing therefore mattered less as a popularity exercise and more as a way to ask practical product questions:

- Can another user understand the workflow without me explaining every step?
- Does practice feel different from simply re-reading notes or reviewing definitions?
- Are generated questions varied enough to remain useful?
- Does feedback help a user decide what to review next?
- Are the controls worth the complexity they add?
- Does the application remain usable when generation or integration behavior is imperfect?

That changed the way I evaluated progress. A feature was no longer automatically valuable because it existed.

## How testing influenced the product

| Observation or product question | Resulting direction |
| --- | --- |
| Recognition alone was not the learning problem I wanted to solve | Emphasize application, inference, method selection, error diagnosis, and explanation in generated practice |
| Repetition reduced the value of generated practice | Add normalized prompt tracking, seen-question avoidance, answer-uniqueness controls, and separation between practice and unit-test banks |
| Different users wanted different kinds of practice | Make quiz length, difficulty, response mix, and challenge level configurable |
| A wrong answer should lead to a next step | Connect question feedback with related resources and flashcards |
| More controls and features could make the tool harder to use | Shift from feature-first development toward utility-first design |
| Framework and integration friction slowed iteration | Simplify the desktop stack and move from earlier JavaFX work to Swing |
| Real generation failures were not isolated edge cases | Add validation, batching, retry behavior, token budgeting, duplicate filtering, and local fallback paths |

These changes are more important to the portfolio than the raw tester count because they show how use and failure changed the engineering priorities.

## Evidence standard for this portfolio

### Claims I make

- ConceptLab was independently designed and developed by Kevin Zhu.
- It was used or tested by 60+ people during development.
- The testing group included friends, peers, and other users.
- Several people adopted the project to support their own learning.
- Testing and repeated use influenced the product decisions described above.

### Claims I do not make

- 60+ monthly active users;
- a specific retention percentage;
- a quantified increase in grades or test scores;
- a specific number of generated study sessions;
- controlled evidence that ConceptLab improves learning outcomes.

Those claims would require telemetry or a more rigorous study that this project did not collect.

## Why this mattered

The most important transition was from building something that worked for me to building something that other people could understand and choose to use.

Early in development, adding functionality often felt like progress. Testing pushed me toward a harder standard: **does this actually make studying clearer, more useful, or more dependable?**

That shift is visible throughout the final project, from configurable practice and feedback to duplicate prevention, guarded generation, local persistence, and deliberate simplification.
