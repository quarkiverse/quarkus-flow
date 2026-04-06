package io.quarkiverse.flow.internal;

public record WorkflowApplicationInfo(String id, boolean ready, Throwable reasonDown) {

    /**
     * App is not ready
     */
    WorkflowApplicationInfo() {
        this(null, false, null);
    }

    /**
     * App is ready
     */
    WorkflowApplicationInfo(String id) {
        this(id, true, null);
    }

    /**
     * App is not ready given the reason
     */
    WorkflowApplicationInfo(Throwable reasonDown) {
        this(null, false, reasonDown);
    }

}
