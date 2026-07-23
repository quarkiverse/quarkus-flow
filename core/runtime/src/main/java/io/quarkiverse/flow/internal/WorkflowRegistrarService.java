package io.quarkiverse.flow.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowCloudEventsConfig;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Central internal API for {@link WorkflowDefinition} logic.
 */
@ApplicationScoped
@Unremovable
public class WorkflowRegistrarService {

    private final Logger LOGGER = LoggerFactory.getLogger(WorkflowRegistrarService.class.getName());

    @Inject
    WorkflowApplication application;

    @Inject
    FlowCloudEventsConfig cloudEventsConfig;

    public WorkflowDefinition register(Workflow workflow) {
        final WorkflowDefinitionId id = WorkflowDefinitionId.of(workflow);
        LOGGER.debug("Registering workflow {}", id);
        applyDefaultEventSource(workflow, id);
        final WorkflowDefinition definition = application.workflowDefinition(workflow);
        return definition;
    }

    /**
     * Applies the configured default CloudEvent {@code source} to emit tasks that declare no source. The default is
     * either an explicit configured value ({@code quarkus.flow.cloud-events.source}) or, when unset and derivation is
     * enabled, the workflow's identity as {@code namespace:name:version}.
     */
    private void applyDefaultEventSource(Workflow workflow, WorkflowDefinitionId id) {
        final String defaultSource = cloudEventsConfig.source()
                .orElseGet(() -> cloudEventsConfig.deriveSourceFromWorkflow() ? id.toString(":") : null);
        if (defaultSource != null) {
            EmitEventSourceInjector.applyDefaultSource(workflow, defaultSource);
        }
    }

}
