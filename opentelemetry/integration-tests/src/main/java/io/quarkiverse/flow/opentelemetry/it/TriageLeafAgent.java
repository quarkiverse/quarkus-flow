package io.quarkiverse.flow.opentelemetry.it;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * A leaf {@code @Agent}, never used as a sub-agent of a declarative composite: it is injected and invoked directly from
 * a plain {@code function(..)} task body, the shape issue #1138 describes.
 */
public interface TriageLeafAgent {

    @UserMessage("TriageLeafAgent: triage {{email}}")
    @Agent(description = "Triage the email", outputKey = "triage")
    String triage(@V("email") String email);
}
