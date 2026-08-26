#!/usr/bin/env python3
"""Build the CheerpJ browser artifact from the canonical ConceptLab sources.

The desktop sources remain the source of truth. This script copies them into a
temporary build directory, applies the smallest browser-only boundary patches,
then compiles Java 17 bytecode for CheerpJ 4.3.
"""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_SOURCES = [
    "EscapeUtil.java",
    "Flashcard.java",
    "LoadingScreenFacts.java",
    "Main.java",
    "Question.java",
    "ResourceLink.java",
    "ResourceType.java",
    "StudySet.java",
]


def replace_once(text: str, pattern: str, replacement: str, label: str, *, regex: bool = False) -> str:
    if regex:
        updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    else:
        count = text.count(pattern)
        updated = text.replace(pattern, replacement, 1)
    if count != 1:
        raise RuntimeError(f"Browser patch {label!r} expected exactly one match, found {count}")
    return updated


def patch_main(source: str) -> str:
    api_block = r"    // API configuration\.\n.*?\n\n    // Core color palette\."
    api_replacement = '''    // API configuration. Desktop builds use local environment credentials.
    // The browser artifact uses a narrow same-origin Vercel proxy and never embeds a provider key.
    private static final boolean BROWSER_MODE = Boolean.parseBoolean(
            System.getProperty("conceptlab.browser", "false"));
    private static final String GROQ_API_KEY_PRIMARY = BROWSER_MODE
            ? "browser-proxy"
            : System.getenv("GROQ_API_KEY_PRIMARY");
    private static final String GROQ_API_KEY_SECONDARY = BROWSER_MODE
            ? null
            : System.getenv("GROQ_API_KEY_SECONDARY");
    private static final String GROQ_MODEL_OPENAI = BROWSER_MODE
            ? "openai/gpt-oss-20b"
            : "openai/gpt-oss-120b";
    private static final String GROQ_MODEL_FALLBACK = BROWSER_MODE
            ? "openai/gpt-oss-120b"
            : "openai/gpt-oss-20b";
    private static final String GROQ_API_URL = BROWSER_MODE
            ? System.getProperty("conceptlab.api.url", "http://localhost:3000/api/conceptlab/ai")
            : "https://api.groq.com/openai/v1/chat/completions";
    private static final int GROQ_ATTEMPTS_PER_COMBINATION = BROWSER_MODE ? 2 : 3;
    private static final int GROQ_MAX_OUTPUT_TOKENS = 4096;
    private static final int GROQ_MIN_OUTPUT_TOKENS = 256;
    private static final int GROQ_TPM_BUDGET = 8000;
    private static final int GROQ_TOKEN_SAFETY_MARGIN = 500;
    private static final int GROQ_QUESTION_BATCH_SIZE = 6;
    private static final int GROQ_SOURCE_CHUNK_CHARS = 6000;
    private static final int GROQ_FORBIDDEN_ITEMS_PER_BATCH = 30;

    // Core color palette.'''
    source = replace_once(source, api_block, api_replacement, "API boundary", regex=True)

    source = replace_once(
        source,
        "    private ProgressRing dashboardRing;\n",
        "    private ProgressRing dashboardRing;\n    private volatile JLabel generationStatusLabel;\n",
        "status field",
    )

    storage_marker = '''    /**
     * Boots storage, constructs the main frame, and shows the start screen.
     */
    private void start() {
        ensureStorageDirectory();
        reloadSetsFromDisk(false);
'''
    storage_replacement = '''    /**
     * Seeds (or resets) the browser demo inside CheerpJ's persistent /files area.
     * Desktop builds never enter this path.
     */
    private void prepareBrowserDemoStorage() {
        if (!BROWSER_MODE) {
            return;
        }
        try {
            Files.createDirectories(setsDir);
            Path demoTarget = setsDir.resolve("Newtonian_Mechanics.clab");
            boolean resetRequested = Boolean.parseBoolean(
                    System.getProperty("conceptlab.resetDemo", "false"));
            if (resetRequested) {
                // Reset only the bundled browser demo. User-created or imported
                // StudySets in the persistent browser profile must never be removed.
                Files.deleteIfExists(demoTarget);
            }

            if (!Files.isRegularFile(demoTarget)) {
                Path seed = Paths.get("/app/demo/newtonian-mechanics.clab");
                if (Files.isRegularFile(seed)) {
                    Files.copy(seed, demoTarget);
                } else {
                    System.err.println("[ConceptLab][Browser] Demo seed file was not available at " + seed);
                }
            }
        } catch (Exception ex) {
            System.err.println("[ConceptLab][Browser] Could not prepare demo storage: " + ex.getMessage());
        }
    }

    /**
     * Boots storage, constructs the main frame, and shows the start screen.
     */
    private void start() {
        ensureStorageDirectory();
        prepareBrowserDemoStorage();
        reloadSetsFromDisk(false);
'''
    source = replace_once(source, storage_marker, storage_replacement, "browser storage seed")

    source = replace_once(
        source,
        '.uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))',
        '.uri(URI.create(GROQ_API_URL))',
        "API URL",
    )
    source = replace_once(
        source,
        '.header("Authorization", "Bearer " + apiKey)\n                        .timeout',
        '.header("Authorization", "Bearer " + apiKey)\n                        .header("X-ConceptLab-Client", BROWSER_MODE ? "browser-v1" : "desktop-v1")\n                        .timeout',
        "browser request marker",
    )

    call_marker = '''    private String callGroqChat(String systemMessage, String userMessage, double temperature) throws Exception {
        throwIfInterrupted();
'''
    call_replacement = '''    private String callGroqChat(String systemMessage, String userMessage, double temperature) throws Exception {
        throwIfInterrupted();
        publishGenerationStatus("Preparing a secure AI request...");
'''
    source = replace_once(source, call_marker, call_replacement, "initial API status")

    combo_marker = '''        for (int i = 0; i < combinations.size(); i++) {
            ApiCombo combo = combinations.get(i);
            try {
'''
    combo_replacement = '''        for (int i = 0; i < combinations.size(); i++) {
            ApiCombo combo = combinations.get(i);
            if (i == 0) {
                publishGenerationStatus("Generating with the primary AI model...");
            } else if (combo.model.equals(GROQ_MODEL_FALLBACK)) {
                publishGenerationStatus("Trying backup generation...");
            } else {
                publishGenerationStatus("Retrying generation...");
            }
            try {
'''
    source = replace_once(source, combo_marker, combo_replacement, "model progress")

    failure_marker = '''        int totalAttempts = combinations.size() * GROQ_ATTEMPTS_PER_COMBINATION;
        throw new IOException("API call failed after all " + totalAttempts + " key/model attempts.", lastEx);
'''
    failure_replacement = '''        int totalAttempts = combinations.size() * GROQ_ATTEMPTS_PER_COMBINATION;
        publishGenerationStatus("AI generation could not finish. Using backup generation...");
        throw new IOException("API call failed after all " + totalAttempts + " key/model attempts.", lastEx);
'''
    source = replace_once(source, failure_marker, failure_replacement, "fallback status")

    retry_marker = '''            } catch (ApiCallException ex) {
                lastEx = ex;
                if (attempt + 1 < maxAttempts) {
                    if (ex.statusCode >= 500 || ex.statusCode == 408 || ex.statusCode == 429) {
                        sleepForGroqRetry(ex.retryDelayMillis, attempt);
                    }
                    continue;
                }
'''
    retry_replacement = '''            } catch (ApiCallException ex) {
                lastEx = ex;
                if (attempt + 1 < maxAttempts) {
                    if (ex.statusCode == 429) {
                        publishGenerationStatus("The AI service is busy. Retrying shortly...");
                    } else if (ex.statusCode >= 500 || ex.statusCode == 408) {
                        publishGenerationStatus("The AI service was temporarily unavailable. Retrying...");
                    } else {
                        publishGenerationStatus("The generated response was not usable. Retrying...");
                    }
                    if (ex.statusCode >= 500 || ex.statusCode == 408 || ex.statusCode == 429) {
                        sleepForGroqRetry(ex.retryDelayMillis, attempt);
                    }
                    continue;
                }
'''
    source = replace_once(source, retry_marker, retry_replacement, "retry status")

    batch_marker = '''            int batchCount = Math.min(GROQ_QUESTION_BATCH_SIZE, targetCount - questions.size());
            String batchSource = sourceChunks.get(batchIndex % sourceChunks.size());
'''
    batch_replacement = '''            int batchCount = Math.min(GROQ_QUESTION_BATCH_SIZE, targetCount - questions.size());
            publishGenerationStatus("Generating question batch " + (batchIndex + 1)
                    + " of up to " + maxBatchCalls + "...");
            String batchSource = sourceChunks.get(batchIndex % sourceChunks.size());
'''
    source = replace_once(source, batch_marker, batch_replacement, "batch status")

    partial_marker = '''        if (questions.isEmpty() && lastBatchFailure != null) {
            throw lastBatchFailure;
        }
        if (questions.size() > targetCount) {
'''
    partial_replacement = '''        if (questions.isEmpty() && lastBatchFailure != null) {
            throw lastBatchFailure;
        }
        if (questions.size() < targetCount) {
            publishGenerationStatus("Generated " + questions.size() + " of " + targetCount
                    + " unique questions. You can continue with the valid results.");
        } else {
            publishGenerationStatus("Checking generated questions...");
        }
        if (questions.size() > targetCount) {
'''
    source = replace_once(source, partial_marker, partial_replacement, "partial success status")

    loading_marker = '''    /**
     * Runs background work behind a modal loading dialog with cancel support.
     */
    private <T> void runWithLoading(
'''
    loading_helpers = '''    /** Updates the active loading dialog from background generation code. */
    private void publishGenerationStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        JLabel label = generationStatusLabel;
        if (label == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (generationStatusLabel == label) {
                label.setText(status);
            }
        });
    }

    /** Maps technical failures to user-safe categories without exposing provider details. */
    private static String friendlyFailureCategory(Throwable cause) {
        String raw = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        if (raw.contains("429") || raw.contains("rate")) {
            return "The AI service is busy right now.";
        }
        if (raw.contains("timeout") || raw.contains("timed out") || raw.contains("408")) {
            return "The AI request timed out.";
        }
        if (raw.contains("too large") || raw.contains("413") || raw.contains("context") || raw.contains("token")) {
            return "This request was too large for the AI service.";
        }
        if (raw.contains("json") || raw.contains("response") || raw.contains("choice")) {
            return "The AI returned a response ConceptLab could not validate.";
        }
        if (raw.contains("connect") || raw.contains("network") || raw.contains("io error")) {
            return "ConceptLab could not reach the AI service.";
        }
        return "Generation could not finish.";
    }

    /** Shows a bounded retry action and makes the no-data-loss behavior explicit. */
    private void showOperationFailure(String title, Throwable cause, Runnable retryAction) {
        String message = friendlyFailureCategory(cause)
                + " Your existing StudySet has not been changed by this failed operation.";
        Object[] options = {"Try Again", "Close"};
        int choice = JOptionPane.showOptionDialog(
                frame,
                toHtml(message, 62),
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 0 && retryAction != null) {
            retryAction.run();
        }
    }

    /**
     * Runs background work behind a modal loading dialog with cancel support.
     */
    private <T> void runWithLoading(
'''
    source = replace_once(source, loading_marker, loading_helpers, "loading helpers")

    status_label_marker = '''        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(FONT_FIELD_LABEL);
        messageLabel.setForeground(COLOR_NAVY);
'''
    status_label_replacement = '''        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(FONT_FIELD_LABEL);
        messageLabel.setForeground(COLOR_NAVY);
        generationStatusLabel = messageLabel;
'''
    source = replace_once(source, status_label_marker, status_label_replacement, "loading status binding")

    done_marker = '''            protected void done() {
                factTimer.stop();
                dialog.dispose();
'''
    done_replacement = '''            protected void done() {
                factTimer.stop();
                generationStatusLabel = null;
                dialog.dispose();
'''
    source = replace_once(source, done_marker, done_replacement, "loading status cleanup")

    generic_error = '''                    } else {
                        showError(cause.getMessage() == null ? "Operation failed." : cause.getMessage());
                    }
'''
    generic_error_replacement = '''                    } else {
                        showOperationFailure(title, cause,
                                () -> runWithLoading(title, message, task, onSuccess));
                    }
'''
    source = replace_once(source, generic_error, generic_error_replacement, "retry dialog")

    banner = "// BROWSER ARTIFACT: generated from canonical sources by browser/build-browser.py\n"
    return banner + source


def build(output: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="conceptlab-browser-") as tmp:
        work = Path(tmp)
        src_dir = work / "src"
        classes = work / "classes"
        src_dir.mkdir()
        classes.mkdir()

        for name in JAVA_SOURCES:
            source = (ROOT / name).read_text(encoding="utf-8")
            if name == "Main.java":
                source = patch_main(source)
            (src_dir / name).write_text(source, encoding="utf-8")

        subprocess.run(
            ["javac", "--release", "17", "-d", str(classes)]
            + [str(src_dir / name) for name in JAVA_SOURCES],
            check=True,
            cwd=ROOT,
        )

        logo = ROOT / "ConceptLabLogo.png"
        if logo.exists():
            shutil.copy2(logo, classes / logo.name)

        output.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["jar", "--create", "--file", str(output), "--main-class", "Main", "-C", str(classes), "."],
            check=True,
            cwd=ROOT,
        )

        subprocess.run(["jar", "--list", "--file", str(output)], check=True, cwd=ROOT)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="ConceptLab-browser.jar")
    args = parser.parse_args()
    try:
        build((ROOT / args.output).resolve())
    except Exception as exc:
        print(f"Browser build failed: {exc}", file=sys.stderr)
        raise
