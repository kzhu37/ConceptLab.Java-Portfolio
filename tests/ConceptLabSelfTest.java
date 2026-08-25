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
}
