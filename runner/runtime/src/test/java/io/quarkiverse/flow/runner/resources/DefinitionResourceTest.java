package io.quarkiverse.flow.runner.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.WorkflowDefinitionHeader;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@DisplayName("DefinitionResource Tests")
class DefinitionResourceTest {

    private DefinitionResource resource;
    private WorkflowApplication mockApplication;
    private HttpHeaders mockHeaders;
    private NamespaceAuthorizationService mockNamespaceAuth;

    @BeforeEach
    void setUp() {
        resource = new DefinitionResource();
        mockApplication = mock(WorkflowApplication.class);
        mockHeaders = mock(HttpHeaders.class);
        mockNamespaceAuth = mock(NamespaceAuthorizationService.class);

        resource.application = mockApplication;
        resource.namespaceAuth = mockNamespaceAuth;

        // Default: return null (all namespaces allowed) unless test overrides
        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(null);
    }

    @Test
    @DisplayName("test_list_definitions_returns_empty_list_when_no_workflows")
    void test_list_definitions_returns_empty_list_when_no_workflows() {
        // Given
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of());

        // When
        Response response = resource.listDefinitions(null);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isInstanceOf(List.class);
        assertThat((List<?>) response.getEntity()).isEmpty();
    }

    @Test
    @DisplayName("test_list_definitions_returns_all_workflows_when_no_namespace_filter")
    void test_list_definitions_returns_all_workflows_when_no_namespace_filter() {
        // Given
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns2", "wf2", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns2", "wf2", "2.0.0"), def2));

        // When
        Response response = resource.listDefinitions(null);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(2);
        assertThat(headers).extracting(WorkflowDefinitionHeader::namespace)
                .containsExactlyInAnyOrder("ns1", "ns2");
    }

    @Test
    @DisplayName("test_list_definitions_filters_by_namespace")
    void test_list_definitions_filters_by_namespace() {
        // Given
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns1", "wf2", "2.0.0");
        WorkflowDefinition def3 = createMockDefinition("ns2", "wf3", "1.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns1", "wf2", "2.0.0"), def2,
                new WorkflowDefinitionId("ns2", "wf3", "1.0.0"), def3));

        // When
        Response response = resource.listDefinitions("ns1");

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(2);
        assertThat(headers).extracting(WorkflowDefinitionHeader::namespace)
                .containsOnly("ns1");
        assertThat(headers).extracting(WorkflowDefinitionHeader::name)
                .containsExactlyInAnyOrder("wf1", "wf2");
    }

    @Test
    @DisplayName("test_list_definitions_includes_metadata_fields")
    void test_list_definitions_includes_metadata_fields() {
        // Given
        WorkflowDefinition def = createMockDefinitionWithMetadata(
                "test-ns", "test-wf", "1.5.0", "1.0.0",
                "Test Workflow", "A test workflow summary",
                Map.of("key1", "value1", "key2", "value2"));

        when(mockApplication.workflowDefinitions())
                .thenReturn(Map.of(new WorkflowDefinitionId("test-ns", "test-wf", "1.5.0"), def));

        // When
        Response response = resource.listDefinitions(null);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(1);

        WorkflowDefinitionHeader header = headers.get(0);
        assertThat(header.namespace()).isEqualTo("test-ns");
        assertThat(header.name()).isEqualTo("test-wf");
        assertThat(header.version()).isEqualTo("1.5.0");
        assertThat(header.dsl()).isEqualTo("1.0.0");
        assertThat(header.title()).isEqualTo("Test Workflow");
        assertThat(header.summary()).isEqualTo("A test workflow summary");
        assertThat(header.metadata()).containsEntry("key1", "value1");
        assertThat(header.metadata()).containsEntry("key2", "value2");

        // Verify HATEOAS links
        assertThat(header.links()).isNotNull();
        assertThat(header.links()).containsKeys("self", "execute");
        assertThat(header.links().get("self").href()).isEqualTo("/runner/definitions/test-ns/test-wf/1.5.0");
        assertThat(header.links().get("execute").href()).isEqualTo("/runner/exec/test-ns/test-wf/1.5.0");
    }

    @Test
    @DisplayName("test_get_definition_returns_404_when_workflow_not_found")
    void test_get_definition_returns_404_when_workflow_not_found() {
        // Given
        when(mockApplication.workflowDefinitions()).thenReturn(Map.of());
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

        // When
        Response response = resource.getDefinition("ns1", "wf1", "1.0.0", mockHeaders);

        // Then
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("test_get_definition_returns_workflow_as_json_when_accept_json")
    void test_get_definition_returns_workflow_as_json_when_accept_json() {
        // Given
        WorkflowDefinition def = createMockDefinition("test-ns", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions())
                .thenReturn(Map.of(new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0"), def));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

        // When
        Response response = resource.getDefinition("test-ns", "test-wf", "1.0.0", mockHeaders);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
        assertThat(response.getEntity()).isInstanceOf(String.class);
        String json = (String) response.getEntity();
        // SDK serializes with spaces: "namespace" : "test-ns"
        assertThat(json).contains("\"namespace\" : \"test-ns\"");
        assertThat(json).contains("\"name\" : \"test-wf\"");
        assertThat(json).contains("\"version\" : \"1.0.0\"");
    }

    @Test
    @DisplayName("test_get_definition_returns_workflow_as_yaml_when_accept_yaml")
    void test_get_definition_returns_workflow_as_yaml_when_accept_yaml() {
        // Given
        WorkflowDefinition def = createMockDefinition("test-ns", "test-wf", "2.0.0");
        when(mockApplication.workflowDefinitions())
                .thenReturn(Map.of(new WorkflowDefinitionId("test-ns", "test-wf", "2.0.0"), def));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.valueOf("application/yaml")));

        // When
        Response response = resource.getDefinition("test-ns", "test-wf", "2.0.0", mockHeaders);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType().toString()).isEqualTo("application/yaml");
        assertThat(response.getEntity()).isInstanceOf(String.class);
        String yaml = (String) response.getEntity();
        assertThat(yaml).contains("namespace: test-ns");
        assertThat(yaml).contains("name: test-wf");
        // SDK YAML serializer doesn't quote version numbers
        assertThat(yaml).contains("version: 2.0.0");
    }

    @Test
    @DisplayName("test_get_definition_returns_json_when_accept_wildcard")
    void test_get_definition_returns_json_when_accept_wildcard() {
        // Given
        WorkflowDefinition def = createMockDefinition("test-ns", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions())
                .thenReturn(Map.of(new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0"), def));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));

        // When
        Response response = resource.getDefinition("test-ns", "test-wf", "1.0.0", mockHeaders);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
        assertThat(response.getEntity()).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("test_get_definition_matches_exact_version")
    void test_get_definition_matches_exact_version() {
        // Given
        WorkflowDefinition def1 = createMockDefinition("test-ns", "test-wf", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("test-ns", "test-wf", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("test-ns", "test-wf", "1.0.0"), def1,
                new WorkflowDefinitionId("test-ns", "test-wf", "2.0.0"), def2));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

        // When - request version 1.0.0
        Response response = resource.getDefinition("test-ns", "test-wf", "1.0.0", mockHeaders);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        String json = (String) response.getEntity();
        assertThat(json).contains("\"version\" : \"1.0.0\"");
        assertThat(json).doesNotContain("\"version\" : \"2.0.0\"");
    }

    @Test
    @DisplayName("test_get_definition_is_case_sensitive_for_namespace")
    void test_get_definition_is_case_sensitive_for_namespace() {
        // Given
        WorkflowDefinition def = createMockDefinition("TestNS", "test-wf", "1.0.0");
        when(mockApplication.workflowDefinitions())
                .thenReturn(Map.of(new WorkflowDefinitionId("TestNS", "test-wf", "1.0.0"), def));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

        // When - request with lowercase namespace
        Response response = resource.getDefinition("testns", "test-wf", "1.0.0", mockHeaders);

        // Then - should not find it (case-sensitive)
        assertThat(response.getStatus()).isEqualTo(404);
    }

    // ABAC (Namespace Authorization) Tests

    @Test
    @DisplayName("test_list_definitions_filters_by_authorized_namespaces_when_no_query_param")
    void test_list_definitions_filters_by_authorized_namespaces_when_no_query_param() {
        // Given - User only authorized for ns1 and ns2
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns2", "wf2", "2.0.0");
        WorkflowDefinition def3 = createMockDefinition("ns3", "wf3", "1.0.0"); // Not authorized

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns2", "wf2", "2.0.0"), def2,
                new WorkflowDefinitionId("ns3", "wf3", "1.0.0"), def3));

        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(Set.of("ns1", "ns2"));

        // When
        Response response = resource.listDefinitions(null);

        // Then - Should only return workflows from ns1 and ns2
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(2);
        assertThat(headers).extracting(WorkflowDefinitionHeader::namespace)
                .containsExactlyInAnyOrder("ns1", "ns2");
    }

    @Test
    @DisplayName("test_list_definitions_returns_all_when_authorized_namespaces_is_null")
    void test_list_definitions_returns_all_when_authorized_namespaces_is_null() {
        // Given - null means all namespaces allowed
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns2", "wf2", "2.0.0");
        WorkflowDefinition def3 = createMockDefinition("ns3", "wf3", "1.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns2", "wf2", "2.0.0"), def2,
                new WorkflowDefinitionId("ns3", "wf3", "1.0.0"), def3));

        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(null);

        // When
        Response response = resource.listDefinitions(null);

        // Then - Should return all workflows
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(3);
    }

    @Test
    @DisplayName("test_list_definitions_returns_all_when_authorized_namespaces_is_empty")
    void test_list_definitions_returns_all_when_authorized_namespaces_is_empty() {
        // Given - empty set means all namespaces allowed
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns2", "wf2", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns2", "wf2", "2.0.0"), def2));

        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(Set.of());

        // When
        Response response = resource.listDefinitions(null);

        // Then - Should return all workflows
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(2);
    }

    @Test
    @DisplayName("test_list_definitions_with_namespace_param_does_not_apply_abac_filter")
    void test_list_definitions_with_namespace_param_does_not_apply_abac_filter() {
        // Given - User authorized for ns1, but requesting ns2 specifically
        // Filter will deny at JAX-RS filter level, but resource just filters by namespace
        WorkflowDefinition def1 = createMockDefinition("ns1", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns2", "wf2", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns1", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns2", "wf2", "2.0.0"), def2));

        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(Set.of("ns1"));

        // When - Requesting specific namespace (filter would block this in real scenario)
        Response response = resource.listDefinitions("ns2");

        // Then - Resource just filters by namespace parameter
        // (Authorization happens in filter layer, not resource layer)
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).hasSize(1);
        assertThat(headers.get(0).namespace()).isEqualTo("ns2");
    }

    @Test
    @DisplayName("test_list_definitions_returns_empty_when_no_workflows_in_authorized_namespaces")
    void test_list_definitions_returns_empty_when_no_workflows_in_authorized_namespaces() {
        // Given - User authorized for ns1, but only ns2 workflows exist
        WorkflowDefinition def1 = createMockDefinition("ns2", "wf1", "1.0.0");
        WorkflowDefinition def2 = createMockDefinition("ns3", "wf2", "2.0.0");

        when(mockApplication.workflowDefinitions()).thenReturn(Map.of(
                new WorkflowDefinitionId("ns2", "wf1", "1.0.0"), def1,
                new WorkflowDefinitionId("ns3", "wf2", "2.0.0"), def2));

        when(mockNamespaceAuth.getAuthorizedNamespaces()).thenReturn(Set.of("ns1"));

        // When
        Response response = resource.listDefinitions(null);

        // Then - Should return empty list
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<WorkflowDefinitionHeader> headers = (List<WorkflowDefinitionHeader>) response.getEntity();
        assertThat(headers).isEmpty();
    }

    // Helper methods

    private WorkflowDefinition createMockDefinition(String namespace, String name, String version) {
        return createMockDefinitionWithMetadata(namespace, name, version, "1.0.0", null, null, Map.of());
    }

    private WorkflowDefinition createMockDefinitionWithMetadata(
            String namespace, String name, String version, String dsl,
            String title, String summary, Map<String, Object> metadataProps) {

        Workflow workflow = mock(Workflow.class);
        Document document = mock(Document.class);

        when(document.getNamespace()).thenReturn(namespace);
        when(document.getName()).thenReturn(name);
        when(document.getVersion()).thenReturn(version);
        when(document.getDsl()).thenReturn(dsl);
        when(document.getTitle()).thenReturn(title);
        when(document.getSummary()).thenReturn(summary);

        if (!metadataProps.isEmpty()) {
            // Use Mockito's Answer to create a metadata mock with getAdditionalProperties()
            var mockMetadata = mock(WorkflowMetadata.class, invocation -> {
                if ("getAdditionalProperties".equals(invocation.getMethod().getName())) {
                    return metadataProps;
                }
                return null;
            });
            when(document.getMetadata()).thenReturn(mockMetadata);
        }

        when(workflow.getDocument()).thenReturn(document);

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.workflow()).thenReturn(workflow);

        return definition;
    }
}
