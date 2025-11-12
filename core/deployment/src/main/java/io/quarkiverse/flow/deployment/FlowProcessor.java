package io.quarkiverse.flow.deployment;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.config.FlowTracingConfig;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.MicroprofileConfigManager;
import io.quarkiverse.flow.recorders.SDKRecorder;
import io.quarkiverse.flow.recorders.WorkflowApplicationRecorder;
import io.quarkiverse.flow.recorders.WorkflowDefinitionRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

class FlowProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowProcessor.class); // NEW

    private static final String FEATURE = "flow";
    private static final DotName FLOW_DOTNAME = DotName.createSimple(Flow.class.getName());
    private static final DotName IDENTIFIER_DOTNAME = DotName.createSimple(Identifier.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Collect all Flow beans.
     */
    @BuildStep
    void collectFlows(CombinedIndexBuildItem index, BuildProducer<DiscoveredFlowBuildItem> wf) {
        for (ClassInfo flow : index.getIndex().getAllKnownSubclasses(FLOW_DOTNAME)) {
            if (flow.isAbstract())
                continue;
            wf.produce(new DiscoveredFlowBuildItem(flow.name().toString()));
        }
    }

    @BuildStep
    void keepAndReflectFlowDescriptors(
            List<DiscoveredFlowBuildItem> discovered,
            BuildProducer<UnremovableBeanBuildItem> keep) {

        List<String> flows = discovered.stream()
                .map(DiscoveredFlowBuildItem::getClassName)
                .distinct()
                .toList();
        // Keep producers from being removed
        keep.produce(UnremovableBeanBuildItem.beanClassNames(flows.toArray(String[]::new)));
    }

    @BuildStep
    AdditionalBeanBuildItem registerRuntimeDefaults() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(JQScopeSupplier.class)
                .addBeanClass(CredentialsProviderSecretManager.class)
                .addBeanClass(MicroprofileConfigManager.class)
                .setUnremovable()
                .build();
    }

    /**
     * Produce one WorkflowDefinition bean per discovered descriptor.
     * Each bean is qualified with @Identifier("<id>").
     */
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void produceWorkflowDefinitions(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans,
            List<DiscoveredFlowBuildItem> discovered) {

        List<String> fqcnList = new java.util.ArrayList<>();

        for (DiscoveredFlowBuildItem it : discovered) {
            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier().annotation(IDENTIFIER_DOTNAME).addValue("value", it.getClassName()).done()
                    .supplier(recorder.workflowDefinitionSupplier(it.getClassName()))
                    .done());
            fqcnList.add(it.getClassName());
        }

        logWorkflowList(fqcnList);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void registerWorkflowApp(WorkflowApplicationRecorder recorder,
            ShutdownContextBuildItem shutdown,
            FlowTracingConfig cfg,
            LaunchModeBuildItem launchMode,
            BuildProducer<SyntheticBeanBuildItem> beans) {

        boolean tracingEnabled = cfg.enabled().orElse(launchMode.getLaunchMode().isDevOrTest());

        beans.produce(SyntheticBeanBuildItem.configure(WorkflowApplication.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .supplier(recorder.workflowAppSupplier(shutdown, tracingEnabled))
                .done());

        LOG.info("Flow: Registering Workflow Application bean: {}", WorkflowApplication.class.getName());
    }

    private void logWorkflowList(List<String> fqcns) {
        if (fqcns.isEmpty()) {
            LOG.info("Flow: No WorkflowDefinition beans were registered.");
            return;
        }

        // sort for stable output
        Collections.sort(fqcns);

        final String header = "Workflow class (Qualifier)";
        int w = header.length();
        for (String s : fqcns)
            w = Math.max(w, s.length());

        String sep = "+" + "-".repeat(w + 2) + "+";
        StringBuilder sb = new StringBuilder(64 + fqcns.size() * (w + 8));
        sb.append('\n');
        sb.append("Flow: Registered WorkflowDefinition beans\n");
        sb.append(sep).append('\n');
        sb.append(String.format("| %-" + w + "s |\n", header));
        sb.append(sep).append('\n');
        for (String s : fqcns) {
            sb.append(String.format("| %-" + w + "s |\n", s));
        }
        sb.append(sep).append('\n');

        LOG.info(sb.toString());
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void overrideObjectMapper(SDKRecorder recorder) {
        recorder.injectQuarkusObjectMapper();
    }

}
