package io.quarkiverse.flow.persistence.common;

import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.NO_PERSISTENCE_WARN_MSG;
import static io.quarkiverse.flow.persistence.common.FlowPersistenceUtils.excludedIds;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(FlowPersistenceApplicationBuilderCustomizer.class);

    @Inject
    FlowPersistenceConfig config;

    @Inject
    Instance<PersistenceInstanceHandlers> persistenceHandlers;

    @Inject
    Instance<AllStrategyCorrelationInfoFactory> allStrategyFactory;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        // Shouldn't happen on users' code, but internally some extensions might optionally look for a PersistenceInstanceHandlers instance.
        if (persistenceHandlers.isResolvable()) {
            PersistenceInstanceWriter actualWriter = persistenceHandlers.get().writer();
            PersistenceApplicationBuilder.builder(builder, config.excludeWorkflows().isEmpty() ? actualWriter
                    : new FilteredPersistenceWriter(actualWriter, excludedIds(config.excludeWorkflows())));
            if (allStrategyFactory.isResolvable()) {
                builder.withAllStrategyCorrelationInfoFactory(allStrategyFactory.get());
            }
        } else
            LOG.warn(NO_PERSISTENCE_WARN_MSG);
    }
}
