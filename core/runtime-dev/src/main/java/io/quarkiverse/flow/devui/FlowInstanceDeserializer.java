package io.quarkiverse.flow.devui;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import io.quarkiverse.flow.devui.FlowInstance.LifecycleEventSummary;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;

/**
 * Custom Jackson deserializer for FlowInstance.
 * Deserializes FlowInstance directly without creating intermediate data structures.
 */
public class FlowInstanceDeserializer extends StdDeserializer<FlowInstance> {

    private static final JacksonModelFactory modelFactory = new JacksonModelFactory();

    public FlowInstanceDeserializer() {
        this(null);
    }

    public FlowInstanceDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public FlowInstance deserialize(JsonParser jp, DeserializationContext context) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        String instanceId = node.get("instanceId").asText();
        String workflowNamespace = node.get("workflowNamespace").asText();
        String workflowName = node.get("workflowName").asText();
        String workflowVersion = node.get("workflowVersion").asText();
        WorkflowStatus status = WorkflowStatus.valueOf(node.get("status").asText());
        Instant startTime = Instant.ofEpochMilli(node.get("startTime").asLong());
        WorkflowModel input = parseWorkflowModel(node.get("input"));

        FlowInstance instance = new FlowInstance(
                instanceId,
                workflowNamespace,
                workflowName,
                workflowVersion,
                status,
                startTime,
                input);

        // Restore mutable state
        JsonNode endTimeNode = node.get("endTime");
        if (endTimeNode != null && !endTimeNode.isNull()) {
            Instant endTime = Instant.ofEpochMilli(endTimeNode.asLong());
            WorkflowModel output = parseWorkflowModel(node.get("output"));
            instance.recordCompletion(endTime, output);
        }

        // Restore history events (excluding the initial "workflow.started" event already added by constructor)
        JsonNode historyNode = node.get("history");
        if (historyNode != null && historyNode.isArray()) {
            List<LifecycleEventSummary> events = new ArrayList<>();
            for (JsonNode eventNode : historyNode) {
                String type = eventNode.get("type").asText();
                String taskName = eventNode.has("taskName") && !eventNode.get("taskName").isNull()
                        ? eventNode.get("taskName").asText()
                        : null;
                Instant timestamp = Instant.ofEpochMilli(eventNode.get("timestamp").asLong());
                String details = eventNode.has("details") && !eventNode.get("details").isNull()
                        ? eventNode.get("details").asText()
                        : null;
                events.add(new LifecycleEventSummary(type, taskName, timestamp, details));
            }

            // Skip first event as it's added by FlowInstance constructor
            events.stream()
                    .skip(1)
                    .forEach(event -> restoreHistoryEvent(instance, event));
        }

        return instance;
    }

    private WorkflowModel parseWorkflowModel(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        // Convert JsonNode to Object and then to WorkflowModel
        Object obj = parseObject(node);
        return obj != null ? modelFactory.fromOther(obj) : null;
    }

    private Object parseObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        // For complex objects, return the JsonNode itself
        // Jackson will handle the conversion when needed
        return node;
    }

    private void restoreHistoryEvent(FlowInstance instance, LifecycleEventSummary event) {
        String eventType = event.type();
        switch (eventType) {
            case "task.failed" -> instance.recordTaskFailure(event.taskName(), event.timestamp(),
                    event.details() != null ? new RuntimeException(event.details()) : null);
            case "workflow.failed" -> instance.recordFailure(event.timestamp(),
                    event.details() != null ? new RuntimeException(event.details()) : null);
            case "workflow.cancelled" -> instance.recordCancellation(event.timestamp());
            case "workflow.suspended" -> instance.recordSuspension(event.timestamp());
            case "workflow.resumed" -> instance.recordResumption(event.timestamp());
            case "workflow.status.changed" -> instance.recordStatusChange(event.details(), event.timestamp());
            default -> instance.recordTaskEvent(eventType, event.taskName(), event.timestamp());
        }
    }
}
