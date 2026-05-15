package io.quarkiverse.flow.deployment;

import static io.quarkiverse.flow.deployment.FlowLoggingUtils.logWorkflowList;
import static io.quarkiverse.flow.deployment.WorkflowNamingConverter.generateFlowClassIdentifier;
import static io.quarkiverse.flow.deployment.WorkflowNamingConverter.namespaceToPackage;
import static io.quarkus.arc.processor.DotNames.SINGLETON;

import java.util.List;
import java.util.Map;
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

import com.github.zafarkhaja.semver.ParseException;
import com.github.zafarkhaja.semver.Version;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.FlowTracingConfig;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
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
            produceVersionedWorkflowDefinitionBean(recorder, beans, identifiers, d);
        }

        if (this.flowDefinitionsConfig.namingStrategy() == FlowDefinitionsConfig.NamingStrategy.SPEC) {
            selectLatestVersionPerWorkflow(fromSpec)
                    .forEach((versionlessId, representative) -> {
                        String displayLabel = versionlessId + "  →  " + representative.specIdentifier() + " (latest)";
                        beans.produce(produceVersionlessSyntheticBean(versionlessId, recorder, representative));
                        identifiers.produce(new FlowIdentifierBuildItem(
                                Set.of(versionlessId),
                                Map.of(versionlessId, displayLabel)));
                    });
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

    private void produceVersionedWorkflowDefinitionBean(WorkflowDefinitionRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beans, BuildProducer<FlowIdentifierBuildItem> identifiers,
            DiscoveredWorkflowBuildItem workflow) {

        String flowSubclassIdentifier = this.flowDefinitionsConfig.namespace().prefix()
                .map(fromConfig -> generateFlowClassIdentifier(workflow.workflowDefinitionId(), namespaceToPackage(fromConfig)))
                .orElse(generateFlowClassIdentifier(workflow.workflowDefinitionId(),
                        namespaceToPackage(workflow.workflowDefinitionId().namespace())));

        String identifier = this.flowDefinitionsConfig.namingStrategy() == FlowDefinitionsConfig.NamingStrategy.SPEC
                ? workflow.specIdentifier()
                : flowSubclassIdentifier;

        beans.produce(produceSyntheticWorkflowDefinitionBean(identifier, recorder, workflow));
        identifiers.produce(new FlowIdentifierBuildItem(Set.of(identifier)));
    }

    /**
     * Groups workflows by their versionless identifier (namespace:name) and selects
     * the workflow with the highest semantic version for each group.
     *
     * @param workflows List of discovered workflows from spec files
     * @return Map of versionless identifiers to their highest-version representative workflow
     */
    private static Map<String, DiscoveredWorkflowBuildItem> selectLatestVersionPerWorkflow(
            List<DiscoveredWorkflowBuildItem> workflows) {
        return workflows.stream()
                .collect(Collectors.toMap(
                        d -> WorkflowNameUtils.versionlessIdentifier(d.namespace(), d.name()),
                        d -> d,
                        (a, b) -> {
                            return tryParseSemver(a.version(), a).compareTo(tryParseSemver(b.version(), b)) >= 0 ? a : b;
                        }));
    }

    public static Version tryParseSemver(String semver, DiscoveredWorkflowBuildItem discoveredWorkflow) {
        try {
            return Version.parse(semver);
        } catch (IllegalArgumentException | ParseException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid semantic version '%s' in workflow '%s:%s' (file: %s). " +
                            "Expected format: MAJOR.MINOR.PATCH (e.g., '1.0.0')",
                            discoveredWorkflow.version(), discoveredWorkflow.namespace(), discoveredWorkflow.name(),
                            discoveredWorkflow.absolutePath()),
                    e);
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void produceGeneratedFlows(List<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<GeneratedBeanBuildItem> classes,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            WorkflowDefinitionRecorder recorder) {

        List<DiscoveredWorkflowBuildItem> fromSpec = workflows.stream().filter(DiscoveredWorkflowBuildItem::fromSpec)
                .toList();

        GeneratedBeanGizmoAdaptor gizmo = new GeneratedBeanGizmoAdaptor(classes);

        for (DiscoveredWorkflowBuildItem workflow : fromSpec) {
            produceVersionedFlowGizmoBean(workflow, gizmo);
        }

        // 2. ONE versionless Flow subclass per unique namespace:name.
        if (flowDefinitionsConfig.namingStrategy() == FlowDefinitionsConfig.NamingStrategy.SPEC) {
            selectLatestVersionPerWorkflow(fromSpec)
                    .forEach((versionlessId, representative) -> produceVersionlessFlowGizmoBean(versionlessId, representative,
                            gizmo));
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
        List<String> allDisplayLabels = registeredIdentifiers.stream()
                .flatMap(item -> item.displayIdentifiers().values().stream())
                .distinct()
                .collect(Collectors.toList());
        logWorkflowList(LOG,
                allDisplayLabels,
                "Flow: No WorkflowDefinition beans were registered.",
                "Flow: Registered WorkflowDefinition beans",
                "Workflow identifier");
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
     * Produces a versionless {@code @Identifier("namespace:name")} synthetic bean that delegates
     * to the highest-semver versioned {@link WorkflowDefinition} bean.
     * <p>
     * An explicit injection point on the versioned bean is declared so CDI creates — and
     * registers into {@code WorkflowApplication} — the versioned bean before this one,
     * preventing any ordering / lookup-before-registration issues at startup.
     */
    private static SyntheticBeanBuildItem produceVersionlessSyntheticBean(String versionlessIdentifier,
            WorkflowDefinitionRecorder recorder, DiscoveredWorkflowBuildItem representative) {
        String versionedIdentifier = representative.specIdentifier();
        return SyntheticBeanBuildItem.configure(WorkflowDefinition.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .setRuntimeInit()
                .addQualifier().annotation(DotNames.IDENTIFIER)
                .addValue("value", versionlessIdentifier).done()
                .addInjectionPoint(
                        ClassType.create(DotName.createSimple(WorkflowDefinition.class)),
                        AnnotationInstance.builder(DotNames.IDENTIFIER).value(versionedIdentifier).build())
                .createWith(recorder.workflowDefinitionVersionlessDelegateCreator(versionedIdentifier))
                .done();
    }

    private void produceVersionedFlowGizmoBean(DiscoveredWorkflowBuildItem workflow,
            GeneratedBeanGizmoAdaptor gizmo) {

        String flowSubclassIdentifier = this.flowDefinitionsConfig.namespace().prefix()
                .map(fromConfig -> generateFlowClassIdentifier(workflow.workflowDefinitionId(), namespaceToPackage(fromConfig)))
                .orElse(generateFlowClassIdentifier(workflow.workflowDefinitionId(),
                        namespaceToPackage(workflow.workflowDefinitionId().namespace())));

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

            FieldCreator fieldCreator = GizmoFlowHelper.addWorkflowDefinitionField(creator, identifier);
            GizmoFlowHelper.addIdentifierMethod(creator, identifier);
            GizmoFlowHelper.addDescriptorMethod(creator, fieldCreator);
        }
    }

    /**
     * Generates a single versionless {@code Flow} subclass qualified with
     * {@code @Identifier("namespace:name")}. Only called once per unique namespace:name pair,
     * so no duplicate class/bean is produced when multiple versions of the same workflow exist.
     */
    private void produceVersionlessFlowGizmoBean(String versionlessId,
            DiscoveredWorkflowBuildItem representative,
            GeneratedBeanGizmoAdaptor gizmo) {

        // Use versionlessId (namespace:name) to generate class name, not the representative's version
        // This ensures the class name remains stable regardless of which version is "latest"
        String namespace = representative.namespace();
        String name = representative.name();

        String versionlessClassName = this.flowDefinitionsConfig.namespace().prefix()
                .map(prefix -> generateFlowClassIdentifier(namespace, name, prefix))
                .orElse(generateFlowClassIdentifier(namespace, name));

        try (ClassCreator creator = ClassCreator.builder()
                .className(versionlessClassName)
                .superClass(DotNames.FLOW.toString())
                .classOutput(gizmo)
                .build()) {

            creator.addAnnotation(Unremovable.class);
            creator.addAnnotation(ApplicationScoped.class);
            creator.addAnnotation(Identifier.class).add("value", versionlessId);

            FieldCreator fieldCreator = GizmoFlowHelper.addWorkflowDefinitionField(creator, versionlessId);
            GizmoFlowHelper.addIdentifierMethod(creator, versionlessId);
            GizmoFlowHelper.addDescriptorMethod(creator, fieldCreator);
        }
    }
}
