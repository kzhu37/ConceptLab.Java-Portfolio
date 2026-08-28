
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable assessment item model.
 *
 * <p>
 * A question can be either multiple-choice or short-answer. The constructor
 * enforces structural invariants so invalid data is rejected immediately.
 */
public final class Question {

    /**
     * Supported answer interaction modes.
     */
    public enum ResponseType {
        MCQ,
        SHORT_ANSWER;

        /**
         * Parses legacy and variant labels into a canonical response type.
         */
        static ResponseType from(String raw) {
            if (raw == null) {
                return MCQ;
            }
            String key = raw.trim().toUpperCase(Locale.ROOT);
            if ("SHORT".equals(key) || "SHORTANSWER".equals(key) || "SHORT_ANSWER".equals(key) || "FREE_RESPONSE".equals(key)) {
                return SHORT_ANSWER;
            }
            return MCQ;
        }
    }

    private final String id;
    private final String topic;
    private final String prompt;
    private final ResponseType responseType;
    private final String[] choices;
    private final int correctIndex;
    private final String[] feedback;
    private final String answerKey;
    private final String solution;
    private final double difficulty;
    private final boolean challenge;

    /**
     * Canonical constructor used by persistence and API parsing.
     */
    public Question(
            String id,
            String topic,
            String prompt,
            ResponseType responseType,
            String[] choices,
            int correctIndex,
            String[] feedback,
            String answerKey,
            String solution,
            double difficulty,
            boolean isChallenge
    ) {
        this.id = normalizeId(id);
        this.topic = normalizeTopic(topic);
        this.prompt = requireNonBlank(prompt, "prompt");
        this.responseType = responseType == null ? ResponseType.MCQ : responseType;
        this.difficulty = validateDifficulty(difficulty);
        this.challenge = isChallenge;

        // Branch by response mode so each question type keeps consistent structure.
        if (this.responseType == ResponseType.MCQ) {
            this.choices = validateChoices(choices);
            this.correctIndex = validateMcqIndex(correctIndex, this.choices.length, "correctIndex");
            this.feedback = normalizeFeedback(feedback, this.choices.length);
            this.answerKey = nonBlankOr(this.choices[this.correctIndex], answerKey);
            this.solution = normalizeOptional(solution);
        } else {
            this.choices = new String[0];
            this.correctIndex = -1;
            this.feedback = normalizeFeedback(feedback, 0);
            this.answerKey = normalizeOptional(answerKey);
            this.solution = normalizeOptional(solution);
        }
    }

    /**
     * Backward-compatible MCQ constructor retained for older call sites.
     */
    public Question(
            String id,
            String topic,
            String prompt,
            String[] choices,
            int correctIndex,
            String[] feedback,
            double difficulty,
            boolean isChallenge
    ) {
        this(id, topic, prompt, ResponseType.MCQ, choices, correctIndex, feedback, null, null, difficulty, isChallenge);
    }

    /**
     * Convenience MCQ constructor that auto-generates id.
     */
    public Question(
            String topic,
            String prompt,
            String[] choices,
            int correctIndex,
            String[] feedback,
            double difficulty,
            boolean isChallenge
    ) {
        this(null, topic, prompt, choices, correctIndex, feedback, difficulty, isChallenge);
    }

    /**
     * Convenience constructor for short-answer questions.
     */
    public Question(
            String topic,
            String prompt,
            String answerKey,
            String solution,
            double difficulty,
            boolean isChallenge
    ) {
        this(null, topic, prompt, ResponseType.SHORT_ANSWER, new String[0], -1, new String[0], answerKey, solution, difficulty, isChallenge);
    }

    /**
     * Returns the stable identity key for this question.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the normalized topic label used for organization.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the question text shown to learners.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Returns how the question expects to be answered.
     */
    public ResponseType getResponseType() {
        return responseType;
    }

    /**
     * Returns true when this question is in multiple-choice mode.
     */
    public boolean isMultipleChoice() {
        return responseType == ResponseType.MCQ;
    }

    /**
     * Returns a defensive copy of answer choices.
     */
    public String[] getChoices() {
        return Arrays.copyOf(choices, choices.length);
    }

    /**
     * Returns the correct choice index for MCQ items, or -1 for short-answer
     * items.
     */
    public int getCorrectIndex() {
        return correctIndex;
    }

    /**
     * Returns per-choice feedback text as a defensive copy.
     */
    public String[] getFeedback() {
        return Arrays.copyOf(feedback, feedback.length);
    }

    /**
     * Returns the canonical answer key text.
     */
    public String getAnswerKey() {
        return answerKey;
    }

    /**
     * Returns worked solution text if available.
     */
    public String getSolution() {
        return solution;
    }

    /**
     * Returns difficulty in normalized range [0,1].
     */
    public double getDifficulty() {
        return difficulty;
    }

    /**
     * Returns whether this question was marked as challenge-style.
     */
    public boolean isChallenge() {
        return challenge;
    }

