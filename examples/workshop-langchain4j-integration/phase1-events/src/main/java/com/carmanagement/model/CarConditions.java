package com.carmanagement.model;

/**
 * Combined result from CarProcessingWorkflow.
 * Output from the @SequenceAgent coordinating multiple agents.
 */
public record CarConditions(
        String generalCondition,
        boolean cleaningRequired) {
}
