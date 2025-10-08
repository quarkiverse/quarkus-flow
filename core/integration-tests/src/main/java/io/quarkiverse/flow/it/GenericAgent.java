package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@ApplicationScoped
public interface GenericAgent {

    @UserMessage("{{message}}")
    String sendMessage(@V("message") String message);
}
