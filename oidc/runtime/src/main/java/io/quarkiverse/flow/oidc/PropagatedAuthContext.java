package io.quarkiverse.flow.oidc;

import io.quarkus.security.identity.SecurityIdentity;

public final class PropagatedAuthContext {

    public record Snapshot(SecurityIdentity identity) {
    }

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private PropagatedAuthContext() {
    }

    static void set(Snapshot snapshot) {
        CURRENT.set(snapshot);
    }

    static void clear() {
        CURRENT.remove();
    }

    static Snapshot current() {
        return CURRENT.get();
    }
}
