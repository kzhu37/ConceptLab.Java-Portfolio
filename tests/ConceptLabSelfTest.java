import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dependency-free smoke tests for ConceptLab's core domain and persistence rules.
 * Run with: java -ea -cp .:tests ConceptLabSelfTest
 */
public final class ConceptLabSelfTest {
    private ConceptLabSelfTest() {}

    public static void main(String[] args) throws Exception {
        testEscapingRoundTrip();
        testQuestionInvariants();
        testResourceValidation();
        testStudySetRoundTrip();
        testCrossBankDuplicateProtection();
        testBestScoreKeepsMaximum();
        testDeclaredCountMismatchRejected();
        testLegacyQuestionFormatLoads();
        testMalformedPersistedQuestionRejected();
        System.out.println("ConceptLab self-test: PASS");
    }

    private static void testEscapingRoundTrip() {
        String original = "force|energy\\momentum\nsecond line";
        String encoded = EscapeUtil.encode(original);
        assert !encoded.equals(original) : "reserved characters should be escaped";
        assert original.equals(EscapeUtil.decode(encoded)) : "escaped text must round-trip";

        List<String> parts = EscapeUtil.splitEscaped("alpha\\|beta|gamma", '|');
        assert parts.size() == 2 : "escaped delimiter must not split a field";
        assert "alpha|beta".equals(EscapeUtil.decode(parts.get(0)));
        assert "gamma".equals(EscapeUtil.decode(parts.get(1)));
    }

    private static void testQuestionInvariants() {
        Question q = new Question(
                "Forces",
                "A 2 kg cart accelerates at 3 m/s^2. What net force acts on it?",
                new String[] {"2 N", "5 N", "6 N", "9 N"},
                2,
                new String[] {"Use F = ma.", "Add is not the correct operation.", "Correct: F = 2 x 3 = 6 N.", "This overestimates the force."},
                0.55,
                false
        );
        assert q.checkAnswer(2);
        assert !q.checkAnswer(0);
        assert !q.normalizedPromptKey().isBlank();
        assert !q.normalizedCorrectAnswerKey().isBlank();

        boolean duplicateChoicesRejected = false;
        try {
            new Question(
                    "Forces",
                    "Invalid choices?",
                    new String[] {"6 N", " 6 n ", "7 N", "8 N"},
                    0,
                    new String[0],
                    0.5,
                    false
            );
        } catch (IllegalArgumentException expected) {
            duplicateChoicesRejected = true;
        }
        assert duplicateChoicesRejected : "normalized duplicate MCQ choices must be rejected";

        boolean invalidDifficultyRejected = false;
        try {
            new Question("Topic", "Prompt", "answer", "solution", 1.2, false);
        } catch (IllegalArgumentException expected) {
            invalidDifficultyRejected = true;
        }
        assert invalidDifficultyRejected : "difficulty outside [0,1] must be rejected";
    }

    private static void testResourceValidation() {
        ResourceLink valid = new ResourceLink(
                "Momentum",
                "Khan Academy: Momentum",
                ResourceType.ARTICLE,
                "https://www.khanacademy.org/science/physics/linear-momentum"
        );
        assert valid.getUrl().startsWith("https://");

        boolean badSchemeRejected = false;
        try {
            new ResourceLink("Topic", "Bad", ResourceType.OTHER, "ftp://example.com/file");
        } catch (IllegalArgumentException expected) {
            badSchemeRejected = true;
        }
        assert badSchemeRejected : "non-http(s) resources must be rejected";
    }

    private static void testStudySetRoundTrip() throws Exception {
        Flashcard card1 = new Flashcard("Forces", "Newton's second law", "Net force equals mass times acceleration: F = ma.");
        Flashcard card2 = new Flashcard("Energy", "Kinetic energy", "KE = 1/2 mv^2.");

        Question practice = new Question(
                "Forces",
                "A 4 kg object experiences a net force of 20 N. What is its acceleration?",
                new String[] {"4 m/s^2", "5 m/s^2", "16 m/s^2", "80 m/s^2"},
                1,
                new String[] {"Divide force by mass.", "Correct: a = F/m = 20/4 = 5.", "This subtracts instead of dividing.", "This multiplies instead of dividing."},
                0.6,
                false
        );
        Question unit = new Question(
                "Energy",
                "Explain why doubling an object's speed quadruples its kinetic energy.",
                "kinetic energy depends on speed squared",
                "Because KE = 1/2 mv^2, replacing v with 2v multiplies v^2 by four.",
                0.78,
                true
        );
        ResourceLink resource = new ResourceLink(
                "Energy",
                "OpenStax: Work and Kinetic Energy",
                ResourceType.REFERENCE,
                "https://openstax.org/books/physics/pages/9-2-work-and-kinetic-energy"
        );

        StudySet original = new StudySet(
                "Mechanics Demo",
                List.of(card1, card2),
                List.of(practice),
                List.of(unit),
                List.of(resource),
                87.5
        );

        Path tempDir = Files.createTempDirectory("conceptlab-selftest-");
        Path file = tempDir.resolve("mechanics.clab");
        original.storeToFile(file);
        StudySet loaded = StudySet.readFromFile(file);

        assert original.getId().equals(loaded.getId());
        assert original.getTitle().equals(loaded.getTitle());
        assert loaded.getFlashcards().size() == 2;
        assert loaded.getPracticeQuestions().size() == 1;
        assert loaded.getUnitTestQuestions().size() == 1;
        assert loaded.getResources().size() == 1;
        assert Math.abs(loaded.getBestUnitTestPercent() - 87.5) < 0.0001;
        assert loaded.getFlashcards().get(0).getFront().equals(card1.getFront());

        Files.deleteIfExists(file);
        Files.deleteIfExists(tempDir);
    }

