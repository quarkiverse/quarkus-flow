package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

@DisplayName("Kubernetes ServiceAccount authentication mechanism tests")
class KubernetesServiceAccountAuthenticationMechanismTest {

    /**
     * A decodable (but unverified) JWT whose payload claims
     * namespace=infrans and serviceAccountName=gateway.
     */
    /**
     * A decodable (but unverified) JWT whose payload claims
     * namespace=infrans and serviceAccountName=gateway.
     */
    private static final String SA_TOKEN = saToken("infrans", "gateway");

    private static String saToken(String namespace, String serviceAccountName) {
        String payload = "{\"kubernetes.io\":{\"serviceaccount\":{\"namespace\":\"" + namespace
                + "\",\"service-account.name\":\"" + serviceAccountName + "\"}}}";
        return base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}") + "."
                + base64Url(payload) + "."
                + base64Url("signature");
    }

    private static String base64Url(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private KubernetesServiceAccountAuthenticationMechanism mechanism;
    private TokenReviewClient tokenReviewClient;
    private TokenReviewCache tokenReviewCache;
    private FlowRunnerConfig config;
    private RoutingContext routingContext;
    private HttpServerRequest request;

    private boolean active = true;

    @BeforeEach
    void setUp() throws IOException {
        tokenReviewClient = mock(TokenReviewClient.class);
        tokenReviewCache = mock(TokenReviewCache.class);
        config = mock(FlowRunnerConfig.class);
        routingContext = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);

        FlowRunnerConfig.Security security = mock(FlowRunnerConfig.Security.class);
        FlowRunnerConfig.Security.Embed embed = mock(FlowRunnerConfig.Security.Embed.class);
        FlowRunnerConfig.Security.Embed.Kubernetes kubernetes = mock(FlowRunnerConfig.Security.Embed.Kubernetes.class);

        when(config.security()).thenReturn(security);
        when(security.embed()).thenReturn(embed);
        when(embed.kubernetes()).thenReturn(kubernetes);
        when(kubernetes.trustedServiceAccounts()).thenReturn(Optional.empty());

        when(routingContext.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + SA_TOKEN);
        when(tokenReviewCache.get(anyString())).thenReturn(Optional.empty());

        mechanism = new KubernetesServiceAccountAuthenticationMechanism() {
            @Override
            boolean isActive() {
                return active;
            }
        };
        mechanism.config = config;
        mechanism.tokenReviewClient = tokenReviewClient;
        mechanism.tokenReviewCache = tokenReviewCache;
    }

    private void trusted(String... serviceAccounts) {
        when(config.security().embed().kubernetes().trustedServiceAccounts())
                .thenReturn(Optional.of(Set.of(serviceAccounts)));
    }

    @Nested
    @DisplayName("Successful authentication")
    class SuccessfulAuthentication {

        @Test
        @DisplayName("valid_trusted_sa_gets_role_and_all_namespaces")
        void valid_trusted_sa_gets_role_and_all_namespaces() throws IOException {
            trusted("infrans/gateway");
            when(tokenReviewClient.review(SA_TOKEN)).thenReturn(
                    new TokenReviewClient.TokenReviewResult(true, "system:serviceaccount:infrans:gateway",
                            Instant.now().plusSeconds(600)));

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNotNull();
            assertThat(identity.getPrincipal().getName()).isEqualTo("system:serviceaccount:infrans:gateway");
            assertThat(identity.getRoles()).contains(AuthzConsts.ROLE_SERVICE_READER);
            assertThat(identity.<Set<String>> getAttribute(AuthzConsts.CLAIM_NAMESPACES)).containsExactly("*");
        }

        @Test
        @DisplayName("valid_untrusted_sa_gets_role_and_own_namespace_only")
        void valid_untrusted_sa_gets_role_and_own_namespace_only() throws IOException {
            when(tokenReviewClient.review(SA_TOKEN)).thenReturn(
                    new TokenReviewClient.TokenReviewResult(true, "system:serviceaccount:infrans:gateway",
                            Instant.now().plusSeconds(600)));

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNotNull();
            assertThat(identity.getRoles()).contains(AuthzConsts.ROLE_SERVICE_READER);
            assertThat(identity.<Set<String>> getAttribute(AuthzConsts.CLAIM_NAMESPACES)).containsExactly("infrans");
        }

        @Test
        @DisplayName("cache_hit_is_used_without_client_call")
        void cache_hit_is_used_without_client_call() throws IOException {
            when(tokenReviewCache.get(SA_TOKEN)).thenReturn(Optional.of(
                    new TokenReviewClient.TokenReviewResult(true, "system:serviceaccount:infrans:gateway", null)));

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNotNull();
            verify(tokenReviewClient, never()).review(anyString());
        }
    }

    @Nested
    @DisplayName("Failed authentication")
    class FailedAuthentication {

        @Test
        @DisplayName("authenticated_false_fails_closed")
        void authenticated_false_fails_closed() throws IOException {
            when(tokenReviewClient.review(SA_TOKEN)).thenReturn(
                    new TokenReviewClient.TokenReviewResult(false, null, null));

            assertThatThrownBy(() -> mechanism.authenticate(routingContext, null).await().indefinitely())
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Invalid ServiceAccount token");
        }

        @Test
        @DisplayName("api_server_exception_fails_closed")
        void api_server_exception_fails_closed() throws IOException {
            when(tokenReviewClient.review(SA_TOKEN)).thenThrow(new IOException("api server unreachable"));

            assertThatThrownBy(() -> mechanism.authenticate(routingContext, null).await().indefinitely())
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("api server unreachable");
        }
    }

    @Nested
    @DisplayName("Abstaining")
    class Abstaining {

        @Test
        @DisplayName("inactive_config_abstains_even_with_sa_token")
        void inactive_config_abstains_even_with_sa_token() throws Exception {
            active = false;

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNull();
            verify(tokenReviewClient, never()).review(anyString());
        }

        @Test
        @DisplayName("no_bearer_header_abstains")
        void no_bearer_header_abstains() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNull();
            verify(tokenReviewClient, never()).review(anyString());
        }

        @Test
        @DisplayName("non_bearer_scheme_abstains")
        void non_bearer_scheme_abstains() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNull();
            verify(tokenReviewClient, never()).review(anyString());
        }

        @Test
        @DisplayName("peek_empty_token_abstains_without_client_call")
        void peek_empty_token_abstains_without_client_call() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");

            SecurityIdentity identity = mechanism.authenticate(routingContext, null).await().indefinitely();

            assertThat(identity).isNull();
            verify(tokenReviewClient, never()).review(anyString());
        }
    }
}
