package io.quarkiverse.flow.langchain4j.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.InjectableInstance;

@ApplicationScoped
public class FlowPlannerFactory {

    @Inject
    InjectableInstance<FlowPlanner> plannerInstance;

    public FlowPlanner newPlanner(FlowAgentServiceWorkflowBuilder workflowBuilder) {
        FlowPlanner planner = plannerInstance.get();
        planner.configure(workflowBuilder, plannerInstance.getHandle());
        return planner;
    }

}
