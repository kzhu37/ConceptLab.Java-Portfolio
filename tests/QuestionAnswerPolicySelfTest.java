/**
 * Regression checks for conservative deterministic answer matching.
 *
 * <p>Run with: java -ea -cp .:tests QuestionAnswerPolicySelfTest
 */
public final class QuestionAnswerPolicySelfTest {
    private QuestionAnswerPolicySelfTest() {}

    public static void main(String[] args) {
        testShortAnswerPolicy();
        testMcqPolicy();
        System.out.println("ConceptLab question-answer policy self-test: PASS");
    }

    private static void testShortAnswerPolicy() {
        Question knownKey = new Question(
                "Momentum",
                "Why is momentum conserved in an isolated collision?",
                "external net impulse is zero",
                "With no net external impulse, total momentum cannot change.",
                0.70,
                false
        );

        assert knownKey.checkAnswerText("External net impulse is zero.");
        assert knownKey.checkAnswerText(
                "Momentum stays constant because the external net impulse is zero in the isolated system."
        );
        assert !knownKey.checkAnswerText("external impulse");
        assert !knownKey.checkAnswerText("zero");
        assert !knownKey.checkAnswerText("");
        assert !knownKey.checkAnswerText(null);

        Question noKey = new Question(
                "Energy",
                "Explain the transfer of energy.",
                "",
                "",
                0.60,
                false
        );
        assert !noKey.checkAnswerText("A plausible but unverified response");
    }

    private static void testMcqPolicy() {
        Question mcq = new Question(
                "Forces",
                "A 2 kg cart accelerates at 3 m/s^2. What net force acts on it?",
                new String[] {"2 N", "5 N", "6 N", "9 N"},
                2,
                new String[] {
                    "Use F = ma.",
                    "Add is not the correct operation.",
                    "Correct: F = 6 N.",
                    "This is too large."
                },
                0.55,
                false
        );

        assert mcq.checkAnswer(2);
        assert !mcq.checkAnswer(0);
    }
}
