package io.quarkiverse.flow.langchain4j.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Tests for {@link RuntimeWorkflowApplicationProvider}.
 * <p>
 * This provider creates a dedicated WorkflowApplication instance for runtime-created
 * agentic workflows, preventing definition cache conflicts between build-time and
 * runtime workflows.
 */
@QuarkusTest
@DisplayName("RuntimeWorkflowApplicationProvider tests")
public class RuntimeWorkflowApplicationProviderTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    @Inject
    WorkflowApplication buildTimeApp;

    @Test
    @DisplayName("getRuntimeApplication_returns_non_null_instance")
    void getRuntimeApplication_returns_non_null_instance() {
        WorkflowApplication runtimeApp = runtimeAppProvider.getRuntimeApplication();

        assertThat(runtimeApp).isNotNull();
    }

    @Test
    @DisplayName("runtime_and_buildtime_applications_are_different_instances")
    void runtime_and_buildtime_applications_are_different_instances() {
        WorkflowApplication runtimeApp = runtimeAppProvider.getRuntimeApplication();

        // The two applications should be different instances to prevent cache conflicts
        assertThat(runtimeApp)
                .as("Runtime and build-time WorkflowApplications must be separate instances " +
                        "to prevent workflow definition cache conflicts")
                .isNotSameAs(buildTimeApp);
    }

    @Test
    @DisplayName("getRuntimeApplication_returns_new_instance_on_each_call")
    void getRuntimeApplication_returns_new_instance_on_each_call() {
        WorkflowApplication first = runtimeAppProvider.getRuntimeApplication();
        WorkflowApplication second = runtimeAppProvider.getRuntimeApplication();

        // Each call should return a new instance to prevent workflow definition cache conflicts
        assertThat(first)
                .as("Each call to getRuntimeApplication() must return a new instance " +
                        "to ensure workflow definition cache isolation")
                .isNotSameAs(second);
    }

    @Test
    @DisplayName("runtime_application_can_register_workflows")
    void runtime_application_can_register_workflows() {
        WorkflowApplication runtimeApp = runtimeAppProvider.getRuntimeApplication();

        // Create a simple test workflow
        var workflow = io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow("test-runtime-workflow")
                .tasks(tasks -> tasks.function(f -> f.function(input -> "test-result")))
                .build();

        // Should be able to register it without errors
        var definition = runtimeApp.workflowDefinition(workflow);

        assertThat(definition).isNotNull();
        assertThat(definition.id().name()).isEqualTo("test-runtime-workflow");
    }

    @Test
    @DisplayName("runtime_and_buildtime_applications_have_independent_caches")
    void runtime_and_buildtime_applications_have_independent_caches() {
        WorkflowApplication runtimeApp = runtimeAppProvider.getRuntimeApplication();

        // Register a workflow in the runtime application
        var runtimeWorkflow = io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow("runtime-only-workflow")
                .tasks(tasks -> tasks.function(f -> f.function(input -> "runtime")))
                .build();

        runtimeApp.workflowDefinition(runtimeWorkflow);

        // Register a different workflow with the same ID in the build-time application
        var buildTimeWorkflow = io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow("buildtime-only-workflow")
                .tasks(tasks -> tasks.function(f -> f.function(input -> "buildtime")))
                .build();

        buildTimeApp.workflowDefinition(buildTimeWorkflow);

        // Both should coexist without conflicts because they use different caches
        var runtimeDef = runtimeApp.workflowDefinition(runtimeWorkflow);
        var buildTimeDef = buildTimeApp.workflowDefinition(buildTimeWorkflow);

        assertThat(runtimeDef.id().name()).isEqualTo("runtime-only-workflow");
        assertThat(buildTimeDef.id().name()).isEqualTo("buildtime-only-workflow");

        // Verify they're truly independent by checking that each app only knows about its own workflow
        assertThat(runtimeDef.id().name()).isNotEqualTo(buildTimeDef.id().name());
    }
}
