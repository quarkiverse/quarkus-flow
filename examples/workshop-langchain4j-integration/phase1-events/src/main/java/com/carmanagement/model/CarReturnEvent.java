package com.carmanagement.model;

/**
 * Event payload for car return events.
 * Wraps the parameters needed by CarProcessingWorkflow.
 */
public record CarReturnEvent(
        CarInfo carInfo,
        Integer carNumber,
        String feedback) {
}
