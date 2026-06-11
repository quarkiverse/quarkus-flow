package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for OIDC authentication using @TestSecurity.
 * <p>
 * This profile uses {@link io.quarkus.test.security.TestSecurity} annotation
 * to mock OIDC authentication without needing a real OIDC server or JWT tokens.
 * This is the recommended approach for testing OIDC-protected endpoints in Quarkus.
 */
public class SecurityOidcProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        // Enable OIDC authentication
        config.put("quarkus.flow.runner.security.type", "oidc");

        // Configure namespace claim extraction
        config.put("quarkus.flow.runner.security.namespace.claim", "namespace");
        config.put("quarkus.flow.runner.security.namespace.validate", "true");

        // Use random port to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        return config;
    }

    @Override
    public String getConfigProfile() {
        return "security-oidc";
    }
}
