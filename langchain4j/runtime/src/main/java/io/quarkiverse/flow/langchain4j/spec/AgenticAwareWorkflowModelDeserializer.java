package io.quarkiverse.flow.langchain4j.spec;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

public class AgenticAwareWorkflowModelDeserializer extends StdDeserializer<AgenticAwareWorkflowModel> {

    private final AgenticAwareModelFactory factory;

    public AgenticAwareWorkflowModelDeserializer() {
        super(AgenticAwareWorkflowModel.class);
        this.factory = new AgenticAwareModelFactory();
    }

    @Override
    public AgenticAwareWorkflowModel deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        DefaultAgenticScope scope = AgenticScopeSerializer.fromJson(parser.getValueAsString());
        return (AgenticAwareWorkflowModel) factory.fromOther(scope);
    }
}
