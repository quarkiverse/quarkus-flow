package io.quarkiverse.flow.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;

import io.quarkiverse.flow.FlowDefinition;
import io.quarkiverse.flow.FlowDescriptor;
import io.quarkiverse.flow.providers.QuarkusJQExpressionFactory;
import io.quarkiverse.flow.recorders.FlowRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

class FlowProcessor {

    private static final String FEATURE = "flow";
    private static final DotName FLOW_DEFINITION_DOTNAME = DotName.createSimple(FlowDefinition.class.getName());
    private static final DotName FLOW_DESCRIPTOR_DOTNAME = DotName.createSimple(FlowDescriptor.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Collect all FlowDescriptor beans.
     */
    @BuildStep
    void collectFlowDescriptors(CombinedIndexBuildItem index, BuildProducer<DiscoveredFlowBuildItem> wf) {
        for (AnnotationInstance inst : index.getIndex().getAnnotations(FLOW_DESCRIPTOR_DOTNAME)) {
            final AnnotationTarget target = inst.target();
            if (target == null || target.kind() != AnnotationTarget.Kind.METHOD) {
                throw new IllegalStateException("@FlowDescriptor must target a METHOD");
            }

            String workflowName = target.asMethod().name();
            AnnotationValue val = inst.value("value");
            if (val != null && val.asString() != null && !val.asString().isBlank()) {
                workflowName = val.asString();
            }

            wf.produce(new DiscoveredFlowBuildItem(target.asMethod(), workflowName));
        }
    }

    /**
     * In case @FlowDescriptors are not bind to any other bean, we mark them as `unremovable`.
     */
    @BuildStep
    void keepAndReflectFlowDescriptors(
            List<DiscoveredFlowBuildItem> discovered,
            BuildProducer<UnremovableBeanBuildItem> keep,
            BuildProducer<ReflectiveMethodBuildItem> reflective) {

        List<String> owners = discovered.stream()
                .map(d -> d.workflow.className)
                .distinct()
                .toList();

        // Keep producers from being removed
        keep.produce(UnremovableBeanBuildItem.beanClassNames(owners.toArray(String[]::new)));

        for (DiscoveredFlowBuildItem d : discovered) {
            reflective.produce(new ReflectiveMethodBuildItem(d.workflow.className, d.workflow.methodName, d.workflow.params));
        }
    }

    /**
     * Make sure our annotation types are in the bean archive, and any helpers if needed.
     */
    @BuildStep
    AdditionalBeanBuildItem coreBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowDefinition.class)
                .addBeanClass(FlowDescriptor.class)
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerRuntimeDefaults() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusJQExpressionFactory.class)
                .setUnremovable()
                .build();
    }

    /**
     * Produce one WorkflowDefinition bean per discovered descriptor.
     * Each bean is qualified with @FlowDefinition("<id>").
     */
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void produceWorkflowDefinitions(FlowRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans,
            List<DiscoveredFlowBuildItem> discovered) {

        for (var it : discovered) {
            var df = it.workflow;// decided at build-time

            // Build a @FlowDefinition("<id>") qualifier
            var qualifier = AnnotationInstance.create(
                    FLOW_DEFINITION_DOTNAME, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", it.workflow.workflowName) });

            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier(qualifier)
                    .supplier(recorder.workflowDefinitionSupplier(df))
                    .done());
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void registerWorkflowApp(FlowRecorder recorder, ShutdownContextBuildItem shutdown,
            BuildProducer<SyntheticBeanBuildItem> beans) {
        beans.produce(SyntheticBeanBuildItem.configure(WorkflowApplication.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .supplier(recorder.workflowAppSupplier(shutdown))
                .done());
    }

}
