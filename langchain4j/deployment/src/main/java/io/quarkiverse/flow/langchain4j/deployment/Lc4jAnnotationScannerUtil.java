package io.quarkiverse.flow.langchain4j.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;

interface Lc4jAnnotationScannerUtil {

    /**
     * Takes the method annotated with {@link ActivationCondition}, extract its name, target agents (from value=Class[]), and
     * description.
     * See an example of annotated interface in {@link ActivationCondition} javadoc.
     *
     * @param iface the target {@link dev.langchain4j.agentic.Agent} interface.
     * @return ConditionalMetadata representing the {@link ActivationCondition}.
     */
    static ConditionalMetadata scanActivationConditions(ClassInfo iface) {
        final Map<String, PredicateMetadata> conditions = new HashMap<>();

        for (MethodInfo method : iface.methods()) {
            final AnnotationInstance activationAnnot = method.annotation(
                    DotName.createSimple(ActivationCondition.class.getName()));

            if (activationAnnot != null) {
                final AnnotationValue description = activationAnnot.value("description");
                PredicateMetadata predicate = new PredicateMetadata(
                        method.name(),
                        method.parameterTypes().stream().map(Type::name).map(DotName::toString).toList(),
                        description == null ? null : description.asString());

                for (Type agentType : activationAnnot.value().asClassArray()) {
                    conditions.put(agentType.name().toString(), predicate);
                }
            }
        }

        return new ConditionalMetadata(conditions);
    }

    /**
     * Takes the metadata from {@link LoopAgent} and predicate from {@link ExitCondition} to build a {@link LoopMetadata}.
     *
     * @param iface the target {@link dev.langchain4j.agentic.Agent} interface.
     * @param agenticMethod the target method annotated with {@link LoopAgent}.
     * @return {@link LoopMetadata} with the discovered data.
     */
    static LoopMetadata scanLoopMetadata(ClassInfo iface, MethodInfo agenticMethod) {
        final int maxIterations = agenticMethod.annotation(DotName.createSimple(LoopAgent.class.getName()))
                .value("maxIterations").asInt();

        PredicateMetadata exitCondition = null;
        boolean testExitAtLoopEnd = false;

        for (MethodInfo method : iface.methods()) {
            final AnnotationInstance exitAnnot = method.annotation(DotName.createSimple(ExitCondition.class.getName()));

            if (exitAnnot != null) {
                final AnnotationValue description = exitAnnot.value("description");
                testExitAtLoopEnd = exitAnnot.value("testExitAtLoopEnd").asBoolean();
                exitCondition = new PredicateMetadata(
                        method.name(),
                        method.parameterTypes().stream().map(Type::name).map(DotName::toString).toList(),
                        description == null ? null : description.asString());
                break;
            }
        }

        return new LoopMetadata(maxIterations, Optional.ofNullable(exitCondition), testExitAtLoopEnd);
    }
}
