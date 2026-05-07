package io.quarkiverse.flow.deployment;

import static io.quarkiverse.flow.deployment.FlowLoggingUtils.logWorkflowList;
import static io.quarkus.arc.processor.DotNames.SINGLETON;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.ws.rs.Priorities;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.FlowTracingConfig;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.FaultToleranceProvider;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.MicroprofileConfigManager;
import io.quarkiverse.flow.providers.WorkflowExceptionMapper;
import io.quarkiverse.flow.recorders.SDKRecorder;
import io.quarkiverse.flow.recorders.WorkflowApplicationCreator;
import io.quarkiverse.flow.recorders.WorkflowApplicationRecorder;
import io.quarkiverse.flow.recorders.WorkflowDefinitionRecorder;
import io.quarkiverse.flow.structuredlogging.EventFormatter;
import io.quarkiverse.flow.structuredlogging.StructuredLoggingListener;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
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
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.smallrye.common.annotation.Identifier;

class FlowProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowProcessor.class); // NEW

    private static final String FEATURE = "flow";
    private static final String DEFAULT_STRUCTURED_LOG_HANDLER = "FLOW_EVENTS";

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
                .addBeanClass(FaultToleranceProvider.class)
                .addBeanClass(WorkflowRegistry.class)
                .addBeanClass(WorkflowApplicationCreator.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    UnremovableBeanBuildItem keepCustomEventAdapters() {
        return UnremovableBeanBuildItem.beanTypes(Set.of(
                DotName.createSimple(EventPublisher.class.getName()),
                DotName.createSimple(EventConsumer.class.getName())));
    }

    @BuildStep
    UnremovableBeanBuildItem keepCustomExecutionListeners() {
        return UnremovableBeanBuildItem.beanTypes(WorkflowExecutionListener.class, WorkflowExecutionCompletableListener.class);
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
            produceWorkflowDefinitionBeanFromSpec(recorder, beans, identifiers, d);
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
                .addInjectionPoint(ClassType.create(DotName.createSimple(it.className())),
                        AnnotationInstance.builder(DotName.createSimple(Any.class)).build())
                .addInjectionPoint(ClassType.create(DotName.createSimple(WorkflowRegistry.class)))
                .createWith(recorder.workflowDefinitionCreator(it.className()))
                .done());
        identifiers.produce(new FlowIdentifierBuildItem(Set.of(it.className())));
    }

    private void produceWorkflowDefinitionBeanFromSpec(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans, BuildProducer<FlowIdentifierBuildItem> identifiers,
            DiscoveredWorkflowBuildItem workflow) {
        String flowSubclassIdentifier = WorkflowNamingConverter.generateFlowClassIdentifier(
                workflow.namespace(), workflow.name(), this.flowDefinitionsConfig.namespace().prefix());

        String identifier = this.flowDefinitionsConfig.namingStrategy() == FlowDefinitionsConfig.NamingStrategy.SPEC
                ? workflow.specIdentifier()
                : flowSubclassIdentifier;

        beans.produce(produceSyntheticWorkflowDefinitionBean(identifier, recorder, workflow));

        identifiers.produce(new FlowIdentifierBuildItem(
                Set.of(identifier)));
    }

    @BuildStep
    void produceGeneratedFlows(List<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<GeneratedBeanBuildItem> classes) {

        List<DiscoveredWorkflowBuildItem> fromSpec = workflows.stream().filter(DiscoveredWorkflowBuildItem::fromSpec)
                .toList();

        GeneratedBeanGizmoAdaptor gizmo = new GeneratedBeanGizmoAdaptor(classes);
        for (DiscoveredWorkflowBuildItem workflow : fromSpec) {
            produceFlowGizmoBean(workflow, gizmo);
        }

    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void registerWorkflowApp(WorkflowApplicationRecorder recorder,
            ShutdownContextBuildItem shutdown,
            FlowTracingConfig cfg,
            LaunchModeBuildItem launchMode,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<SyntheticBeanBuildItem> beans) {

        boolean isMicrometerSupported = metricsCapability
                .map(capability -> capability.metricsSupported(MetricsFactory.MICROMETER)).orElse(false);
        boolean isTracingEnabled = cfg.enabled().orElse(launchMode.getLaunchMode().isDevOrTest());

        beans.produce(SyntheticBeanBuildItem.configure(WorkflowApplication.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .addInjectionPoint(ClassType.create(DotName.createSimple(WorkflowApplicationCreator.class)))
                .createWith(recorder.workflowAppCreator(shutdown, isTracingEnabled, isMicrometerSupported))
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
    void overrideObjectMapper(SDKRecorder recorder, BeanContainerBuildItem beanContainer) {
        recorder.injectQuarkusObjectMapper(beanContainer.getValue());
    }

    @BuildStep(onlyIf = { IsDevelopment.class })
    public void watchChanges(List<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles) {

        List<String> locations = workflows.stream().filter(DiscoveredWorkflowBuildItem::fromSpec)
                .map(DiscoveredWorkflowBuildItem::absolutePath)
                .toList();

        for (String location : locations) {
            watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                    .setLocation(location)
                    .setRestartNeeded(true)
                    .build());
        }
    }

    @BuildStep
    void configureRegistryPrometheusIntegration(FlowMetricsConfig metricsConfig,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        boolean micrometerExecutionListenerNeeded = metricsConfig.enabled()
                && metricsCapability.map(capability -> capability.metricsSupported(MetricsFactory.MICROMETER)).orElse(false);
        if (micrometerExecutionListenerNeeded) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(MicrometerExecutionListener.class).setUnremovable()
                    .setDefaultScope(SINGLETON)
                    .build());
        }
    }

    @BuildStep
    void configureStructuredLogging(FlowStructuredLoggingConfig structuredLoggingConfig,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        if (!structuredLoggingConfig.enabled()) {
            return;
        }

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(StructuredLoggingListener.class).setUnremovable()
                .setDefaultScope(SINGLETON)
                .build());
    }

    @BuildStep
    void detectQuarkusLoggingJson(FlowStructuredLoggingConfig structuredLoggingConfig,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> configDefaults,
            LaunchModeBuildItem launchMode) {

        if (!structuredLoggingConfig.enabled())
            return;

        // Provide sensible defaults for the structured logging file handler
        // Users can override these in application.properties if needed
        String loggerCategory = EventFormatter.class.getPackageName();

        switch (structuredLoggingConfig.handler().mode()) {
            case NONE -> LOG.info("Structured Logging enabled, but automatic logging handler disabled. " +
                    "You have to configure the output via quarkus.log.* configuration for the category {}", loggerCategory);
            case FILE -> createStructuredLoggingFileHandler(configDefaults, launchMode, loggerCategory);
            case CONTAINER -> createStructuredLoggingStdoutHandler(configDefaults, loggerCategory);
        }
    }

    private void createStructuredLoggingFileHandler(BuildProducer<RunTimeConfigurationDefaultBuildItem> configDefaults,
            LaunchModeBuildItem launchMode,
            String loggerCategory) {
        // Enable file handler by default when structured logging is enabled
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.handler.file.\"" + DEFAULT_STRUCTURED_LOG_HANDLER + "\".enable",
                "true"));

        // Set raw JSON format (no timestamps, just the event JSON)
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.handler.file.\"" + DEFAULT_STRUCTURED_LOG_HANDLER + "\".format",
                "%s%n"));

        // Set default path based on launch mode
        String defaultPath = launchMode.getLaunchMode().isDevOrTest()
                ? "target/quarkus-flow-events.log" // Dev/test: use target directory
                : "/var/log/quarkus-flow/events.log"; // Prod: use standard location

        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.handler.file.\"" + DEFAULT_STRUCTURED_LOG_HANDLER + "\".path",
                defaultPath));

        // Assign this handler to the structured logging category
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.category.\"" + loggerCategory + "\".handlers",
                DEFAULT_STRUCTURED_LOG_HANDLER));

        // Prevent workflow events from appearing in console (to avoid double logging)
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.category.\"" + loggerCategory + "\".use-parent-handlers",
                "false"));

        LOG.info("Quarkus Flow structured logging file handler auto-configured. " +
                "Events will be written to: {} (override with quarkus.log.handler.file.{}.path)", defaultPath,
                DEFAULT_STRUCTURED_LOG_HANDLER);
    }

    private void createStructuredLoggingStdoutHandler(BuildProducer<RunTimeConfigurationDefaultBuildItem> configDefaults,
            String loggerCategory) {
        // Enable console handler
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.handler.console.\"" + DEFAULT_STRUCTURED_LOG_HANDLER + "\".enable",
                "true"));
        // Set raw JSON format (no timestamps, just the event JSON)
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.handler.console.\"" + DEFAULT_STRUCTURED_LOG_HANDLER + "\".format",
                "%s%n"));
        // Assign this handler to the structured logging category
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.category.\"" + loggerCategory + "\".handlers",
                DEFAULT_STRUCTURED_LOG_HANDLER));
        // Prevent workflow events from appearing in parent console handler
        configDefaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.log.category.\"" + loggerCategory + "\".use-parent-handlers",
                "false"));

        LOG.info("Quarkus Flow structured logging console handler auto-configured. " +
                "Events will be written to stdout (containers mode)");
    }

    private static SyntheticBeanBuildItem produceSyntheticWorkflowDefinitionBean(String identifier,
            WorkflowDefinitionRecorder recorder, DiscoveredWorkflowBuildItem workflow) {
        return SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .addQualifier().annotation(DotNames.IDENTIFIER)
                .addValue("value", identifier).done()
                .addInjectionPoint(ClassType.create(DotName.createSimple(WorkflowRegistry.class)))
                .createWith(recorder.workflowDefinitionFromFileCreator(
                        workflow.name(), workflow.content(), WorkflowFormat.fromFileName(workflow.name())))
                .done();
    }

    /**
     * Generates a CDI-managed subclass of {@code Flow} using Gizmo.
     *
     * <p>
     * The generated class has this effective structure:
     *
     * <pre>
     * {@code
     * &#64;Unremovable
     * &#64;ApplicationScoped
     * &#64;Identifier(identifier)
     * class {className} extends Flow {
     *
     *     &#64;Inject
     *     &#64;Identifier(identifier)
     *     public WorkflowDefinition workflowDefinition;
     *
     *     public Workflow descriptor() {
     *         return this.workflowDefinition.workflow();
     *     }
     * }
     * }
     * </pre>
     * <p>
     * When {@code specIdentifier} is {@code false}, the CDI bean identifier is prefixed with
     * {@code Normal}, but the injected {@code workflowDefinition} field still uses {@code className}
     * as its {@code @Identifier} value.
     */
    private void produceFlowGizmoBean(DiscoveredWorkflowBuildItem workflow,
            GeneratedBeanGizmoAdaptor gizmo) {

        String flowSubclassIdentifier = WorkflowNamingConverter.generateFlowClassIdentifier(
                workflow.namespace(), workflow.name(), this.flowDefinitionsConfig.namespace().prefix());

        String identifier = flowDefinitionsConfig.namingStrategy() == FlowDefinitionsConfig.NamingStrategy.SPEC
                ? workflow.specIdentifier()
                : flowSubclassIdentifier;

        try (ClassCreator creator = ClassCreator.builder()
                .className(flowSubclassIdentifier)
                .superClass(DotNames.FLOW.toString())
                .classOutput(gizmo)
                .build()) {

            creator.addAnnotation(Unremovable.class);
            creator.addAnnotation(ApplicationScoped.class);
            creator.addAnnotation(Identifier.class).add("value", identifier);

            // @Inject @Identifier(identifier) public WorkflowDefinition workflowDefinition;
            FieldCreator fieldCreator = GizmoFlowHelper.addWorkflowDefinitionField(creator, identifier);

            // public String identifier() { return identifier; }
            GizmoFlowHelper.addIdentifierMethod(creator, identifier);

            // public Workflow descriptor() method
            GizmoFlowHelper.addDescriptorMethod(creator, fieldCreator);
        }
    }
}
