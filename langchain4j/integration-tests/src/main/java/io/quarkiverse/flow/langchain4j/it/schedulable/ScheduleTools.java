package io.quarkiverse.flow.langchain4j.it.schedulable;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;

@ApplicationScoped
public class ScheduleTools {

    @Tool("WhatsApp summarizer")
    public String summarizeWhatsapp() {
        return "You do not have WhatsApp message";
    }

    @Tool("Email summarizer")
    public String summarizeEmail() {
        return "Your email inbox is empty";
    }
}
