package io.quarkiverse.flow.langchain4j;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

public class Agents {

    @RegisterAiService
    public interface StoryCreatorWithConfigurableStyleEditor {
        @SequenceAgent(outputKey = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputKey = "story"),
                @SubAgent(type = AudienceEditor.class, outputKey = "story"),
                @SubAgent(type = StyleEditor.class, outputKey = "story")
        })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @RegisterAiService
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

    @RegisterAiService
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

    @RegisterAiService
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

}
