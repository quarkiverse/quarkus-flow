package org.acme.langchain4j;

import static java.util.Objects.requireNonNullElse;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Example LangChain4j agentic workflows backed by Quarkus Flow.
 * <p>
 * When the app boots, quarkus-langchain4j creates beans for these
 *
 * @RegisterAiService interfaces. The quarkus-flow-langchain4j extension transparently builds WorkflowDefinitions for
 *                    the
 * @SequenceAgent and @ParallelAgent methods and registers them in the Quarkus Flow runtime.
 *                <p>
 *                You will see them under the Quarkus Flow Dev UI: - document.name ~=
 *                "story-creator-with-configurable-style-editor" - document.name ~= "evening-planner-agent"
 */
public final class Agents {

    private Agents() {
    }

    // --- Domain types --------------------------------------------------------

    public enum Mood {
        ROMANTIC,
        CHILL,
        PARTY,
        FAMILY
    }

    // --- 1) Sequential workflow: story creator --------------------------------

    /**
     * Top-level workflow interface that chains three sub-agents:
     * <p>
     * 1. CreativeWriter -> drafts the story 2. AudienceEditor -> adapts to a given audience 3. StyleEditor -> adapts
     * the writing style
     * <p>
     * The Quarkus Flow integration builds a workflow whose input schema matches the method parameters (topic, style,
     * audience). In Dev UI, you’ll see a workflow with a document name derived from this class.
     */
    @RegisterAiService
    public interface StoryCreatorWithConfigurableStyleEditor {

        @SequenceAgent(outputKey = "story", subAgents = { CreativeWriter.class, AudienceEditor.class, StyleEditor.class })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @RegisterAiService
    public interface CreativeWriter {

        @Agent(name = "Creative writer", description = "Draft a short story about a topic.", outputKey = "story")
        @SystemMessage("""
                You are a creative fiction writer.
                Write short, vivid stories, 4–6 sentences long.
                """)
        @UserMessage("""
                Topic: {topic}

                Write a short story about this topic.
                """)
        String draft(@V("topic") String topic);
    }

    @RegisterAiService
    public interface AudienceEditor {

        @Agent(name = "Audience editor", description = "Adapt story to a target audience.", outputKey = "story")
        @SystemMessage("""
                You rewrite stories to better fit the target audience.
                Keep structure similar but adjust language, tone, and difficulty.
                """)
        @UserMessage("""
                Audience: {audience}

                Rewrite the story below so it is ideal for this audience.
                Story:
                {story}
                """)
        String adapt(@V("story") String story, @V("audience") String audience);
    }

    @RegisterAiService
    public interface StyleEditor {

        @Agent(name = "Style editor", description = "Adapt story to a specific writing style.", outputKey = "story")
        @SystemMessage("""
                You rewrite stories in the requested writing style
                (for example: fantasy, noir, comedy, sci-fi).
                Keep the meaning, adjust style.
                """)
        @UserMessage("""
                Style: {style}

                Rewrite the story below with this style.
                Story:
                {story}
                """)
        String restyle(@V("story") String story, @V("style") String style);
    }

    // --- 2) Parallel workflow: evening planner --------------------------------

    /**
     * Example of a parallel workflow that plans an evening.
     * <p>
     * The @ParallelAgent method fans out to three sub-agents in parallel and then returns a single aggregated
     * EveningPlan.
     * <p>
     * The Quarkus Flow integration builds a fork-join style workflow where each branch represents one of the sub-agents
     * below.
     */
    @RegisterAiService
    public interface EveningPlannerAgent {
        /**
         * LC4J post-processor that builds the final EveningPlan from the values written by the sub-agents + original
         * inputs still in the scope.
         */
        @Output
        static EveningPlan toEveningPlan(@V("city") String city, @V("mood") Mood mood, @V("dinner") String dinner,
                @V("drinks") String drinks, @V("activity") String activity) {

            return new EveningPlan(requireNonNullElse(city, "unknown city"), mood != null ? mood : Mood.CHILL,
                    requireNonNullElse(dinner, "surprise dinner"), requireNonNullElse(drinks, "surprise drinks"),
                    requireNonNullElse(activity, "surprise activity"));
        }

        @ParallelAgent(outputKey = "plan", subAgents = { DinnerAgent.class, DrinksAgent.class, ActivityAgent.class })
        EveningPlan plan(@V("city") String city, @V("mood") Mood mood);
    }

    // Not sure why we need to force this to avoid quarkus-langchain4j complaining about outputKeys.
    // TODO: open an issue
    public interface DumbAgent {
        @Agent(outputKey = "city")
        String city();

        @Agent(outputKey = "mood")
        Mood mood();

        @Agent(outputKey = "topic")
        String topic();

        @Agent(outputKey = "style")
        String style();

        @Agent(outputKey = "audience")
        String audience();
    }

    @RegisterAiService
    public interface DinnerAgent {

        @Agent(name = "Dinner planner", outputKey = "dinner")
        @SystemMessage("""
                You suggest a single, concrete dinner option in the given city
                for a given mood. Be specific and short.
                """)
        @UserMessage("""
                City: {city}
                Mood: {mood}

                Suggest where to have dinner (one place, one sentence).
                """)
        String suggestDinner(@V("city") String city, @V("mood") Mood mood);
    }

    @RegisterAiService
    public interface DrinksAgent {

        @Agent(name = "Drinks planner", outputKey = "drinks")
        @SystemMessage("""
                You suggest one place for drinks after dinner, matching the mood.
                """)
        @UserMessage("""
                City: {city}
                Mood: {mood}

                Suggest where to have a drink (one place, one sentence).
                """)
        String suggestDrinks(@V("city") String city, @V("mood") Mood mood);
    }

    @RegisterAiService
    public interface ActivityAgent {

        @Agent(name = "Activity planner", outputKey = "activity")
        @SystemMessage("""
                You suggest one short activity to wrap up the evening.
                """)
        @UserMessage("""
                City: {city}
                Mood: {mood}

                Suggest one activity, after dinner and drinks, to finish the evening.
                """)
        String suggestActivity(@V("city") String city, @V("mood") Mood mood);
    }

    public record EveningPlan(String city, Mood mood, String dinner, String drinks, String activity) {
    }

}
