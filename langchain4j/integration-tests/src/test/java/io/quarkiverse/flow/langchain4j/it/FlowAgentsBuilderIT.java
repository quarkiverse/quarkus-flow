package io.quarkiverse.flow.langchain4j.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FlowAgentsBuilderIT {

    @Inject
    Agents.StoryCreatorWithConfigurableStyleEditor storyCreator;

    @Inject
    Agents.EveningPlannerAgent eveningPlannerAgent;

    @Inject
    Agents.ExpertRouterAgent expertRouterAgent;

    @Inject
    Agents.StoryCreatorWithReviewWithCounter storyCreatorWithCounter;

    @Test
    void checkSequenceWorkflow() {
        assertThat(storyCreator).isNotNull();
        final String story = storyCreator.write("dungeons and dragons", "fantasy", "nerds");
        assertThat(story).isNotEmpty();
    }

    @Test
    void checkParallelWorkflow() {
        assertThat(eveningPlannerAgent).isNotNull();
        final List<Agents.EveningPlan> plan = eveningPlannerAgent.plan("romantic");
        assertThat(plan).hasSize(3);
    }

    @Test
    void checkConditionalRouterWorkflow() {
        assertThat(expertRouterAgent).isNotNull();
        ResultWithAgenticScope<String> result = expertRouterAgent.ask("I broke my leg what should I do");
        String response = result.result();
        assertThat(response).isNotBlank();
        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("category")).isEqualTo(Agents.RequestCategory.MEDICAL);
    }

    @Test
    void checkLoopWorkflow() {
        assertThat(storyCreatorWithCounter).isNotNull();
        Agents.loopCount = new AtomicInteger();

        ResultWithAgenticScope<String> result = storyCreatorWithCounter.write("dragons and wizards", "comedy");
        String story = result.result();
        assertThat(story).isNotBlank();
        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.7);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5).hasSize(Agents.loopCount.get());

        List<AgentInvocation> styleEditorAgentCalls = agenticScope.agentInvocations("editStory");
        assertThat(styleEditorAgentCalls).hasSizeBetween(1, 5).hasSize(Agents.loopCount.get());

        Agents.loopCount = null;
    }

}
