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
    CALL_HTTP(CallHTTP.class),
    CALL_ASYNC_API(CallAsyncAPI.class),
    CALL_GRPC(CallGRPC.class),
    CALL_OPEN_API(CallOpenAPI.class),
    CALL_A2A(CallA2A.class),
    CALL_FUNCTION(CallFunction.class),
    DO(DoTask.class),
    EMIT(EmitTask.class),
    FOR(ForTask.class),
    FORK(ForkTask.class),
    LISTEN(ListenTask.class),
    RAISE(RaiseTask.class),
    RUN(RunTask.class),
    SET(SetTask.class),
    SWITCH(SwitchTask.class),
    TRY(TryTask.class),
    WAIT(WaitTask.class);

    private final Class<? extends TaskBase> taskClass;

    TaskType(Class<? extends TaskBase> taskClass) {
        this.taskClass = taskClass;
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
}
