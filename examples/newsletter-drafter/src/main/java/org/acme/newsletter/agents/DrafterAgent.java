package org.acme.newsletter.agents;

import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;

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
        You are an expert financial copywriter.
        Write a draft investment newsletter based on the provided inputs.
        Ensure it reads naturally and incorporates all requested data.
        Return ONLY valid JSON.
        """)
public interface DrafterAgent {

    @Agent(outputKey = "draft")
    @UserMessage("""
            Please write the newsletter using the following information:

            - Market mood: {request.tone}
            - Top movers: {request.topMovers}
            - Macro data: "{request.macroData}"
            - Requested Tone: {request.tone}
            - Length: {request.length}
            """)
    NewsletterDraft draft(@MemoryId String memoryId, @V("request") NewsletterRequest request);
}