package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NamespaceAuthorizationFilter}.
 * <p>
 * The filter is a thin adapter: it extracts the namespace from the request URI and delegates
 * the authorization decision to {@link NamespaceAuthorizationService}. The decision logic itself
 * is covered by {@link NamespaceAuthorizationServiceTest}.
 */
@DisplayName("NamespaceAuthorizationFilter Unit Tests")
class NamespaceAuthorizationFilterTest {

    private NamespaceAuthorizationFilter filter;
    private NamespaceAuthorizationService namespaceAuthzService;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() {
        filter = new NamespaceAuthorizationFilter();

        namespaceAuthzService = mock(NamespaceAuthorizationService.class);
        uriInfo = mock(UriInfo.class);

        filter.namespaceAuthzService = namespaceAuthzService;
        filter.uriInfo = uriInfo;

        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    }

    @Test
    @DisplayName("test_filter_skips_when_no_namespace_in_request")
    void test_filter_skips_when_no_namespace_in_request() {
        filter.filter();

        verify(namespaceAuthzService, never()).isNamespaceAuthorized(anyString());
    }

    @Test
    @DisplayName("test_filter_allows_when_service_authorizes_namespace")
    void test_filter_allows_when_service_authorizes_namespace() {
        setPathNamespace("team-a");
        when(namespaceAuthzService.isNamespaceAuthorized("team-a")).thenReturn(true);

        assertThatCode(() -> filter.filter()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_filter_denies_when_service_rejects_namespace")
    void test_filter_denies_when_service_rejects_namespace() {
        setPathNamespace("team-a");
        when(namespaceAuthzService.isNamespaceAuthorized("team-a")).thenReturn(false);

        assertThatThrownBy(() -> filter.filter())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized for namespace: team-a");
    }

    @Test
    @DisplayName("test_extract_namespace_from_path_parameter")
    void test_extract_namespace_from_path_parameter() {
        setPathNamespace("my-namespace");
        when(namespaceAuthzService.isNamespaceAuthorized("my-namespace")).thenReturn(true);

        filter.filter();

        verify(namespaceAuthzService).isNamespaceAuthorized("my-namespace");
    }

    @Test
    @DisplayName("test_extract_namespace_from_query_parameter")
    void test_extract_namespace_from_query_parameter() {
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("namespace", "query-namespace");
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.isNamespaceAuthorized("query-namespace")).thenReturn(true);

        filter.filter();

        verify(namespaceAuthzService).isNamespaceAuthorized("query-namespace");
    }

    @Test
    @DisplayName("test_path_parameter_takes_precedence_over_query_parameter")
    void test_path_parameter_takes_precedence_over_query_parameter() {
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "path-ns");
        queryParams.putSingle("namespace", "query-ns");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.isNamespaceAuthorized("path-ns")).thenReturn(true);

        filter.filter();

        verify(namespaceAuthzService).isNamespaceAuthorized("path-ns");
        verify(namespaceAuthzService, never()).isNamespaceAuthorized("query-ns");
    }

    @Test
    @DisplayName("test_blank_path_parameter_falls_back_to_query_parameter")
    void test_blank_path_parameter_falls_back_to_query_parameter() {
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", "   ");
        queryParams.putSingle("namespace", "query-ns");
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(namespaceAuthzService.isNamespaceAuthorized("query-ns")).thenReturn(true);

        filter.filter();

        verify(namespaceAuthzService).isNamespaceAuthorized("query-ns");
    }

    private void setPathNamespace(String namespace) {
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.putSingle("namespace", namespace);
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
    }
}
