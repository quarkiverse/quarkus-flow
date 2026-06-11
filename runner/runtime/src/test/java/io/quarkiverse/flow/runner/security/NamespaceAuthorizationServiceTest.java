package io.quarkiverse.flow.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.security.identity.SecurityIdentity;

@DisplayName("NamespaceAuthorizationService Unit Tests")
class NamespaceAuthorizationServiceTest {

    private NamespaceAuthorizationService service;
    private FlowRunnerConfig config;
    private FlowRunnerConfig.Security security;
    private FlowRunnerConfig.Security.Namespace namespaceConfig;
    private SecurityIdentity securityIdentity;

    @BeforeEach
    void setUp() {
        service = new NamespaceAuthorizationService();
        config = mock(FlowRunnerConfig.class);
        security = mock(FlowRunnerConfig.Security.class);
        namespaceConfig = mock(FlowRunnerConfig.Security.Namespace.class);
        securityIdentity = mock(SecurityIdentity.class);

        service.config = config;
        service.securityIdentity = securityIdentity;

        when(config.security()).thenReturn(security);
        when(security.namespace()).thenReturn(namespaceConfig);
        when(namespaceConfig.claim()).thenReturn("namespace");
    }

    @Test
    @DisplayName("test_get_authorized_namespaces_returns_null_when_no_attribute")
    void test_get_authorized_namespaces_returns_null_when_no_attribute() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(null);
        when(securityIdentity.getAttribute("namespace")).thenReturn(null);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("test_get_authorized_namespaces_from_standard_claim")
    void test_get_authorized_namespaces_from_standard_claim() {
        // Given - Set attribute as API_KEY mechanism would
        Set<String> namespaces = Set.of("team-a", "team-b");
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(namespaces);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("team-a", "team-b");
    }

    @Test
    @DisplayName("test_get_authorized_namespaces_from_configured_claim")
    void test_get_authorized_namespaces_from_configured_claim() {
        // Given - OIDC mode with custom claim name
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(null);
        when(namespaceConfig.claim()).thenReturn("custom_namespaces");
        List<String> namespaces = List.of("ns1", "ns2", "ns3");
        when(securityIdentity.getAttribute("custom_namespaces")).thenReturn(namespaces);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("ns1", "ns2", "ns3");
    }

    @Test
    @DisplayName("test_convert_set_to_set")
    void test_convert_set_to_set() {
        // Given
        Set<String> namespaces = Set.of("a", "b", "c");
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(namespaces);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).isEqualTo(namespaces);
    }

    @Test
    @DisplayName("test_convert_list_to_set")
    void test_convert_list_to_set() {
        // Given - From OIDC JWT array claim
        List<String> namespaces = List.of("x", "y", "z");
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(namespaces);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    @DisplayName("test_convert_single_string_to_set")
    void test_convert_single_string_to_set() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("single-namespace");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactly("single-namespace");
    }

    @Test
    @DisplayName("test_convert_comma_separated_string_to_set")
    void test_convert_comma_separated_string_to_set() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("ns1,ns2,ns3");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("ns1", "ns2", "ns3");
    }

    @Test
    @DisplayName("test_convert_comma_separated_string_with_spaces_to_set")
    void test_convert_comma_separated_string_with_spaces_to_set() {
        // Given - Spaces should be trimmed
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("ns1, ns2 , ns3");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("ns1", "ns2", "ns3");
    }

    @Test
    @DisplayName("test_convert_blank_string_returns_null")
    void test_convert_blank_string_returns_null() {
        // Given - Empty string means all namespaces allowed
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("test_convert_whitespace_string_returns_null")
    void test_convert_whitespace_string_returns_null() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("   ");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("test_convert_comma_separated_with_blank_entries_filters_them")
    void test_convert_comma_separated_with_blank_entries_filters_them() {
        // Given - Blank entries should be filtered out
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn("ns1,,ns2, ,ns3");

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactlyInAnyOrder("ns1", "ns2", "ns3");
    }

    @Test
    @DisplayName("test_convert_other_object_type_to_set")
    void test_convert_other_object_type_to_set() {
        // Given - Fallback to toString()
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(42);

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactly("42");
    }

    @Test
    @DisplayName("test_empty_set_attribute_returns_empty_set")
    void test_empty_set_attribute_returns_empty_set() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(Set.of());

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("test_fallback_to_configured_claim_when_standard_claim_missing")
    void test_fallback_to_configured_claim_when_standard_claim_missing() {
        // Given
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(null);
        when(namespaceConfig.claim()).thenReturn("app_namespaces");
        when(securityIdentity.getAttribute("app_namespaces")).thenReturn(Set.of("app-ns"));

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactly("app-ns");
    }

    @Test
    @DisplayName("test_standard_claim_takes_precedence_over_configured_claim")
    void test_standard_claim_takes_precedence_over_configured_claim() {
        // Given - Both present, standard should win
        when(securityIdentity.getAttribute(AuthzConsts.CLAIM_NAMESPACES)).thenReturn(Set.of("standard-ns"));
        when(namespaceConfig.claim()).thenReturn("custom_namespaces");
        when(securityIdentity.getAttribute("custom_namespaces")).thenReturn(Set.of("custom-ns"));

        // When
        Set<String> result = service.getAuthorizedNamespaces();

        // Then
        assertThat(result).containsExactly("standard-ns");
    }
}
