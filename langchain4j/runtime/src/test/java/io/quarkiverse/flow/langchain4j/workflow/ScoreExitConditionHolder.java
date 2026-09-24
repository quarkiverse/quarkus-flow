package io.quarkiverse.flow.langchain4j.workflow;

import dev.langchain4j.service.V;

/**
 * Holder for a generated-style {@code @ExitCondition} static method used by
 * {@code buildLoopExitPredicate_toleratesMissingKeyOnFirstEvaluation} in
 * {@link FlowAgentServicesMockedTest}.
 * <p>
 * Must be a top-level public class so that {@link
 * io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow#buildLoopExitPredicate}
 * can invoke the method via reflection without an {@link IllegalAccessException}.
 */
public class ScoreExitConditionHolder {

    /**
     * Mimics a generated {@code @ExitCondition} method: reads {@code score} from scope.
     * When {@code score} is absent the first time, {@code buildLoopExitPredicate} catches
     * {@code MissingArgumentException} and retries with {@code null}, so the method sees
     * {@code null} and returns {@code false}. After the scope has {@code score = 0.9}
     * the predicate returns {@code true}.
     */
    public static boolean exit(@V("score") Double score) {
        return score != null && score >= 0.8;
    }
}
