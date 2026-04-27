package org.acme.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.BuildTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

/**
 * Coordinator Workflow - orchestrates the build pipeline.
 *
 * Pattern: Thin orchestrator that:
 * 1. Decomposes build spec into tasks
 * 2. Emits task events for each task (choreography, not orchestration)
 * 3. Each task is handled by separate TaskWorkflow instance
 *
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
                        // 1. Decompose build spec into individual tasks and emit events
                        function("decomposeAndEmit", (BuildSpec spec) -> {
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

                        // 2. ForEach task, emit event (handled by separate TaskWorkflow instances)
                        list -> list.forEach(j -> j
                                .collection((Collection<?> tasks) -> tasks)
                                .each("task")
                                .tasks(
                                        emitJson("taskStarted", "org.acme.build.task.started", BuildTask.class))),

                        // 3. Done - tasks run independently
                        consume("done", (Object ignored) -> {
                            LOG.info("All task events emitted, coordinator workflow complete");
                        }, Object.class))
                .build();
    }
}
