package io.quarkiverse.flow.dsl.types.utils;

import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.quarkiverse.flow.dsl.types.TypedContextPredicate;
import io.quarkiverse.flow.dsl.types.TypedFilterPredicate;
import io.quarkiverse.flow.dsl.types.TypedPredicate;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskMetadata;

public class TaskPredicate {

    private static final String PREDICATE_KEY_PREFIX = "predicate-";

    private TaskPredicate() {
    }

    public static <T, V extends TaskBase> TaskMetadata withPredicate(
            V task, String name, Predicate<T> predicate) {
        return TypesUtils.initMetadata(task).withAdditionalProperty(concat(name), predicate);
    }

    public static <T, V extends TaskBase> V withPredicate(
            V task, String name, Predicate<T> predicate, Class<T> predicateClass) {
        TypesUtils.initMetadata(task)
                .withAdditionalProperty(
                        concat(name),
                        predicateClass == null ? predicate : new TypedPredicate<>(predicate, predicateClass));
        return task;
    }

    public static <T, V extends TaskBase> V withPredicate(
            V task, String name, ContextPredicate<T> predicate) {
        TypesUtils.initMetadata(task).withAdditionalProperty(concat(name), predicate);
        return task;
    }

    public static <T, V extends TaskBase> V withPredicate(
            V task, String name, ContextPredicate<T> predicate, Class<T> predicateClass) {
        TypesUtils.initMetadata(task)
                .withAdditionalProperty(
                        concat(name),
                        predicateClass == null
                                ? predicate
                                : new TypedContextPredicate<>(predicate, predicateClass));
        return task;
    }

    public static <T, V extends TaskBase> V withPredicate(
            V task, String name, FilterPredicate<T> predicate) {
        TypesUtils.initMetadata(task).withAdditionalProperty(concat(name), predicate);
        return task;
    }

    public static <T, V extends TaskBase> V withPredicate(
            V task, String name, FilterPredicate<T> predicate, Class<T> predicateClass) {
        TypesUtils.initMetadata(task)
                .withAdditionalProperty(
                        concat(name),
                        predicateClass == null
                                ? predicate
                                : new TypedFilterPredicate<>(predicate, predicateClass));
        return task;
    }

    public static Object predicate(TaskBase task, String name) {
        return task.getMetadata() != null
                ? task.getMetadata().getAdditionalProperties().get(concat(name))
                : null;
    }

    private static String concat(String name) {
        return PREDICATE_KEY_PREFIX + name;
    }
}
