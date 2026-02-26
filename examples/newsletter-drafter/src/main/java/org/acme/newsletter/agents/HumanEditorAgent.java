package org.acme.newsletter.agents;

import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterDraft;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
@SystemMessage("""
        You are an executive assistant and copywriter.
        Your job is to update a newsletter draft based on your manager's (the human's) notes.

        Rules:
        - The human's instructions are the ultimate authority. Override previous text if requested.
        - Match the tone requested by the human.
        - Maintain the structure: Title, Lead, and Body.
        """)
public interface HumanEditorAgent {
    @UserMessage("""
            Please update the draft based on the manager's notes.

            --- ORIGINAL DRAFT ---
            Title: {review.draft.title}
            Lead: {review.draft.lead}
            Body: {review.draft.body}

            --- MANAGER NOTES ---
            {review.notes}
            """)
    NewsletterDraft edit(@V("review") HumanReview review);

}
