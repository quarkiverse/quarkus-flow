package io.quarkiverse.flow.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.events.InMemoryEvents;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.executors.func.JavaTaskExecutorFactory;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import io.serverlessworkflow.impl.jackson.schema.JsonSchemaValidatorFactory;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;

final class FlowNativeProcessor {

    /**
     * Registers the CNCF Java SDK default providers for native compilation.
     */
    @BuildStep
    void registerSDKServiceProviders(BuildProducer<ServiceProviderBuildItem> sp) {

        // TODO: make all of them @DefaultBeans so users can easily replace
        // TODO: these providers must be compatible with Quarkus Ecosystem

        sp.produce(new ServiceProviderBuildItem(ExpressionFactory.class.getName(),
                JQExpressionFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(TaskExecutorFactory.class.getName(),
                JavaTaskExecutorFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(SchemaValidatorFactory.class.getName(),
                JsonSchemaValidatorFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventConsumer.class.getName(),
                InMemoryEvents.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventPublisher.class.getName(),
                InMemoryEvents.class.getName()));
    }

}
