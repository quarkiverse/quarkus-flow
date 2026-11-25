package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;

@DisplayName("Kubernetes in-cluster detection tests")
class KubernetesInClusterTest {

    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private FlowRunnerConfig.Security.Embed embed;
    private FlowRunnerConfig.Security.Embed.Kubernetes kubernetes;

    @BeforeEach
    void setUp() {
        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        embed = mock(FlowRunnerConfig.Security.Embed.class);
        kubernetes = mock(FlowRunnerConfig.Security.Embed.Kubernetes.class);

        when(config.security()).thenReturn(security);
        when(security.embed()).thenReturn(embed);
        when(embed.kubernetes()).thenReturn(kubernetes);
    }

    @Test
    @DisplayName("test_mechanism_active_when_enabled_true")
    void testMechanismActiveWhenEnabledTrue() {
        when(kubernetes.enabled()).thenReturn("true");

        assertThat(KubernetesInCluster.isMechanismActive(config)).isTrue();
    }

    @Test
    @DisplayName("test_mechanism_inactive_when_enabled_false")
    void testMechanismInactiveWhenEnabledFalse() {
        when(kubernetes.enabled()).thenReturn("false");

        assertThat(KubernetesInCluster.isMechanismActive(config)).isFalse();
    }

    @Test
    @DisplayName("test_mechanism_auto_requires_env_and_token_file")
    void testMechanismAutoRequiresEnvAndTokenFile() {
        // auto + not in cluster -> inactive
        assertThat(KubernetesInCluster.computeActive("auto", false)).isFalse();
        // auto + in cluster -> active
        assertThat(KubernetesInCluster.computeActive("auto", true)).isTrue();
        // auto config delegates to in-cluster detection
        when(kubernetes.enabled()).thenReturn("auto");
        assertThat(KubernetesInCluster.isMechanismActive(config))
                .isEqualTo(KubernetesInCluster.isRunningInCluster());
    }

    @Test
    @DisplayName("test_compute_active_matrix")
    void testComputeActiveMatrix() {
        assertThat(KubernetesInCluster.computeActive("true", false)).isTrue();
        assertThat(KubernetesInCluster.computeActive("true", true)).isTrue();
        assertThat(KubernetesInCluster.computeActive("false", false)).isFalse();
        assertThat(KubernetesInCluster.computeActive("false", true)).isFalse();
        // unknown values fall back to the in-cluster check (auto semantics)
        assertThat(KubernetesInCluster.computeActive(null, true)).isTrue();
        assertThat(KubernetesInCluster.computeActive(null, false)).isFalse();
    }

    @Test
    @DisplayName("test_is_running_in_cluster_reads_env_and_token_file")
    void testIsRunningInClusterReadsEnvAndTokenFile() {
        boolean expected = System.getenv("KUBERNETES_SERVICE_HOST") != null
                && java.nio.file.Files.isReadable(
                        java.nio.file.Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token"));

        assertThat(KubernetesInCluster.isRunningInCluster()).isEqualTo(expected);
    }
}
