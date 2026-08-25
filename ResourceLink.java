
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable metadata for a study resource URL.
 *
 * <p>
 * Links are validated at construction time to keep broken or malformed URLs out
 * of persisted study sets.
 */
public final class ResourceLink {

    private final String id;
    private final String topic;
    private final String title;
    private final ResourceType type;
    private final String url;

    /**
     * Creates a resource link with explicit id control for persistence.
     */
    public ResourceLink(String id, String topic, String title, ResourceType type, String url) {
        this.id = normalizeId(id);
        this.topic = normalizeTopic(topic);
        this.title = requireNonBlank(title, "title");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.url = validateHttpUrl(url);
    }

    /**
     * Creates a resource link and generates a UUID id automatically.
     */
    public ResourceLink(String topic, String title, ResourceType type, String url) {
        this(null, topic, title, type, url);
    }

    /**
     * Returns the stable identity key for this resource.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the normalized topic used to relate resources to content.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the display title shown in the UI.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the semantic category for this resource.
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * Returns the validated HTTP(S) URL.
     */
    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResourceLink that)) {
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
        return "ResourceLink{id='" + id + "', title='" + title + "', type=" + type + "}";
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
     * Normalizes missing topics to a safe default bucket.
     */
    private static String normalizeTopic(String value) {
        if (value == null || value.isBlank()) {
            return "General";
        }
        return value.trim();
    }

    /**
     * Validates required non-empty string fields.
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null/blank");
        }
        return value.trim();
    }

    /**
     * Validates URL syntax and enforces http/https schemes with non-empty host.
     */
    private static String validateHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("url must not be null/blank");
        }

        String trimmed = value.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Malformed URL: " + value, ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("URL scheme must be http or https: " + value);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL host must not be empty: " + value);
        }

        return uri.toString();
    }
}
