package io.quarkiverse.flow.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

import io.quarkiverse.flow.FlowRegistry;
import io.quarkiverse.flow.FlowRunner;
import io.quarkiverse.flow.recorders.DiscoveredFlow;
import io.quarkiverse.flow.recorders.FlowRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.serverlessworkflow.impl.WorkflowApplication;

class FlowProcessor {

    private static final String FEATURE = "quarkus-flow";
    private static final DotName FLOW_DEFINITION_DOTNAME = DotName.createSimple("io.quarkiverse.flow.FlowDefinition");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Collect all FlowDefinition beans.
     */
    @BuildStep
    void wireDefinitions(CombinedIndexBuildItem index, BuildProducer<DiscoveredFlowBuildItem> wf) {
        for (AnnotationInstance inst : index.getIndex().getAnnotations(FLOW_DEFINITION_DOTNAME)) {
            final AnnotationTarget target = inst.target();
            if (target == null || target.kind() != AnnotationTarget.Kind.METHOD) {
                throw new IllegalStateException("@FlowDefinition must target a METHOD");
            }
            wf.produce(new DiscoveredFlowBuildItem(target.asMethod()));
        }
    }

    /**
     * Register all workflows collected and add them to the FlowRegister.
     */
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void registerWorkflows(FlowRecorder recorder, BeanContainerBuildItem beanContainer,
            List<DiscoveredFlowBuildItem> discovered) {
        List<DiscoveredFlow> defs = discovered.stream().map(b -> b.workflow).toList();
        recorder.registerWorkflows(beanContainer.getValue(), defs);
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

    @BuildStep
    AdditionalBeanBuildItem registerCoreBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowRegistry.class)
                .setUnremovable()
                .addBeanClass(FlowRunner.class)
                .setUnremovable()
                .build();
    }

    /**
     * In case @WorkflowDefinitions are not bind to any other bean, we mark them as `unremovable`.
     */
    @BuildStep
    void keepWorkflowProducers(List<DiscoveredFlowBuildItem> discovered,
            BuildProducer<UnremovableBeanBuildItem> keep) {
        keep.produce(UnremovableBeanBuildItem.beanClassNames(
                discovered.stream().map(d -> d.workflow.className()).distinct().toArray(String[]::new)));
    }

}
