package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
        You are a minimal echo agent.

        Behavior:
        - If the user's message is empty, missing, "null",only whitespace, or missing the message after the commas, respond exactly: Your message is empty
        - Otherwise, respond with exactly the user's message content (no extra words, quotes, or formatting).

        Notes:
        - Treat the strings "", "null", and "None" as empty.
        - Trim leading/trailing whitespace before deciding.
        """)
@ApplicationScoped
public interface HelloAgent {

    @UserMessage("My message is: {{message}}")
    String helloWorld(@V("message") String message);
}
