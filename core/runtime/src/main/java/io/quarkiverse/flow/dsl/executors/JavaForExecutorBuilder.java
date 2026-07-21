package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.executors.JavaFuncUtils.safeObject;

import java.util.Collection;
import java.util.Optional;

import io.quarkiverse.flow.dsl.types.LoopPredicate;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndex;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexContext;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexFilter;
import io.quarkiverse.flow.dsl.types.TypedFunction;
import io.quarkiverse.flow.dsl.types.utils.ForTaskFunction;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.executors.ForExecutor.ForExecutorBuilder;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;

public class JavaForExecutorBuilder extends ForExecutorBuilder {

    protected JavaForExecutorBuilder(
            WorkflowMutablePosition position, ForTask task, WorkflowDefinition definition) {
        super(position, task, definition);
    }

    @Override
    protected Optional<WorkflowPredicate> buildWhileFilter() {
        final Object whilePred = ForTaskFunction.getWhilePredicate(task);
        Optional<Class<?>> whileClass = ForTaskFunction.getWhileClass(task);
        String varName = task.getFor().getEach();
        String indexName = task.getFor().getAt();
        if (whilePred instanceof LoopPredicateIndexFilter pred) {
            return Optional.of(
                    (w, t, n) -> {
                        Object item = safeObject(t.variables().get(varName));
                        return pred.test(
                                JavaFuncUtils.convert(n, whileClass),
                                item,
                                (Integer) safeObject(t.variables().get(indexName)),
                                w,
                                t);
                    });
        } else if (whilePred instanceof LoopPredicate pred) {
            return Optional.of(
                    (w, t, n) -> {
                        Object item = safeObject(t.variables().get(varName));
                        return pred.test(JavaFuncUtils.convert(n, whileClass), item);
                    });
        } else if (whilePred instanceof LoopPredicateIndexContext pred) {
            return Optional.of(
                    (w, t, n) -> {
                        Object item = safeObject(t.variables().get(varName));
                        return pred.test(
                                JavaFuncUtils.convert(n, whileClass),
                                item,
                                (Integer) safeObject(t.variables().get(indexName)),
                                w);
                    });
        } else if (whilePred instanceof LoopPredicateIndex pred) {
            return Optional.of(
                    (w, t, n) -> {
                        Object item = safeObject(t.variables().get(varName));
                        return pred.test(
                                JavaFuncUtils.convert(n, whileClass),
                                item,
                                (Integer) safeObject(t.variables().get(indexName)));
                    });
        }
        return super.buildWhileFilter();
    }

    protected WorkflowValueResolver<Collection<?>> buildCollectionFilter() {
        Object inCollection = collectionFilterObject();
        return inCollection != null
                ? application
                        .expressionFactory()
                        .resolveCollection(ExpressionDescriptor.object(inCollection))
                : super.buildCollectionFilter();
    }

    private Object collectionFilterObject() {
        return ForTaskFunction.getForClass(task)
                .<Object> map(forClass -> typedCollectionFunction(forClass))
                .orElse(ForTaskFunction.getInCollection(task));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object typedCollectionFunction(Class<?> forClass) {
        return new TypedFunction(ForTaskFunction.getInCollection(task), forClass);
    }
}
