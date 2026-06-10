package io.quarkiverse.flow.langchain4j.spec;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

public class AgenticAwareWorkflowModelSerializer extends StdSerializer<AgenticAwareWorkflowModel> {

    public AgenticAwareWorkflowModelSerializer() {
        super(AgenticAwareWorkflowModel.class);
    }

    @Override
    public void serialize(AgenticAwareWorkflowModel value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(AgenticScopeSerializer.toJson(value.as(DefaultAgenticScope.class).orElseThrow()));
    }
}
