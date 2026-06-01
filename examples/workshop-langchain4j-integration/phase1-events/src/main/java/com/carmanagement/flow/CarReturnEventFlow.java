package com.carmanagement.flow;

import com.carmanagement.agentic.workflow.CarProcessingWorkflow;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarReturnEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.function.Function;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

/**
 * Event-driven Flow that wraps the workshop's CarProcessingWorkflow.
 *
 * Demonstrates the pattern:
 * - Listen for car-return CloudEvents from Kafka
 * - Extract event data and map to @SequenceAgent parameters
 * - Call the workshop's sequential workflow (zero modifications)
 * - Emit completion event to Kafka
 *
 * Key design:
 * - one() filters CloudEvent collection by type (CNCF spec returns collections)
 * - inputFrom() extracts and transforms event data
 * - Workshop @SequenceAgent code remains completely unchanged
 */
@ApplicationScoped
public class CarReturnEventFlow extends Flow {

    private static final Logger LOG = Logger.getLogger(CarReturnEventFlow.class);

    @Inject
    CarProcessingWorkflow carProcessingWorkflow;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Workflow descriptor() {
        return workflow("car-return-events")
                // Listen for car-return events
                // Note: one() filters the CloudEvent collection by type
                // (CNCF Serverless Workflow spec returns events as collections)
                .schedule(on(one("org.acme.car.returned")))
                .tasks(
                        // Call the workshop's @SequenceAgent workflow
                        function("processWithAgents",
                                (CarReturnEvent event) -> {
                                    LOG.infof("Processing car return: car %d, feedback: %s",
                                            event.carNumber(), event.feedback());

                                    CarConditions result = carProcessingWorkflow.processCarReturn(
                                            event.carInfo(),
                                            event.carNumber(),
                                            event.feedback());

                                    LOG.infof("Car %d processing complete: condition=%s, cleaning=%s",
                                            event.carNumber(),
                                            result.generalCondition(),
                                            result.cleaningRequired() ? "required" : "not required");

                                    return result;
                                })
                                .inputFrom(extractEventData(), JsonNode.class),

                        // Emit completion event
                        emitJson("complete", "org.acme.car.processed", CarConditions.class))
                .build();
    }

    private Function<JsonNode, CarReturnEvent> extractEventData() {
        return node -> {
            try {
                // CloudEvent data field contains the event payload
                JsonNode data = node.isArray() ? node.get(0).get("data") : node.get("data");
                if (data == null || data.isNull()) {
                    throw new IllegalArgumentException("CloudEvent data field is missing or null");
                }
                return objectMapper.treeToValue(data, CarReturnEvent.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to parse event data: " + e.getMessage(), e);
            }
        };
    }
}
