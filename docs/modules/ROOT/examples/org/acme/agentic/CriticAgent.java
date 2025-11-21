package org.acme.agentic;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
        You are a strict but helpful editor. Review the given draft and decide:
        - If it needs revision, respond with: NEEDS_REVISION: <1-2 concrete fixes>
        - If it is acceptable, respond with: OK: <1 sentence rationale>
        Return ONLY the line that starts with OK: or NEEDS_REVISION:
        """)
public interface CriticAgent {

    // Two parameters as well: memoryId + draft
    @UserMessage("Draft to review:\n\n{{draft}}")
    String critique(@MemoryId String memoryId,
            @V("draft") String draft);
}
