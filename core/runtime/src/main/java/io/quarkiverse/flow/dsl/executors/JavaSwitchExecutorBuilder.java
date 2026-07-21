package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;

import io.quarkiverse.flow.dsl.types.utils.TaskPredicate;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.executors.SwitchExecutor.SwitchExecutorBuilder;

public class JavaSwitchExecutorBuilder extends SwitchExecutorBuilder {

    protected JavaSwitchExecutorBuilder(
            WorkflowMutablePosition position, SwitchTask task, WorkflowDefinition definition) {
        super(position, task, definition);
    }

    @Override
    protected Optional<WorkflowPredicate> buildFilter(SwitchItem item) {
        Object predicate = TaskPredicate.predicate(task, item.getName());
        return predicate != null
                ? Optional.of(JavaFuncUtils.from(application, predicate))
                : super.buildFilter(item);
    }
}
