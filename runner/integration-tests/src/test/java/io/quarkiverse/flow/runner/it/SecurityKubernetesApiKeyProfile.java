package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile.TestResourceEntry;

/**
 * Test profile pinning the API_KEY coexistence acceptance criterion of the
 * Kubernetes ServiceAccount authentication mechanism (issue #1160):
 * {@code security.type=API_KEY} (one configured key) combined with
 * {@code service.kubernetes.enabled=true} and a WireMock-mocked kube-apiserver.
 * <p>
 * The Kubernetes API server is mocked with the same
 * {@link SecurityKubernetesProfile.KubeApiServerResource} pattern as
 * {@link SecurityKubernetesProfile}; TokenReview responses are stubbed per
 * token in {@link SecurityKubernetesApiKeyIT}.
 */
public class SecurityKubernetesApiKeyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        // API_KEY security mode with a single invoker key
        config.put("quarkus.flow.runner.security.type", "api-key");
        config.put("quarkus.flow.runner.security.api-keys.invoker-key.secret", "test-invoker-secret-key-456");
        config.put("quarkus.flow.runner.security.api-keys.invoker-key.roles", "flow-invoker");

        // Always activate the Kubernetes SA service-to-service mechanism (even off-cluster)
        config.put("quarkus.flow.runner.security.service.kubernetes.enabled", "true");

        // Disable namespace validation (the API-key identity carries no namespaces claim)
        config.put("quarkus.flow.runner.security.namespace.validate", "false");

        // Disable the default OIDC tenant in every non-OIDC profile
        config.put("quarkus.oidc.tenant-enabled", "false");
        config.put("quarkus.oidc.devservices.enabled", "false");
        config.put("quarkus.keycloak.devservices.enabled", "false");
        config.put("quarkus.oidc.auth-server-url", "http://localhost.not.used");

        // Use random port to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        return config;
    }

    @Override
    public String getConfigProfile() {
        return "security-kubernetes-api-key";
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(SecurityKubernetesProfile.KubeApiServerResource.class));
    }
}
