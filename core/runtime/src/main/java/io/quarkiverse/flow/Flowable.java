package io.quarkiverse.flow;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Users wants to extend {@link Flow}.
 */
public interface Flowable {

    /**
     * Workflow descriptor you can describe via the CNCF Java SDK DSL.
     */
    Workflow descriptor();

    default WorkflowDefinitionId id() {
        return WorkflowDefinitionId.of(this.descriptor());
    }

}
