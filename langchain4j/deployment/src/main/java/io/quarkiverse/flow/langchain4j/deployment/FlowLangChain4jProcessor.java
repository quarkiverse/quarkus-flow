package io.quarkiverse.flow.langchain4j.deployment;

import static io.quarkiverse.flow.deployment.FlowLoggingUtils.logWorkflowList;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import io.quarkiverse.flow.langchain4j.recorders.AgenticWorkflowDescriptor;
import io.quarkiverse.flow.langchain4j.recorders.FlowLangChain4jWorkflowRecorder;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowLangChain4jProcessor {

    private static final String FEATURE = "flow-langchain4j";

    private static final Logger LOG = LoggerFactory.getLogger(FlowLangChain4jProcessor.class.getName());

    private static final DotName SEQUENCE_AGENT = DotName.createSimple(SequenceAgent.class.getName());
    private static final DotName CONDITIONAL_AGENT = DotName.createSimple(ConditionalAgent.class.getName());
    private static final DotName LOOP_AGENT = DotName.createSimple(LoopAgent.class.getName());
    private static final DotName PARALLEL_AGENT = DotName.createSimple(ParallelAgent.class.getName());

    private static boolean isWorkflowPatternMethod(MethodInfo method) {
        return method.hasAnnotation(SEQUENCE_AGENT)
                || method.hasAnnotation(CONDITIONAL_AGENT)
                || method.hasAnnotation(LOOP_AGENT)
                || method.hasAnnotation(PARALLEL_AGENT);
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIfNot = IsProduction.class)
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
                if (!isWorkflowPatternMethod(method)) {
                    continue;
                }

                List<String> paramTypeNames = method.parameterTypes()
                        .stream()
                        .map(Type::name)
                        .map(Object::toString) // DotName -> FQCN
                        .collect(toList());

                agenticWorkflows.add(new FlowAgenticWorkflowBuildItem(
                        iface.name().toString(),
                        method.name(),
                        paramTypeNames));
            }
        }

        producer.produce(agenticWorkflows);
    }

    @BuildStep(onlyIfNot = IsProduction.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerAgenticWorkflowsAtRuntime(FlowLangChain4jWorkflowRecorder recorder,
            List<FlowAgenticWorkflowBuildItem> agenticWorkflows) {

        if (agenticWorkflows == null || agenticWorkflows.isEmpty()) {
            return;
        }
        List<String> lc4jWorkflows = agenticWorkflows.stream().map(FlowAgenticWorkflowBuildItem::ifaceName).toList();
        logWorkflowList(LOG,
                lc4jWorkflows,
                "Flow: No LangChain4j workflows were registered.",
                "Flow: Registered LangChain4j Workflows",
                "LangChain4j Agentic Workflows");

        // Convert deployment build items to runtime DTOs
        List<AgenticWorkflowDescriptor> descriptors = agenticWorkflows.stream()
                .map(bi -> new AgenticWorkflowDescriptor(
                        bi.ifaceName(),
                        bi.methodName(),
                        bi.parameterTypeNames()))
                .collect(toList());

        recorder.registerAgenticWorkflows(descriptors);
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
