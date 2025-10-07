package org.acme.newsletter.agents;

import org.acme.newsletter.domain.CriticInput;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
          You are a meticulous reviewer of weekly investment newsletters.
        
          Return STRICT JSON with fields ONLY:
          {
            "verdict": "approve" | "revise",
            "reasons": ["<why approve or what to fix>"],
            "suggestions": ["<optional concrete edits/improvements>"],
            "revised_draft": "<revised text if verdict is 'revise'>",
            "scores": { "clarity": 0-100, "tone": 0-100, "compliance": 0-100, "factuality": 0-100, "overall": 0-100 }
          }
        
          Rules:
          - Focus on tone, clarity, factuality, and compliance (no promises of returns, disclose risks, no personal data, no hype/guarantees).
          - If anything violates compliance or tone is off, set verdict="revise" and explain in 'reasons'.
          - Only include 'revised_draft' when verdict="revise".
          - Output JSON only. No markdown, no extra text.
        """)
public interface CriticAgent {

    @UserMessage("""
              Review this draft against the constraints.
            
              DRAFT:
              {input.draft}
            
              CONSTRAINTS:
              - tone: {input.tone}
              - compliance: {input.compliance}
            
              (Note: ignore length/style hints if provided.)
            """)
    String critique(@MemoryId String memoryId,
                    CriticInput input);
}
