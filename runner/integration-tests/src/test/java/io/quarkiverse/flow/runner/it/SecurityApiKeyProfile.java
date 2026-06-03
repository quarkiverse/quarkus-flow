package io.quarkiverse.flow.runner.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for API Key authentication mode.
 * Configures the runner with API_KEY security type and two test API keys.
 */
public class SecurityApiKeyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // Enable API Key authentication
                "quarkus.flow.runner.security.type", "api-key",

                // Admin API key with flow-admin role
                "quarkus.flow.runner.security.api-keys.admin-key.secret", "test-admin-secret-key-123",
                "quarkus.flow.runner.security.api-keys.admin-key.roles", "flow-admin",

                // Invoker API key with flow-invoker role
                "quarkus.flow.runner.security.api-keys.invoker-key.secret", "test-invoker-secret-key-456",
                "quarkus.flow.runner.security.api-keys.invoker-key.roles", "flow-invoker",

                // Disable namespace validation for simpler testing
                "quarkus.flow.runner.security.namespace.validate", "false",

                // Use random port to avoid conflicts
                "quarkus.http.test-port", "0");
    }

    @Override
    public String getConfigProfile() {
        return "security-api-key";
    }
}
