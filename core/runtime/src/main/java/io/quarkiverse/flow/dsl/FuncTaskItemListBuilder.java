package io.quarkiverse.flow.dsl;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.spi.FuncDoFluent;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.fluent.spec.BaseTaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.TaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.WaitTaskBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowTaskBuilder;

public class FuncTaskItemListBuilder extends BaseTaskItemListBuilder<FuncTaskItemListBuilder>
        implements FuncDoFluent<FuncTaskItemListBuilder> {

    protected static final String TYPE_FUNCTION = "function";

    public FuncTaskItemListBuilder(final int listOffsetSize) {
        super(listOffsetSize);
    }

    public FuncTaskItemListBuilder(final List<TaskItem> list) {
        super(list);
    }

    @Override
    protected FuncTaskItemListBuilder self() {
        return this;
    }

    @Override
    protected FuncTaskItemListBuilder newItemListBuilder(int listOffsetSize) {
        return new FuncTaskItemListBuilder(listOffsetSize);
    }

    @Override
    public FuncTaskItemListBuilder function(String name, Consumer<FuncCallTaskBuilder> consumer) {
        name = this.defaultNameAndRequireConfig(name, consumer, TYPE_FUNCTION);
        final FuncCallTaskBuilder callTaskJavaBuilder = new FuncCallTaskBuilder();
        consumer.accept(callTaskJavaBuilder);
        return addTaskItem(new TaskItem(name, new Task().withCallTask(callTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder function(Consumer<FuncCallTaskBuilder> consumer) {
        return this.function(null, consumer);
    }

    @Override
    public FuncTaskItemListBuilder set(String name, Consumer<FuncSetTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_SET);
        final FuncSetTaskBuilder funcSetTaskBuilder = new FuncSetTaskBuilder();
        itemsConfigurer.accept(funcSetTaskBuilder);
        return this.addTaskItem(new TaskItem(name, new Task().withSetTask(funcSetTaskBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder set(String name, String expr) {
        return this.set(name, s -> s.expr(expr));
    }

    public FuncTaskItemListBuilder set(String name, Map<String, Object> map) {
        return this.set(name, s -> s.expr(map));
    }

    @Override
    public FuncTaskItemListBuilder emit(String name, Consumer<FuncEmitTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_EMIT);
        final FuncEmitTaskBuilder emitTaskJavaBuilder = new FuncEmitTaskBuilder();
        itemsConfigurer.accept(emitTaskJavaBuilder);
        return this.addTaskItem(
                new TaskItem(name, new Task().withEmitTask(emitTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder listen(
            String name, Consumer<FuncListenTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_LISTEN);
        final FuncListenTaskBuilder listenTaskJavaBuilder = new FuncListenTaskBuilder(this);
        itemsConfigurer.accept(listenTaskJavaBuilder);
        return this.addTaskItem(
                new TaskItem(name, new Task().withListenTask(listenTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder raise(
            String name, Consumer<FuncRaiseTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_RAISE);
        final FuncRaiseTaskBuilder raiseTaskJavaBuilder = new FuncRaiseTaskBuilder();
        itemsConfigurer.accept(raiseTaskJavaBuilder);
        return this.addTaskItem(
                new TaskItem(name, new Task().withRaiseTask(raiseTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder forEach(
            String name, Consumer<FuncForTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_FOR);
        final FuncForTaskBuilder forTaskJavaBuilder = new FuncForTaskBuilder();
        itemsConfigurer.accept(forTaskJavaBuilder);
        return this.addTaskItem(new TaskItem(name, new Task().withForTask(forTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder switchCase(
            String name, Consumer<FuncSwitchTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_SWITCH);
        final FuncSwitchTaskBuilder funcSwitchTaskBuilder = new FuncSwitchTaskBuilder();
        itemsConfigurer.accept(funcSwitchTaskBuilder);
        return this.addTaskItem(
                new TaskItem(name, new Task().withSwitchTask(funcSwitchTaskBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder fork(String name, Consumer<FuncForkTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_FORK);
        final FuncForkTaskBuilder forkTaskJavaBuilder = new FuncForkTaskBuilder();
        itemsConfigurer.accept(forkTaskJavaBuilder);
        return this.addTaskItem(
                new TaskItem(name, new Task().withForkTask(forkTaskJavaBuilder.build())));
    }

    @Override
    public FuncTaskItemListBuilder http(
            String name, Consumer<FuncCallHttpTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_HTTP);

        final FuncCallHttpTaskBuilder httpTaskJavaBuilder = new FuncCallHttpTaskBuilder();
        itemsConfigurer.accept(httpTaskJavaBuilder);

        final CallHTTP callHTTP = httpTaskJavaBuilder.build();
        final CallTask callTask = new CallTask();
        callTask.setCallHTTP(callHTTP);
        final Task task = new Task();
        task.setCallTask(callTask);

        return this.addTaskItem(new TaskItem(name, task));
    }

    @Override
    public FuncTaskItemListBuilder openapi(
            String name, Consumer<FuncCallOpenAPITaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_OPENAPI);

        final FuncCallOpenAPITaskBuilder openAPITaskBuilder = new FuncCallOpenAPITaskBuilder();
        itemsConfigurer.accept(openAPITaskBuilder);

        final CallOpenAPI callOpenAPI = openAPITaskBuilder.build();
        final CallTask callTask = new CallTask();
        callTask.setCallOpenAPI(callOpenAPI);
        final Task task = new Task();
        task.setCallTask(callTask);

        return this.addTaskItem(new TaskItem(name, task));
    }

    @Override
    public FuncTaskItemListBuilder grpc(
            String name, Consumer<FuncCallGrpcTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_GRPC);

        final FuncCallGrpcTaskBuilder grpcTaskBuilder = new FuncCallGrpcTaskBuilder();
        itemsConfigurer.accept(grpcTaskBuilder);

        final CallGRPC callGRPC = grpcTaskBuilder.build();
        final CallTask callTask = new CallTask();
        callTask.setCallGRPC(callGRPC);
        final Task task = new Task();
        task.setCallTask(callTask);

        return this.addTaskItem(new TaskItem(name, task));
    }

    @Override
    public FuncTaskItemListBuilder workflow(
            String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.addDelegatedWorkflow(name, itemsConfigurer, TYPE_WORKFLOW);
    }

    @Override
    public FuncTaskItemListBuilder workflow(Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.workflow(null, itemsConfigurer);
    }

    @Override
    public FuncTaskItemListBuilder subflow(
            String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.addDelegatedWorkflow(name, itemsConfigurer, TYPE_WORKFLOW);
    }

    @Override
    public FuncTaskItemListBuilder subflow(Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.subflow(null, itemsConfigurer);
    }

    private FuncTaskItemListBuilder addDelegatedWorkflow(
            String name, Consumer<WorkflowTaskBuilder> itemsConfigurer, String delegateType) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, delegateType);
        // Preserve the original offset when delegating to avoid duplicate task names
        // across appended .tasks(...) blocks
        final int effectiveOffset = this.getOffset() + this.mutableList().size();
        final TaskItemListBuilder delegate = new TaskItemListBuilder(effectiveOffset);
        delegate.workflow(name, itemsConfigurer);
        final List<TaskItem> taskItems = delegate.build();
        if (taskItems.size() != 1) {
            throw new IllegalStateException(
                    String.format(
                            "Expected %s delegate '%s' to build exactly 1 TaskItem, but got %d",
                            delegateType, name, taskItems.size()));
        }
        return addTaskItem(taskItems.get(0));
    }

    @Override
    public FuncTaskItemListBuilder tryCatch(
            String name, Consumer<FuncTryTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_TRY);
        final FuncTryTaskBuilder tryTaskBuilder = new FuncTryTaskBuilder();
        itemsConfigurer.accept(tryTaskBuilder);
        return this.addTaskItem(new TaskItem(name, new Task().withTryTask(tryTaskBuilder.build())));
    }

    public FuncTaskItemListBuilder wait(Consumer<WaitTaskBuilder> itemsConfigurer) {
        return wait(null, itemsConfigurer);
    }

    public FuncTaskItemListBuilder wait(String name, Consumer<WaitTaskBuilder> itemsConfigurer) {
        name = this.defaultNameAndRequireConfig(name, itemsConfigurer, TYPE_WAIT);
        final WaitTaskBuilder waitTaskBuilder = new WaitTaskBuilder();
        itemsConfigurer.accept(waitTaskBuilder);
        return this.addTaskItem(new TaskItem(name, new Task().withWaitTask(waitTaskBuilder.build())));
    }
}
