package com.carmanagement.agentic.workflow;

import com.carmanagement.agentic.agents.CarConditionFeedbackAgent;
import com.carmanagement.agentic.agents.CleaningAgent;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarInfo;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;

/**
 * Sequential workflow coordinating multiple AI agents.
 * From workshop Step 02 - @SequenceAgent pattern.
 *
 * Coordinates: CleaningAgent → CarConditionFeedbackAgent
 */
public interface CarProcessingWorkflow {

    @SequenceAgent(outputKey = "carConditions", subAgents = { CleaningAgent.class, CarConditionFeedbackAgent.class })
    CarConditions processCarReturn(CarInfo carInfo, Integer carNumber, String feedback);

    @Output
    static CarConditions output(String carCondition, String cleaningAgentResult) {
        boolean cleaningRequired = !cleaningAgentResult.toUpperCase().contains("NOT_REQUIRED");
        return new CarConditions(carCondition, cleaningRequired);
    }
}
