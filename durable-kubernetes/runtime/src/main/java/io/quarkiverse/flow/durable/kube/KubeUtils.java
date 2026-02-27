package io.quarkiverse.flow.durable.kube;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;

public final class KubeUtils {
    private KubeUtils() {
    }

    static Optional<String> ownerName(HasMetadata obj, String kind) {
        List<OwnerReference> owners = obj.getMetadata() != null ? obj.getMetadata().getOwnerReferences() : null;
        if (owners == null || owners.isEmpty()) {
            return Optional.empty();
        }
        return owners.stream()
                .filter(o -> kind.equals(o.getKind()))
                .sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getController()),
                        Boolean.TRUE.equals(a.getController())))
                .map(OwnerReference::getName)
                .findFirst();
    }

    static Map<String, String> mergeMaps(Map<String, String> current, Map<String, String> add) {
        if ((current == null || current.isEmpty()) && (add == null || add.isEmpty())) {
            return Collections.emptyMap();
        }
        Map<String, String> merged = new HashMap<>();
        if (current != null) {
            merged.putAll(current);
        }
        if (add != null) {
            merged.putAll(add);
        }
        return merged;
    }

    static boolean mapsEqual(Map<String, String> a, Map<String, String> b) {
        if (a == null || a.isEmpty()) {
            return b == null || b.isEmpty();
        }
        return a.equals(b);
    }
}
