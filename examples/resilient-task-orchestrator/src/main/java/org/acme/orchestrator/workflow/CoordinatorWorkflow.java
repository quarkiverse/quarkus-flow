package org.acme.orchestrator.workflow;

import java.util.Collection;
import java.util.List;

import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.BuildTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.forEach;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.forEachItem;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

/**
 * Coordinator Workflow - orchestrates the build pipeline.
 * <p>
 * Pattern: Thin orchestrator that:
 * 1. Decomposes build spec into tasks
 * 2. Emits task events for each task (choreography, not orchestration)
 * 3. Each task is handled by separate TaskWorkflow instance
 * <p>
 * This design enables:
 * - Independent task execution (fault isolation)
 * - Parallel task processing
 * - Easy resume (tasks are independent workflows)
 */
@ApplicationScoped
public class CoordinatorWorkflow extends Flow {
    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorWorkflow.class);

    @Override
    public Workflow descriptor() {
        return workflow("build-coordinator")
                .tasks(
                        // 1. Decompose build spec into individual tasks
                        function("decompose", (BuildSpec spec) -> {
                            LOG.info("Decomposing build spec for project: {}", spec.projectName());
                            List<BuildTask> tasks = spec.tasks().stream()
                                    .map(taskName -> new BuildTask(
                                            spec.projectName() + "-" + taskName,
                                            taskName,
                                            spec.projectName(),
                                            spec.gitRef()))
                                    .toList();
                            LOG.info("Created {} tasks: {}", tasks.size(),
                                    tasks.stream().map(BuildTask::id).toList());
                            return tasks;
                        }, BuildSpec.class),
                        forEach((Collection<BuildTask> buildTasks) -> buildTasks,
                                emitJson("org.acme.build.task.started", BuildTask.class)
                                        .inputFrom("$item")))
                .build();
    }
}
