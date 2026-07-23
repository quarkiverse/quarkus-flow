package io.quarkiverse.flow.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.DoTask;
import io.serverlessworkflow.api.types.EmitEventDefinition;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.EmitTaskConfiguration;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.EventSource;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ForkTaskConfiguration;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.SubscriptionIterator;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.Workflow;

class EmitEventSourceInjectorTest {

    private static final String DEFAULT_SOURCE = "acme:order-processor:1.0.0";

    @Test
    @DisplayName("applies_default_source_to_top_level_emit_without_source")
    void applies_default_source_to_top_level_emit_without_source() {
        TaskItem emit = emit("emit", null);
        Workflow workflow = new Workflow().withDo(List.of(emit));

        EmitEventSourceInjector.applyDefaultSource(workflow, DEFAULT_SOURCE);

        assertThat(sourceOf(emit)).isEqualTo(DEFAULT_SOURCE);
    }

    @Test
    @DisplayName("preserves_explicit_source")
    void preserves_explicit_source() {
        TaskItem emit = emit("emit", "https://petstore.com");
        Workflow workflow = new Workflow().withDo(List.of(emit));

        EmitEventSourceInjector.applyDefaultSource(workflow, DEFAULT_SOURCE);

        assertThat(sourceOf(emit)).isEqualTo("https://petstore.com");
    }

    @Test
    @DisplayName("applies_default_source_to_emits_nested_in_every_container")
    void applies_default_source_to_emits_nested_in_every_container() {
        TaskItem inDo = emit("inDo", null);
        TaskItem inFor = emit("inFor", null);
        TaskItem inFork = emit("inFork", null);
        TaskItem inTry = emit("inTry", null);
        TaskItem inCatch = emit("inCatch", null);
        TaskItem inListenForeach = emit("inForeach", null);

        Workflow workflow = new Workflow()
                .withDo(List.of(
                        doItem("do", inDo),
                        forItem("for", inFor),
                        forkItem("fork", inFork),
                        tryItem("try", inTry, inCatch),
                        listenForeachItem("listen", inListenForeach)));

        EmitEventSourceInjector.applyDefaultSource(workflow, DEFAULT_SOURCE);

        assertThat(List.of(
                sourceOf(inDo),
                sourceOf(inFor),
                sourceOf(inFork),
                sourceOf(inTry),
                sourceOf(inCatch),
                sourceOf(inListenForeach)))
                .containsOnly(DEFAULT_SOURCE);
    }

    @Test
    @DisplayName("is_noop_for_null_or_blank_default_and_null_workflow")
    void is_noop_for_null_or_blank_default_and_null_workflow() {
        TaskItem emit = emit("emit", null);
        Workflow workflow = new Workflow().withDo(List.of(emit));

        EmitEventSourceInjector.applyDefaultSource(workflow, null);
        EmitEventSourceInjector.applyDefaultSource(workflow, "  ");
        EmitEventSourceInjector.applyDefaultSource(null, DEFAULT_SOURCE);

        assertThat(sourceOf(emit)).isNull();
    }

    @Test
    @DisplayName("skips_emit_without_with_block_without_error")
    void skips_emit_without_with_block_without_error() {
        // emit configuration with an event definition but no 'with' properties
        EmitTask emit = new EmitTask()
                .withEmit(new EmitTaskConfiguration().withEvent(new EmitEventDefinition()));
        TaskItem item = new TaskItem("emit", new Task().withEmitTask(emit));
        Workflow workflow = new Workflow().withDo(List.of(item));

        EmitEventSourceInjector.applyDefaultSource(workflow, DEFAULT_SOURCE);

        assertThat(emit.getEmit().getEvent().getWith()).isNull();
    }

    // ----- model builders -----

    private static TaskItem emit(String name, String sourceOrNull) {
        EventProperties props = new EventProperties().withType("com.acme.test.v1");
        if (sourceOrNull != null) {
            props.withSource(new EventSource()
                    .withUriTemplate(new UriTemplate().withLiteralUri(URI.create(sourceOrNull))));
        }
        EmitTask emit = new EmitTask()
                .withEmit(new EmitTaskConfiguration().withEvent(new EmitEventDefinition().withWith(props)));
        return new TaskItem(name, new Task().withEmitTask(emit));
    }

    private static TaskItem doItem(String name, TaskItem... children) {
        return new TaskItem(name, new Task().withDoTask(new DoTask().withDo(List.of(children))));
    }

    private static TaskItem forItem(String name, TaskItem... children) {
        return new TaskItem(name, new Task().withForTask(new ForTask().withDo(List.of(children))));
    }

    private static TaskItem forkItem(String name, TaskItem... children) {
        return new TaskItem(name,
                new Task().withForkTask(
                        new ForkTask().withFork(new ForkTaskConfiguration().withBranches(List.of(children)))));
    }

    private static TaskItem tryItem(String name, TaskItem tryChild, TaskItem catchChild) {
        return new TaskItem(name,
                new Task().withTryTask(new TryTask()
                        .withTry(List.of(tryChild))
                        .withCatch(new TryTaskCatch().withDo(List.of(catchChild)))));
    }

    private static TaskItem listenForeachItem(String name, TaskItem child) {
        return new TaskItem(name,
                new Task().withListenTask(
                        new ListenTask().withForeach(new SubscriptionIterator().withDo(List.of(child)))));
    }

    // ----- assertions -----

    private static String sourceOf(TaskItem item) {
        EventProperties props = item.getTask().getEmitTask().getEmit().getEvent().getWith();
        EventSource source = props.getSource();
        if (source == null) {
            return null;
        }
        UriTemplate template = source.getUriTemplate();
        if (template != null) {
            return template.getLiteralUri() != null
                    ? template.getLiteralUri().toString()
                    : template.getLiteralUriTemplate();
        }
        return source.getRuntimeExpression();
    }
}
