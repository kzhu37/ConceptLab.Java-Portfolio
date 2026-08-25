
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent aggregate root for one ConceptLab study set.
 *
 * <p>
 * This class stores durable learning content and is responsible for: input
 * validation, duplicate prevention, and round-trip file serialization.
 */
public final class StudySet {

    /**
     * Current file signature and version marker.
     */
    public static final String FILE_HEADER = "CONCEPTLAB_STUDYSET|v4";

    /**
     * Section names used by the plain-text persistence format.
     */
    private static final String SECTION_META = "META";
    private static final String SECTION_FLASHCARDS = "FLASHCARDS";
    private static final String SECTION_PRACTICE = "PRACTICE";
    private static final String SECTION_UNITTEST = "UNITTEST";
    private static final String SECTION_RESOURCES = "RESOURCES";

    private final String id;
    private final String title;
    private final List<Flashcard> flashcards;
    private final List<Question> practiceQuestions;
    private final List<Question> unitTestQuestions;
    private final List<ResourceLink> resources;
    private double bestUnitTestPercent;

    /**
     * Canonical constructor for loading and creating full sets.
     */
    public StudySet(
            String id,
            String title,
            List<Flashcard> flashcards,
            List<Question> practiceQuestions,
            List<Question> unitTestQuestions,
            List<ResourceLink> resources,
            double bestUnitTestPercent
    ) {
        this.id = normalizeId(id);
        this.title = requireNonBlank(title, "title");
        this.flashcards = copyFlashcards(flashcards);

        this.practiceQuestions = new ArrayList<>();
        this.unitTestQuestions = new ArrayList<>();
        replacePracticeQuestions(practiceQuestions);
        replaceUnitTestQuestions(unitTestQuestions);

        this.resources = copyResources(resources);
        this.bestUnitTestPercent = normalizeBestPercent(bestUnitTestPercent);
    }

    /**
     * Convenience constructor that auto-generates set id.
     */
    public StudySet(
            String title,
            List<Flashcard> flashcards,
            List<Question> practiceQuestions,
            List<Question> unitTestQuestions,
            List<ResourceLink> resources,
            double bestUnitTestPercent
    ) {
        this(null, title, flashcards, practiceQuestions, unitTestQuestions, resources, bestUnitTestPercent);
    }

    /**
     * Creates an empty set with only title populated.
     */
    public StudySet(String title) {
        this(title, List.of(), List.of(), List.of(), List.of(), -1.0);
    }

    /**
     * Returns the stable set id.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable set title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns an immutable view of flashcards.
     */
    public List<Flashcard> getFlashcards() {
        return Collections.unmodifiableList(flashcards);
    }

    /**
     * Returns an immutable view of practice questions.
     */
    public List<Question> getPracticeQuestions() {
        return Collections.unmodifiableList(practiceQuestions);
    }

    /**
     * Returns an immutable view of unit test questions.
     */
    public List<Question> getUnitTestQuestions() {
        return Collections.unmodifiableList(unitTestQuestions);
    }

    /**
     * Returns an immutable view of linked resources.
     */
    public List<ResourceLink> getResources() {
        return Collections.unmodifiableList(resources);
    }

    /**
     * Returns the best recorded unit-test percentage, or -1 when unavailable.
     */
    public double getBestUnitTestPercent() {
        return bestUnitTestPercent;
    }

    /**
     * Adds one flashcard and enforces id uniqueness inside this set.
     */
    public void addFlashcard(Flashcard flashcard) {
        Flashcard card = Objects.requireNonNull(flashcard, "flashcard must not be null");
        if (containsFlashcardId(card.getId())) {
            throw new IllegalArgumentException("Duplicate flashcard id: " + card.getId());
        }
        flashcards.add(card);
    }

    /**
     * Adds a practice question while enforcing duplicate and overlap rules.
     */
    public void addPracticeQuestion(Question question) {
        Question q = Objects.requireNonNull(question, "question must not be null");
        ensureQuestionAllowed(q, practiceQuestions, unitTestQuestions, "practice");
        practiceQuestions.add(q);
    }