    /**
     * Evaluates MCQ correctness by selected index.
     */
    public boolean checkAnswer(int chosenIndex) {
        if (!isMultipleChoice()) {
            return false;
        }
        validateMcqIndex(chosenIndex, choices.length, "chosenIndex");
        return chosenIndex == correctIndex;
    }

    /**
     * Evaluates a known-key short answer conservatively after normalization.
     *
     * <p>
     * Exact matches are accepted. A longer response is also accepted when it
     * contains the complete stored key. Partial fragments are not accepted
     * merely because they appear inside the key.
     */
    public boolean checkAnswerText(String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) {
            return false;
        }
        String user = normalizeKey(userAnswer);
        String key = normalizeKey(answerKey);
        if (key.isBlank()) {
            return false;
        }
        return user.equals(key) || user.contains(key);
    }

    /**
     * Returns feedback for a selected choice or short-answer response context.
     */
    public String feedbackForChoice(int chosenIndex) {
        if (!isMultipleChoice()) {
            return !solution.isBlank() ? solution : "Review the worked solution and key concept connections.";
        }

        validateMcqIndex(chosenIndex, choices.length, "chosenIndex");
        if (feedback.length >= choices.length) {
            return feedback[chosenIndex];
        }
        if (chosenIndex == correctIndex && !solution.isBlank()) {
            return solution;
        }
        return chosenIndex == correctIndex ? "Correct." : "Not correct.";
    }

    /**
     * Lowercase + trim + non-alphanumeric-to-space + collapsed whitespace.
     */
    public String normalizedPromptKey() {
        return normalizeKey(prompt);
    }

    /**
     * Lowercase + trim + non-alphanumeric-to-space + collapsed whitespace.
     */
    public String normalizedCorrectAnswerKey() {
        return normalizeKey(answerKey);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Question question)) {
            return false;
        }
        return id.equals(question.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Question{id='" + id + "', type=" + responseType + ", topic='" + topic + "', difficulty=" + difficulty + "}";
    }

    /**
     * Generates a UUID when no usable id is provided.
     */
    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    /**
     * Normalizes missing topic values to a safe default.
     */
    private static String normalizeTopic(String value) {
        if (value == null || value.isBlank()) {
            return "General";
        }
        return value.trim();
    }

    /**
     * Validates finite difficulty values in the closed interval [0,1].
     */
    private static double validateDifficulty(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("difficulty must be finite");
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("difficulty must be in range [0,1]");
        }
        return value;
    }

    /**
     * Validates MCQ index bounds for arrays of known size.
     */
    private static int validateMcqIndex(int value, int choiceCount, String fieldName) {
        if (value < 0 || value >= choiceCount) {
            throw new IllegalArgumentException(fieldName + " must be in range [0," + (choiceCount - 1) + "]");
        }
        return value;
    }

    /**
     * Enforces fixed-size, non-blank, and uniqueness constraints for MCQ
     * options.
     */
    private static String[] validateChoices(String[] value) {
        Objects.requireNonNull(value, "choices must not be null");
        if (value.length != 4) {
            throw new IllegalArgumentException("choices must have length 4");
        }

        String[] copy = Arrays.copyOf(value, value.length);
        String[] normalized = new String[copy.length];
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] == null || copy[i].isBlank()) {
                throw new IllegalArgumentException("choices[" + i + "] must not be null/blank");
            }
            copy[i] = copy[i].trim();
            normalized[i] = normalizeKey(copy[i]);
            for (int j = 0; j < i; j++) {
                if (normalized[j].equals(normalized[i])) {
                    throw new IllegalArgumentException("choices must be distinct after normalization");
                }
            }
        }
        return copy;
    }

    /**
     * Normalizes feedback arrays and pads when needed for MCQ compatibility.
     */
    private static String[] normalizeFeedback(String[] value, int expectedChoices) {
        if (value == null) {
            return expectedChoices > 0 ? blankArray(expectedChoices) : new String[0];
        }

        String[] copy = Arrays.copyOf(value, value.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = normalizeOptional(copy[i]);
        }

        if (expectedChoices > 0) {
            if (copy.length == expectedChoices) {
                return copy;
            }
            if (copy.length == 0) {
                return blankArray(expectedChoices);
            }
            String[] padded = blankArray(expectedChoices);
            for (int i = 0; i < Math.min(expectedChoices, copy.length); i++) {
                padded[i] = copy[i];
            }
            return padded;
        }

        return copy;
    }

    /**
     * Builds an array of empty strings with fixed size.
     */
    private static String[] blankArray(int size) {
        String[] out = new String[size];
        Arrays.fill(out, "");
        return out;
    }

    /**
     * Validates required text fields and trims valid values.
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null/blank");
        }
        return value.trim();
    }

    /**
     * Chooses the first non-blank value with fallback behavior.
     */
    private static String nonBlankOr(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "";
    }

    /**
     * Converts nullable optional text into a trimmed, never-null string.
     */
    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Produces a normalized matching key for duplicate detection and answer
     * checks.
     */
    private static String normalizeKey(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceAll("[^a-z0-9]", " ");
        return normalized.trim().replaceAll("\\s+", " ");
    }
}
