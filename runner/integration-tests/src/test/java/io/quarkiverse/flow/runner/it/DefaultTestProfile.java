package io.quarkiverse.flow.runner.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Default test profile for integration tests that don't test security features.
 * Explicitly sets security.type=none to ensure tests run without authentication.
 */
public class DefaultTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // Explicitly disable security for non-security tests
                "quarkus.flow.runner.security.type", "none",

                // Disable the default OIDC tenant in every non-OIDC user
                "quarkus.oidc.tenant-enabled", "false",

                // Use random port to avoid conflicts
                "quarkus.http.test-port", "0",

                // Disable Keycloak DevServices (not needed for NONE security mode)
                "quarkus.oidc.devservices.enabled", "false",
                "quarkus.keycloak.devservices.enabled", "false",
                "quarkus.oidc.auth-server-url", "http://localhost.not.used");
    }

    @Override
    public String getConfigProfile() {
        return "default-test";
    }
}
