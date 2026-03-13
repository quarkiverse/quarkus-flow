package org.acme.newsletter.agents;

import org.acme.newsletter.domain.CriticReview;
import org.acme.newsletter.domain.NewsletterDraft;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
@SystemMessage("""
         You are a meticulous reviewer of weekly investment newsletters.
          Your job is to evaluate the provided draft based on tone, clarity, factuality, and compliance.

          Compliance Rules:
          - NO promises of returns.
          - MUST disclose risks.
          - NO personal data.
          - NO hype or guarantees.

          Evaluation Rules:
          - If the draft violates ANY compliance rules or the tone is poor, set the verdict to REVISE and list the reasons.
          - If the draft is excellent and compliant, set the verdict to APPROVE.

          CRITICAL OUTPUT INSTRUCTIONS:
          You MUST return a fully populated JSON object. Do not omit any fields.
          Your JSON must contain exactly these four keys:
          1. "verdict": either "APPROVE" or "REVISE"
          2. "reasons": an array of strings explaining your verdict
          3. "suggestions": an array of strings with ideas for improvement
          4. "scores": a nested object containing integer scores (0-100) for "clarity", "tone", "compliance", "factuality", and "overall".
        """)
public interface CriticAgent {

    @Agent(outputKey = "review")
    @UserMessage("""
            Please review the following newsletter draft:

            Title: {draft.title}
            Lead: {draft.lead}
            Body: {draft.body}
            """)
    CriticReview critique(@MemoryId String memoryId, @V("draft") NewsletterDraft draft);
}