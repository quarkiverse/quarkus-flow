package io.quarkiverse.flow.opentelemetry.it;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * {@code sequence(classify, parallel(details, summary))}: three AI service calls driven by two generated agentic
 * workflows.
 */
public interface IntakeAgents {

    @SequenceAgent(outputKey = "summary", subAgents = { ClassifierAgent.class, ExtractionAgent.class })
    String process(@V("email") String email);

    interface ClassifierAgent {
        @UserMessage("ClassifierAgent: classify {{email}}")
        @Agent(description = "Classify the email", outputKey = "type")
        String classify(@V("email") String email);
    }

    interface ExtractionAgent {
        @ParallelAgent(outputKey = "summary", subAgents = { DetailsAgent.class, SummaryAgent.class })
        String extract(@V("email") String email);
    }

    interface DetailsAgent {
        @UserMessage("DetailsAgent: extract the details of {{email}}")
        @Agent(description = "Extract the details of the email", outputKey = "details")
        String details(@V("email") String email);
    }

    interface SummaryAgent {
        @UserMessage("SummaryAgent: summarize {{email}}")
        @Agent(description = "Summarize the email", outputKey = "summary")
        String summarize(@V("email") String email);
    }
}
