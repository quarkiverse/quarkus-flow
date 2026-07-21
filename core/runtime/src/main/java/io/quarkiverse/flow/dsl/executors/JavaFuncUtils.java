package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;

public class JavaFuncUtils {

    static Object safeObject(Object obj) {
        return obj instanceof WorkflowModel model ? model.asJavaObject() : obj;
    }

    static WorkflowPredicate from(WorkflowApplication application, Object predicate) {
        return application.expressionFactory().buildPredicate(ExpressionDescriptor.object(predicate));
    }

    @SuppressWarnings("unchecked")
    static <T> T convertT(WorkflowModel model, Optional<Class<T>> inputClass) {
        return model.isNull()
                ? null
                : inputClass
                        .map(
                                c -> model
                                        .as(c)
                                        .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                        "Model " + model + " cannot be converted to type " + c)))
                        .orElseGet(() -> (T) model.asJavaObject());
    }

    static Object convert(WorkflowModel model, Optional<Class<?>> inputClass) {
        if (model.isNull()) {
            return null;
        }
        return inputClass.isPresent()
                ? model
                        .as(inputClass.orElseThrow())
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Model " + model + " cannot be converted to type " + inputClass))
                : model.asJavaObject();
    }

    private JavaFuncUtils() {
    }
}
