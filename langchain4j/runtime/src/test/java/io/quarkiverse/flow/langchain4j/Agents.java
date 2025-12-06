package io.quarkiverse.flow.langchain4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

public class Agents {

    static AtomicInteger loopCount;

    public enum RequestCategory {
        LEGAL,
        MEDICAL,
        TECHNICAL,
        UNKNOWN
    }

    @RegisterAiService
    public interface StoryCreatorWithConfigurableStyleEditor {
        @SequenceAgent(outputKey = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputKey = "story"),
                @SubAgent(type = AudienceEditor.class, outputKey = "story"),
                @SubAgent(type = StyleEditor.class, outputKey = "story")
        })
        @Agent("write")
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    public interface CreativeWriter {
        @UserMessage("""
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(description = "Generate a story based on the given topic", outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface AudienceEditor {
        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better align with the target audience of {{audience}}.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StyleEditor {
        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given style", outputKey = "story")
        String editStory(@V("story") String story, @V("style") String style);
    }

    @RegisterAiService
    public interface EveningPlannerAgent {
        @Output
        static List<EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
            List<EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        }

        @ParallelAgent(outputKey = "plans", subAgents = {
                @SubAgent(type = FoodExpert.class, outputKey = "meals"),
                @SubAgent(type = MovieExpert.class, outputKey = "movies")
        })
        List<EveningPlan> plan(@V("mood") String mood);
    }

    public interface FoodExpert {
        @UserMessage("""
                You are a great evening planner.
                Propose a list of 3 meals matching the given mood.
                The mood is {{mood}}.
                For each meal, just give the name of the meal.
                Provide a list with the 3 items and nothing else.
                """)
        @Agent(outputKey = "meals")
        List<String> findMeal(@V("mood") String mood);
    }

    public interface MovieExpert {
        @UserMessage("""
                You are a great evening planner.
                Propose a list of 3 movies matching the given mood.
                The mood is {{mood}}.
                Provide a list with the 3 items and nothing else.
                """)
        @Agent(outputKey = "movies")
        List<String> findMovie(@V("mood") String mood);
    }

    public interface CategoryRouter {
        @UserMessage("""
                Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
                In case the request doesn't belong to any of those categories categorize it as 'unknown'.
                Reply with only one of those words and nothing else.
                The user request is: '{{request}}'.
                """)
        @Agent(description = "Categorize a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    public interface MedicalExpert {
        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A medical expert")
        @Agent(description = "A medical expert")
        String medical(@V("request") String request);
    }

    public interface LegalExpert {
        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A legal expert")
        @Agent(description = "A legal expert")
        String legal(@V("request") String request);
    }

    public interface TechnicalExpert {
        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A technical expert")
        @Agent(description = "A technical expert")
        String technical(@V("request") String request);
    }

    @RegisterAiService
    public interface ExpertsAgent {

        @ActivationCondition(MedicalExpert.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(LegalExpert.class)
        static boolean activateLegal(AgenticScope agenticScope) {
            return agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL;
        }

        @ActivationCondition(TechnicalExpert.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ConditionalAgent(outputKey = "response", subAgents = {
                @SubAgent(type = MedicalExpert.class, outputKey = "response"),
                @SubAgent(type = LegalExpert.class, outputKey = "response"),
                @SubAgent(type = TechnicalExpert.class, outputKey = "response"),
        })
        String askExpert(@V("request") String request);
    }

    public interface ExpertRouterAgent {
        @SequenceAgent(outputKey = "response", subAgents = {
                @SubAgent(type = CategoryRouter.class, outputKey = "category"),
                @SubAgent(type = ExpertsAgent.class, outputKey = "response")
        })
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface DumbAgent {
        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "loopCounter")
        int dumb(@V("request") String request);

        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "request")
        String dumb();

        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "topic")
        String dumb1();

        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "style")
        String dumb2();

        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "audience")
        String dumb3();

        @Agent(description = "A dumb agent to workaround a dumb check", outputKey = "mood")
        String dumb4();

    }

    public interface StyleScorer {
        @UserMessage("""
                You are a critical reviewer.
                Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
                Return only the score and nothing else.

                The story is: "{{story}}"
                """)
        @Agent("Score a story based on how well it aligns with a given style")
        double scoreStyle(@V("story") String story, @V("style") String style);
    }

    @RegisterAiService
    public interface StyleReviewLoopAgentWithCounter {

        @ExitCondition(testExitAtLoopEnd = true)
        static boolean exit(@V("score") double score, @LoopCounter int loopCounter) {
            loopCount.set(loopCounter);
            return score >= 0.8;
        }

        @LoopAgent(description = "Review the given story to ensure it aligns with the specified style", outputKey = "story", maxIterations = 5, subAgents = {
                @SubAgent(type = StyleScorer.class, outputKey = "score"),
                @SubAgent(type = StyleEditor.class, outputKey = "story")
        })
        String write(@V("story") String story);
    }

    @RegisterAiService
    public interface StoryCreatorWithReviewWithCounter {
        @SequenceAgent(outputKey = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputKey = "story"),
                @SubAgent(type = StyleReviewLoopAgentWithCounter.class, outputKey = "story")
        })
        @Agent("write")
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }

    public record EveningPlan(String movie, String meal) {
    }

}
