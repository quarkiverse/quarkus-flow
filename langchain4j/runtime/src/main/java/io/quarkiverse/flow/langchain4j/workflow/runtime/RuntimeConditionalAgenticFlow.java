package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import jakarta.enterprise.inject.Vetoed;

import com.networknt.schema.utils.Strings;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.langchain4j.workflow.flow.ConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

// WorkflowApplication now accessed via RuntimeWorkflowApplicationProvider

@Vetoed
public class RuntimeConditionalAgenticFlow extends ConditionalAgenticFlow {

    private final String agentClassName;
    private final List<String> agentTaskNames = new ArrayList<>();
    private final Map<Integer, Predicate<AgenticScope>> predicates = new HashMap<>();
    private final RuntimeWorkflowApplicationProvider runtimeAppProvider;

    public RuntimeConditionalAgenticFlow(String agentClassName, RuntimeWorkflowApplicationProvider runtimeAppProvider) {
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

    @Override
    protected Map<Integer, Predicate<AgenticScope>> activationPredicates() {
        return predicates;
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

    public void addSubAgentWithPredicate(Predicate<AgenticScope> predicate, Object agent) {
        int index = agentTaskNames.size();
        predicates.put(index, predicate);
        addSubAgentTaskName(agent.getClass().getName());
    }

    @Override
    public void init() {
        this.definition = runtimeAppProvider.getRuntimeApplication().workflowDefinition(this.descriptor());
    }
}
