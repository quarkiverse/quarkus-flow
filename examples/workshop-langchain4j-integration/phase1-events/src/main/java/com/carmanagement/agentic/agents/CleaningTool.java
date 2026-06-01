package com.carmanagement.agentic.agents;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Tool that allows AI agents to request cleaning services.
 * In production, this would integrate with a cleaning service API.
 */
@ApplicationScoped
public class CleaningTool {

    private static final Logger LOG = Logger.getLogger(CleaningTool.class);

    @Tool("Request professional cleaning for a vehicle")
    public String requestCleaning(String carIdentifier) {
        LOG.infof("Cleaning requested for vehicle: %s", carIdentifier);
        return String.format("Cleaning requested for vehicle %s. Service ticket created.", carIdentifier);
    }
}
