package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import io.serverlessworkflow.api.types.CallA2A;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.DoTask;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.RaiseTask;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.WaitTask;

public enum TaskType {
    CALL_HTTP(CallHTTP.class, "call_http"),
    CALL_ASYNCAPI(CallAsyncAPI.class, "call_asyncapi"),
    CALL_GRPC(CallGRPC.class, "call_grpc"),
    CALL_OPENAPI(CallOpenAPI.class, "call_openapi"),
    CALL_A2A(CallA2A.class, "call_a2a"),
    CALL_FUNCTION(CallFunction.class, "call_function"),
    DO(DoTask.class, "do"),
    EMIT(EmitTask.class, "emit"),
    FOR(ForTask.class, "for"),
    FORK(ForkTask.class, "fork"),
    LISTEN(ListenTask.class, "listen"),
    RAISE(RaiseTask.class, "raise"),
    RUN(RunTask.class, "run"),
    SET(SetTask.class, "set"),
    SWITCH(SwitchTask.class, "switch"),
    TRY(TryTask.class, "try"),
    WAIT(WaitTask.class, "wait");

    private final Class<? extends TaskBase> taskClass;
    private final String value;

    TaskType(Class<? extends TaskBase> taskClass, String value) {
        this.taskClass = taskClass;
        this.value = value;
    }

    private static final Map<Class<? extends TaskBase>, TaskType> BY_CLASS = new ConcurrentHashMap<>();

    static {
        for (TaskType type : values()) {
            BY_CLASS.put(type.taskClass, type);
        }
    }

    static TaskType fromTask(TaskBase taskBase) {
        if (taskBase == null) {
            throw new IllegalArgumentException("taskBase cannot be null");
        }
        TaskType type = BY_CLASS.get(taskBase.getClass());
        if (type == null) {
            throw new NoSuchElementException("TaskBase: " + taskBase.getClass() + " is not recognized.");
        }
        return type;
    }

    @Override
    public String toString() {
        return value;
    }
}
