package io.quarkiverse.flow;

public final class MethodRef {
    final String ownerClass;
    final String methodName;
    final boolean isStatic;

    MethodRef(String ownerClass, String methodName, String sig, boolean isStatic) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.isStatic = isStatic;
    }
}
