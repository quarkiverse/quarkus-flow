package io.quarkiverse.flow.opentelemetry.it;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * A second leaf {@code @Agent}, called together with {@link TriageLeafAgent} from the same plain {@code function(..)}
 * task body, so first-wins attribution can be exercised against a real task span.
 */
public interface EscalationLeafAgent {

    @UserMessage("EscalationLeafAgent: decide whether to escalate {{email}}")
    @Agent(description = "Decide whether to escalate the email", outputKey = "escalation")
    String escalate(@V("email") String email);
}