    private static void testCrossBankDuplicateProtection() {
        Question practice = new Question(
                "Momentum",
                "Why is momentum conserved in an isolated collision?",
                "external net impulse is zero",
                "With no net external impulse, total momentum cannot change.",
                0.7,
                false
        );
        Question overlapping = new Question(
                "Momentum",
                "  WHY is momentum conserved in an isolated collision?  ",
                "no external impulse",
                "Equivalent prompt with different formatting.",
                0.75,
                true
        );

        boolean overlapRejected = false;
        try {
            new StudySet("Duplicate Guard", List.of(), List.of(practice), List.of(overlapping), List.of(), -1.0);
        } catch (IllegalArgumentException expected) {
            overlapRejected = true;
        }
        assert overlapRejected : "practice and unit-test banks must remain disjoint by normalized prompt";
    }

    private static void testBestScoreKeepsMaximum() {
        StudySet set = new StudySet("Score Guard");
        set.updateBestUnitTestPercent(72.0);
        set.updateBestUnitTestPercent(68.0);
        assert Math.abs(set.getBestUnitTestPercent() - 72.0) < 0.0001 : "best score must not move backward";
        set.updateBestUnitTestPercent(91.0);
        assert Math.abs(set.getBestUnitTestPercent() - 91.0) < 0.0001 : "higher score should replace prior best";
    }

    private static void testDeclaredCountMismatchRejected() throws Exception {
        Path file = Files.createTempFile("conceptlab-count-mismatch-", ".clab");
        String corrupt = String.join("\n",
                StudySet.FILE_HEADER,
                "META",
                "id=count-test",
                "title=Count Test",
                "bestUnitTestPercent=-1.0",
                "",
                "FLASHCARDS|N=1",
                "",
                "PRACTICE|N=0",
                "",
                "UNITTEST|N=0",
                "",
                "RESOURCES|N=0"
        );
        Files.writeString(file, corrupt, StandardCharsets.UTF_8);

        boolean rejected = false;
        try {
            StudySet.readFromFile(file);
        } catch (IOException expected) {
            rejected = expected.getMessage().contains("FLASHCARDS count mismatch");
        }
        assert rejected : "declared section count corruption must be rejected";
        Files.deleteIfExists(file);
    }

    private static void testLegacyQuestionFormatLoads() throws Exception {
        Path file = Files.createTempFile("conceptlab-legacy-", ".clab");
        String legacy = String.join("\n",
                "CONCEPTLAB_STUDYSET|v3",
                "META",
                "id=legacy-test",
                "title=Legacy Test",
                "bestUnitTestPercent=82.0",
                "",
                "FLASHCARDS|N=0",
                "",
                "PRACTICE|N=1",
                "Q|legacy-q|Forces|0.6|false|A 3 kg object accelerates at 2 m/s^2. What is the net force?|3 N|5 N|6 N|9 N|2|Use F = ma.|Add is not correct.|Correct: 6 N.|This is too large.",
                "",
                "UNITTEST|N=0",
                "",
                "RESOURCES|N=0"
        );
        Files.writeString(file, legacy, StandardCharsets.UTF_8);

        StudySet loaded = StudySet.readFromFile(file);
        assert loaded.getPracticeQuestions().size() == 1 : "legacy question should load";
        Question q = loaded.getPracticeQuestions().get(0);
        assert "legacy-q".equals(q.getId());
        assert q.getResponseType() == Question.ResponseType.MCQ;
        assert q.checkAnswer(2);
        assert Math.abs(loaded.getBestUnitTestPercent() - 82.0) < 0.0001;

        Files.deleteIfExists(file);
    }

    private static void testMalformedPersistedQuestionRejected() throws Exception {
        Path file = Files.createTempFile("conceptlab-malformed-question-", ".clab");
        String malformed = String.join("\n",
                StudySet.FILE_HEADER,
                "META",
                "id=malformed-test",
                "title=Malformed Test",
                "bestUnitTestPercent=-1.0",
                "",
                "FLASHCARDS|N=0",
                "",
                "PRACTICE|N=1",
                "Q4|bad-q|Forces|0.5|false|MCQ|Broken question|0|||4|A|B|C",
                "",
                "UNITTEST|N=0",
                "",
                "RESOURCES|N=0"
        );
        Files.writeString(file, malformed, StandardCharsets.UTF_8);

        boolean rejected = false;
        try {
            StudySet.readFromFile(file);
        } catch (IOException expected) {
            rejected = true;
        }
        assert rejected : "malformed persisted question records must be rejected";
        Files.deleteIfExists(file);
    }
}
