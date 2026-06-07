package io.quarkiverse.flow.langchain4j.spec;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.serverlessworkflow.impl.marshaller.CustomObjectMarshaller;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;

public class AgenticAwareWorkflowModelMarshaller implements CustomObjectMarshaller<AgenticAwareWorkflowModel> {

    @Override
    public void write(WorkflowOutputBuffer buffer, AgenticAwareWorkflowModel value) {
        try {
            buffer.writeBytes(JsonUtils.mapper().writeValueAsBytes(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to marshal agentic workflow model", e);
        }
    }

    @Override
    public AgenticAwareWorkflowModel read(WorkflowInputBuffer buffer, Class<? extends AgenticAwareWorkflowModel> clazz) {
        try {
            return JsonUtils.mapper().readValue(buffer.readBytes(), clazz);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unmarshal agentic workflow model", e);
        }
    }

    @Override
    public Class<AgenticAwareWorkflowModel> getObjectClass() {
        return AgenticAwareWorkflowModel.class;
    }
}
