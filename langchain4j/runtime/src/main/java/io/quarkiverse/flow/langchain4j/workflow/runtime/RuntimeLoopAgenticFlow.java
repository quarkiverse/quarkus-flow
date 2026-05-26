package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import jakarta.enterprise.inject.Vetoed;

import com.networknt.schema.utils.Strings;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

@Vetoed
public class RuntimeLoopAgenticFlow extends LoopAgenticFlow {

    private final String agentClassName;
    private final List<String> agentTaskNames = new ArrayList<>();
    private final RuntimeWorkflowApplicationProvider runtimeAppProvider;
    protected int maxIterations = Integer.MAX_VALUE;
    protected BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    protected boolean testExitAtLoopEnd = false;

    public RuntimeLoopAgenticFlow(String agentClassName, RuntimeWorkflowApplicationProvider runtimeAppProvider) {
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
    protected int maxIterations() {
        return maxIterations;
    }

    @Override
    protected BiPredicate<AgenticScope, Integer> exitCondition() {
        return exitCondition;
    }

    @Override
    protected boolean testExitAtLoopEnd() {
        return testExitAtLoopEnd;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public void setExitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        this.exitCondition = exitCondition;
    }

    public void setTestExitAtLoopEnd(boolean testExitAtLoopEnd) {
        this.testExitAtLoopEnd = testExitAtLoopEnd;
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
