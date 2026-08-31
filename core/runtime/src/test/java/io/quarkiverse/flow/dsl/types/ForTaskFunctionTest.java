package io.quarkiverse.flow.dsl.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.types.utils.ForTaskFunction;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;

class ForTaskFunctionTest {

    // 'for.in' being required is validated at fluent DSL build time (FuncForTaskBuilder.build()).
    // These low-level accessors assume that invariant already holds, so a null 'for.in' still
    // surfaces as an NPE, just without a dedicated message.

    @Test
    void get_for_class_throws_when_for_in_is_null() {
        ForTask task = taskWithNullIn();

        assertThatNullPointerException().isThrownBy(() -> ForTaskFunction.getForClass(task));
    }

    @Test
    void get_in_collection_throws_when_for_in_is_null() {
        ForTask task = taskWithNullIn();

        assertThatNullPointerException().isThrownBy(() -> ForTaskFunction.getInCollection(task));
    }

    @Test
    void with_collection_populates_for_in_so_get_in_collection_succeeds() {
        ForTask task = new ForTask();
        // withCollection must set for.in before returning
        ForTaskFunction.withCollection(task, (List<String> items) -> items);

        assertThat(ForTaskFunction.getInCollection(task)).isNotNull();
    }

    @Test
    void with_collection_does_not_overwrite_existing_for_in() {
        ForTask task = new ForTask();
        // First call sets for.in
        ForTaskFunction.withCollection(task, (List<String> items) -> items);
        Object firstCollection = ForTaskFunction.getInCollection(task);

        // Second call must not overwrite the existing for.in
        ForTaskFunction.withCollection(task, (Collection<String> items) -> items);

        assertThat(ForTaskFunction.getInCollection(task)).isSameAs(firstCollection);
    }

    /** A ForTask whose {@code for} block exists but {@code for.in} is null. */
    private static ForTask taskWithNullIn() {
        ForTask task = new ForTask();
        task.setFor(new ForTaskConfiguration()); // for.in stays null
        return task;
    }
}
