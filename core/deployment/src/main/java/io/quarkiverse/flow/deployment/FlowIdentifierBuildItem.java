package io.quarkiverse.flow.deployment;

import java.util.Objects;
import java.util.Set;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item representing a set of flow identifiers.
 */
public final class FlowIdentifierBuildItem extends MultiBuildItem {

    private final Set<String> identifiers;

    public FlowIdentifierBuildItem(Set<String> identifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "'identifiers' must not be null");
    }

    public Set<String> identifiers() {
        return identifiers;
    }
}
