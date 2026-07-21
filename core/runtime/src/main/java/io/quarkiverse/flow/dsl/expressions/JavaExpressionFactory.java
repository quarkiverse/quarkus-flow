package io.quarkiverse.flow.dsl.expressions;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import io.cloudevents.CloudEventData;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.quarkiverse.flow.dsl.types.TypedContextFunction;
import io.quarkiverse.flow.dsl.types.TypedContextPredicate;
import io.quarkiverse.flow.dsl.types.TypedFilterFunction;
import io.quarkiverse.flow.dsl.types.TypedFilterPredicate;
import io.quarkiverse.flow.dsl.types.TypedFunction;
import io.quarkiverse.flow.dsl.types.TypedPredicate;
import io.quarkiverse.flow.dsl.types.utils.TypesUtils;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskMetadata;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.expressions.AbstractExpressionFactory;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;
import io.serverlessworkflow.impl.expressions.ObjectExpression;

@SuppressWarnings("unchecked")
public class JavaExpressionFactory extends AbstractExpressionFactory {

    @Override
    public ObjectExpression buildExpression(ExpressionDescriptor descriptor) {
        Object value = descriptor.asObject();
        if (value instanceof Function func) {
            return (w, t, n) -> func.apply(n.asJavaObject());
        } else if (value instanceof TypedFunction func) {
            return (w, t, n) -> func.function().apply(convert(n, func.argClass()));
        } else if (value instanceof FilterFunction func) {
            return (w, t, n) -> func.apply(n.asJavaObject(), w, t);
        } else if (value instanceof TypedFilterFunction func) {
            return (w, t, n) -> func.function().apply(convert(n, func.argClass()), w, t);
        } else if (value instanceof ContextFunction func) {
            return (w, t, n) -> func.apply(n.asJavaObject(), w);
        } else if (value instanceof TypedContextFunction func) {
            return (w, t, n) -> func.function().apply(convert(n, func.argClass()), w);
        } else {
            return (w, t, n) -> value;
        }
    }

    private <T> T convert(WorkflowModel model, Class<T> argClass) {
        return model.isNull()
                ? null
                : model
                        .as(argClass)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Cannot convert model " + model.asJavaObject() + " to class" + argClass));
    }

    @Override
    public int priority(ExpressionDescriptor descriptor) {
        Object value = descriptor.asObject();
        if (value instanceof Function
                || value instanceof TypedFunction
                || value instanceof FilterFunction
                || value instanceof TypedFilterFunction
                || value instanceof ContextFunction
                || value instanceof TypedContextFunction
                || value instanceof Predicate
                || value instanceof TypedPredicate
                || value instanceof ContextPredicate
                || value instanceof TypedContextPredicate
                || value instanceof FilterPredicate
                || value instanceof TypedFilterPredicate
                || value instanceof Boolean) {
            return DEFAULT_PRIORITY - 500;
        } else if (descriptor.asString() == null) {
            return MIN_PRIORITY;
        } else {
            return DEFAULT_PRIORITY + 500;
        }
    }

    private WorkflowPredicate fromPredicate(Predicate pred) {
        return (w, t, n) -> pred.test(n.asJavaObject());
    }

    private WorkflowPredicate fromPredicate(TypedPredicate pred) {
        return (w, t, n) -> pred.pred().test(convert(n, pred.argClass()));
    }

    private WorkflowPredicate fromPredicate(ContextPredicate pred) {
        return (w, t, n) -> pred.test(n.asJavaObject(), w);
    }

    private WorkflowPredicate fromPredicate(TypedContextPredicate pred) {
        return (w, t, n) -> pred.predicate().test(convert(n, pred.argClass()), w);
    }

    private WorkflowPredicate fromPredicate(FilterPredicate pred) {
        return (w, t, n) -> pred.test(n.asJavaObject(), w, t);
    }

    private WorkflowPredicate fromPredicate(TypedFilterPredicate pred) {
        return (w, t, n) -> pred.predicate().test(convert(n, pred.argClass()), w, t);
    }

    @Override
    public Optional<WorkflowPredicate> buildIfFilter(TaskBase task) {
        TaskMetadata metadata = task.getMetadata();
        if (metadata != null) {
            Object obj = metadata.getAdditionalProperties().get(TypesUtils.IF_PREDICATE);
            if (obj instanceof Predicate pred) {
                return Optional.of(fromPredicate(pred));
            } else if (obj instanceof TypedPredicate pred) {
                return Optional.of(fromPredicate(pred));
            } else if (obj instanceof ContextPredicate pred) {
                return Optional.of(fromPredicate(pred));
            } else if (obj instanceof TypedContextPredicate pred) {
                return Optional.of(fromPredicate(pred));
            } else if (obj instanceof FilterPredicate pred) {
                return Optional.of(fromPredicate(pred));
            } else if (obj instanceof TypedFilterPredicate pred) {
                return Optional.of(fromPredicate(pred));
            }
        }
        return Optional.empty();
    }

    @Override
    public WorkflowPredicate buildPredicate(ExpressionDescriptor desc) {
        Object value = desc.asObject();
        if (value instanceof Predicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof TypedPredicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof ContextPredicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof TypedContextPredicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof FilterPredicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof TypedFilterPredicate pred) {
            return fromPredicate(pred);
        } else if (value instanceof Boolean bool) {
            return (w, f, n) -> bool;
        } else {
            throw new IllegalArgumentException("value should be a predicate or a boolean");
        }
    }

    @Override
    protected String toString(Object eval) {
        return asClass(eval, String.class);
    }

    @Override
    protected CloudEventData toCloudEventData(Object eval) {
        return asClass(eval, CloudEventData.class);
    }

    @Override
    protected OffsetDateTime toDate(Object eval) {
        return asClass(eval, OffsetDateTime.class);
    }

    @Override
    protected Map<String, Object> toMap(Object eval) {
        return asClass(eval, Map.class);
    }

    @Override
    protected Collection<?> toCollection(Object obj) {
        return asClass(obj, Collection.class);
    }

    private <T> T asClass(Object obj, Class<T> clazz) {
        return clazz.cast(obj);
    }
}
