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

    private final WorkflowApplicationCreator creator;

    @Inject
    public RuntimeWorkflowApplicationProvider(WorkflowApplicationCreator creator) {
        this.creator = creator;
    }

    /**
     * Creates a new WorkflowApplication instance for runtime-created workflows.
     * <p>
     * Each call returns a fresh instance with its own workflow definition cache,
     * preventing cache conflicts between different runtime workflow instances.
     *
     * @return a new runtime workflow application
     */
    public WorkflowApplication getRuntimeApplication() {
        // Create a NEW instance each time to ensure isolation between workflows
        return creator.create(false, false);
    }
}
