package io.quarkiverse.flow.langchain4j;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

public class Agents {

    @RegisterAiService
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
}
