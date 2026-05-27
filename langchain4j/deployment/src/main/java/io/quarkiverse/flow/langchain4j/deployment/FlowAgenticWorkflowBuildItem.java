package io.quarkiverse.flow.langchain4j.deployment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build-time representation of an agentic method to be turned into
 * a runtime AgenticWorkflowDescriptor by the recorder.
 */
public final class FlowAgenticWorkflowBuildItem extends MultiBuildItem {

    private final String ifaceName;
    private final MethodInfo method;
    private final AgenticWorkflowBlueprint blueprint;

    FlowAgenticWorkflowBuildItem(String ifaceName,
            MethodInfo method,
            AgenticWorkflowBlueprint blueprint) {
        this.ifaceName = ifaceName;
        this.method = method;
        this.blueprint = blueprint;
    }

    public String ifaceName() {
        return ifaceName;
    }

    public MethodInfo method() {
        return method;
    }

    public AgenticSystemTopology topology() {
        return blueprint.topology();
    }

    public List<Type> subAgents() {
        return blueprint.subAgents();
    }

    public String description() {
        return blueprint.description();
    }

    public Optional<ConditionalMetadata> conditionalMetadata() {
        return Optional.ofNullable(blueprint.conditionalMetadata());
    }

    public Optional<LoopMetadata> loopMetadata() {
        return Optional.ofNullable(blueprint.loopMetadata());
    }

    @Override
    public String toString() {
        return "FlowAgenticWorkflowBuildItem{" +
                "ifaceName='" + ifaceName + '\'' +
                ", method='" + method + '\'' +
                ", blueprint=" + blueprint +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        FlowAgenticWorkflowBuildItem that = (FlowAgenticWorkflowBuildItem) o;
        return Objects.equals(ifaceName, that.ifaceName) && Objects.equals(method, that.method)
                && Objects.equals(blueprint, that.blueprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ifaceName, method, blueprint);
    }
}
