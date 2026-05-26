package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.inject.Vetoed;

import com.networknt.schema.utils.Strings;

import io.quarkiverse.flow.langchain4j.workflow.flow.ParallelAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

// WorkflowApplication now accessed via RuntimeWorkflowApplicationProvider

@Vetoed
public class RuntimeParallelAgenticFlow extends ParallelAgenticFlow {

    private final String agentClassName;
    private final List<String> agentTaskNames = new ArrayList<>();
    private final RuntimeWorkflowApplicationProvider runtimeAppProvider;

    public RuntimeParallelAgenticFlow(String agentClassName, RuntimeWorkflowApplicationProvider runtimeAppProvider) {
        // Add unique ID to prevent workflow definition caching conflicts between test instances
        this.agentClassName = agentClassName;
        this.runtimeAppProvider = runtimeAppProvider;
    }

    @Override
    public String agentClassName() {
        return agentClassName;
    }

    @Override
    protected List<String> subAgentTaskNames() {
        return agentTaskNames;
    }

    public void addSubAgentTaskName(String taskName) {
        Objects.requireNonNull(taskName, "taskName must not be null");
        if (Strings.isBlank(taskName)) {
            throw new IllegalArgumentException("taskName must not be blank");
        }
        agentTaskNames.add(taskName);
    }

    public void addSubAgentTaskName(Object... agents) {
        for (Object agent : agents) {
            this.addSubAgentTaskName(agent.getClass().getName());
        }
    }

    public void addSubAgentTaskName(Collection<?> agents) {
        for (Object agent : agents) {
            this.addSubAgentTaskName(agent.getClass().getName());
        }
    }

    @Override
    public void init() {
        this.definition = runtimeAppProvider.getRuntimeApplication().workflowDefinition(this.descriptor());
    }
}