    /**
     * Adds a unit test question while enforcing duplicate and overlap rules.
     */
    public void addUnitTestQuestion(Question question) {
        Question q = Objects.requireNonNull(question, "question must not be null");
        ensureQuestionAllowed(q, unitTestQuestions, practiceQuestions, "unit test");
        unitTestQuestions.add(q);
    }

    /**
     * Replaces the entire practice bank after full validation.
     */
    public void replacePracticeQuestions(List<Question> questions) {
        practiceQuestions.clear();
        if (questions == null) {
            return;
        }
        for (Question question : questions) {
            addPracticeQuestion(question);
        }
    }

    /**
     * Replaces the entire unit test bank after full validation.
     */
    public void replaceUnitTestQuestions(List<Question> questions) {
        unitTestQuestions.clear();
        if (questions == null) {
            return;
        }
        for (Question question : questions) {
            addUnitTestQuestion(question);
        }
    }

    /**
     * Adds one resource and enforces id uniqueness inside this set.
     */
    public void addResource(ResourceLink resource) {
        ResourceLink link = Objects.requireNonNull(resource, "resource must not be null");
        if (containsResourceId(link.getId())) {
            throw new IllegalArgumentException("Duplicate resource id: " + link.getId());
        }
        resources.add(link);
    }

    /**
     * Updates best score by keeping the historical maximum.
     */
    public void updateBestUnitTestPercent(double percent) {
        double normalized = clampPercent(percent);
        if (bestUnitTestPercent < 0.0) {
            bestUnitTestPercent = normalized;
            return;
        }
        bestUnitTestPercent = Math.max(bestUnitTestPercent, normalized);
    }

    /**
     * Produces a file-system-safe stem derived from title or id.
     */
    public String getSafeFileStem() {
        String compact = title.replaceAll("\\s+", "_").trim();
        String safe = compact.replaceAll("[^A-Za-z0-9_-]", "");
        return safe.isBlank() ? id : safe;
    }

    /**
     * Serializes this study set to the versioned text format.
     */
    public void storeToFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(FILE_HEADER);
            writer.newLine();

            writer.write(SECTION_META);
            writer.newLine();
            writer.write("id=" + EscapeUtil.encode(id));
            writer.newLine();
            writer.write("title=" + EscapeUtil.encode(title));
            writer.newLine();
            writer.write("bestUnitTestPercent=" + bestUnitTestPercent);
            writer.newLine();
            writer.newLine();

            writer.write(SECTION_FLASHCARDS + "|N=" + flashcards.size());
            writer.newLine();
            for (Flashcard card : flashcards) {
                writer.write(toFlashcardLine(card));
                writer.newLine();
            }
            writer.newLine();

            writer.write(SECTION_PRACTICE + "|N=" + practiceQuestions.size());
            writer.newLine();
            for (Question question : practiceQuestions) {
                writer.write(toQuestionLine(question));
                writer.newLine();
            }
            writer.newLine();

            writer.write(SECTION_UNITTEST + "|N=" + unitTestQuestions.size());
            writer.newLine();
            for (Question question : unitTestQuestions) {
                writer.write(toQuestionLine(question));
                writer.newLine();
            }
            writer.newLine();

