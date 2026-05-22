package io.quarkiverse.flow.langchain4j.deployment;

import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotations.ALL_AGENT_ANNOTATIONS;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.flow.langchain4j.workflow.ConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.LoopAgenticFlow;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

/**
 * Helper methods for Gizmo bytecode generation of {@link io.quarkiverse.flow.langchain4j.workflow.AgenticFlow} classes.
 * <p>
 * This class generates Flow implementations at build-time from LangChain4j agent annotations.
 */
final class GizmoAgentFlowsHelper {

    private GizmoAgentFlowsHelper() {
        // Prevent instantiation
    }

    static String generateClassName(String agentInterfaceFqcn) {
        // Handle nested classes: "com.example.Outer$Inner" → "com.example.GeneratedOuter$InnerFlow"
        // Handle regular classes: "com.example.Agent" → "com.example.GeneratedAgentFlow"

        int lastDot = agentInterfaceFqcn.lastIndexOf('.');
        if (lastDot == -1) {
            // Default package
            return "Generated" + agentInterfaceFqcn + "Flow";
        }

        String packageName = agentInterfaceFqcn.substring(0, lastDot);
        String classNamePart = agentInterfaceFqcn.substring(lastDot + 1);

        // For nested classes (Outer$Inner), keep the $ separator
        return packageName + ".Generated" + classNamePart + "Flow";
    }

    /**
     * Computes task names from subAgent types for workflow visualization.
     * <p>
     * Returns method names only (without indices). These will be used as base names
     * for workflow tasks like "methodName-0", "methodName-1" for visual representation.
     *
     * @param index the Jandex index for scanning classes
     * @param subAgents the list of sub-agent types
     * @return list of method names (task name bases)
     * @throws IllegalStateException if agent method cannot be found or is invalid
     */
    static List<String> computeTaskNames(IndexView index, List<Type> subAgents) {
        if (subAgents.isEmpty()) {
            return List.of();
        }

        List<String> taskNames = new ArrayList<>(subAgents.size());

        for (Type subAgentType : subAgents) {
            String methodName = findAgentMethodName(index, subAgentType);
            if (methodName.isBlank()) {
                throw new IllegalStateException(
                        "Agent method name is empty for subAgent: " + subAgentType.name() +
                                ". This indicates a bug in the agent method discovery logic.");
            }
            taskNames.add(methodName);
        }

        return taskNames;
    }

    /**
     * Finds the agent method name by scanning for LangChain4j agent annotations.
     *
     * @param index the Jandex index
     * @param subAgentType the sub-agent class to scan
     * @return the method name of the agent method
     * @throws IllegalStateException if class is not indexed or no agent method found
     */
    private static String findAgentMethodName(IndexView index, Type subAgentType) {
        ClassInfo classInfo = index.getClassByName(subAgentType.name());
        if (classInfo == null) {
            throw new IllegalStateException(
                    "Class not found in Jandex index: " + subAgentType.name() +
                            ". Ensure the class is part of the application or a dependency with a Jandex index.");
        }

        // Try each agent annotation type in order
        for (DotName annotation : ALL_AGENT_ANNOTATIONS) {
            for (MethodInfo method : classInfo.methods()) {
                if (method.hasAnnotation(annotation)) {
                    return method.name();
                }
            }
        }

        throw new IllegalStateException(
                "No agent method found in " + subAgentType.name() +
                        ". Expected a method annotated with one of: @Agent, @SequenceAgent, @ParallelAgent, @LoopAgent, or @ConditionalAgent. "
                        +
                        "Verify the class has the correct LangChain4j annotations.");
    }

    static void generateAgentClassNameMethod(ClassCreator classCreator, String agentInterfaceFqcn) {
        try (MethodCreator mc = classCreator.getMethodCreator("agentClassName", String.class)) {
            mc.returnValue(mc.load(agentInterfaceFqcn));
        }
    }

    static void generateAgentDescriptionMethod(ClassCreator classCreator, String description, String agentInterfaceFqcn) {
        try (MethodCreator mc = classCreator.getMethodCreator("description", String.class)) {
            if (description == null || description.isBlank()) {
                description = "LC4J agent workflow for " + agentInterfaceFqcn + ".";
            }
            mc.returnValue(mc.load(description));
        }
    }

    static void generateSubAgentTaskNamesMethod(ClassCreator classCreator, List<String> taskNames) {
        try (MethodCreator mc = classCreator.getMethodCreator("subAgentTaskNames", List.class)
                .setModifiers(Modifier.PROTECTED)) {
            ResultHandle array = mc.newArray(String.class, taskNames.size());
            for (int i = 0; i < taskNames.size(); i++) {
                mc.writeArrayValue(array, i, mc.load(taskNames.get(i)));
            }

            ResultHandle list = mc.invokeStaticInterfaceMethod(
                    MethodDescriptor.ofMethod(List.class, "of", List.class, Object[].class),
                    array);
            mc.returnValue(list);
        }
    }

