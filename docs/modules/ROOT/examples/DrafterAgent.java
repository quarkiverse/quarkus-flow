package org.acme.agentic;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

// tag::agent[]
@RegisterAiService
@ApplicationScoped
@SystemMessage("""
        You draft a short, friendly newsletter paragraph.
        Return ONLY the final draft text (no extra markup).
        """)
public interface DrafterAgent {

    // Exactly two parameters: memoryId + one argument (brief)
    @UserMessage("Brief:\n{{brief}}")
    String draft(@MemoryId String memoryId,
            @V("brief") String brief);
}
// end::agent[]
