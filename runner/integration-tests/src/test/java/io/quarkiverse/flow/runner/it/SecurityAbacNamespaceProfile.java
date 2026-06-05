package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for ABAC namespace authorization.
 * Configures API keys with namespace restrictions to test namespace-level access control.
 */
public class SecurityAbacNamespaceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        // Enable API Key authentication
        config.put("quarkus.flow.runner.security.type", "api-key");

        // Admin key with access to ALL namespaces (no restriction)
        config.put("quarkus.flow.runner.security.api-keys.admin-key.secret", "admin-all-namespaces");
        config.put("quarkus.flow.runner.security.api-keys.admin-key.roles", "flow-admin");

        // Team A key - restricted to team-a namespace only
        config.put("quarkus.flow.runner.security.api-keys.team-a-key.secret", "team-a-secret");
        config.put("quarkus.flow.runner.security.api-keys.team-a-key.roles", "flow-invoker");
        config.put("quarkus.flow.runner.security.api-keys.team-a-key.namespaces", "team-a");

        // Team B key - restricted to team-b namespace only
        config.put("quarkus.flow.runner.security.api-keys.team-b-key.secret", "team-b-secret");
        config.put("quarkus.flow.runner.security.api-keys.team-b-key.roles", "flow-invoker");
        config.put("quarkus.flow.runner.security.api-keys.team-b-key.namespaces", "team-b");

        // Multi-namespace key - can access both team-a and team-b
        config.put("quarkus.flow.runner.security.api-keys.multi-key.secret", "multi-namespace-secret");
        config.put("quarkus.flow.runner.security.api-keys.multi-key.roles", "flow-invoker");
        config.put("quarkus.flow.runner.security.api-keys.multi-key.namespaces", "team-a,team-b");

        // ENABLE namespace validation (critical for ABAC tests)
        config.put("quarkus.flow.runner.security.namespace.validate", "true");

        // Use random port to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        return config;
    }

    @Override
    public String getConfigProfile() {
        return "security-abac-namespace";
    }
}
