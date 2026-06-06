package io.quarkiverse.flow.runner.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for NONE security mode (development mode).
 * Configures the runner with security.type=none, which allows all requests without authentication.
 */
public class SecurityNoneProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // Disable security (development mode)
                "quarkus.flow.runner.security.type", "none",

                // Use random port to avoid conflicts
                "quarkus.http.test-port", "0",

                // Disable Keycloak DevServices (not needed for NONE security mode)
                "quarkus.oidc.devservices.enabled", "false",
                "quarkus.keycloak.devservices.enabled", "false",
                "quarkus.oidc.auth-server-url", "http://localhost.not.used");
    }

    @Override
    public String getConfigProfile() {
        return "security-none";
    }
}
