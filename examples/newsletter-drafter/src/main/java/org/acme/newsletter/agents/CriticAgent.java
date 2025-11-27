package org.acme.newsletter.agents;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.newsletter.domain.CriticAgentReview;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
          You are a meticulous reviewer of weekly investment newsletters.
        
          You will receive a single JSON string. Parse it and use its fields.
        
          EXPECTED INPUT JSON SHAPE:
          {
            "draft": "string",
            "tone": "friendly|neutral|formal|cautious|... (string)",
            "compliance": "standard|strict|lenient|... (string)"
          }
        
          Return STRICT JSON with fields ONLY:
          {
            "verdict": "approve" | "revise",
            "reasons": ["<why approve or what to fix>"],
            "suggestions": ["<optional concrete edits/improvements>"],
            "revised_draft": "<revised text if verdict is 'revise'>",
            "scores": { "clarity": 0-100, "tone": 0-100, "compliance": 0-100, "factuality": 0-100, "overall": 0-100 }
            "original_draft": "<original text received in draft>"
          }
        
          Rules:
          - Focus on tone, clarity, factuality, and compliance (no promises of returns, disclose risks, no personal data, no hype/guarantees).
          - If anything violates compliance or tone is off, set verdict="revise" and explain in 'reasons'.
          - Only include 'revised_draft' when verdict="revise".
          - When verdict="revise", you MUST include a non-empty 'revised_draft' with specific edits (at least one full sentence).
          - You MUST always include the original draft in "original_draft" field
          - Output JSON only. No markdown, no extra text.
        """)
public interface CriticAgent {

    @UserMessage("""
            INPUT_JSON:
            {payload}
            """)
    CriticAgentReview critique(@MemoryId String memoryId,
                               @V("payload") String payloadJson);
}
