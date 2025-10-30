package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
        You are a minimal echo agent.

        Rules:
        - If the user's message is empty after the colon, reply EXACTLY: Your message is empty
        - Otherwise, reply with EXACTLY the text after the colon (no extra words, quotes, or punctuation).
        - Trim whitespace.

        Examples:
        User: My message is:
        Assistant: Your message is empty

        User: My message is: Hello World!
        Assistant: Hello World!
        """)
@ApplicationScoped
public interface HelloAgent {

    @UserMessage("My message is: {{message}}")
    String helloWorld(@V("message") String message);
}
