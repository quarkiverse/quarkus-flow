package io.quarkiverse.flow.deployment;

import org.jboss.jandex.ClassInfo;

import io.quarkiverse.flow.Flowable;
import io.quarkiverse.flow.dsl.executors.DataTypeConverter;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.LambdaCapturingTypeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.additional.NamedWorkflowAdditionalObject;
import io.serverlessworkflow.impl.auth.JWTConverter;
import io.serverlessworkflow.impl.events.CloudEventPredicateFactory;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.executors.CallableTaskBuilder;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

final class FlowNativeProcessor {

    /**
     * Registers the CNCF Java SDK default providers for native compilation.
     */
    @BuildStep
    void registerSDKServiceProviders(BuildProducer<ServiceProviderBuildItem> sp) {
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(ExpressionFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(TaskExecutorFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(EventConsumer.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(EventPublisher.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(WorkflowExecutionListener.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(WorkflowModelFactory.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(HttpRequestDecorator.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(JWTConverter.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(DataTypeConverter.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(CallableTaskBuilder.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(NamedWorkflowAdditionalObject.class.getName()));
        sp.produce(ServiceProviderBuildItem.allProvidersFromClassPath(CloudEventPredicateFactory.class.getName()));
    }

    @BuildStep
    void runtimeInitialization(BuildProducer<RuntimeInitializedClassBuildItem> rt) {
        rt.produce(new RuntimeInitializedClassBuildItem(
                "io.serverlessworkflow.impl.executors.openapi.jackson.JacksonUnifiedOpenAPIReaderFactory$JacksonUnifiedOpenAPIReaderHolder"));
    }

    @BuildStep
    void registerSerializableFunctions(CombinedIndexBuildItem indexedClasses,
            BuildProducer<LambdaCapturingTypeBuildItem> producer) {
        indexedClasses.getIndex().getAllKnownImplementations(Flowable.class).stream().map(ClassInfo::toString)
                .map(LambdaCapturingTypeBuildItem::new).forEach(producer::produce);
    }

    @BuildStep
    ReflectiveClassBuildItem registerForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Jakarta validation's constraints used by the Open Workflow Specification sdk-java types.
                "org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator",
                "org.hibernate.validator.internal.constraintvalidators.bv.NotNullValidator",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArray",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfBoolean",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfByte",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfChar",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfDouble",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfFloat",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfInt",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfLong",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfPrimitives",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArraysOfShort",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForCharSequence",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForCollection",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForMap")
                .queryConstructors(true)
                .constructors(true)
                .methods(true)
                .fields(true)
                .classes()
                .build();
    }
}
