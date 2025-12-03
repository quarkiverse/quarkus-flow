package io.quarkiverse.flow.langchain4j.spec;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.Map;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;

/**
 * WorkflowModelFactory that is AgenticScope-aware.
 * <p>
 * It wraps the default Jackson-based WorkflowModelFactory and only
 * intercepts LangChain4j AgenticScope inputs. Everything else is
 * delegated untouched.
 */
public class AgenticAwareModelFactory implements WorkflowModelFactory {

    private static final String DELEGATE_FACTORY_CLASS = JacksonModelFactory.class.getName();
    private final WorkflowModelFactory delegate;

    public AgenticAwareModelFactory() {
        this.delegate = discoverDelegate();
    }

    private WorkflowModelFactory discoverDelegate() {
        try {
            Class<?> clazz = Class.forName(DELEGATE_FACTORY_CLASS, true,
                    Thread.currentThread().getContextClassLoader());
            if (!WorkflowModelFactory.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException(
                        DELEGATE_FACTORY_CLASS + " does not implement WorkflowModelFactory");
            }
            @SuppressWarnings("unchecked")
            Constructor<? extends WorkflowModelFactory> ctor = (Constructor<? extends WorkflowModelFactory>) clazz
                    .getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot instantiate delegate WorkflowModelFactory: " + DELEGATE_FACTORY_CLASS, e);
        }
    }

    @Override
    public WorkflowModel fromOther(Object obj) {
        // Only intercept AgenticScope; everything else uses the delegate's behavior
        if (obj instanceof AgenticScope scope) {
            // Delegate model is built from the *state* (Map) of the scope
            WorkflowModel delegateModel = delegate.from(scope.state());
            return new AgenticAwareWorkflowModel(scope, delegateModel);
        }

        return delegate.fromOther(obj);
    }

    // -------- Full delegation for the rest of the API --------

    @Override
    public WorkflowModel combine(Map<String, WorkflowModel> workflowVariables) {
        return delegate.combine(workflowVariables);
    }

    @Override
    public WorkflowModelCollection createCollection() {
        return delegate.createCollection();
    }

    @Override
    public WorkflowModel from(boolean value) {
        return delegate.from(value);
    }

    @Override
    public WorkflowModel from(Number value) {
        return delegate.from(value);
    }

    @Override
    public WorkflowModel from(String value) {
        return delegate.from(value);
    }

    @Override
    public WorkflowModel from(CloudEvent ce) {
        return delegate.from(ce);
    }

    @Override
    public WorkflowModel from(CloudEventData ce) {
        return delegate.from(ce);
    }

    @Override
    public WorkflowModel from(OffsetDateTime value) {
        return delegate.from(value);
    }

    @Override
    public WorkflowModel from(Map<String, Object> map) {
        return delegate.from(map);
    }

    @Override
    public WorkflowModel fromNull() {
        return delegate.fromNull();
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY - 1;
    }
}
