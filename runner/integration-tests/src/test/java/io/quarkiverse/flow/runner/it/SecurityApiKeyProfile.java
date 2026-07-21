package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for API Key authentication mode.
 * Configures the runner with API_KEY security type and two test API keys.
 */
public class SecurityApiKeyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Enable API Key authentication
        config.put("quarkus.flow.runner.security.type", "api-key");

        // Admin API key with flow-admin role
        config.put("quarkus.flow.runner.security.api-keys.admin-key.secret", "test-admin-secret-key-123");
        config.put("quarkus.flow.runner.security.api-keys.admin-key.roles", "flow-admin");

        // Disable the default OIDC tenant in every non-OIDC user
        config.put("quarkus.oidc.tenant-enabled", "false");

        // Invoker API key with flow-admin role
        config.put(
                "quarkus.flow.runner.security.api-keys.admin-key.secret",
                "test-admin-secret-key-123");
        config.put(
                "quarkus.flow.runner.security.api-keys.admin-key.roles",
                "flow-admin");

        // Invoker API key with flow-invoker role
        config.put(
                "quarkus.flow.runner.security.api-keys.invoker-key.secret",
                "test-invoker-secret-key-456");
        config.put(
                "quarkus.flow.runner.security.api-keys.invoker-key.roles",
                "flow-invoker");

        // Disable namespace validation for simpler testing
        config.put(
                "quarkus.flow.runner.security.namespace.validate",
                "false");

        // Use random port to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        // Disable Keycloak DevServices (not needed for API_KEY security mode)
        config.put("quarkus.oidc.devservices.enabled", "false");
        config.put("quarkus.keycloak.devservices.enabled", "false");
        config.put("quarkus.oidc.auth-server-url", "http://localhost.not.used");

        return config;
    }

    @Override
    public String getConfigProfile() {
        return "security-api-key";
    }
}
