package io.quarkiverse.flow.langchain4j;

import java.util.List;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class Agents {

    public interface StoryCreatorWithConfigurableStyleEditor {
        @SequenceAgent(outputKey = "story", subAgents = {
                CreativeWriter.class, AudienceEditor.class, StyleEditor.class
        })
        @Agent(description = "write", outputKey = "story")
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

    public interface SequenceWithA2AAgent {
        @SequenceAgent(outputKey = "finalResult", subAgents = {
                RemoteDataFetcher.class, LocalProcessor.class
        })
        String processWithRemote(@V("request") String request);
    }

    public interface RemoteDataFetcher {
        @A2AClientAgent(a2aServerUrl = "http://localhost:7777", description = "Fetch data from remote A2A agent", outputKey = "remoteData")
        String fetchData(@V("request") String request);
    }

    public interface LocalProcessor {
        @UserMessage("""
                You are a data processor.
                Process the following data and return a summary.
                Data: {{remoteData}}
                """)
        @Agent(description = "Process the fetched data locally", outputKey = "finalResult")
        String process(@V("remoteData") String data);
    }

    // ParallelMapperAgent test
    public interface SequenceWithParallelMapperAgent {
        @SequenceAgent(outputKey = "results", subAgents = {
                ItemsGenerator.class, ItemsMapper.class
        })
        List<String> processItems(@V("request") String request);
    }

    public interface ItemsGenerator {
        @UserMessage("""
                Generate a list of items based on: {{request}}
                Return a comma-separated list.
                """)
        @Agent(description = "Generate items to process", outputKey = "items")
        List<String> generateItems(@V("request") String request);
    }

    public interface ItemsMapper {
        @ParallelMapperAgent(subAgent = ItemProcessor.class, outputKey = "results")
        List<String> mapItems(@V("items") List<String> items);
    }

    public interface ItemProcessor {
        @UserMessage("""
                Process this item: {{item}}
                Return the processed result.
                """)
        @Agent(description = "Process a single item", outputKey = "result")
        String processItem(@V("item") String item);
    }

    // SupervisorAgent test
    public interface SequenceWithSupervisorAgent {
        @SequenceAgent(outputKey = "finalResult", subAgents = {
                TaskSupervisor.class
        })
        String superviseTasks(@V("request") String request);
    }

    public interface TaskSupervisor {
        @UserMessage("""
                You are a supervisor coordinating multiple agents.
                Request: {{request}}
                """)
        @SupervisorAgent(subAgents = { CreativeWriter.class, AudienceEditor.class }, outputKey = "supervisorResult")
        String supervise(@V("request") String request);
    }

    // PlannerAgent test
    public interface SequenceWithPlannerAgent {
        @SequenceAgent(outputKey = "finalResult", subAgents = {
                TaskPlanner.class
        })
        String planTasks(@V("request") String request);
    }

    public interface TaskPlanner {
        @UserMessage("""
                You are a planner creating execution plans.
                Request: {{request}}
                """)
        @PlannerAgent(outputKey = "plan", subAgents = { CreativeWriter.class, StyleEditor.class })
        String plan(@V("request") String request);
    }
}