            writer.write(SECTION_RESOURCES + "|N=" + resources.size());
            writer.newLine();
            for (ResourceLink resource : resources) {
                writer.write(toResourceLine(resource));
                writer.newLine();
            }
        }
    }

    /**
     * Parses and validates a persisted study set file.
     */
    public static StudySet readFromFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("Study set file is empty: " + file);
        }

        String header = lines.get(0).trim();
        if (!header.startsWith("CONCEPTLAB_STUDYSET|v")) {
            throw new IOException("Unsupported study set header: " + header);
        }

        String id = null;
        String title = null;
        double bestPercent = -1.0;
        List<Flashcard> flashcards = new ArrayList<>();
        List<Question> practiceQuestions = new ArrayList<>();
        List<Question> unitTestQuestions = new ArrayList<>();
        List<ResourceLink> resources = new ArrayList<>();

        Section section = Section.NONE;
        int expectedFlashcards = -1;
        int expectedPractice = -1;
        int expectedUnit = -1;
        int expectedResources = -1;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            int lineNumber = i + 1;

            if (trimmed.isEmpty()) {
                continue;
            }

            if (SECTION_META.equals(trimmed)) {
                section = Section.META;
                continue;
            }
            if (trimmed.startsWith(SECTION_FLASHCARDS + "|N=")) {
                expectedFlashcards = parseSectionCount(trimmed, lineNumber, line, SECTION_FLASHCARDS);
                section = Section.FLASHCARDS;
                continue;
            }
            if (trimmed.startsWith(SECTION_PRACTICE + "|N=")) {
                expectedPractice = parseSectionCount(trimmed, lineNumber, line, SECTION_PRACTICE);
                section = Section.PRACTICE;
                continue;
            }
            if (trimmed.startsWith(SECTION_UNITTEST + "|N=")) {
                expectedUnit = parseSectionCount(trimmed, lineNumber, line, SECTION_UNITTEST);
                section = Section.UNITTEST;
                continue;
            }
            if (trimmed.startsWith(SECTION_RESOURCES + "|N=")) {
                expectedResources = parseSectionCount(trimmed, lineNumber, line, SECTION_RESOURCES);
                section = Section.RESOURCES;
                continue;
            }

            try {
                switch (section) {
                    case META -> {
                        int eq = line.indexOf('=');
                        if (eq <= 0) {
                            throw malformed(lineNumber, line, "META line must be key=value");
                        }
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1);
                        switch (key) {
                            case "id", "setId" ->
                                id = EscapeUtil.decode(value);
                            case "title" ->
                                title = EscapeUtil.decode(value);
                            case "bestUnitTestPercent" ->
                                bestPercent = parseDouble(value, lineNumber, line);
                            default -> {
                                // Ignore unknown keys for forward compatibility.
                            }
                        }
                    }
                    case FLASHCARDS ->
                        flashcards.add(parseFlashcardLine(line, lineNumber));
                    case PRACTICE ->
                        practiceQuestions.add(parseQuestionLine(line, lineNumber));
                    case UNITTEST ->
                        unitTestQuestions.add(parseQuestionLine(line, lineNumber));
                    case RESOURCES ->
                        resources.add(parseResourceLine(line, lineNumber));
                    case NONE ->
                        throw malformed(lineNumber, line, "Found data before section header");
                }
            } catch (IllegalArgumentException ex) {
                throw malformed(lineNumber, line, ex.getMessage());
            }
        }

        if (title == null || title.isBlank()) {
            throw new IOException("Missing required META field: title");
        }

        if (expectedFlashcards >= 0 && expectedFlashcards != flashcards.size()) {
            throw new IOException("FLASHCARDS count mismatch. expected=" + expectedFlashcards + " actual=" + flashcards.size());
        }
        if (expectedPractice >= 0 && expectedPractice != practiceQuestions.size()) {
            throw new IOException("PRACTICE count mismatch. expected=" + expectedPractice + " actual=" + practiceQuestions.size());
        }
        if (expectedUnit >= 0 && expectedUnit != unitTestQuestions.size()) {
            throw new IOException("UNITTEST count mismatch. expected=" + expectedUnit + " actual=" + unitTestQuestions.size());
        }
        if (expectedResources >= 0 && expectedResources != resources.size()) {
            throw new IOException("RESOURCES count mismatch. expected=" + expectedResources + " actual=" + resources.size());
        }

        return new StudySet(id, title, flashcards, practiceQuestions, unitTestQuestions, resources, bestPercent);
    }

    private boolean containsFlashcardId(String candidateId) {
        return flashcards.stream().anyMatch(item -> item.getId().equals(candidateId));
    }

    private boolean containsResourceId(String candidateId) {
        return resources.stream().anyMatch(item -> item.getId().equals(candidateId));
    }

    /**
     * Enforces identity and normalized prompt uniqueness inside and across
     * banks.
     */
    private void ensureQuestionAllowed(
            Question question,
            List<Question> targetBank,
            List<Question> otherBank,
            String bankName
    ) {
        if (targetBank.stream().anyMatch(existing -> existing.getId().equals(question.getId()))) {
            throw new IllegalArgumentException("Duplicate " + bankName + " question id: " + question.getId());
        }

        String promptKey = question.normalizedPromptKey();
        if (targetBank.stream().anyMatch(existing -> existing.normalizedPromptKey().equals(promptKey))) {
            throw new IllegalArgumentException("Duplicate " + bankName + " question prompt: " + question.getPrompt());
        }

        if (otherBank.stream().anyMatch(existing -> existing.normalizedPromptKey().equals(promptKey))) {
            throw new IllegalArgumentException("Practice and unit test banks must be disjoint by prompt");
        }
    }

    /**
     * Converts one flashcard object to a durable line record.
     */
    private static String toFlashcardLine(Flashcard flashcard) {
        return String.join(
                "|",
                "FC",
                EscapeUtil.encode(flashcard.getId()),
                EscapeUtil.encode(flashcard.getTopic()),
                EscapeUtil.encode(flashcard.getFront()),
                EscapeUtil.encode(flashcard.getBack())
        );
    }

    /**
     * Converts one question object to the v4 durable line format.
     */
    private static String toQuestionLine(Question question) {
        List<String> parts = new ArrayList<>();
        String[] choices = question.getChoices();
        String[] feedback = question.getFeedback();

        parts.add("Q4");
        parts.add(EscapeUtil.encode(question.getId()));
        parts.add(EscapeUtil.encode(question.getTopic()));
        parts.add(Double.toString(question.getDifficulty()));
        parts.add(Boolean.toString(question.isChallenge()));
        parts.add(question.getResponseType().name());
        parts.add(EscapeUtil.encode(question.getPrompt()));
        parts.add(Integer.toString(question.getCorrectIndex()));
        parts.add(EscapeUtil.encode(question.getAnswerKey()));
        parts.add(EscapeUtil.encode(question.getSolution()));
        parts.add(Integer.toString(choices.length));
        for (String choice : choices) {
            parts.add(EscapeUtil.encode(choice));
        }
        parts.add(Integer.toString(feedback.length));
        for (String item : feedback) {
            parts.add(EscapeUtil.encode(item));
        }
        return String.join("|", parts);
    }

    /**
     * Converts one resource object to a durable line record.
     */
    private static String toResourceLine(ResourceLink resource) {
        return String.join(
                "|",
                "R",
                EscapeUtil.encode(resource.getId()),
                EscapeUtil.encode(resource.getTopic()),
                resource.getType().name(),
                EscapeUtil.encode(resource.getTitle()),
                EscapeUtil.encode(resource.getUrl())
        );
    }

    /**
     * Parses one flashcard record from serialized text.
     */
    private static Flashcard parseFlashcardLine(String line, int lineNumber) throws IOException {
        List<String> parts = EscapeUtil.splitEscaped(line, '|');
        if (parts.size() != 5 || !"FC".equals(parts.get(0))) {
            throw malformed(lineNumber, line, "Expected flashcard format FC|id|topic|front|back");
        }

        return new Flashcard(
                decode(parts.get(1), lineNumber, line),
                decode(parts.get(2), lineNumber, line),
                decode(parts.get(3), lineNumber, line),
                decode(parts.get(4), lineNumber, line)
        );
    }

    /**
     * Parses one question record and supports multiple legacy versions.
     */
    private static Question parseQuestionLine(String line, int lineNumber) throws IOException {
        List<String> parts = EscapeUtil.splitEscaped(line, '|');

        if (!"Q".equals(parts.get(0)) && !"Q4".equals(parts.get(0))) {
            throw malformed(lineNumber, line, "Expected question format starting with Q");
        }

        // v4 flexible format
        if ("Q4".equals(parts.get(0))) {
            if (parts.size() < 12) {
                throw malformed(lineNumber, line, "Q4 question field count mismatch");
            }

            String id = decode(parts.get(1), lineNumber, line);
            String topic = decode(parts.get(2), lineNumber, line);
            double difficulty = parseDouble(parts.get(3), lineNumber, line);
            boolean challenge = parseBoolean(parts.get(4), lineNumber, line, "isChallenge");
            Question.ResponseType responseType = Question.ResponseType.from(parts.get(5));
            String prompt = decode(parts.get(6), lineNumber, line);
            int correctIndex = parseInt(parts.get(7), lineNumber, line, "correctIndex");
            String answerKey = decode(parts.get(8), lineNumber, line);
            String solution = decode(parts.get(9), lineNumber, line);

            int cursor = 10;
            int choiceCount = parseInt(parts.get(cursor), lineNumber, line, "choiceCount");
            cursor++;
            if (choiceCount < 0) {
                throw malformed(lineNumber, line, "choiceCount must be >= 0");
            }
            if (cursor + choiceCount > parts.size()) {
                throw malformed(lineNumber, line, "choiceCount exceeds available fields");
            }
            String[] choices = new String[choiceCount];
            for (int i = 0; i < choiceCount; i++) {
                choices[i] = decode(parts.get(cursor + i), lineNumber, line);
            }
            cursor += choiceCount;

            if (cursor >= parts.size()) {
                throw malformed(lineNumber, line, "Missing feedbackCount");
            }
            int feedbackCount = parseInt(parts.get(cursor), lineNumber, line, "feedbackCount");
            cursor++;
            if (feedbackCount < 0) {
                throw malformed(lineNumber, line, "feedbackCount must be >= 0");
            }
            if (cursor + feedbackCount != parts.size()) {
                throw malformed(lineNumber, line, "feedbackCount mismatch");
            }
            String[] feedback = new String[feedbackCount];
            for (int i = 0; i < feedbackCount; i++) {
                feedback[i] = decode(parts.get(cursor + i), lineNumber, line);
            }

            return new Question(
                    id,
                    topic,
                    prompt,
                    responseType,
                    choices,
                    correctIndex,
                    feedback,
                    answerKey,
                    solution,
                    difficulty,
                    challenge
            );
        }

        // v3 format: Q + 14 values = 15 parts.
        if (parts.size() == 15) {
            String[] choices = {
                decode(parts.get(6), lineNumber, line),
                decode(parts.get(7), lineNumber, line),
                decode(parts.get(8), lineNumber, line),
                decode(parts.get(9), lineNumber, line)
            };
            String[] feedback = {
                decode(parts.get(11), lineNumber, line),
                decode(parts.get(12), lineNumber, line),
                decode(parts.get(13), lineNumber, line),
                decode(parts.get(14), lineNumber, line)
            };

            return new Question(
                    decode(parts.get(1), lineNumber, line),
                    decode(parts.get(2), lineNumber, line),
                    decode(parts.get(5), lineNumber, line),
                    choices,
                    parseInt(parts.get(10), lineNumber, line, "correctIndex"),
                    feedback,
                    parseDouble(parts.get(3), lineNumber, line),
                    parseBoolean(parts.get(4), lineNumber, line, "isChallenge")
            );
        }

        // Backward compatibility with old format that includes two extra related-id fields.
        if (parts.size() == 17) {
            String[] choices = {
                decode(parts.get(6), lineNumber, line),
                decode(parts.get(7), lineNumber, line),
                decode(parts.get(8), lineNumber, line),
                decode(parts.get(9), lineNumber, line)
            };
            String[] feedback = {
                decode(parts.get(11), lineNumber, line),
                decode(parts.get(12), lineNumber, line),
                decode(parts.get(13), lineNumber, line),
                decode(parts.get(14), lineNumber, line)
            };

            return new Question(
                    decode(parts.get(1), lineNumber, line),
                    decode(parts.get(2), lineNumber, line),
                    decode(parts.get(5), lineNumber, line),
                    choices,
                    parseInt(parts.get(10), lineNumber, line, "correctIndex"),
                    feedback,
                    parseDouble(parts.get(3), lineNumber, line),
                    parseBoolean(parts.get(4), lineNumber, line, "isChallenge")
            );
        }

        throw malformed(lineNumber, line, "Question field count mismatch");
    }

    /**
     * Parses one resource record and supports older version variants.
     */
    private static ResourceLink parseResourceLine(String line, int lineNumber) throws IOException {
        List<String> parts = EscapeUtil.splitEscaped(line, '|');
        if (!"R".equals(parts.get(0))) {
            throw malformed(lineNumber, line, "Expected resource format starting with R");
        }

        ResourceType type;
        try {
            type = ResourceType.valueOf(parts.get(3).trim());
        } catch (IllegalArgumentException ex) {
            throw malformed(lineNumber, line, "Unknown resource type: " + parts.get(3));
        }

        // v3: R|id|topic|type|title|url
        if (parts.size() == 6) {
            return new ResourceLink(
                    decode(parts.get(1), lineNumber, line),
                    decode(parts.get(2), lineNumber, line),
                    decode(parts.get(4), lineNumber, line),
                    type,
                    decode(parts.get(5), lineNumber, line)
            );
        }

        // v2: R|id|topic|type|name|notes|url
        if (parts.size() == 7) {
            return new ResourceLink(
                    decode(parts.get(1), lineNumber, line),
                    decode(parts.get(2), lineNumber, line),
                    decode(parts.get(4), lineNumber, line),
                    type,
                    decode(parts.get(6), lineNumber, line)
            );
        }

        throw malformed(lineNumber, line, "Resource field count mismatch");
    }

    /**
     * Parses section count declarations such as FLASHCARDS|N=12.
     */
    private static int parseSectionCount(String trimmed, int lineNumber, String line, String sectionName) throws IOException {
        String prefix = sectionName + "|N=";
        if (!trimmed.startsWith(prefix)) {
            throw malformed(lineNumber, line, "Invalid section header for " + sectionName);
        }

        String raw = trimmed.substring(prefix.length()).trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw malformed(lineNumber, line, "Invalid section count for " + sectionName + ": " + raw);
        }
    }

    /**
     * Decodes one escaped token and maps decode errors to file context.
     */
    private static String decode(String encoded, int lineNumber, String line) throws IOException {
        try {
            return EscapeUtil.decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw malformed(lineNumber, line, ex.getMessage());
        }
    }

    /**
     * Parses an integer field with contextual error messages.
     */
    private static int parseInt(String raw, int lineNumber, String line, String fieldName) throws IOException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw malformed(lineNumber, line, "Invalid integer for " + fieldName + ": " + raw);
        }
    }

    /**
     * Parses a floating-point field with contextual error messages.
     */
    private static double parseDouble(String raw, int lineNumber, String line) throws IOException {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            throw malformed(lineNumber, line, "Invalid difficulty/double value: " + raw);
        }
    }

    /**
     * Parses strict boolean text values.
     */
    private static boolean parseBoolean(String raw, int lineNumber, String line, String fieldName) throws IOException {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw malformed(lineNumber, line, "Invalid boolean for " + fieldName + ": " + raw);
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
     * Validates required text fields.
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null/blank");
        }
        return value.trim();
    }

    /**
     * Defensive-copy utility that also validates flashcard id uniqueness.
     */
    private static List<Flashcard> copyFlashcards(List<Flashcard> source) {
        if (source == null) {
            return new ArrayList<>();
        }

        List<Flashcard> copy = new ArrayList<>(source.size());
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < source.size(); i++) {
            Flashcard item = Objects.requireNonNull(source.get(i), "flashcards contains null at index " + i);
            if (!ids.add(item.getId())) {
                throw new IllegalArgumentException("Duplicate flashcard id: " + item.getId());
            }
            copy.add(item);
        }
        return copy;
    }

    /**
     * Defensive-copy utility that also validates resource id uniqueness.
     */
    private static List<ResourceLink> copyResources(List<ResourceLink> source) {
        if (source == null) {
            return new ArrayList<>();
        }

        List<ResourceLink> copy = new ArrayList<>(source.size());
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < source.size(); i++) {
            ResourceLink item = Objects.requireNonNull(source.get(i), "resources contains null at index " + i);
            if (!ids.add(item.getId())) {
                throw new IllegalArgumentException("Duplicate resource id: " + item.getId());
            }
            copy.add(item);
        }
        return copy;
    }

    /**
     * Normalizes stored best score where -1 means not yet available.
     */
    private static double normalizeBestPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("bestUnitTestPercent must be finite");
        }
        if (value < 0.0) {
            return -1.0;
        }
        return Math.min(100.0, value);
    }

    /**
     * Clamps runtime score updates into the inclusive [0,100] range.
     */
    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("percent must be finite");
        }
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(100.0, value);
    }

    /**
     * Builds a consistent parse error with line number and source content.
     */
    private static IOException malformed(int lineNumber, String line, String reason) {
        return new IOException("Malformed study set at line " + lineNumber + ": " + reason + " | content: " + line);
    }

    /**
     * Parsing cursor for the section currently being processed.
     */
    private enum Section {
        NONE,
        META,
        FLASHCARDS,
        PRACTICE,
        UNITTEST,
        RESOURCES
    }
}
