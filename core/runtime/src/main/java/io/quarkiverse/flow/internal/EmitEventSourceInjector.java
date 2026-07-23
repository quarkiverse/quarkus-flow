package io.quarkiverse.flow.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.EmitTaskConfiguration;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.EventSource;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Applies a default CloudEvent {@code source} to every {@code emit} task in a workflow that does not already declare
 * its own source.
 * <p>
 * The traversal is fully recursive: {@code emit} tasks can be nested inside {@code do}, {@code fork} branches,
 * {@code try}/{@code catch} blocks, {@code for} loops, and {@code listen} {@code foreach} iterators. Existing sources
 * (literal or runtime expression) are never overwritten.
 */
public final class EmitEventSourceInjector {

    private EmitEventSourceInjector() {
    }

    /**
     * Injects {@code defaultSource} into every emit task of the given workflow that has an event {@code with} block
     * but no {@code source}. No-op when the workflow or default source is absent.
     *
     * @param workflow the workflow whose emit tasks are visited (mutated in place)
     * @param defaultSource the literal source to apply when an emit declares no source
     */
    public static void applyDefaultSource(Workflow workflow, String defaultSource) {
        if (workflow == null || defaultSource == null || defaultSource.isBlank()) {
            return;
        }
        visit(workflow.getDo(), defaultSource);
    }

    private static void visit(List<TaskItem> items, String defaultSource) {
        if (items == null) {
            return;
        }
        for (TaskItem item : items) {
            if (item == null) {
                continue;
            }
            visit(item.getTask(), defaultSource);
        }
    }

    private static void visit(Task task, String defaultSource) {
        if (task == null) {
            return;
        }
        EmitTask emit = task.getEmitTask();
        if (emit != null) {
            applyToEmit(emit, defaultSource);
            return;
        }
        if (task.getDoTask() != null) {
            visit(task.getDoTask().getDo(), defaultSource);
            return;
        }
        ForTask forTask = task.getForTask();
        if (forTask != null) {
            visit(forTask.getDo(), defaultSource);
            return;
        }
        ForkTask forkTask = task.getForkTask();
        if (forkTask != null && forkTask.getFork() != null) {
            visit(forkTask.getFork().getBranches(), defaultSource);
            return;
        }
        TryTask tryTask = task.getTryTask();
        if (tryTask != null) {
            visit(tryTask.getTry(), defaultSource);
            if (tryTask.getCatch() != null) {
                visit(tryTask.getCatch().getDo(), defaultSource);
            }
            return;
        }
        ListenTask listenTask = task.getListenTask();
        if (listenTask != null && listenTask.getForeach() != null) {
            visit(listenTask.getForeach().getDo(), defaultSource);
        }
    }

    private static void applyToEmit(EmitTask emit, String defaultSource) {
        EmitTaskConfiguration configuration = emit.getEmit();
        if (configuration == null || configuration.getEvent() == null) {
            return;
        }
        EventProperties properties = configuration.getEvent().getWith();
        if (properties == null || properties.getSource() != null) {
            return;
        }
        properties.setSource(literalSource(defaultSource));
    }

    private static EventSource literalSource(String value) {
        EventSource source = new EventSource();
        try {
            source.withUriTemplate(new UriTemplate().withLiteralUri(new URI(value)));
        } catch (URISyntaxException ex) {
            source.withUriTemplate(new UriTemplate().withLiteralUriTemplate(value));
        }
        return source;
    }
}
