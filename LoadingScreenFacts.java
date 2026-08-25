
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides short educational facts for loading dialogs.
 *
 * <p>
 * The application uses two access modes: deterministic rotation for the first
 * visible fact, and random sampling while long-running background work
 * continues.
 */
public final class LoadingScreenFacts {

    /**
     * Shared fact bank displayed during generation and grading operations.
     */
    public static final String[] FACTS = {
        "Active recall strengthens memory more than re-reading.",
        "Spaced repetition usually beats cramming.",
        "Teaching a concept is a fast understanding check.",
        "Short focused sessions often outperform marathon study blocks.",
        "A byte is 8 bits.",
        "Java Strings are immutable objects.",
        "The JVM enables cross-platform Java bytecode execution.",
        "Hash maps are near O(1) average lookup time.",
        "Most bugs come from wrong assumptions.",
        "Clear variable names reduce maintenance cost.",
        "Unit tests should be small and deterministic.",
        "Reading stack traces carefully saves debugging time.",
        "Arrays have fixed size; ArrayLists can grow.",
        "Loop boundaries are common off-by-one bug sources.",
        "Escaping delimiters protects save-file integrity.",
        "UUIDs are designed for global uniqueness.",
        "Good MCQ distractors mirror common misconceptions.",
        "Asking why often reveals deeper understanding than asking what.",
        "Diagrams can reduce cognitive load for complex topics.",
        "Sleep helps consolidate learning.",
        "The speed of light is about 300,000 km/s.",
        "Water expands when it freezes.",
        "Earth's atmosphere is mostly nitrogen and oxygen.",
        "Force equals mass times acceleration.",
        "Density is mass divided by volume.",
        "Power is the rate of doing work.",
        "Kinetic energy is one-half m v squared.",
        "Momentum is mass times velocity.",
        "Frequency is measured in hertz.",
        "Sound needs a medium to travel.",
        "Light can travel through a vacuum.",
        "In triangles, interior angles sum to 180 degrees.",
        "Sine and cosine map angle to side ratios.",
        "Functions map inputs to outputs.",
        "Exponential growth multiplies by a factor each step.",
        "Correlation does not imply causation.",
        "Standard deviation measures spread.",
        "The median is the middle sorted value.",
        "Photosynthesis stores energy in sugars.",
        "Cellular respiration releases stored chemical energy.",
        "Proteins are chains of amino acids.",
        "DNA uses four primary bases.",
        "Avogadro's number is about 6.022 x 10^23.",
        "Catalysts speed reactions without being consumed.",
        "Work is force times displacement along the force direction.",
        "Good software design prefers cohesion over coupling.",
        "Input validation prevents many runtime failures.",
        "Graceful error handling improves user trust.",
        "Consistent file formats simplify future migrations.",
        "Browser-based interactives can improve concept retention.",
        "Practice under test-like conditions improves performance.",
        "Feedback is most useful when it is immediate and specific.",
        "Complex tasks are easier after decomposition into steps.",
        "Reliable persistence needs both write and read validation.",
        "Guard clauses can make code paths easier to reason about.",
        "Small reversible changes reduce risk in refactors.",
        "Deterministic generators are easier to test than random-only output.",
        "Strong invariants make bugs obvious earlier.",
        "UI state and data state should stay synchronized.",
        "Good defaults reduce user friction."
    };

    /**
     * Global pointer for deterministic round-robin access.
     */
    private static final AtomicInteger NEXT_INDEX = new AtomicInteger(0);

    private LoadingScreenFacts() {
        // Utility class.
    }

    /**
     * Returns a random fact with uniform distribution across the fact bank.
     */
    public static String randomFact() {
        return FACTS[ThreadLocalRandom.current().nextInt(FACTS.length)];
    }

    /**
     * Returns the next fact in stable rotation order.
     *
     * <p>
     * Using floorMod keeps index wrapping correct even if the atomic value
     * overflows.
     */
    public static String nextFact() {
        int index = Math.floorMod(NEXT_INDEX.getAndIncrement(), FACTS.length);
        return FACTS[index];
    }
}
