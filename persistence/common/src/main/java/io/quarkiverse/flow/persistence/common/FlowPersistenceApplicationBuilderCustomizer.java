package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.persistence.PersistenceApplicationBuilder;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceWriter;

@ApplicationScoped
@Unremovable
public class FlowPersistenceApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Inject
    FlowPersistenceConfig config;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        final InstanceHandle<PersistenceInstanceHandlers> persistenceInstanceHandlers = Arc.container()
                .instance(PersistenceInstanceHandlers.class, Any.Literal.INSTANCE);
        if (persistenceInstanceHandlers.isAvailable()) {
            PersistenceInstanceWriter actualWriter = persistenceInstanceHandlers.get().writer();
            PersistenceInstanceWriter writer = config.excludeWorkflows().isEmpty() ? actualWriter
                    : new FilteredPersistenceWriter(actualWriter, excludedIds(config.excludeWorkflows()));
            PersistenceApplicationBuilder.builder(builder, writer);
        } else {
            throw new IllegalStateException(
                    "No PersistenceInstanceHandlers available. Make sure that you add one of quarkus-flow-jpa, quarkus-flow-mvstore or quarkus-flow-redis in your classpath.");
        }
    }
}