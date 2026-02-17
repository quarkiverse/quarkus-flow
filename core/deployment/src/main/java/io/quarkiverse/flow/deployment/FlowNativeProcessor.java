package io.quarkiverse.flow.deployment;

import io.quarkiverse.flow.converters.Multi2CompletableFuture;
import io.quarkiverse.flow.converters.Uni2CompletableFuture;
import io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.auth.JWTConverter;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.events.InMemoryEvents;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.executors.func.DataTypeConverter;
import io.serverlessworkflow.impl.executors.func.JavaTaskExecutorFactory;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;
import io.serverlessworkflow.impl.executors.http.oauth.jackson.JacksonJWTConverter;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.func.JavaExpressionFactory;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;

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
        sp.produce(new ServiceProviderBuildItem(ExpressionFactory.class.getName(),
                JavaExpressionFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(TaskExecutorFactory.class.getName(),
                JavaTaskExecutorFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventConsumer.class.getName(),
                InMemoryEvents.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventPublisher.class.getName(),
                InMemoryEvents.class.getName()));
        sp.produce(new ServiceProviderBuildItem(WorkflowModelFactory.class.getName(),
                JacksonModelFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(HttpRequestDecorator.class.getName(),
                MetadataPropagationRequestDecorator.class.getName()));
        sp.produce(new ServiceProviderBuildItem(JWTConverter.class.getName(), JacksonJWTConverter.class.getName()));
        sp.produce(new ServiceProviderBuildItem(DataTypeConverter.class.getName(),
                Uni2CompletableFuture.class.getName(), Multi2CompletableFuture.class.getName()));
    }

}
