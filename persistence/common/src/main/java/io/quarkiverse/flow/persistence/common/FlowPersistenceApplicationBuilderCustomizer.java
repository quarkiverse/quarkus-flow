package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.persistence.PersistenceApplicationBuilder;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceWriter;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;

@ApplicationScoped
@Unremovable
public class FlowPersistenceApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Inject
    FlowPersistenceConfig config;

    @Inject
    PersistenceInstanceHandlers persistenceHandlers;

    @Inject
    Instance<AllStrategyCorrelationInfoFactory> allStrategyFactory;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        PersistenceInstanceWriter actualWriter = persistenceHandlers.writer();
        PersistenceApplicationBuilder.builder(builder, config.excludeWorkflows().isEmpty() ? actualWriter
                : new FilteredPersistenceWriter(actualWriter, excludedIds(config.excludeWorkflows())));
        if (allStrategyFactory.isResolvable()) {
            builder.withAllStrategyCorrelationInfoFactory(allStrategyFactory.get());
        }
    }
}
