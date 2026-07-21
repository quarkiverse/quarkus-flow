package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Unit tests for {@link NamespaceAuthorizationFilter}.
 * <p>
 * Tests the modern {@code @ServerRequestFilter} implementation which uses
 * a parameter-less {@code filter()} method instead of the legacy
 * {@code filter(ContainerRequestContext)} signature.
 */
@DisplayName("NamespaceAuthorizationFilter Unit Tests")
class NamespaceAuthorizationFilterTest {

    private NamespaceAuthorizationFilter filter;
    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private FlowRunnerConfig.Security.Namespace namespaceConfig;
    private NamespaceAuthorizationService namespaceAuthzService;
    private UriInfo uriInfo;
    private SecurityIdentity securityIdentity;
    private JsonWebToken jsonWebToken;

    @BeforeEach
    void setUp() {
        filter = new NamespaceAuthorizationFilter();

        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        namespaceConfig = mock(FlowRunnerConfig.Security.Namespace.class);
        namespaceAuthzService = mock(NamespaceAuthorizationService.class);
        uriInfo = mock(UriInfo.class);
        securityIdentity = mock(SecurityIdentity.class);
        jsonWebToken = mock(JsonWebToken.class);

        filter.config = config;
        filter.namespaceAuthzService = namespaceAuthzService;
        filter.uriInfo = uriInfo;
        filter.securityIdentity = securityIdentity;

        when(config.security()).thenReturn(security);
        when(security.namespace()).thenReturn(namespaceConfig);
    }

    @Test
    @DisplayName("test_filter_skips_when_validation_disabled")
    void test_filter_skips_when_validation_disabled() {
        // Given
        when(namespaceConfig.validate()).thenReturn(false);

        // When
        filter.filter();

        // Then
        verify(namespaceAuthzService, never()).getAuthorizedNamespaces();
    }

    @Test
    @DisplayName("test_filter_skips_when_no_namespace_in_request")
    void test_filter_skips_when_no_namespace_in_request() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);

        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();

        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        // When
        filter.filter();

        // Then
        verify(namespaceAuthzService, never()).getAuthorizedNamespaces();
    }

    @Test
    @DisplayName("test_filter_allows_when_authorized_namespaces_is_null")
    void test_filter_allows_when_authorized_namespaces_is_null() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(null);

        // The default mocked principal is null, so this represents a
        // non-OIDC security mode with unrestricted namespace access.

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_when_authorized_namespaces_is_empty")
    void test_filter_allows_when_authorized_namespaces_is_empty() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of());

        // The default mocked principal is null, so this represents a
        // non-OIDC security mode with unrestricted namespace access.

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_non_oidc_identity_with_empty_namespaces")
    void test_filter_allows_non_oidc_identity_with_empty_namespaces() {
        when(namespaceConfig.validate()).thenReturn(true);
        when(securityIdentity.getPrincipal()).thenReturn(() -> "api-key-user");
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of());

        setPathNamespace("team-a");

        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_rejects_oidc_invoker_without_namespace_claim")
    void test_filter_rejects_oidc_invoker_without_namespace_claim() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no authorized namespaces");
    }

    @Test
    @DisplayName("test_filter_rejects_oidc_invoker_with_empty_namespaces")
    void test_filter_rejects_oidc_invoker_with_empty_namespaces() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of());

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void test_filter_rejects_oidc_invoker_with_blank_namespaces() {
        when(namespaceConfig.validate()).thenReturn(true);
        when(securityIdentity.getPrincipal()).thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("", "   "));

        setPathNamespace("team-a");

        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no authorized namespaces");
    }

    @Test
    @DisplayName("test_filter_allows_oidc_admin_without_namespace_claim")
    void test_filter_allows_oidc_admin_without_namespace_claim() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        when(securityIdentity.getPrincipal()).thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(true);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(null);

        setPathNamespace("team-a");

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_oidc_admin_with_empty_namespaces")
    void test_filter_allows_oidc_admin_with_empty_namespaces() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(true);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of());

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_when_namespace_in_authorized_set")
    void test_filter_allows_when_namespace_in_authorized_set() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("team-a", "team-b"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_denies_when_namespace_not_in_authorized_set")
    void test_filter_denies_when_namespace_not_in_authorized_set() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-c");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("team-a", "team-b"));

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(
                        "not authorized for namespace: team-c");
    }

    @Test
    @DisplayName("test_extract_namespace_from_path_parameter")
    void test_extract_namespace_from_path_parameter() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("my-namespace");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("my-namespace"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_extract_namespace_from_query_parameter")
    void test_extract_namespace_from_query_parameter() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);

        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();

        queryParams.putSingle("namespace", "query-namespace");

        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("query-namespace"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_path_parameter_takes_precedence_over_query_parameter")
    void test_path_parameter_takes_precedence_over_query_parameter() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);

        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();

        pathParams.putSingle("namespace", "path-ns");
        queryParams.putSingle("namespace", "query-ns");

        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("path-ns"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_blank_path_parameter_falls_back_to_query_parameter")
    void test_blank_path_parameter_falls_back_to_query_parameter() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);

        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();

        pathParams.putSingle("namespace", "   ");
        queryParams.putSingle("namespace", "query-ns");

        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("query-ns"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_validates_multiple_namespaces_correctly")
    void test_filter_validates_multiple_namespaces_correctly() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("ns1", "ns2", "ns3"));

        // When/Then
        for (String namespace : new String[] {
                "ns1",
                "ns2",
                "ns3"
        }) {
            setPathNamespace(namespace);

            assertThatCode(() -> filter.filter())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("test_filter_case_sensitive_namespace_matching")
    void test_filter_case_sensitive_namespace_matching() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("Team-A");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("team-a"));

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(
                        "not authorized for namespace: Team-A");
    }

    @Test
    @DisplayName("test_filter_with_single_authorized_namespace")
    void test_filter_with_single_authorized_namespace() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("only-ns");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("only-ns"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_error_message_contains_namespace")
    void test_filter_error_message_contains_namespace() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("forbidden-namespace");

        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("allowed"));

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("forbidden-namespace");
    }

    @Test
    @DisplayName("test_filter_allows_oidc_invoker_for_authorized_namespace")
    void test_filter_allows_oidc_invoker_for_authorized_namespace() {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        setPathNamespace("team-a");

        when(securityIdentity.getPrincipal())
                .thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("team-a", "team-b"));

        // When/Then
        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_oidc_invoker_with_exact_wildcard")
    void test_filter_allows_oidc_invoker_with_exact_wildcard() {
        when(namespaceConfig.validate()).thenReturn(true);
        when(securityIdentity.getPrincipal()).thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of("*"));

        setPathNamespace("any-namespace");

        assertThatCode(() -> filter.filter())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "my*",
            "*test",
            "team-*",
            "**",
            " * "
    })
    @DisplayName("test_filter_rejects_oidc_non_exact_wildcard")
    void test_filter_rejects_oidc_non_exact_wildcard(
            String namespaceClaim) {

        when(namespaceConfig.validate()).thenReturn(true);
        when(securityIdentity.getPrincipal()).thenReturn(jsonWebToken);
        when(securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN))
                .thenReturn(false);
        when(namespaceAuthzService.getAuthorizedNamespaces())
                .thenReturn(Set.of(namespaceClaim));

        setPathNamespace("team-a");

        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class);
    }

    private void setPathNamespace(String namespace) {
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();

        pathParams.putSingle("namespace", namespace);

        when(uriInfo.getPathParameters()).thenReturn(pathParams);
    }
}
