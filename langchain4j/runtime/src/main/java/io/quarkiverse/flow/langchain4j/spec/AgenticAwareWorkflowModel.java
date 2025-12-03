package io.quarkiverse.flow.langchain4j.spec;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.serverlessworkflow.impl.WorkflowModel;

public class AgenticAwareWorkflowModel implements WorkflowModel {

    private final AgenticScope agenticScope;
    private final WorkflowModel delegate;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgenticAwareWorkflowModel(AgenticScope agenticScope, WorkflowModel delegate) {
        this.agenticScope = agenticScope;
        this.delegate = delegate;
    }

    @Override
    public <T> Optional<T> as(Class<T> clazz) {
        Optional<T> converted = convert(clazz);
        if (converted.isPresent()) {
            return converted;
        }
        return delegate.as(clazz);
    }

    @SuppressWarnings("unchecked")
    protected <T> Optional<T> convert(Class<T> clazz) {
        if (agenticScope != null) {
            if (clazz.isAssignableFrom(AgenticScope.class)) {
                return Optional.of(clazz.cast(agenticScope));
            }

            if (clazz == JsonNode.class) {
                Map<String, Object> state = agenticScope.state();
                JsonNode node = mapper.valueToTree(state);
                return Optional.of(clazz.cast(node));
            }

            if (clazz == Map.class) {
                return (Optional<T>) asMap();
            }

            Object state = agenticScope.state();
            if (clazz.isInstance(state)) {
                return Optional.of(clazz.cast(state));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> asBoolean() {
        return delegate.asBoolean();
    }

    @Override
    public Collection<WorkflowModel> asCollection() {
        return delegate.asCollection();
    }

    @Override
    public Optional<String> asText() {
        return delegate.asText();
    }

    @Override
    public Optional<OffsetDateTime> asDate() {
        return delegate.asDate();
    }

    @Override
    public Optional<Number> asNumber() {
        return delegate.asNumber();
    }

    @Override
    public Optional<Map<String, Object>> asMap() {
        if (agenticScope != null) {
            return Optional.of(agenticScope.state());
        }
        return delegate.asMap();
    }

    @Override
    public Object asJavaObject() {
        return agenticScope;
    }

    @Override
    public Class<?> objectClass() {
        return delegate.objectClass();
    }
}
