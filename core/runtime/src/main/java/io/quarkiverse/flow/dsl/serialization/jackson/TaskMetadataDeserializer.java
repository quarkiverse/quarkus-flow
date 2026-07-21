package io.quarkiverse.flow.dsl.serialization.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.serverlessworkflow.api.types.TaskMetadata;

public class TaskMetadataDeserializer extends JsonDeserializer<TaskMetadata> {

    @Override
    public TaskMetadata deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            TaskMetadata result = new TaskMetadata();
            SerializationUtils.deserializeMap(p, ctxt, result.getAdditionalProperties());
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }
}
