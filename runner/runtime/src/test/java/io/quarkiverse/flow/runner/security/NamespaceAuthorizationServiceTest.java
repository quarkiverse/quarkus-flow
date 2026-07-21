package io.quarkiverse.flow.runner.security;

import static io.quarkiverse.flow.runner.security.AuthzConsts.CLAIM_NAMESPACES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.identity.SecurityIdentity;

@DisplayName("Namespace authorization service tests")
class NamespaceAuthorizationServiceTest {

    private static final String CLAIM_NAME = "namespace";

    private NamespaceAuthorizationService service;
    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private FlowRunnerConfig.Security.Namespace namespaceConfig;
    private SecurityIdentity securityIdentity;
    private JsonWebToken jsonWebToken;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = new NamespaceAuthorizationService();

        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        namespaceConfig = mock(FlowRunnerConfig.Security.Namespace.class);
        securityIdentity = mock(SecurityIdentity.class);
        jsonWebToken = mock(JsonWebToken.class);
        objectMapper = new ObjectMapper();

        service.config = config;
        service.securityIdentity = securityIdentity;
        service.objectMapper = objectMapper;

        when(config.security()).thenReturn(security);
        when(security.namespace()).thenReturn(namespaceConfig);
        when(namespaceConfig.claim()).thenReturn(CLAIM_NAME);
    }

    @Test
    @DisplayName("JSON-P array claim values are normalized to Java strings")
    void shouldNormalizeJsonArrayClaim() {
        JsonArray claim = Json.createArrayBuilder()
                .add("team-a")
                .add("team-b")
                .build();

        useJwtClaim(claim);

        assertThat(service.getAuthorizedNamespaces())
                .containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("Jackson array claim values are normalized")
    void shouldNormalizeJacksonArrayClaim() throws Exception {
        JsonNode claim = objectMapper.readTree("""
                ["team-a", "team-b"]
                """);

        useJwtClaim(claim);

        assertThat(service.getAuthorizedNamespaces())
                .containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("Serialized JSON array claims are parsed")
    void shouldNormalizeSerializedJsonArrayClaim() {
        useJwtClaim("""
                ["team-a", "team-b", "team-a", " "]
                """);

        assertThat(service.getAuthorizedNamespaces())
                .containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("Comma-separated namespace claims are parsed")
    void shouldNormalizeCommaSeparatedClaim() {
        useJwtClaim("team-a, team-b, team-a");

        assertThat(service.getAuthorizedNamespaces())
                .containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("Collection values are trimmed and blank values removed")
    void shouldNormalizeCollectionValues() {
        useJwtClaim(List.of(
                " team-a ",
                "",
                "   ",
                "team-b",
                "team-a"));

        assertThat(service.getAuthorizedNamespaces())
                .containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("Present empty collection remains an empty set")
    void shouldPreserveEmptyCollection() {
        useJwtClaim(List.of());

        assertThat(service.getAuthorizedNamespaces())
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("Missing namespace claim returns null")
    void shouldReturnNullWhenClaimIsMissing() {
        useJwtClaim(null);

        assertThat(service.getAuthorizedNamespaces())
                .isNull();
    }

    @Test
    @DisplayName("Standard namespaces attribute takes precedence")
    void shouldPreferStandardNamespacesAttribute() {
        when(securityIdentity.getAttribute(CLAIM_NAMESPACES))
                .thenReturn(Set.of("api-key-namespace"));

        when(securityIdentity.getAttribute(CLAIM_NAME))
                .thenReturn(Set.of("configured-claim-namespace"));

        assertThat(service.getAuthorizedNamespaces())
                .containsExactly("api-key-namespace");

        verify(securityIdentity, never())
                .getAttribute(CLAIM_NAME);
        verify(jsonWebToken, never())
                .getClaim(CLAIM_NAME);
    }

    @Test
    @DisplayName("Configured identity attribute takes precedence over JWT")
    void shouldPreferConfiguredIdentityAttributeOverJwtClaim() {
        when(securityIdentity.getAttribute(CLAIM_NAMESPACES))
                .thenReturn(null);

        when(securityIdentity.getAttribute(CLAIM_NAME))
                .thenReturn(Set.of("identity-attribute-namespace"));

        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);

        doReturn(Set.of("jwt-namespace"))
                .when(jsonWebToken)
                .getClaim(CLAIM_NAME);

        assertThat(service.getAuthorizedNamespaces())
                .containsExactly("identity-attribute-namespace");

        verify(jsonWebToken, never())
                .getClaim(CLAIM_NAME);
    }

    private void useJwtClaim(Object claim) {
        when(securityIdentity.getAttribute(CLAIM_NAMESPACES))
                .thenReturn(null);
        when(securityIdentity.getAttribute(CLAIM_NAME))
                .thenReturn(null);
        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);

        doReturn(claim)
                .when(jsonWebToken)
                .getClaim(CLAIM_NAME);
    }
}
