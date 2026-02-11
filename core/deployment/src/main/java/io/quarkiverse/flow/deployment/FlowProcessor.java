package io.quarkiverse.flow.deployment;

import static io.quarkiverse.flow.deployment.FlowLoggingUtils.logWorkflowList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.config.FlowTracingConfig;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.MicroprofileConfigManager;
import io.quarkiverse.flow.providers.WorkflowExceptionMapper;
import io.quarkiverse.flow.recorders.SDKRecorder;
import io.quarkiverse.flow.recorders.WorkflowApplicationRecorder;
import io.quarkiverse.flow.recorders.WorkflowDefinitionRecorder;
import io.quarkiverse.flow.recorders.WorkflowMicrometerRecorder;
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
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.smallrye.common.annotation.Identifier;

class FlowProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowProcessor.class); // NEW

    private static final String FEATURE = "flow";

    FlowDefinitionsConfig flowDefinitionsConfig;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void keepAndReflectFlowDescriptors(
            List<DiscoveredWorkflowBuildItem> discoveredWorkflows,
            BuildProducer<UnremovableBeanBuildItem> keep) {

        if (discoveredWorkflows.isEmpty()) {
            return;
        }

        List<String> workflowClassNames = discoveredWorkflows.stream().filter(DiscoveredWorkflowBuildItem::fromSource)
                .map(DiscoveredWorkflowBuildItem::className)
                .distinct()
                .toList();

        // Keep producers from being removed
        keep.produce(UnremovableBeanBuildItem.beanClassNames(workflowClassNames.toArray(String[]::new)));
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
            List<DiscoveredWorkflowBuildItem> discoveredWorkflows) {

        List<DiscoveredWorkflowBuildItem> fromSource = discoveredWorkflows.stream()
                .filter(DiscoveredWorkflowBuildItem::fromSource)
                .toList();
        for (DiscoveredWorkflowBuildItem d : fromSource) {
            produceWorkflowBeanFromSource(recorder, beans, identifiers, d);
        }

        List<DiscoveredWorkflowBuildItem> fromSpec = discoveredWorkflows.stream()
                .filter(DiscoveredWorkflowBuildItem::fromSpec)
                .toList();
        for (DiscoveredWorkflowBuildItem d : fromSpec) {
            produceWorkflowBeanFromSpec(recorder, beans, identifiers, d);
        }
    }

    private void produceWorkflowBeanFromSource(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans, BuildProducer<FlowIdentifierBuildItem> identifiers,
            DiscoveredWorkflowBuildItem it) {
        beans.produce(SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .addQualifier().annotation(DotNames.IDENTIFIER).addValue("value", it.className()).done()
                .supplier(recorder.workflowDefinitionSupplier(it.className()))
                .done());
        identifiers.produce(new FlowIdentifierBuildItem(Set.of(it.className())));
    }

    private void produceWorkflowBeanFromSpec(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans, BuildProducer<FlowIdentifierBuildItem> identifiers,
            DiscoveredWorkflowBuildItem workflow) {
        String flowSubclassIdentifier = WorkflowNamingConverter.generateFlowClassIdentifier(
                workflow.namespace(), workflow.name(), this.flowDefinitionsConfig.namespace().prefix());

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

    @BuildStep
    void produceGeneratedFlows(List<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<GeneratedBeanBuildItem> classes,
            FlowDefinitionsConfig definitionsConfig) {

        List<DiscoveredWorkflowBuildItem> fromSpec = workflows.stream().filter(DiscoveredWorkflowBuildItem::fromSpec)
                .toList();

        GeneratedBeanGizmoAdaptor gizmo = new GeneratedBeanGizmoAdaptor(classes);
        for (DiscoveredWorkflowBuildItem workflow : fromSpec) {
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

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    WorkflowApplicationBuilderBuildItem produceApplicationBuilder(WorkflowApplicationRecorder recorder, FlowTracingConfig cfg,
            LaunchModeBuildItem launchMode) {
        return new WorkflowApplicationBuilderBuildItem(
                recorder.workflowAppBuilderSupplier(cfg.enabled().orElse(launchMode.getLaunchMode().isDevOrTest())));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void registerWorkflowApp(WorkflowApplicationRecorder recorder,
            ShutdownContextBuildItem shutdown,
            WorkflowApplicationBuilderBuildItem appBuilder,
            BuildProducer<SyntheticBeanBuildItem> beans) {
        beans.produce(SyntheticBeanBuildItem.configure(WorkflowApplication.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .supplier(recorder.workflowAppSupplier(appBuilder.builder(), shutdown))
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
        logWorkflowList(LOG,
                allIdentifiers,
                "Flow: No WorkflowDefinition beans were registered.",
                "Flow: Registered WorkflowDefinition beans",
                "Workflow class (Qualifier)");
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void overrideObjectMapper(SDKRecorder recorder) {
        recorder.injectQuarkusObjectMapper();
    }

    @BuildStep(onlyIf = { IsDevelopment.class })
    public void watchChanges(List<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles) {

        List<String> specLocations = workflows.stream().filter(DiscoveredWorkflowBuildItem::fromSpec)
                .map(DiscoveredWorkflowBuildItem::location)
                .toList();

        for (String location : specLocations) {
            watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                    .setLocation(location)
                    .setRestartNeeded(true)
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureRegistryPrometheusIntegration(WorkflowMicrometerRecorder recorder, FlowMetricsConfig metricsConfig,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<SyntheticBeanBuildItem> beans,
            BuildProducer<RemovedResourceBuildItem> removeResource,
            LaunchModeBuildItem launchModeBuildItem) {

        if (!launchModeBuildItem.getLaunchMode().isDevOrTest()) {
            removeResource.produce(new RemovedResourceBuildItem(
                    ArtifactKey.fromString("io.quarkiverse.flow:quarkus-flow"),
                    Collections.singleton("META-INF/grafana/grafana-dashboard-quarkus-flow.json")));
        }

        if (!metricsConfig.enabled()) {
            return;
        }

        metricsCapability.map(capability -> capability.metricsSupported(MetricsFactory.MICROMETER))
                .ifPresent(micrometerIsSupported -> {
                    if (micrometerIsSupported) {
                        beans.produce(SyntheticBeanBuildItem.configure(MicrometerExecutionListener.class)
                                .setRuntimeInit()
                                .unremovable()
                                .scope(Singleton.class)
                                .types(WorkflowExecutionListener.class)
                                .supplier(recorder.supplyMicrometerExecutionListener(metricsConfig))
                                .done());
                    }
                });
    }

}
