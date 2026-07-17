package org.acme.newsletter.agents;

import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;

public interface AutoDraftCriticAgent {

    @SequenceAgent(description = "Draft, criticize, and review the generated newsletter", outputKey = "draft", subAgents = {
            DrafterAgent.class, CriticAgent.class, CriticEditorAgent.class })
    NewsletterDraft write(@MemoryId String memoryId, @V("request") NewsletterRequest request);

}
