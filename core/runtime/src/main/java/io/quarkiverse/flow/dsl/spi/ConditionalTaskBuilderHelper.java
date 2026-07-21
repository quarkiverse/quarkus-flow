package io.quarkiverse.flow.dsl.spi;

import io.quarkiverse.flow.dsl.types.utils.TypesUtils;
import io.serverlessworkflow.api.types.TaskBase;

class ConditionalTaskBuilderHelper {

    private ConditionalTaskBuilderHelper() {
    }

    static void setMetadata(TaskBase task, Object predicate) {
        TypesUtils.initMetadata(task).setAdditionalProperty(TypesUtils.IF_PREDICATE, predicate);
    }
}
