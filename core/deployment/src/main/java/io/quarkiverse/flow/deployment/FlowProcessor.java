package io.quarkiverse.flow.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.config.FlowTracingConfig;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.MicroprofileConfigManager;
import io.quarkiverse.flow.providers.WorkflowExceptionMapper;
import io.quarkiverse.flow.recorders.SDKRecorder;
import io.quarkiverse.flow.recorders.WorkflowApplicationRecorder;
import io.quarkiverse.flow.recorders.WorkflowDefinitionRecorder;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowException;

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
    AdditionalBeanBuildItem registerRuntimeServices() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(JQScopeSupplier.class)
                .addBeanClass(CredentialsProviderSecretManager.class)
                .addBeanClass(MicroprofileConfigManager.class)
                .addBeanClass(HttpClientProvider.class)
                .addBeanClass(WorkflowRegistry.class)
                .setUnremovable()
                .build();
    }

    /**
     * Registers our default {@link WorkflowExceptionMapper} to the JAX-RS exception mappers.
     */
    @BuildStep
    void registerWorkflowExceptionMapper(BuildProducer<ExceptionMapperBuildItem> mappers) {
        mappers.produce(new ExceptionMapperBuildItem(
                WorkflowExceptionMapper.class.getName(),
                WorkflowException.class.getName(),
                Priorities.USER,
                true));
    }

    /**
     * Produce one WorkflowDefinition bean per discovered descriptor.
     * Each bean is qualified with @Identifier("<id>").
     */
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void produceWorkflowDefinitions(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans,
            BuildProducer<FlowIdentifierBuildItem> identifiers,
            List<DiscoveredFlowBuildItem> discoveredFlows) {

        for (DiscoveredFlowBuildItem it : discoveredFlows) {
            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier().annotation(DotNames.IDENTIFIER).addValue("value", it.getClassName()).done()
                    .supplier(recorder.workflowDefinitionSupplier(it.getClassName()))
                    .done());
            identifiers.produce(new FlowIdentifierBuildItem(Set.of(it.getClassName())));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void produceWorkflowDefinitionsFromFile(
            List<DiscoveredWorkflowFileBuildItem> workflows,
            BuildProducer<SyntheticBeanBuildItem> beans,
            BuildProducer<FlowIdentifierBuildItem> identifiers,
            WorkflowDefinitionRecorder recorder,
            FlowDefinitionsConfig config) {
        for (DiscoveredWorkflowFileBuildItem workflow : workflows) {

            String flowSubclassIdentifier = WorkflowNamingConverter.generateFlowClassIdentifier(
                    workflow.namespace(), workflow.name(), config.namespace().prefix());

            beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .setRuntimeInit()
                    .addQualifier().annotation(DotNames.IDENTIFIER)
                    .addValue("value", workflow.regularIdentifier()).done()
                    .addQualifier().annotation(DotNames.IDENTIFIER)
                    .addValue("value", flowSubclassIdentifier).done()
                    .supplier(recorder.workflowDefinitionFromFileSupplier(workflow.location()))
                    .done());

            identifiers.produce(new FlowIdentifierBuildItem(
                    Set.of(flowSubclassIdentifier, workflow.regularIdentifier())));
        }
    }

    @BuildStep
    void produceGeneratedFlows(List<DiscoveredWorkflowFileBuildItem> workflows,
            BuildProducer<GeneratedBeanBuildItem> classes,
            FlowDefinitionsConfig definitionsConfig) {

        GeneratedBeanGizmoAdaptor gizmo = new GeneratedBeanGizmoAdaptor(classes);
        for (DiscoveredWorkflowFileBuildItem workflow : workflows) {
            String flowSubclassIdentifier = WorkflowNamingConverter.generateFlowClassIdentifier(
                    workflow.namespace(), workflow.name(), definitionsConfig.namespace().prefix());

            try (ClassCreator creator = ClassCreator.builder()
                    .className(flowSubclassIdentifier)
                    .superClass(DotNames.FLOW.toString())
                    .classOutput(gizmo)
                    .build()) {

                creator.addAnnotation(Unremovable.class);
                creator.addAnnotation(ApplicationScoped.class);
                creator.addAnnotation(Identifier.class).add("value", flowSubclassIdentifier);

                // workflowDefinition field
                FieldCreator fieldCreator = creator.getFieldCreator("workflowDefinition",
                        WorkflowDefinition.class.getName());
                fieldCreator.setModifiers(Opcodes.ACC_PUBLIC);
                fieldCreator.addAnnotation(Inject.class);
                fieldCreator.addAnnotation(Identifier.class)
                        .add("value", flowSubclassIdentifier);

                // descriptor() method
                var method = creator.getMethodCreator("descriptor", Workflow.class);
                method.setModifiers(Opcodes.ACC_PUBLIC);
                method.returnValue(
                        method.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(WorkflowDefinition.class, "workflow", Workflow.class),
                                method.readInstanceField(fieldCreator.getFieldDescriptor(), method.getThis())));
            }
        }
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

    @BuildStep
    @Produce(SyntheticBeanBuildItem.class)
    void logRegisteredWorkflows(
            List<FlowIdentifierBuildItem> registeredIdentifiers) {
        List<String> allIdentifiers = registeredIdentifiers.stream().map(FlowIdentifierBuildItem::identifiers)
                .map(set -> String.join(", ", set))
                .distinct()
                .collect(Collectors.toList());
        logWorkflowList(allIdentifiers);
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
                    .setLocation(workflow.location())
                    .setRestartNeeded(true)
                    .build());
        }
    }
}
