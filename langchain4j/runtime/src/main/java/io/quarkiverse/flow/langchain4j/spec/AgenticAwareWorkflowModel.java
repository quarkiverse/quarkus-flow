package io.quarkiverse.flow.langchain4j.spec;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.serverlessworkflow.impl.AbstractWorkflowModel;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.jackson.JsonUtils;

public class AgenticAwareWorkflowModel extends AbstractWorkflowModel {

    private final AgenticScope agenticScope;
    private final WorkflowModel delegate;

    public AgenticAwareWorkflowModel(AgenticScope agenticScope, WorkflowModel delegate) {
        this.agenticScope = agenticScope;
        this.delegate = delegate;
    }

    protected <T> Optional<T> convert(Class<T> clazz) {
        if (agenticScope != null) {
            if (AgenticScope.class.isAssignableFrom(clazz)) {
                return Optional.of(clazz.cast(agenticScope));
            }

            Object state = agenticScope.state();
            if (state != null && state.getClass().isAssignableFrom(clazz)) {
                return Optional.of(clazz.cast(state));
            }

            if (JsonNode.class.isAssignableFrom(clazz)) {
                return Optional.of(clazz.cast(JsonUtils.fromValue(agenticScope.state())));
            }
        }
        return delegate.as(clazz);
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
        return agenticScope != null ? agenticScope : delegate.asJavaObject();
    }

    @Override
    public Class<?> objectClass() {
        return agenticScope != null ? AgenticScope.class : delegate.objectClass();
    }
}
