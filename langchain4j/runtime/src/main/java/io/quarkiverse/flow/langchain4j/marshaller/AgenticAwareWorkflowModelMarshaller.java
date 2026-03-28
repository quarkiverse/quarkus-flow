package io.quarkiverse.flow.langchain4j.marshaller;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.flow.langchain4j.spec.AgenticAwareWorkflowModel;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.serverlessworkflow.impl.marshaller.CustomObjectMarshaller;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import io.serverlessworkflow.impl.model.jackson.JacksonModel;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;

public class AgenticAwareWorkflowModelMarshaller implements CustomObjectMarshaller<AgenticAwareWorkflowModel> {

    private static final JacksonModelFactory FACTORY = new JacksonModelFactory();

    @Override
    public void write(WorkflowOutputBuffer buffer, AgenticAwareWorkflowModel object) {
        try {
            JsonNode node = object.as(JsonNode.class).orElseGet(() -> JsonUtils.fromValue(object.asMap().orElse(null)));
            buffer.writeBytes(JsonUtils.mapper().writeValueAsBytes(node));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public AgenticAwareWorkflowModel read(WorkflowInputBuffer buffer, Class<? extends AgenticAwareWorkflowModel> clazz) {
        try {
            JsonNode node = JsonUtils.mapper().readTree(buffer.readBytes());
            WorkflowModel model = FACTORY.fromOther(node);
            JacksonModel delegate = (JacksonModel) model;
            return new AgenticAwareWorkflowModel(null, delegate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Class<AgenticAwareWorkflowModel> getObjectClass() {
        return AgenticAwareWorkflowModel.class;
    }
}
