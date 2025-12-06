package io.quarkiverse.flow.langchain4j.deployment;

import java.util.List;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build-time representation of an agentic method to be turned into
 * a runtime AgenticWorkflowDescriptor by the recorder.
 */
public final class FlowAgenticWorkflowBuildItem extends MultiBuildItem {

    private final String ifaceName;
    private final String methodName;
    private final List<String> parameterTypeNames;

    public FlowAgenticWorkflowBuildItem(String ifaceName, String methodName, List<String> parameterTypeNames) {
        this.ifaceName = ifaceName;
        this.methodName = methodName;
        this.parameterTypeNames = List.copyOf(parameterTypeNames);
    }

    public String ifaceName() {
        return ifaceName;
    }

    public String methodName() {
        return methodName;
    }

    public List<String> parameterTypeNames() {
        return parameterTypeNames;
    }

    @Override
    public String toString() {
        return "FlowAgenticWorkflowBuildItem{" +
                "ifaceName='" + ifaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypeNames=" + parameterTypeNames +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        FlowAgenticWorkflowBuildItem that = (FlowAgenticWorkflowBuildItem) o;
        return Objects.equals(ifaceName, that.ifaceName) && Objects.equals(methodName, that.methodName)
                && Objects.equals(parameterTypeNames, that.parameterTypeNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ifaceName, methodName, parameterTypeNames);
    }
}
