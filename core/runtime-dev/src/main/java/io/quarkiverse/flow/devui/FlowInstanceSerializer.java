package io.quarkiverse.flow.devui;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.quarkiverse.flow.devui.FlowInstance.LifecycleEventSummary;

public class FlowInstanceSerializer extends StdSerializer<FlowInstance> {

    public FlowInstanceSerializer() {
        this(null);
    }

    public FlowInstanceSerializer(Class<FlowInstance> t) {
        super(t);
    }

    @Override
    public void serialize(FlowInstance value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        gen.writeStringField("instanceId", value.getInstanceId());
        gen.writeStringField("workflowNamespace", value.getWorkflowNamespace());
        gen.writeStringField("workflowName", value.getWorkflowName());
        gen.writeStringField("workflowVersion", value.getWorkflowVersion());
        gen.writeStringField("status", value.getStatus().name());
        gen.writeNumberField("startTime", value.getStartTime().toEpochMilli());
        gen.writeNumberField("lastUpdateTime", value.getLastUpdateTime().toEpochMilli());

        if (value.getEndTime() != null) {
            gen.writeNumberField("endTime", value.getEndTime().toEpochMilli());
        } else {
            gen.writeNullField("endTime");
        }

        if (value.getErrorCode() != null) {
            gen.writeStringField("errorCode", value.getErrorCode());
        } else {
            gen.writeNullField("errorCode");
        }

        if (value.getErrorMessage() != null) {
            gen.writeStringField("errorMessage", value.getErrorMessage());
        } else {
            gen.writeNullField("errorMessage");
        }

        // Serialize WorkflowModel as its Java object representation
        if (value.getInput() != null) {
            gen.writeObjectField("input", value.getInput().asJavaObject());
        } else {
            gen.writeNullField("input");
        }

        if (value.getOutput() != null) {
            gen.writeObjectField("output", value.getOutput().asJavaObject());
        } else {
            gen.writeNullField("output");
        }

        // Serialize history
        gen.writeArrayFieldStart("history");
        for (LifecycleEventSummary event : value.getHistory()) {
            gen.writeStartObject();
            gen.writeStringField("type", event.type());
            if (event.taskName() != null) {
                gen.writeStringField("taskName", event.taskName());
            } else {
                gen.writeNullField("taskName");
            }
            gen.writeNumberField("timestamp", event.timestamp().toEpochMilli());
            if (event.details() != null) {
                gen.writeStringField("details", event.details());
            } else {
                gen.writeNullField("details");
            }
            gen.writeEndObject();
        }
        gen.writeEndArray();

        gen.writeEndObject();
    }
}
