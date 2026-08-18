package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

@DisplayName("API Key authentication mechanism tests")
class ApiKeyAuthenticationMechanismTest {

    private ApiKeyAuthenticationMechanism mechanism;
    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private RoutingContext routingContext;
    private HttpServerRequest request;

    private static final String VALID_KEY = "correct-api-key-secret";

    @BeforeEach
    void setUp() {
        mechanism = new ApiKeyAuthenticationMechanism();

        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        routingContext = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);

        mechanism.config = config;

        when(config.security()).thenReturn(security);
        when(security.type()).thenReturn(FlowRunnerConfig.Security.Type.API_KEY);
        when(routingContext.request()).thenReturn(request);

        FlowRunnerConfig.Security.ApiKey apiKey = mock(FlowRunnerConfig.Security.ApiKey.class);
        when(apiKey.secret()).thenReturn(VALID_KEY);
        when(apiKey.roles()).thenReturn(Set.of(AuthzConsts.ROLE_INVOKER));
        when(apiKey.namespaces()).thenReturn(Optional.of(Set.of("*")));
        when(security.apiKeys()).thenReturn(Map.of("test-key", apiKey));
    }

    @Nested
    @DisplayName("Constant-time API key comparison")
    class TimingSafeComparison {

        @Test
        @DisplayName("valid_key_authenticates_successfully")
        void valid_key_authenticates_successfully() {
            when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_KEY);

            SecurityIdentity identity = mechanism.authenticate(routingContext, null)
                    .await().indefinitely();

            assertThat(identity).isNotNull();
            assertThat(identity.getPrincipal().getName()).isEqualTo("api-key:test-key");
            assertThat(identity.getRoles()).contains(AuthzConsts.ROLE_INVOKER);
        }

        @Test
        @DisplayName("wrong_key_with_same_length_must_be_rejected")
        void wrong_key_with_same_length_must_be_rejected() {
            String wrongKey = "x".repeat(VALID_KEY.length());
            when(request.getHeader("Authorization")).thenReturn("Bearer " + wrongKey);

            try {
                mechanism.authenticate(routingContext, null).await().indefinitely();
                assertThat(false).as("Should have thrown AuthenticationFailedException").isTrue();
            } catch (Exception e) {
                assertThat(e).hasMessageContaining("Invalid API key");
            }
        }

        @Test
        @DisplayName("wrong_key_with_different_length_must_be_rejected")
        void wrong_key_with_different_length_must_be_rejected() {
            when(request.getHeader("Authorization")).thenReturn("Bearer short");

            try {
                mechanism.authenticate(routingContext, null).await().indefinitely();
                assertThat(false).as("Should have thrown AuthenticationFailedException").isTrue();
            } catch (Exception e) {
                assertThat(e).hasMessageContaining("Invalid API key");
            }
        }

        @Test
        @DisplayName("key_differing_only_in_last_character_must_be_rejected")
        void key_differing_only_in_last_character_must_be_rejected() {
            String almostRight = VALID_KEY.substring(0, VALID_KEY.length() - 1) + "X";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + almostRight);

            try {
                mechanism.authenticate(routingContext, null).await().indefinitely();
                assertThat(false).as("Should have thrown AuthenticationFailedException").isTrue();
            } catch (Exception e) {
                assertThat(e).hasMessageContaining("Invalid API key");
            }
        }
    }
}
