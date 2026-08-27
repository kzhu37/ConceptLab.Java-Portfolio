import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regression checks for ConceptLab generation policy and failure-safe local paths.
 *
 * <p>These tests intentionally use reflection so the portfolio can verify the
 * existing private orchestration helpers without widening the production API
 * solely for testing.
 */
public final class GenerationPolicySelfTest {
    private GenerationPolicySelfTest() {}

    public static void main(String[] args) throws Exception {
        testSourceChunking();
        testTokenBudgetBounds();
        testTokenFailureClassification();
        testGeneratedQuestionParsingRejectsMalformedItems();
        testDuplicateFiltering();
        testDeterministicFallbackQuestionsRemainValid();
        testOfflineAnswerChecking();
        System.out.println("ConceptLab generation-policy self-test: PASS");
    }

    private static void testSourceChunking() throws Exception {
        Method method = privateMethod("splitTextForApi", String.class, int.class);
        String source = String.join(" ", java.util.Collections.nCopies(
                120,
                "Newtonian mechanics connects force mass acceleration momentum and energy through testable relationships."
        ));

        @SuppressWarnings("unchecked")
        List<String> chunks = (List<String>) method.invoke(null, source, 240);

        assert chunks.size() > 1 : "long source material should be split into multiple chunks";
        for (String chunk : chunks) {
            assert !chunk.isBlank() : "source chunks must not be blank";
            assert chunk.length() <= 240 : "source chunk exceeded configured character bound";
        }
    }

    private static void testTokenBudgetBounds() throws Exception {
        Method method = privateMethod(
                "calculateGroqMaxOutputTokens",
                String.class,
                String.class
        );

        int shortPrompt = (Integer) method.invoke(null, "system", "brief prompt");
        int mediumPrompt = (Integer) method.invoke(null, "system", "x".repeat(12_000));
        int oversizedPrompt = (Integer) method.invoke(null, "system", "x".repeat(30_000));

        assert shortPrompt >= 256 && shortPrompt <= 4096 : "output budget must stay within configured bounds";
        assert mediumPrompt >= 256 && mediumPrompt <= shortPrompt : "larger prompts should not receive a larger output budget";
        assert oversizedPrompt == 256 : "very large prompts should clamp to the minimum output allowance";
    }

    private static void testTokenFailureClassification() throws Exception {
        Method method = privateMethod("isTokenRelatedFailure", int.class, String.class);

        assert (Boolean) method.invoke(null, 413, "") : "HTTP 413 should be token-related";
        assert (Boolean) method.invoke(null, 429, "") : "blank HTTP 429 should be treated as quota/rate related";
        assert (Boolean) method.invoke(null, 400, "maximum context length exceeded") : "context-length failures should be token-related";
        assert (Boolean) method.invoke(null, 500, "insufficient_quota") : "quota failures should be token-related";
        assert !(Boolean) method.invoke(null, 400, "invalid request body") : "unrelated validation failures should not be token-related";
    }

    private static void testGeneratedQuestionParsingRejectsMalformedItems() throws Exception {
        Main app = new Main();
        Method method = privateMethod("parseQuestionsFromApiJson", List.class, String.class);

        List<Object> payload = new ArrayList<>();
        payload.add(mcq(
                "Forces",
                "A 4 kg cart accelerates at 3 m/s^2. What net force acts on it?",
                List.of("7 N", "12 N", "16 N", "24 N"),
                1,
                0.62
        ));
        payload.add(mcq(
                "Forces",
                "Which duplicate-choice item should be rejected?",
                List.of("same", " SAME ", "third", "fourth"),
                0,
                0.5
        ));
        payload.add(mcq(
                "Forces",
                "Which wrong-size item should be rejected?",
                List.of("one", "two", "three"),
                0,
                0.5
        ));

        Map<String, Object> blankPrompt = new LinkedHashMap<>();
        blankPrompt.put("topic", "Forces");
        blankPrompt.put("response_type", "MCQ");
        blankPrompt.put("prompt", " ");
        blankPrompt.put("choices", List.of("A", "B", "C", "D"));
        blankPrompt.put("correct_index", 0);
        blankPrompt.put("difficulty", 0.5);
        blankPrompt.put("challenge", false);
        payload.add(blankPrompt);

        Map<String, Object> shortAnswer = new LinkedHashMap<>();
        shortAnswer.put("topic", "Energy");
        shortAnswer.put("response_type", "SHORT_ANSWER");
        shortAnswer.put("prompt", "Explain why doubling speed quadruples kinetic energy.");
        shortAnswer.put("choices", List.of());
        shortAnswer.put("correct_index", -1);
        shortAnswer.put("difficulty", 0.75);
        shortAnswer.put("challenge", true);
        payload.add(shortAnswer);

        @SuppressWarnings("unchecked")
        List<Question> parsed = (List<Question>) method.invoke(app, payload, "Mechanics");

        assert parsed.size() == 2 : "only structurally valid generated questions should survive parsing";
        assert parsed.get(0).isMultipleChoice();
        assert parsed.get(0).getChoices().length == 4;
        assert parsed.get(1).getResponseType() == Question.ResponseType.SHORT_ANSWER;
        assert parsed.get(1).getChoices().length == 0;
    }

