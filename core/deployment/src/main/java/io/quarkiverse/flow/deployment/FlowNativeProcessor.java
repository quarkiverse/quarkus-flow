package io.quarkiverse.flow.deployment;

import com.github.f4b6a3.ulid.UlidCreator;
import com.github.f4b6a3.ulid.UlidFactory;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.events.InMemoryEvents;
import io.serverlessworkflow.impl.executors.DefaultTaskExecutorFactory;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import io.serverlessworkflow.impl.jackson.schema.JsonSchemaValidatorFactory;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;

final class FlowNativeProcessor {

    /**
     * see <a href="https://github.com/serverlessworkflow/sdk-java/issues/812">Native-image build fails due to UlidCreator
     * static
     * initialization (Random in image heap)</a>
     */
    @BuildStep
    void runtimeInitUlid(BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem(
                "com.github.f4b6a3.ulid.UlidCreator$MonotonicFactoryHolder"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                com.github.f4b6a3.ulid.UlidCreator.class.getName()));
        producer.produce(new RuntimeInitializedClassBuildItem(
                com.github.f4b6a3.ulid.UlidFactory.class.getName()));
    }

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
                DefaultTaskExecutorFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(SchemaValidatorFactory.class.getName(),
                JsonSchemaValidatorFactory.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventConsumer.class.getName(),
                InMemoryEvents.class.getName()));
        sp.produce(new ServiceProviderBuildItem(EventPublisher.class.getName(),
                InMemoryEvents.class.getName()));
    }

}
