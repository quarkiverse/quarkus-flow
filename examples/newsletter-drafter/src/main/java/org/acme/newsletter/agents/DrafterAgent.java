package org.acme.newsletter.agents;

import org.acme.newsletter.domain.NewsletterInput;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
        You draft a weekly investment newsletter.\s
        Return JSON with fields:
        {
          "draft": "<final draft text>",
        }
        If 'notes' are provided, incorporate them.
        """)
public interface DrafterAgent {

    @UserMessage("""
            Create or refine a newsletter with:
            - marketMood: {input.marketMood}
            - topMovers: {input.topMovers}
            - macroData: {input.macroData}
            - tone: {input.tone}
            - length: {input.length}
            - notes: {input.notes}
            Return JSON as specified.
            """)
    String draft(@MemoryId String memoryId,
                 NewsletterInput input);
}