    private static void testDuplicateFiltering() throws Exception {
        Main app = new Main();
        Method method = privateMethod("filterUnique", List.class, Set.class, Set.class);

        Question first = new Question(
                "Momentum",
                "Why is momentum conserved in an isolated collision?",
                new String[] {"No external impulse", "Mass is zero", "Speed is fixed", "Energy cannot change"},
                0,
                new String[0],
                0.7,
                false
        );
        Question repeatedPrompt = new Question(
                "Momentum",
                "  WHY is momentum conserved in an isolated collision?  ",
                new String[] {"External impulse is zero", "Mass disappears", "Speed is fixed", "Energy is constant"},
                0,
                new String[0],
                0.72,
                false
        );
        Question repeatedAnswer = new Question(
                "Momentum",
                "What condition keeps total momentum unchanged?",
                new String[] {"No external impulse", "Every object stops", "Mass vanishes", "Time stops"},
                0,
                new String[0],
                0.68,
                false
        );

        Set<String> blockedPrompts = new LinkedHashSet<>();
        Set<String> blockedAnswers = new LinkedHashSet<>();

        @SuppressWarnings("unchecked")
        List<Question> filtered = (List<Question>) method.invoke(
                app,
                List.of(first, repeatedPrompt, repeatedAnswer),
                blockedPrompts,
                blockedAnswers
        );

        assert filtered.size() == 1 : "normalized duplicate prompts and correct answers should be filtered";
        assert blockedPrompts.contains(first.normalizedPromptKey());
        assert blockedAnswers.contains(first.normalizedCorrectAnswerKey());
    }

    private static void testDeterministicFallbackQuestionsRemainValid() throws Exception {
        Main app = new Main();
        Method method = privateMethod(
                "generateQuestions",
                List.class,
                int.class,
                double.class,
                boolean.class,
                int.class,
                String.class,
                Set.class,
                Set.class,
                boolean.class
        );

        List<String> facets = List.of(
                "Newton's second law relates net force, mass, and acceleration for an object.",
                "Momentum equals mass times velocity and changes when an impulse acts.",
                "Kinetic energy depends on mass and the square of an object's speed.",
                "Mechanical energy can transfer between kinetic and potential forms.",
                "An isolated system has no net external impulse acting across the interaction.",
                "Work connects an applied force with displacement along the force direction.",
                "Acceleration describes the rate at which velocity changes over time.",
                "Free-body diagrams isolate an object and represent the external forces acting on it."
        );

        Set<String> blockedPrompts = new LinkedHashSet<>();
        Set<String> blockedAnswers = new LinkedHashSet<>();

        @SuppressWarnings("unchecked")
        List<Question> generated = (List<Question>) method.invoke(
                app,
                facets,
                8,
                0.65,
                true,
                0,
                "Newtonian Mechanics",
                blockedPrompts,
                blockedAnswers,
                false
        );

        assert generated.size() == 8 : "fallback generation should satisfy the requested count for a sufficient facet pool";

        Set<String> prompts = new HashSet<>();
        Set<String> answers = new HashSet<>();
        for (Question question : generated) {
            assert question.isMultipleChoice() : "0 percent open response should produce MCQs";
            assert question.getChoices().length == 4 : "fallback MCQs must keep four choices";
            assert question.getCorrectIndex() >= 0 && question.getCorrectIndex() < 4;
            assert question.getDifficulty() >= 0.0 && question.getDifficulty() <= 1.0;
            assert prompts.add(question.normalizedPromptKey()) : "fallback prompts must remain unique";
            assert answers.add(question.normalizedCorrectAnswerKey()) : "fallback correct-answer text must remain unique";
        }

        assert blockedPrompts.size() == generated.size() : "generated prompts should be registered in the blocked set";
        assert blockedAnswers.size() == generated.size() : "generated answers should be registered in the blocked set";
    }

    private static void testOfflineAnswerChecking() throws Exception {
        Main app = new Main();
        Method method = privateMethod(
                "fallbackAnswerCheck",
                Question.class,
                String.class,
                Integer.class
        );

        Question mcq = new Question(
                "Forces",
                "A 2 kg cart accelerates at 3 m/s^2. What is the net force?",
                new String[] {"2 N", "5 N", "6 N", "9 N"},
                2,
                new String[] {"Use F = ma.", "Add is not the correct operation.", "Correct: F = 6 N.", "This is too large."},
                0.55,
                false
        );

        Object correctResult = method.invoke(app, mcq, "6 N", Integer.valueOf(2));
        assert booleanField(correctResult, "correct") : "known-answer MCQ fallback should grade deterministically";
        assert stringField(correctResult, "feedback").contains("Correct") : "correct fallback should include useful feedback";

        Question remoteOnlyShortAnswer = new Question(
                null,
                "Energy",
                "Explain the energy transfer in this system.",
                Question.ResponseType.SHORT_ANSWER,
                new String[0],
                -1,
                new String[0],
                "",
                "",
                0.7,
                false
        );

        Object unavailableResult = method.invoke(app, remoteOnlyShortAnswer, "Some reasoning", null);
        assert !booleanField(unavailableResult, "correct");
        assert stringField(unavailableResult, "feedback").contains("Automatic grading is currently unavailable")
                : "fallback must explain when a remote-only short answer cannot be graded";
    }

    private static Map<String, Object> mcq(
            String topic,
            String prompt,
            List<String> choices,
            int correctIndex,
            double difficulty
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("topic", topic);
        item.put("response_type", "MCQ");
        item.put("prompt", prompt);
        item.put("choices", choices);
        item.put("correct_index", correctIndex);
        item.put("difficulty", difficulty);
        item.put("challenge", false);
        return item;
    }

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method method = Main.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static boolean booleanField(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(object);
    }

    private static String stringField(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return String.valueOf(field.get(object));
    }
}
