package io.quarkiverse.flow.persistence.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.persistence.PersistenceApplicationBuilder;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@ApplicationScoped
@Unremovable
public class FlowPersistenceApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        final InstanceHandle<PersistenceInstanceHandlers> persistenceInstanceHandlers = Arc.container()
                .instance(PersistenceInstanceHandlers.class, Any.Literal.INSTANCE);
        if (persistenceInstanceHandlers.isAvailable())
            PersistenceApplicationBuilder.builder(builder, persistenceInstanceHandlers.get().writer());
        else
            throw new IllegalStateException(
                    "No PersistenceInstanceHandlers available. Make sure that you add one of quarkus-flow-jpa, quarkus-flow-mvstore or quarkus-flow-redis in your classpath.");
    }
}
