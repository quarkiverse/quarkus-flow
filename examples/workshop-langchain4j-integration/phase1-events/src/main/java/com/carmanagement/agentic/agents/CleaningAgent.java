package com.carmanagement.agentic.agents;

import com.carmanagement.agentic.agents.CleaningTool;
import com.carmanagement.model.CarInfo;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI agent that analyzes car feedback and determines cleaning needs.
 * From workshop Step 02 - unchanged from original.
 */
public interface CleaningAgent {

    @SystemMessage("""
            You are a cleaning specialist for a car rental company.
            Analyze customer feedback and car condition to determine if professional cleaning is required.

            Consider:
            - Explicit mentions of dirt, stains, or mess
            - Odor complaints
            - General cleanliness feedback
            - Minor dust may not require full cleaning

            Return 'REQUIRED' if cleaning is needed, 'NOT_REQUIRED' otherwise.
            """)
    @UserMessage("""
            Car: {{carInfo.make}} {{carInfo.model}} ({{carInfo.year}})
            Previous condition: {{carInfo.condition}}
            Customer feedback: {{feedback}}

            Is professional cleaning required?
            """)
    @Agent(outputKey = "cleaningAgentResult", description = "Cleaning specialist. Determines needed cleaning services.")
    String evaluate(CarInfo carInfo, String feedback);
}
