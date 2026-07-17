package io.quarkiverse.flow.langchain4j.it.schedulable;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.annotations.ScheduleOn;

public class MessageSummaryAgentic {

    public static String summaryTemplate(String content) {
        return """
                Thanks for using Quarkus Flow!

                ---
                %s
                ---
                Have a good day!
                """.formatted(content);
    }

    public interface EmailSummaryAgentic {

        @ScheduleOn(every = "PT3S")
        @SequenceAgent(subAgents = { EmailSummary.class })
        String emailSummary();

        @Output
        static String summary(@V("emailSummary") String emailSummary) {
            return summaryTemplate(emailSummary);
        }
    }

    public interface WhatsAppSummaryAgentic {

        @ScheduleOn(cron = "* * * * *")
        @SequenceAgent(subAgents = { WhatsAppSummary.class })
        String whatsAppSummary();

        @Output
        static String summary(@V("whatsAppSummary") String whatsAppSummary) {
            return summaryTemplate(whatsAppSummary);
        }
    }

    public interface EmailSummary {

        @SystemMessage("""
                You are an agent which use email tools to summary email inbox.
                """)
        @UserMessage("Summary my email inbox using the Email summarizer tool.")
        @Agent(value = "Email summarizer", outputKey = "emailSummary")
        String emailSummarizer();
    }

    public interface WhatsAppSummary {

        @SystemMessage("""
                You are an agent which use WhatsApp tools to summary WhatsApp messages.
                """)
        @UserMessage("Summarize my whatsapp messages using the WhatsApp summarizer tool.")
        @Agent(value = "WhatsApp summarizer", outputKey = "whatsAppSummary")
        String whatsAppSummarizer();
    }
}
