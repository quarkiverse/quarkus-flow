package io.quarkiverse.flow.internal;

public record WorkflowInvoker(String beanClassName, String methodName, String[] parameterTypeNames, String kind) {

}
