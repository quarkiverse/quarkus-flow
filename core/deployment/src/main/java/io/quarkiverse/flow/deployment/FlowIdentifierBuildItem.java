package io.quarkiverse.flow.deployment;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item representing a set of flow identifiers.
 * <p>
 * Each item carries the CDI qualifier string ({@link #identifiers()}) and an optional
 * human-readable display label ({@link #displayIdentifiers()}) used only for log output.
 * For most beans the two are identical; for versionless beans the display label includes
 * the resolved version so that the startup log is self-explanatory.
 */
public final class FlowIdentifierBuildItem extends MultiBuildItem {

    private final Set<String> identifiers;
    /** identifier → display label (may equal the identifier when no extra annotation is needed). */
    private final Map<String, String> displayIdentifiers;

    public FlowIdentifierBuildItem(Set<String> identifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "'identifiers' must not be null");
        // default: display label == CDI qualifier
        this.displayIdentifiers = identifiers.stream().collect(Collectors.toMap(id -> id, id -> id));
    }

    /**
     * Creates a build item where the versionless identifier carries an annotated display label.
     *
     * @param identifiers the CDI qualifier strings
     * @param displayIdentifiers map of {@code identifier → display label}
     */
    public FlowIdentifierBuildItem(Set<String> identifiers, Map<String, String> displayIdentifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "'identifiers' must not be null");
        this.displayIdentifiers = Objects.requireNonNull(displayIdentifiers, "'displayIdentifiers' must not be null");
    }

    /** Returns the CDI qualifier strings for these beans. */
    public Set<String> identifiers() {
        return identifiers;
    }

    /**
     * Returns a map of {@code identifier → display label} used only for log output.
     * For versionless beans the label includes the resolved version.
     */
    public Map<String, String> displayIdentifiers() {
        return displayIdentifiers;
    }
}