    static void generateConditionalMetadataField(ClassCreator classCreator, String agentInterfaceFqcn,
            ConditionalMetadata conditionalMetadata, List<Type> subAgents) {
        try (MethodCreator mc = classCreator.getMethodCreator("activationPredicates", Map.class)
                .setModifiers(Modifier.PROTECTED)) {
            Map<String, PredicateMetadata> conditions = conditionalMetadata.activationConditions();

            // Build map from agent class name to subagent index
            Map<String, Integer> classNameToIndex = new HashMap<>();
            for (int i = 0; i < subAgents.size(); i++) {
                classNameToIndex.put(subAgents.get(i).name().toString(), i);
            }

            // Create HashMap and populate with put() calls
            ResultHandle map = mc.newInstance(MethodDescriptor.ofConstructor(HashMap.class));

            for (Map.Entry<String, PredicateMetadata> entry : conditions.entrySet()) {
                String agentClassName = entry.getKey();
                PredicateMetadata predMeta = entry.getValue();

                // Key: subagent index (Integer)
                Integer index = classNameToIndex.get(agentClassName);
                if (index == null) {
                    throw new IllegalStateException(
                            "No subagent index found for activation condition on class: " + agentClassName +
                                    ". Available subagents: " + classNameToIndex.keySet());
                }
                ResultHandle key = mc.load(index);

                // Value: buildActivationPredicate(agentClass, methodName, paramTypes)
                ResultHandle agentClass = mc.loadClass(agentInterfaceFqcn);
                ResultHandle methodName = mc.load(predMeta.methodName());
                ResultHandle paramTypes = buildStringList(mc, predMeta.parameterTypeNames());

                ResultHandle predicate = mc.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(
                                ConditionalAgenticFlow.class,
                                "buildActivationPredicate",
                                Predicate.class,
                                Class.class, String.class, List.class),
                        mc.getThis(),
                        agentClass, methodName, paramTypes);

                // map.put(index, predicate)
                mc.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class, Object.class),
                        map, key, predicate);
            }

            mc.returnValue(map);
        }
    }

    private static ResultHandle buildStringList(MethodCreator mc, List<String> strings) {
        // Create array at runtime and populate it
        ResultHandle array = mc.newArray(String.class, strings.size());
        for (int i = 0; i < strings.size(); i++) {
            mc.writeArrayValue(array, i, mc.load(strings.get(i)));
        }

        // Pass the single array ResultHandle to List.of(Object[])
        return mc.invokeStaticInterfaceMethod(
                MethodDescriptor.ofMethod(List.class, "of", List.class, Object[].class),
                array);
    }

    static void generateLoopMetadataFields(ClassCreator classCreator, String agentInterfaceFqcn, LoopMetadata loopMetadata) {
        // Generate: int maxIterations() { return 5; }
        try (MethodCreator mc = classCreator.getMethodCreator("maxIterations", int.class)) {
            mc.returnValue(mc.load(loopMetadata.maxIterations()));
        }

        // Generate: boolean testExitAtLoopEnd() { return true; }
        try (MethodCreator mc = classCreator.getMethodCreator("testExitAtLoopEnd", boolean.class)) {
            mc.returnValue(mc.load(loopMetadata.testExitAtLoopEnd()));
        }

        // Generate: Function<AgenticScope, Integer, Boolean> exitCondition()
        try (MethodCreator mc = classCreator.getMethodCreator("exitCondition", BiPredicate.class)) {

            if (loopMetadata.exitCondition().isPresent()) {
                PredicateMetadata exitCond = loopMetadata.exitCondition().get();

                // Call: buildLoopExitPredicate(agentClass, methodName, paramTypes)
                // Returns: BiPredicate<AgenticScope, Integer>
                ResultHandle agentClass = mc.loadClass(agentInterfaceFqcn);
                ResultHandle methodName = mc.load(exitCond.methodName());
                ResultHandle paramTypes = buildStringList(mc, exitCond.parameterTypeNames());

                ResultHandle predicate = mc.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(
                                LoopAgenticFlow.class,
                                "buildLoopExitPredicate",
                                BiPredicate.class,
                                Class.class, String.class, List.class),
                        mc.getThis(),
                        agentClass, methodName, paramTypes);

                mc.returnValue(predicate);
            } else {
                // No exit condition - return null
                mc.returnValue(mc.loadNull());
            }
        }

    }

    /**
     * Generates the {@code getInputSchemaJson()} method that returns the build-time generated JSON schema string.
     * <p>
     * Parsing is handled by the base {@link io.quarkiverse.flow.langchain4j.workflow.AgenticFlow} class.
     *
     * @param classCreator the Gizmo class creator
     * @param index the Jandex index for type inspection
     * @param agentMethod the agent method to generate schema from
     */
    static void generateInputSchemaMethod(ClassCreator classCreator, IndexView index, MethodInfo agentMethod) {
        // Generate the schema at build-time using Jandex and serialize to JSON string
        JsonNode schemaNode = JandexMethodInputJsonSchema.generateSchemaNode(index, agentMethod);
        String schemaJson = (schemaNode != null) ? schemaNode.toString() : "";

        // Override getInputSchemaJson() to return the JSON string literal
        // Generated code: protected String getInputSchemaJson() { return "...json..."; }
        try (MethodCreator mc = classCreator.getMethodCreator("getInputSchemaJson", String.class)
                .setModifiers(Modifier.PROTECTED)) {
            mc.returnValue(mc.load(schemaJson));
        }
    }

}
