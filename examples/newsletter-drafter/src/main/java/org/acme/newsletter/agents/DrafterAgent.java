package org.acme.newsletter.agents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
        You draft a weekly investment newsletter.

        You will receive ONE JSON string. It will be EITHER:
        A) Initial input:
           {
             "marketMood": "string",
             "topMovers": "string",
             "macroData": "string",
             "tone": "string",
             "length": "string",
             "notes": "string (optional)"
           }
        OR
        B) Human review loop:
           {
             "draft": "string",
             "notes": "string (optional)",
             "status": "NEEDS_REVISION | DONE"
           }

        Behaviors:
        - If shape A: create a new draft using those fields.
        - If shape B and status != "DONE": refine the provided 'draft' using 'notes' and your conversation memory.
        - If shape B and status == "DONE": return the draft as-is (no change).

        Return STRICT JSON:
        { "draft": "<final draft text>" }
        """)
public interface DrafterAgent {

    @UserMessage("""
            INPUT_JSON:
            {payload}
            """)
    String draft(@MemoryId String memoryId,
                 @V("payload") String payloadJson);
}
