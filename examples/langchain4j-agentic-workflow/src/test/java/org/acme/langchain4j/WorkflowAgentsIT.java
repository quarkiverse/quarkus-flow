package org.acme.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WorkflowAgentsIT {

    @Inject
    Agents.StoryCreatorWithConfigurableStyleEditor storyCreator;

    @Inject
    Agents.EveningPlannerAgent eveningPlanner;

    @Test
    void sequential_story_creator_produces_story() {
        assertThat(storyCreator).isNotNull();

        String story = storyCreator.write("a dragon that learns to code in Java", "fantasy", "software developers");

        assertThat(story).isNotBlank();
    }

    @Test
    void parallel_evening_planner_runs_all_branches() {
        assertThat(eveningPlanner).isNotNull();

        Agents.EveningPlan plan = eveningPlanner.plan("Toronto", Agents.Mood.ROMANTIC);

        assertThat(plan).isNotNull();
        assertThat(plan.dinner()).isNotBlank();
        assertThat(plan.drinks()).isNotBlank();
        assertThat(plan.activity()).isNotBlank();

        plan = eveningPlanner.plan("New York", Agents.Mood.ROMANTIC);
        assertThat(plan).isNotNull();
        assertThat(plan.dinner()).isNotBlank();
        assertThat(plan.drinks()).isNotBlank();
        assertThat(plan.activity()).isNotBlank();

    }
}
