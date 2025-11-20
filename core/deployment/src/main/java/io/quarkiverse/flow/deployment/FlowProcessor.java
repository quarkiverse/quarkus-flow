package io.quarkiverse.flow.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

class FlowProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowProcessor.class); // NEW

    private static final String FEATURE = "flow";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void keepAndReflectFlowDescriptors(
            List<DiscoveredFlowBuildItem> discoveredFlows,
            BuildProducer<UnremovableBeanBuildItem> keep) {

        List<String> flows = discoveredFlows.stream()
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
            List<DiscoveredFlowBuildItem> discoveredFlows,
            List<DiscoveredWorkflowFileBuildItem> workflows) {

        List<String> identifiers = new ArrayList<>();

        for (DiscoveredFlowBuildItem it : discoveredFlows) {
            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier().annotation(DotNames.IDENTIFIER).addValue("value", it.getClassName()).done()
                    .supplier(recorder.workflowDefinitionSupplier(it.getClassName()))
                    .done());
            identifiers.add(it.getClassName());
        }

        for (DiscoveredWorkflowFileBuildItem workflow : workflows) {
            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier().annotation(DotNames.IDENTIFIER)
                    .addValue("value", workflow.identifier()).done()
                    .supplier(recorder.workflowDefinitionFromFileSupplier(workflow.locationString()))
                    .done());

            identifiers.add(workflow.identifier());
        }

        logWorkflowList(identifiers);
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

    private void logWorkflowList(List<String> identifiers) {
        if (identifiers.isEmpty()) {
            LOG.info("Flow: No WorkflowDefinition beans were registered.");
            return;
        }

        // sort for stable output
        Collections.sort(identifiers);

        final String header = "Workflow class (Qualifier)";
        int w = header.length();
        for (String s : identifiers)
            w = Math.max(w, s.length());

        String sep = "+" + "-".repeat(w + 2) + "+";
        StringBuilder sb = new StringBuilder(64 + identifiers.size() * (w + 8));
        sb.append('\n');
        sb.append("Flow: Registered WorkflowDefinition beans\n");
        sb.append(sep).append('\n');
        sb.append(String.format("| %-" + w + "s |\n", header));
        sb.append(sep).append('\n');
        for (String s : identifiers) {
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

    @BuildStep(onlyIf = { IsDevelopment.class })
    public void watchChanges(List<DiscoveredWorkflowFileBuildItem> workflows,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles) {
        for (DiscoveredWorkflowFileBuildItem workflow : workflows) {
            watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                    .setLocation(workflow.locationString())
                    .setRestartNeeded(true)
                    .build());
        }
    }
}
