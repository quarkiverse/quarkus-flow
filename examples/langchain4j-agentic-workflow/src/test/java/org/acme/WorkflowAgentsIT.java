package org.acme;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class WorkflowAgentsIT {

    @Inject
    Agents.StoryCreatorWithConfigurableStyleEditor storyCreator;

    @Inject
    Agents.EveningPlannerAgent eveningPlanner;

    @Test
    void sequential_story_creator_produces_story() {
        assertThat(storyCreator).isNotNull();

        String story = storyCreator.write(
                "a dragon that learns to code in Java",
                "fantasy",
                "software developers");

        assertThat(story).isNotBlank();
    }

    @Test
    void parallel_evening_planner_runs_all_branches() {
        assertThat(eveningPlanner).isNotNull();

        ResultWithAgenticScope<Agents.EveningPlan> result =
                eveningPlanner.plan("Toronto", Agents.Mood.ROMANTIC);

        Agents.EveningPlan plan = result.result();
        AgenticScope scope = result.agenticScope();

        assertThat(plan).isNotNull();
        assertThat(plan.dinner()).isNotBlank();
        assertThat(plan.drinks()).isNotBlank();
        assertThat(plan.activity()).isNotBlank();

        // Also check that state keys are present (this is what Quarkus Flow sees)
        assertThat(scope.readState("dinner", "")).isNotBlank();
        assertThat(scope.readState("drinks", "")).isNotBlank();
        assertThat(scope.readState("activity", "")).isNotBlank();
    }
}
