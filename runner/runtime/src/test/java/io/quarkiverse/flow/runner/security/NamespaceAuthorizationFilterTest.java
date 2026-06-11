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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;

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

    @BeforeEach
    void setUp() {
        filter = new NamespaceAuthorizationFilter();
        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        namespaceConfig = mock(FlowRunnerConfig.Security.Namespace.class);
        namespaceAuthzService = mock(NamespaceAuthorizationService.class);
        uriInfo = mock(UriInfo.class);

        filter.config = config;
        filter.namespaceAuthzService = namespaceAuthzService;
        filter.uriInfo = uriInfo;

        when(config.security()).thenReturn(security);
        when(security.namespace()).thenReturn(namespaceConfig);
    }

    @Test
    @DisplayName("test_filter_skips_when_validation_disabled")
    void test_filter_skips_when_validation_disabled() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(false);

        // When
        filter.filter();

        // Then
        verify(namespaceAuthzService, never()).getAuthorizedNamespaces();
    }

    @Test
    @DisplayName("test_filter_skips_when_no_namespace_in_request")
    void test_filter_skips_when_no_namespace_in_request() throws Exception {
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
    void test_filter_allows_when_authorized_namespaces_is_null() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "team-a");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(null);

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_when_authorized_namespaces_is_empty")
    void test_filter_allows_when_authorized_namespaces_is_empty() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "team-a");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of());

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_allows_when_namespace_in_authorized_set")
    void test_filter_allows_when_namespace_in_authorized_set() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "team-a");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("team-a", "team-b"));

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_denies_when_namespace_not_in_authorized_set")
    void test_filter_denies_when_namespace_not_in_authorized_set() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "team-c");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("team-a", "team-b"));

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Access denied to namespace: team-c");
    }

    @Test
    @DisplayName("test_extract_namespace_from_path_parameter")
    void test_extract_namespace_from_path_parameter() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "my-namespace");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("my-namespace"));

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_extract_namespace_from_query_parameter")
    void test_extract_namespace_from_query_parameter() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("namespace", "query-namespace");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("query-namespace"));

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_path_parameter_takes_precedence_over_query_parameter")
    void test_path_parameter_takes_precedence_over_query_parameter() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "path-ns");
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("namespace", "query-ns");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("path-ns"));

        // When/Then - Should validate against path-ns, not query-ns
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_blank_path_parameter_falls_back_to_query_parameter")
    void test_blank_path_parameter_falls_back_to_query_parameter() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "   "); // Blank
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("namespace", "query-ns");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("query-ns"));

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_validates_multiple_namespaces_correctly")
    void test_filter_validates_multiple_namespaces_correctly() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("ns1", "ns2", "ns3"));

        // Test each authorized namespace
        for (String ns : new String[] { "ns1", "ns2", "ns3" }) {
            MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
            pathParams.putSingle("namespace", ns);
            when(uriInfo.getPathParameters()).thenReturn(pathParams);

            // When/Then
            assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("test_filter_case_sensitive_namespace_matching")
    void test_filter_case_sensitive_namespace_matching() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "Team-A"); // Different case
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("team-a"));

        // When/Then - Should fail (case-sensitive)
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Access denied to namespace: Team-A");
    }

    @Test
    @DisplayName("test_filter_with_single_authorized_namespace")
    void test_filter_with_single_authorized_namespace() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "only-ns");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("only-ns"));

        // When/Then
        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_error_message_contains_namespace")
    void test_filter_error_message_contains_namespace() throws Exception {
        // Given
        when(namespaceConfig.validate()).thenReturn(true);
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "forbidden-namespace");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(namespaceAuthzService.getAuthorizedNamespaces()).thenReturn(Set.of("allowed"));

        // When/Then
        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("forbidden-namespace");
    }
}
