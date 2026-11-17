package io.quarkiverse.flow.runner.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;

@DisplayName("DiscoveryResource Tests")
class DiscoveryResourceTest {

    private DiscoveryResource resource;
    private WorkflowApplication mockApplication;
    private NamespaceAuthorizationService mockNamespaceAuth;
    private Instance<InstanceLocator> mockLocatorInstance;
    private InstanceLocator mockLocator;

    @BeforeEach
    void setUp() {
        resource = new DiscoveryResource();
        mockApplication = mock(WorkflowApplication.class);
        resource.application = mockApplication;

        mockNamespaceAuth = mock(NamespaceAuthorizationService.class);
        when(mockNamespaceAuth.isNamespaceAuthorized(anyString())).thenReturn(true);

        WorkflowDefinitionLookup lookup = new WorkflowDefinitionLookup();
        lookup.application = mockApplication;
        lookup.namespaceAuth = mockNamespaceAuth;
        resource.definitionLookup = lookup;

        mockLocator = mock(InstanceLocator.class);
        mockLocatorInstance = mock(Instance.class);
        resource.locator = mockLocatorInstance;

        when(mockApplication.id()).thenReturn("runner-pod-0");
        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of());
    }

    // --- helpers ---

    private WorkflowInstance mockInstance(String id, WorkflowStatus status, Instant startedAt) {
        WorkflowInstance instance = mock(WorkflowInstance.class);
        when(instance.id()).thenReturn(id);
        when(instance.status()).thenReturn(status);
        when(instance.startedAt()).thenReturn(startedAt);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private InstanceLocation body(Response response) {
        return (InstanceLocation) response.getEntity();
    }

    // --- tests ---

    @Test
    @DisplayName("test_memory_hit_returns_200_with_application_id")
    void test_memory_hit_returns_200_with_application_id() {
        WorkflowInstance instance = mockInstance("inst-1", WorkflowStatus.RUNNING, Instant.now());

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        WorkflowDefinitionId defId = new WorkflowDefinitionId("default", "flow-a", "1.0.0");
        when(definition.id()).thenReturn(defId);
        when(definition.activeInstance("inst-1")).thenReturn(Optional.of(instance));

        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of(defId, definition));

        Response response = resource.discover("default", "flow-a", "1.0.0", "inst-1");

        assertThat(response.getStatus()).isEqualTo(200);
        InstanceLocation location = body(response);
        assertThat(location.namespace()).isEqualTo("default");
        assertThat(location.name()).isEqualTo("flow-a");
        assertThat(location.version()).isEqualTo("1.0.0");
        assertThat(location.instanceId()).isEqualTo("inst-1");
        assertThat(location.applicationId()).isEqualTo("runner-pod-0");
    }

    @Test
    @DisplayName("test_memory_miss_locator_hit_returns_200_from_persistence")
    void test_memory_miss_locator_hit_returns_200_from_persistence() {
        // No definition in memory
        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of());

        // Locator is configured and returns a location
        InstanceLocation persistedLocation = new InstanceLocation("default", "flow-a", "1.0.0", "inst-2",
                "runner-pod-1");
        when(mockLocatorInstance.isResolvable()).thenReturn(true);
        when(mockLocatorInstance.get()).thenReturn(mockLocator);
        when(mockLocator.locate("default", "flow-a", "1.0.0", "inst-2"))
                .thenReturn(Optional.of(persistedLocation));

        Response response = resource.discover("default", "flow-a", "1.0.0", "inst-2");

        assertThat(response.getStatus()).isEqualTo(200);
        InstanceLocation location = body(response);
        assertThat(location.applicationId()).isEqualTo("runner-pod-1");
    }

    @Test
    @DisplayName("test_memory_miss_locator_miss_returns_404")
    void test_memory_miss_locator_miss_returns_404() {
        // No definition in memory
        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of());

        // Locator is configured but returns empty
        when(mockLocatorInstance.isResolvable()).thenReturn(true);
        when(mockLocatorInstance.get()).thenReturn(mockLocator);
        when(mockLocator.locate("default", "flow-a", "1.0.0", "inst-3"))
                .thenReturn(Optional.empty());

        Response response = resource.discover("default", "flow-a", "1.0.0", "inst-3");

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("test_memory_miss_no_locator_returns_404")
    void test_memory_miss_no_locator_returns_404() {
        // No definition in memory
        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of());

        // Locator is not configured (unsatisfied Instance)
        when(mockLocatorInstance.isResolvable()).thenReturn(false);

        Response response = resource.discover("default", "flow-a", "1.0.0", "inst-4");

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("test_definition_not_found_skips_to_locator")
    void test_definition_not_found_skips_to_locator() {
        // Definition doesn't exist
        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of());

        // Locator is configured and returns a location
        InstanceLocation location = new InstanceLocation("default", "flow-b", "1.0.0", "inst-5",
                "runner-pod-2");
        when(mockLocatorInstance.isResolvable()).thenReturn(true);
        when(mockLocatorInstance.get()).thenReturn(mockLocator);
        when(mockLocator.locate("default", "flow-b", "1.0.0", "inst-5"))
                .thenReturn(Optional.of(location));

        Response response = resource.discover("default", "flow-b", "1.0.0", "inst-5");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-2");
    }

    @Test
    @DisplayName("test_instance_not_in_active_registry_skips_to_locator")
    void test_instance_not_in_active_registry_skips_to_locator() {
        // Definition exists but instance is not active (not in memory)
        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        WorkflowDefinitionId defId = new WorkflowDefinitionId("default", "flow-c", "1.0.0");
        when(definition.id()).thenReturn(defId);
        when(definition.activeInstance("inst-6")).thenReturn(Optional.empty());

        when(mockApplication.workflowDefinitions()).thenReturn(java.util.Map.of(defId, definition));

        // Locator finds it in persistence
        InstanceLocation location = new InstanceLocation("default", "flow-c", "1.0.0", "inst-6",
                "runner-pod-3");
        when(mockLocatorInstance.isResolvable()).thenReturn(true);
        when(mockLocatorInstance.get()).thenReturn(mockLocator);
        when(mockLocator.locate("default", "flow-c", "1.0.0", "inst-6"))
                .thenReturn(Optional.of(location));

        Response response = resource.discover("default", "flow-c", "1.0.0", "inst-6");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).applicationId()).isEqualTo("runner-pod-3");
    }
}
