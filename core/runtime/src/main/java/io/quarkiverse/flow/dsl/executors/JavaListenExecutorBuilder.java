package io.quarkiverse.flow.dsl.executors;

import io.quarkiverse.flow.dsl.types.utils.TaskPredicate;
import io.quarkiverse.flow.dsl.types.utils.TypesUtils;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.Until;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.executors.ListenExecutor.ListenExecutorBuilder;

public class JavaListenExecutorBuilder extends ListenExecutorBuilder {

    protected JavaListenExecutorBuilder(
            WorkflowMutablePosition position, ListenTask task, WorkflowDefinition definition) {
        super(position, task, definition);
    }

    @Override
    protected WorkflowPredicate buildUntilPredicate(Until until) {
        Object predicate = TaskPredicate.predicate(task, TypesUtils.UNTIL_PRED_NAME);
        return predicate != null
                ? JavaFuncUtils.from(application, predicate)
                : super.buildUntilPredicate(until);
    }
}
