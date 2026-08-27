from pathlib import Path

README = Path("README.md")
text = README.read_text()

old_hero = '''<table>
  <tr>
    <td width="50%">
      <img src="docs/media/conceptlab-dashboard.png" alt="ConceptLab dashboard showing a Newtonian Mechanics StudySet">
    </td>
    <td width="50%">
      <img src="docs/media/browser-production.png" alt="ConceptLab browser edition running the real Java Swing application through CheerpJ">
    </td>
  </tr>
  <tr>
    <td align="center"><sub>A loaded StudySet brings flashcards, practice, unit testing, resources, and saved progress into one local workflow.</sub></td>
    <td align="center"><sub>The browser edition runs the real Swing application through CheerpJ, with persistent StudySets and a server-side AI boundary.</sub></td>
  </tr>
</table>'''
new_hero = '''<p align="center">
  <img src="docs/media/app-dashboard.png" alt="ConceptLab dashboard showing a generated Newton's Laws StudySet" width="100%">
</p>
<p align="center">
  <sub>A generated StudySet brings flashcards, fresh practice, unit testing, related resources, and saved progress into one desktop workflow.</sub>
</p>'''
if old_hero not in text:
    raise SystemExit("Current hero block not found")
text = text.replace(old_hero, new_hero, 1)

glance_marker = '''| **Verification** | Tests desktop and browser builds, persistence, AI contracts, security boundaries, reproducible UI capture, and the live deployment. |

## Engineering highlights'''
glance_insert = '''| **Verification** | Tests desktop and browser builds, persistence, AI contracts, security boundaries, reproducible UI capture, and the live deployment. |

<p align="center">
  <img src="docs/media/app-feedback-question.png" alt="ConceptLab open-response unit-test question showing an incorrect answer and targeted misconception feedback" width="100%">
</p>
<p align="center">
  <sub>An open-response mistake is evaluated for specific misconceptions instead of receiving only a score.</sub>
</p>

## Engineering highlights'''
if glance_marker not in text:
    raise SystemExit("At-a-glance insertion point not found")
text = text.replace(glance_marker, glance_insert, 1)

browser_marker = '''**Live browser:** [conceptlab-browser.vercel.app](https://conceptlab-browser.vercel.app)

## Product workflow'''
browser_insert = '''**Live browser:** [conceptlab-browser.vercel.app](https://conceptlab-browser.vercel.app)

<p align="center">
  <img src="docs/media/browser-live-quiz.png" alt="ConceptLab production browser edition running the real Swing application through CheerpJ with a loaded Newtonian Mechanics quiz" width="100%">
</p>
<p align="center">
  <sub>The public browser edition runs the real Swing application through CheerpJ while keeping StudySets persistent and AI credentials server-side.</sub>
</p>

## Product workflow'''
if browser_marker not in text:
    raise SystemExit("Browser insertion point not found")
text = text.replace(browser_marker, browser_insert, 1)

old_product = '''<table>
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

Users can change question count, difficulty, response style, challenge level, whether previously seen prompts should be avoided, and whether correct-answer text should remain unique.'''
new_product = '''<p align="center">
  <img src="docs/media/app-create-study-set.png" alt="ConceptLab StudySet creation form populated with Newton's Laws source material, goals, and custom instructions" width="100%">
</p>
<p align="center">
  <sub>Source material, explicit learning goals, and custom instructions define what the StudySet should teach before generation begins.</sub>
</p>

The system can build flashcards, a broader unit-test bank, and related learning resources. Fresh practice is generated on demand so the user is not limited to one fixed question set.

### Practice is configurable, not fixed

<p align="center">
  <img src="docs/media/app-practice-settings.png" alt="ConceptLab fresh-practice settings showing question count, difficulty, response mix, challenge, seen-question, and uniqueness controls" width="100%">
</p>
<p align="center">
  <sub>Fresh practice can be tuned by question count, difficulty, response style, challenge level, seen-question avoidance, and answer uniqueness.</sub>
</p>

Users can change question count, difficulty, response style, challenge level, whether previously seen prompts should be avoided, and whether correct-answer text should remain unique.'''
if old_product not in text:
    raise SystemExit("Current product screenshot block not found")
text = text.replace(old_product, new_product, 1)

old_feedback = '''<p align="center">
  <img src="docs/media/answer-feedback.png" alt="ConceptLab unit-test feedback after an incorrect Newtonian mechanics response" width="650">
</p>
<p align="center">
  <sub>A submitted response produces immediate correctness feedback and an explanation, turning a mistake into the next review step.</sub>
</p>'''
new_feedback = '''<p align="center">
  <img src="docs/media/app-feedback-review.png" alt="ConceptLab feedback flow connecting a Newtonian mechanics misconception to related resources and flashcards" width="100%">
</p>
<p align="center">
  <sub>After explaining the misconception, ConceptLab links the same mistake to targeted resources and related flashcards so feedback becomes the next review step.</sub>
</p>'''
if old_feedback not in text:
    raise SystemExit("Current feedback screenshot block not found")
text = text.replace(old_feedback, new_feedback, 1)

old_testing = '''The desktop product captures in this README are generated through [`tools/PortfolioCapture.java`](tools/PortfolioCapture.java). CI verifies that the real application can still produce fresh captures, but it does not automatically commit binary screenshot changes back to the repository. The browser image above is captured from the public production deployment; the separate Playwright smoke workflow also records production browser screenshots as CI artifacts.'''
new_testing = '''The product screenshots in this README are full-resolution captures from the actual desktop application, with one targeted screenshot from the public production browser edition. CI independently launches and drives the real desktop application through [`tools/PortfolioCapture.java`](tools/PortfolioCapture.java) and uploads reproducible verification captures as workflow artifacts. The Playwright production smoke workflow separately records browser evidence. The selected README images optimize clarity and presentation; the automated captures provide independent verification that the underlying behavior remains reproducible.'''
if old_testing not in text:
    raise SystemExit("Current screenshot provenance paragraph not found")
text = text.replace(old_testing, new_testing, 1)

README.write_text(text)

capture = Path("tools/PortfolioCapture.java")
capture_text = capture.read_text()
old_comment = " * Generates reproducible screenshots for the public portfolio README."
new_comment = " * Generates reproducible verification screenshots for CI and portfolio regression checks."
if old_comment not in capture_text:
    raise SystemExit("PortfolioCapture summary comment not found")
capture_text = capture_text.replace(old_comment, new_comment, 1)
old_detail = " * captures the exact screens used in the README. It does not require API keys;"
new_detail = " * captures representative product screens for verification. It does not require API keys;"
if old_detail not in capture_text:
    raise SystemExit("PortfolioCapture detail comment not found")
capture_text = capture_text.replace(old_detail, new_detail, 1)
capture.write_text(capture_text)
