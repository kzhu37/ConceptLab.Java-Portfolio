
import java.util.UUID;

/**
 * Immutable flashcard entity used by generated and persisted study sets.
 *
 * <p>
 * Identity is stable by {@code id}. Two flashcards with the same id are treated
 * as the same logical item even if other fields differ.
 */
public final class Flashcard {

    private final String id;
    private final String topic;
    private final String front;
    private final String back;

    /**
     * Creates a flashcard with explicit id control for persistence round-trips.
     */
    public Flashcard(String id, String topic, String front, String back) {
        this.id = normalizeId(id);
        this.topic = normalizeTopic(topic);
        this.front = requireNonBlank(front, "front");
        this.back = requireNonBlank(back, "back");
    }

    /**
     * Creates a flashcard and generates a UUID id automatically.
     */
    public Flashcard(String topic, String front, String back) {
        this(null, topic, front, back);
    }

    /**
     * Returns the stable identity key for this flashcard.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the normalized topic label used for grouping and display.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the prompt side of the flashcard.
     */
    public String getFront() {
        return front;
    }

    /**
     * Returns the explanation side of the flashcard.
     */
    public String getBack() {
        return back;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Flashcard that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Flashcard{id='" + id + "', topic='" + topic + "'}";
    }

    /**
     * Generates a UUID when the caller does not provide a usable id.
     */
    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    /**
     * Normalizes missing topics to a safe default.
     */
    private static String normalizeTopic(String value) {
        if (value == null || value.isBlank()) {
            return "General";
        }
        return value.trim();
    }

    /**
     * Rejects null or blank required fields and trims valid input.
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null/blank");
        }
        return value.trim();
    }
}
