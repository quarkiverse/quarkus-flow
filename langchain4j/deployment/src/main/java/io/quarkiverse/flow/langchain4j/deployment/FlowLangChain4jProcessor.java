package io.quarkiverse.flow.langchain4j.deployment;

import static io.quarkiverse.flow.langchain4j.deployment.AgenticTopologyMapper.getFlowClass;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.computeTaskNames;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateAgentClassNameMethod;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateAgentDescriptionMethod;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateClassName;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateConditionalMetadataField;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateInputSchemaMethod;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateInvokerMethodNameMethod;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateInvokerMethodParamsMethod;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateLoopMetadataFields;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateSubAgentTaskNamesMethod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.deployment.DiscoveredWorkflowBuildItem;
import io.quarkiverse.flow.langchain4j.workflow.flow.AgenticFlow;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentBuildItem;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.smallrye.common.annotation.Identifier;

/**
 * Build processor for LangChain4j agentic workflow integration.
 * <p>
 * Generates {@link AgenticFlow} implementations at build-time from LangChain4j agent annotations.
 */
public class FlowLangChain4jProcessor {

    private static final String FEATURE = "flow-langchain4j";

    private static final Logger LOG = LoggerFactory.getLogger(FlowLangChain4jProcessor.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void collectAgenticWorkflows(List<DetectedAiAgentBuildItem> detectedAgents,
            BuildProducer<FlowAgenticWorkflowBuildItem> producer) {

        final Set<FlowAgenticWorkflowBuildItem> agenticWorkflows = new LinkedHashSet<>();

        for (DetectedAiAgentBuildItem item : detectedAgents) {
            ClassInfo iface = item.getIface();
            List<MethodInfo> methods = item.getAgenticMethods();

            if (methods == null || methods.isEmpty()) {
                continue;
            }

            for (MethodInfo method : methods) {
                final AgenticWorkflowBlueprint blueprint = AgenticWorkflowBlueprint.fromAgenticMethod(method);
                if (blueprint == null)
                    continue;

                agenticWorkflows.add(new FlowAgenticWorkflowBuildItem(
                        iface.name().toString(),
                        method,
                        blueprint));
            }
        }

        producer.produce(agenticWorkflows);
    }

    @BuildStep
    void generateAgenticFlowClasses(
            CombinedIndexBuildItem combinedIndex,
            LaunchModeBuildItem launchMode,
            List<FlowAgenticWorkflowBuildItem> agenticWorkflows,
            BuildProducer<GeneratedBeanBuildItem> generatedClasses,
            BuildProducer<DiscoveredWorkflowBuildItem> discoveredWorkflows) {

        this.checkForGeneratedFlowClassesCollisions(agenticWorkflows);

        boolean isDevMode = launchMode.getLaunchMode().isDevOrTest();

        for (FlowAgenticWorkflowBuildItem workflow : agenticWorkflows) {
            final String generatedClassName = generateClassName(workflow.ifaceName());

            // Get the appropriate AgenticFlow implementation class for this topology
            Class<? extends AgenticFlow> flowInterface = getFlowClass(workflow.topology());

            if (workflow.subAgents().isEmpty()) {
                LOG.warn("Flow: Agent interface {} has no sub-agents. Generated workflow will be empty.",
                        workflow.ifaceName());
            }

            final ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedClasses);
            try (ClassCreator classCreator = ClassCreator.builder()
                    .classOutput(classOutput)
                    .className(generatedClassName)
                    .superClass(flowInterface.getName())
                    .build()) {
                classCreator.addAnnotation(Identifier.class).add("value", generatedClassName);
                classCreator.addAnnotation(Unremovable.class);
                classCreator.addAnnotation(ApplicationScoped.class);

                // Generate: String agentClassName() { return "..."; }
                generateAgentClassNameMethod(classCreator, workflow.ifaceName());

                // Generate: List<String> subAgentTaskNames() { return List.of("methodName1", "methodName2"...); }
                generateSubAgentTaskNamesMethod(classCreator, computeTaskNames(combinedIndex.getIndex(), workflow.subAgents()));

                // Generate: String description() { return "..."; }
                generateAgentDescriptionMethod(classCreator, workflow.description(), workflow.ifaceName());

                // Generate: String getInputSchemaJson() { return "...json..."; }
                generateInputSchemaMethod(classCreator, combinedIndex.getIndex(), workflow.method());

                // Generate DevUI invoker metadata methods (dev mode only)
                if (isDevMode) {
                    // Generate: String invokerMethodName() { return "methodName"; }
                    generateInvokerMethodNameMethod(classCreator, workflow.method().name());

                    // Generate: String[] invokerMethodParams() { return new String[] {...}; }
                    generateInvokerMethodParamsMethod(classCreator, workflow.method());
                }

                // Generate topology-specific metadata fields (if applicable)
                if (workflow.conditionalMetadata().isPresent()) {
                    generateConditionalMetadataField(classCreator, workflow.ifaceName(),
                            workflow.conditionalMetadata().get(), workflow.subAgents());
                }
                if (workflow.loopMetadata().isPresent()) {
                    generateLoopMetadataFields(classCreator, workflow.ifaceName(), workflow.loopMetadata().get());
                }
            }

            // Notify core module
            discoveredWorkflows.produce(DiscoveredWorkflowBuildItem.fromSource(generatedClassName));
        }

    }

    private void checkForGeneratedFlowClassesCollisions(List<FlowAgenticWorkflowBuildItem> agenticWorkflows) {
        Set<String> generatedClassNames = new LinkedHashSet<>();
        for (FlowAgenticWorkflowBuildItem workflow : agenticWorkflows) {
            final String generatedClassName = generateClassName(workflow.ifaceName());
            if (!generatedClassNames.add(generatedClassName)) {
                throw new IllegalStateException(
                        "Duplicate generated class name detected: " + generatedClassName +
                                " for agent interface: " + workflow.ifaceName() +
                                ". This should not happen - please report as a bug.");
            }
        }

    }

    /**
     * Mark all interfaces that have at least one FlowAgenticWorkflowBuildItem as unremovable.
     * We use our own build items here, which already reflect the filtered workflow methods.
     */
    @BuildStep(onlyIfNot = IsProduction.class)
    UnremovableBeanBuildItem markAgenticBeansUnremovable(List<FlowAgenticWorkflowBuildItem> agenticWorkflows) {
        if (agenticWorkflows == null || agenticWorkflows.isEmpty()) {
            return null;
        }

        Set<DotName> ifaceTypes = agenticWorkflows.stream()
                .map(FlowAgenticWorkflowBuildItem::ifaceName)
                .map(DotName::createSimple)
                .collect(Collectors.toSet());

        if (ifaceTypes.isEmpty()) {
            return null;
        }

        // Any bean that has one of these interfaces as a bean type is considered unremovable
        return UnremovableBeanBuildItem.beanTypes(ifaceTypes);
    }

}
