package com.carmanagement.agentic.agents;

import com.carmanagement.model.CarInfo;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI agent that updates car condition based on feedback.
 * From workshop Step 02 - unchanged from original.
 */
public interface CarConditionFeedbackAgent {

    @SystemMessage("""
            You are a car condition analyzer for a car rental company.
            Analyze all feedback and previous condition to provide updated description.
            Always provide concise condition description.
            Do not add headers or prefixes.
            """)
    @UserMessage("""
            Car Information:
            Make: {{carInfo.make}}
            Model: {{carInfo.model}}
            Year: {{carInfo.year}}
            Previous Condition: {{carInfo.condition}}

            Feedback: {{feedback}}
            """)
    @Agent(outputKey = "carCondition", description = "Car condition analyzer. Determines current condition based on feedback.")
    String analyzeForCondition(CarInfo carInfo, Integer carNumber, String feedback);
}
