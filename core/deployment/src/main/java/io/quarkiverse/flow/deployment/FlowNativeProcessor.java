package io.quarkiverse.flow.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.auth.JWTConverter;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.executors.CallableTaskBuilder;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.executors.func.DataTypeConverter;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;

final class FlowNativeProcessor {

    /**
     * Registers the CNCF Java SDK default providers for native compilation.
     */
    @BuildStep
    void registerSDKServiceProviders(BuildProducer<ServiceProviderBuildItem> sp) {

        // TODO: make all of them @DefaultBeans so users can easily replace
        // TODO: these providers must be compatible with Quarkus Ecosystem
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(ExpressionFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(TaskExecutorFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(EventConsumer.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(EventPublisher.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(WorkflowModelFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(HttpRequestDecorator.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(JWTConverter.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(DataTypeConverter.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(CallableTaskBuilder.class.getName()));
    }
}
