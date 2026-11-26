package io.quarkiverse.flow.runner.it;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile.TestResourceEntry;

/**
 * Test profile for the service-to-service Kubernetes ServiceAccount authentication
 * mechanism (issue #1160).
 * <p>
 * Security type is NONE so that only the Kubernetes mechanism is expected to
 * act on ServiceAccount-shaped Bearer tokens (garbage tokens make it abstain
 * and fall through to the permit-all/anonymous path). The Kubernetes API
 * server is mocked with WireMock ({@link KubeApiServerResource}); TokenReview
 * responses are stubbed per token in {@link SecurityKubernetesIT}.
 */
public class SecurityKubernetesProfile implements QuarkusTestProfile {

    /**
     * Trusted ServiceAccount, configured in {@code trusted-service-accounts}
     * as a full Kubernetes username: grants the {@code *} (all) namespaces
     * authorization.
     */
    static final String TRUSTED_SA = "system:serviceaccount:infrans:gateway";

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();

        // Permit-all security: the Kubernetes mechanism is the only active
        // authentication for ServiceAccount-shaped tokens
        config.put("quarkus.flow.runner.security.type", "none");

        // Always activate the Kubernetes SA service-to-service mechanism (even off-cluster)
        config.put("quarkus.flow.runner.security.service.kubernetes.enabled", "true");

        // Trusted ServiceAccounts (full Kubernetes usernames)
        config.put("quarkus.flow.runner.security.service.kubernetes.trusted-service-accounts", TRUSTED_SA);

        // Namespace validation must be ON for the ABAC cases
        config.put("quarkus.flow.runner.security.namespace.validate", "true");

        // Disable the default OIDC tenant in every non-OIDC profile
        config.put("quarkus.oidc.tenant-enabled", "false");
        config.put("quarkus.oidc.devservices.enabled", "false");
        config.put("quarkus.keycloak.devservices.enabled", "false");
        config.put("quarkus.oidc.auth-server-url", "http://localhost.not.used");

        // Use random port to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        // The WireMock kube-apiserver (KubeApiServerResource) is the only API
        // server for the extension-managed client: no Dev Services cluster
        config.put("quarkus.kubernetes-client.devservices.enabled", "false");

        return config;
    }

    @Override
    public String getConfigProfile() {
        return "security-kubernetes";
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KubeApiServerResource.class));
    }

    /**
     * Starts a WireMock server standing in for the Kubernetes API server and
     * points the extension-managed Kubernetes client at it via the standard
     * {@code quarkus.kubernetes-client.*} properties. The client sends no
     * {@code Authorization} header off-cluster (no projected token file), so
     * the stubs accept any credentials.
     */
    public static class KubeApiServerResource implements QuarkusTestResourceLifecycleManager {

        private static WireMockServer server;

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            server.start();
            WireMock.configureFor("localhost", server.port());
            return Map.of(
                    "quarkus.kubernetes-client.api-server-url", server.baseUrl(),
                    "quarkus.kubernetes-client.trust-certs", "true");
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
                server = null;
            }
        }

        static WireMockServer server() {
            if (server == null) {
                throw new IllegalStateException("WireMock kube-apiserver not started");
            }
            return server;
        }
    }
}
