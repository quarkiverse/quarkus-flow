package io.quarkiverse.flow.dsl.executors;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.LoopFunction;
import io.quarkiverse.flow.dsl.types.LoopFunctionIndex;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.executors.CallFunctionExecutorBuilder;
import io.serverlessworkflow.impl.executors.CallableTaskFactory;

@SuppressWarnings("unchecked")
public class JavaCallFunctionBuilder extends CallFunctionExecutorBuilder {

    private static final Logger logger = LoggerFactory.getLogger(JavaCallFunctionBuilder.class);

    @Override
    public int priority() {
        return DEFAULT_PRIORITY - 10;
    }

    @Override
    public CallableTaskFactory init(
            CallFunction task, WorkflowDefinition definition, WorkflowMutablePosition position) {
        if (CallJava.JAVA_CALL_KEY.equals(task.getCall())) {
            if (task.getWith() == null) {
                throw new IllegalArgumentException(
                        "At least one key "
                                + CallJava.FUNCTION_NAME_KEY
                                + " is expected as Java function argument");
            }
            Map<String, Object> props = task.getWith().getAdditionalProperties();
            Object obj = props.get(CallJava.FUNCTION_NAME_KEY);
            if (obj == null) {
                throw new IllegalArgumentException(
                        "Missing required Java function argument '" + CallJava.FUNCTION_NAME_KEY + "'");
            }
            Optional<Class<?>> input = (Optional<Class<?>>) props.getOrDefault(CallJava.INPUT_CLASS_KEY, Optional.empty());
            Optional<Class<?>> output = (Optional<Class<?>>) props.getOrDefault(CallJava.OUTPUT_CLASS_KEY, Optional.empty());
            if (obj instanceof ContextFunction fn) {
                return () -> new JavaContextFunctionCallExecutor(input, output, fn);
            } else if (obj instanceof FilterFunction fn) {
                return () -> new JavaFilterFunctionCallExecutor(input, output, fn);
            } else if (obj instanceof LoopFunction loop) {
                return () -> new JavaLoopFunctionCallExecutor(
                        loop, (String) props.get(CallJava.VAR_NAME_KEY), input, output);
            } else if (obj instanceof LoopFunctionIndex loop) {
                return () -> new JavaLoopFunctionIndexCallExecutor(
                        loop,
                        (String) props.get(CallJava.VAR_NAME_KEY),
                        (String) props.get(CallJava.INDEX_NAME_KEY),
                        input,
                        output);

            } else if (obj instanceof Function fn) {
                return () -> new JavaFunctionCallExecutor(input, output, fn);
            } else if (obj instanceof Consumer consumer) {
                return () -> new JavaConsumerCallExecutor(input, consumer);
            } else {
                throw new UnsupportedOperationException("Unrecognized function " + obj);
            }
        } else {
            logger.info("Calling regular function handler for task call {}", task.getCall());
            return super.init(task, definition, position);
        }
    }
}
