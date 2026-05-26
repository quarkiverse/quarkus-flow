package io.quarkiverse.flow.langchain4j.workflow.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.recorders.WorkflowApplicationCreator;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Provides a dedicated WorkflowApplication instance for runtime-created agentic workflows.
 * <p>
 * This separate instance prevents workflow definition caching conflicts between:
 * <ul>
 * <li>Build-time generated workflows (registered in the main CDI WorkflowApplication)</li>
 * <li>Runtime-created workflows (programmatic API usage, often in tests)</li>
 * </ul>
 * <p>
 * The runtime instance shares the same Quarkus infrastructure (ConfigManager, HttpClientProvider,
 * FaultToleranceProvider, etc.) but maintains its own workflow definition cache.
 */
@ApplicationScoped
public class RuntimeWorkflowApplicationProvider {

    private final WorkflowApplication runtimeApplication;

    @Inject
    public RuntimeWorkflowApplicationProvider(WorkflowApplicationCreator creator) {
        // Create a separate WorkflowApplication instance for runtime workflows
        // This uses the same infrastructure but has its own workflow definition cache
        this.runtimeApplication = creator.create(false, false);
    }

    /**
     * Returns the dedicated WorkflowApplication instance for runtime-created workflows.
     *
     * @return the runtime workflow application
     */
    public WorkflowApplication getRuntimeApplication() {
        return runtimeApplication;
    }
}
