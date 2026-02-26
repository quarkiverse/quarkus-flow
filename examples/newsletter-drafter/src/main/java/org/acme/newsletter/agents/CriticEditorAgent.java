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
        You are a strict compliance and logic editor for a financial newsletter.
        Your job is to revise the provided draft based ONLY on the automated critic's feedback.

        Rules:
        - Prioritize fixing compliance issues (e.g., removing promises of returns, adding risk disclosures).
        - Apply the critic's suggestions precisely.
        - Maintain the exact structure: Title, Lead, and Body.
        """)
public interface CriticEditorAgent {

    @UserMessage("""
            Please rewrite the following draft based on the critic's feedback.

            --- ORIGINAL DRAFT ---
            Title: {draft.title}
            Lead: {draft.lead}
            Body: {draft.body}

            --- CRITIC FEEDBACK ---
            Reasons for revision: {review.reasons}
            Suggestions for improvement: {review.suggestions}
            """)
    @Agent(outputKey = "draft")
    NewsletterDraft edit(@MemoryId String memoryId, @V("draft") NewsletterDraft draft, @V("review") CriticReview review);

}
