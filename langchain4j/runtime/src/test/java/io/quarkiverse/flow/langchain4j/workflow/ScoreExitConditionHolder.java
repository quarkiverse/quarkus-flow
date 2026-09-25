package io.quarkiverse.flow.langchain4j.workflow;

import dev.langchain4j.service.V;

/** Static exit condition invoked through the same reflection path as generated flows. */
public class ScoreExitConditionHolder {

    public static boolean exit(@V("score") Double score) {
        return score >= 0.8;
    }
}
