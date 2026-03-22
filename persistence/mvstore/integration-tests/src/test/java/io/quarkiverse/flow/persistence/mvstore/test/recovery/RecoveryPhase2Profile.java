package io.quarkiverse.flow.persistence.mvstore.test.recovery;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class RecoveryPhase2Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "recovery.phase", "phase2",
                "quarkus.flow.persistence.mvstore.db-path", "target/recovery-mvstore.db");
    }
}
