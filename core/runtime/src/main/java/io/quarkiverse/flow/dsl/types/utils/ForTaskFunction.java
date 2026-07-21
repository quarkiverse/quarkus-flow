package io.quarkiverse.flow.dsl.types.utils;

import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.flow.dsl.types.LoopPredicate;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndex;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexContext;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexFilter;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.TaskMetadata;

public class ForTaskFunction {

    private ForTaskFunction() {
    }

    public static final String WHILE_PREDICATE = "whilePredicate";
    public static final String WHILE_CLASS = "whileClass";
    public static final String ITEM_CLASS = "itemClass";
    public static final String FOR_CLASS = "forClass";
    public static final String COLLECTION = "inCollection";

    public static <T, V> ForTask withWhile(ForTask task, LoopPredicate<T, V> whilePredicate) {
        return withWhile(task, whilePredicate, Optional.empty(), Optional.empty());
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicate<T, V> whilePredicate, Class<T> modelClass) {
        return withWhile(task, whilePredicate, Optional.ofNullable(modelClass), Optional.empty());
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicate<T, V> whilePredicate, Class<T> modelClass, Class<V> itemClass) {
        return withWhile(
                task, whilePredicate, Optional.ofNullable(modelClass), Optional.ofNullable(itemClass));
    }

    public static <T, V> ForTask withWhile(ForTask task, LoopPredicateIndex<T, V> whilePredicate) {
        return withWhile(task, whilePredicate, Optional.empty(), Optional.empty());
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicateIndex<T, V> whilePredicate, Class<T> modelClass) {
        return withWhile(task, whilePredicate, Optional.ofNullable(modelClass), Optional.empty());
    }

    public static <T, V> ForTask withWhile(
            ForTask task,
            LoopPredicateIndex<T, V> whilePredicate,
            Class<T> modelClass,
            Class<V> itemClass) {
        return withWhile(
                task, whilePredicate, Optional.ofNullable(modelClass), Optional.ofNullable(itemClass));
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicateIndexContext<T, V> whilePredicate) {
        Optional<MethodType> methodType = ReflectionUtils.methodType(whilePredicate);
        return withWhile(
                task,
                whilePredicate,
                methodType.map(m -> m.parameterType(0)),
                methodType.map(m -> m.parameterType(1)));
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicateIndexContext<T, V> whilePredicate, Class<T> modelClass) {
        return withWhile(
                task,
                whilePredicate,
                Optional.ofNullable(modelClass),
                ReflectionUtils.methodType(whilePredicate).map(m -> m.parameterType(1)));
    }

    public static <T, V> ForTask withWhile(
            ForTask task,
            LoopPredicateIndexContext<T, V> whilePredicate,
            Class<T> modelClass,
            Class<V> itemClass) {
        return withWhile(
                task, whilePredicate, Optional.ofNullable(modelClass), Optional.ofNullable(itemClass));
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicateIndexFilter<T, V> whilePredicate) {
        Optional<MethodType> methodType = ReflectionUtils.methodType(whilePredicate);
        return withWhile(
                task,
                whilePredicate,
                methodType.map(m -> m.parameterType(0)),
                methodType.map(m -> m.parameterType(1)));
    }

    public static <T, V> ForTask withWhile(
            ForTask task, LoopPredicateIndexFilter<T, V> whilePredicate, Class<T> modelClass) {
        return withWhile(
                task,
                whilePredicate,
                Optional.ofNullable(modelClass),
                ReflectionUtils.methodType(whilePredicate).map(m -> m.parameterType(1)));
    }

    public static <T, V> ForTask withWhile(
            ForTask task,
            LoopPredicateIndexFilter<T, V> whilePredicate,
            Class<T> modelClass,
            Class<V> itemClass) {
        return withWhile(
                task, whilePredicate, Optional.ofNullable(modelClass), Optional.ofNullable(itemClass));
    }

    private static <T, V> ForTask withWhile(
            ForTask forTask,
            Object whilePredicate,
            Optional<Class<?>> modelClass,
            Optional<Class<?>> itemClass) {
        TypesUtils.initMetadata(forTask)
                .withAdditionalProperty(WHILE_PREDICATE, whilePredicate)
                .withAdditionalProperty(WHILE_CLASS, modelClass)
                .withAdditionalProperty(ITEM_CLASS, itemClass);
        return forTask;
    }

    public static <T, V> ForTask withCollection(
            ForTask forTask, Function<T, Collection<V>> collection) {
        return withCollection(forTask, collection, null);
    }

    public static <T, V> ForTask withCollection(
            ForTask forTask, Function<T, Collection<V>> collection, Class<T> colArgClass) {
        TaskMetadata metadata = TypesUtils.initMetadata(forTask);
        metadata.withAdditionalProperty(COLLECTION, collection);
        metadata.withAdditionalProperty(FOR_CLASS, Optional.ofNullable(colArgClass));
        ForTaskConfiguration forConfig = forTask.getFor();
        if (forConfig == null) {
            forConfig = new ForTaskConfiguration();
            forTask.setFor(forConfig);
        }
        if (forConfig.getIn() == null) {
            forConfig.setIn("Handling item collection with metadata key " + ForTaskFunction.COLLECTION);
        }
        return forTask;
    }

    public static Object getWhilePredicate(ForTask task) {
        return task.getMetadata() == null
                ? null
                : task.getMetadata().getAdditionalProperties().get(WHILE_PREDICATE);
    }

    @SuppressWarnings("unchecked")
    public static Optional<Class<?>> getWhileClass(ForTask task) {
        return task.getMetadata() == null
                ? Optional.empty()
                : (Optional<Class<?>>) task.getMetadata()
                        .getAdditionalProperties()
                        .getOrDefault(WHILE_CLASS, Optional.empty());
    }

    @SuppressWarnings("unchecked")
    public static Optional<Class<?>> getForClass(ForTask task) {
        return task.getMetadata() == null
                ? Optional.empty()
                : (Optional<Class<?>>) task.getMetadata().getAdditionalProperties().getOrDefault(FOR_CLASS, Optional.empty());
    }

    @SuppressWarnings("unchecked")
    public static Optional<Class<?>> getItemClass(ForTask task) {
        return task.getMetadata() == null
                ? Optional.empty()
                : (Optional<Class<?>>) task.getMetadata().getAdditionalProperties().getOrDefault(ITEM_CLASS, Optional.empty());
    }

    @SuppressWarnings("unchecked")
    public static Function<?, Collection<?>> getInCollection(ForTask task) {
        return task.getMetadata() == null
                ? null
                : (Function<?, Collection<?>>) task.getMetadata().getAdditionalProperties().get(COLLECTION);
    }
}
