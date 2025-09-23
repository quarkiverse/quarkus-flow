package io.quarkiverse.flow;

public record FlowKey(String className, String methodName) {

    @Override
    public String toString() {
        return className + "#" + methodName;
    }
}
