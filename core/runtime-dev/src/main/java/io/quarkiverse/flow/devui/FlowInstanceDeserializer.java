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
        WorkflowModel input = readWorkflowModel(node.get("input"));

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
            WorkflowModel output = readWorkflowModel(node.get("output"));
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
        }

        return instance;
    }

    private WorkflowModel readWorkflowModel(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return modelFactory.from(node.toString());
    }

}
