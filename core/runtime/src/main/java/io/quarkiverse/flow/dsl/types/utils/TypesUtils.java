package io.quarkiverse.flow.dsl.types.utils;

import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskMetadata;

public class TypesUtils {

    private TypesUtils() {
    }

    /** Metadata entry name for the DSL’s “when”/“if” predicate. */
    public static final String IF_PREDICATE = "if_predicate";

    /** Metadata entry name for event until predicate */
    public static final String UNTIL_PRED_NAME = "until";

    public static TaskMetadata initMetadata(TaskBase task) {
        TaskMetadata metadata = task.getMetadata();
        if (metadata == null) {
            metadata = new TaskMetadata();
            task.setMetadata(metadata);
        }
        return metadata;
    }
}
