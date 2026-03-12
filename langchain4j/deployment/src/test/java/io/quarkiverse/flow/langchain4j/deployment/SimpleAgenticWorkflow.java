package io.quarkiverse.flow.langchain4j.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@ApplicationScoped
public class SimpleAgenticWorkflow {

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface CreativeWriter {
        @UserMessage("""
                You are a creative writer.
                Generate a draft of a short novel around the given topic.
                Return only the novel and nothing else.
                The topic is {topic}.
                """)
        String generateNovel(String topic);
    }
}
